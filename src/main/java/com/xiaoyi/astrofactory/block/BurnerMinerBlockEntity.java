package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.power.FuelPowerSource;
import com.xiaoyi.astrofactory.power.PowerSource;

import java.util.List;

/**
 * 采矿机方块实体：烧煤产出原矿。
 *
 * 设计（异星工厂式）：
 * - 燃料槽 1 格：燃烧臂从 FACING 背面送煤进来（规格 §5.3 唯一自动输出例外是矿口，
 *   燃料必须经机械臂）
 * - 固定周期产出一个物品，产物按脚下矿脉的矿种决定（规格 §5.6「矿脉枯竭」）
 * - 产出后尝试推入 FACING 面相邻的传送带或容器；推不进去就积压在内部缓存
 * - 内部缓存有上限，满了就停机（可观测的 backpressure）
 * - 烧煤：没有燃料预算就停机（规格红线：燃烧期吃煤）
 * - 脚下没有矿脉 / 矿脉已采空 → 停机，不再凭空产矿（红线：矿脉会枯竭）
 */
public class BurnerMinerBlockEntity extends BlockEntity implements Container {
    /** 每 80 tick（4 秒）产出一个（规格 §5.2）。 */
    public static final int PRODUCE_INTERVAL = 80;
    /** 内部缓存上限。 */
    public static final int BUFFER_SIZE = 16;
    /** 燃料槽大小。 */
    public static final int FUEL_SLOT = 0;
    public static final int FUEL_CONTAINER_SIZE = 1;

    /**
     * 找矿脉的搜索范围：以 2×2 机器正下方为中心，向下最多找 {@link #ORE_SCAN_DEPTH} 格，
     * 水平方向在 2×2 覆盖范围内（含向外 {@link #ORE_SCAN_MARGIN} 格）。
     *
     * <p>规格 §5.6 的矿田是埋在地表下 1 格的单层圆盘，机器通常直接压在上面，
     * 所以向下找几格足够；给一点水平余量是为了容忍玩家把机器摆在矿田边缘。</p>
     */
    public static final int ORE_SCAN_DEPTH = 4;
    public static final int ORE_SCAN_MARGIN = 1;
    /** 找不到矿时重新扫描的冷却（tick）。 */
    public static final int SCAN_COOLDOWN = 20;

    private int progress = 0;
    /** 上次找到的矿脉位置（缓存，避免每 tick 全范围扫描）。 */
    private BlockPos cachedVeinPos = null;
    /** 重扫冷却计数。 */
    private int scanCooldown = 0;
    private final ItemStack[] buffer = new ItemStack[BUFFER_SIZE];
    /** 燃料槽：燃烧臂往这里塞煤。 */
    private final SimpleContainer fuelContainer = new SimpleContainer(FUEL_CONTAINER_SIZE);
    private final FuelPowerSource power = new FuelPowerSource(fuelContainer, FUEL_SLOT);

    public BurnerMinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BURNER_MINER_BE.get(), pos, state);
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = ItemStack.EMPTY;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BurnerMinerBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    /**
     * 区块加载时自修复多方块结构（规格 §5.1「区块重载自修复」）。
     * master 在但 dummy 缺 → 补；位置被占 → 整机清掉，不留幽灵。
     */
    @Override
    public void onLoad() {
        super.onLoad();
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof BurnerMinerBlock miner && state.getValue(BurnerMinerBlock.PART) == 0) {
            miner.repair(level, this.getBlockPos(), state);
        }
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        // 0. 前置条件：脚下要有矿脉、缓存没满。任一不满足就停机且不烧燃料
        //    （异星工厂式：钻机被背压堵住 / 矿挖完了，不该继续吃煤）。
        boolean canWork = bufferSpace() > 0 && veinAvailable(level, pos, state);

        // 1. 燃料检查：没燃料就停机（规格红线：燃烧期吃煤）
        boolean hasFuel = canWork && power.consume(true);

        // 2. 推进生产进度（有燃料才推进）
        if (hasFuel) {
            power.consume(false); // 实际消费 1 tick
            if (progress < PRODUCE_INTERVAL) {
                progress++;
                if (progress >= PRODUCE_INTERVAL) {
                    progress = 0;
                    produce(level, pos, state);
                }
            }
        } else {
            progress = Math.max(0, progress - 1); // 缓慢回退，避免瞬间重启
        }

        // 3. 尝试把缓存推出去
        boolean pushed = flushOutput(level, pos, state);

        // 4. 更新 ACTIVE 状态（正在挖 = 亮）；整机 4 格一起切，保证外观一致
        boolean active = hasFuel;
        if (state.getValue(BurnerMinerBlock.ACTIVE) != active) {
            for (BlockPos cell : com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                    .allCells(pos, state.getValue(BurnerMinerBlock.FACING), 2)) {
                BlockState cellState = level.getBlockState(cell);
                if (cellState.getBlock() instanceof BurnerMinerBlock
                        && cellState.getValue(BurnerMinerBlock.ACTIVE) != active) {
                    level.setBlock(cell, cellState.setValue(BurnerMinerBlock.ACTIVE, active),
                            Block.UPDATE_CLIENTS);
                }
            }
        }

        if (pushed) {
            setChanged();
        }
    }

    // ------------------------------------------------------------ Container 燃料槽
    // 采矿机无 GUI，但实现 Container（1 格燃料槽）让燃烧臂能往里塞煤。

    @Override
    public int getContainerSize() {
        return fuelContainer.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return fuelContainer.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return fuelContainer.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return fuelContainer.removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return fuelContainer.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        fuelContainer.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        fuelContainer.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return fuelContainer.stillValid(player);
    }

    @Override
    public void clearContent() {
        fuelContainer.clearContent();
    }

    /**
     * 尝试产出一个原矿。
     *
     * <p>规格 §5.6「矿脉枯竭」：产出必须来自脚下的真实矿脉。
     * 找不到矿脉（或已采空）就不产出——机器空转烧煤，但不出矿，
     * 这样玩家能观察到"矿挖完了"，必须搬机器去新矿田。</p>
     *
     * <p>扣减顺序：先扣矿脉储量再入缓存。缓存满时提前返回，不扣储量，
     * 避免背压状态下白白吃掉矿石。</p>
     */
    private void produce(Level level, BlockPos pos, BlockState state) {
        if (bufferSpace() <= 0) {
            return; // 缓存满，停机（不扣矿脉储量）
        }
        OreVeinBlock.OreKind kind = consumeFromVein(level, pos, state);
        if (kind == null) {
            return; // 脚下没矿或已采空
        }
        addToBuffer(OreVeinBlock.oreDrop(kind));
        setChanged();
    }

    /**
     * 从脚下矿脉扣 1 储量，返回该矿脉的矿种；没矿 / 采空返回 null。
     *
     * <p>优先用缓存位置（命中率高，省扫描）；缓存失效或该格已不是矿脉时重新扫描。</p>
     */
    private OreVeinBlock.OreKind consumeFromVein(Level level, BlockPos pos, BlockState state) {
        if (cachedVeinPos != null) {
            OreVeinBlock.OreKind kind = tryMine(level, cachedVeinPos);
            if (kind != null) {
                return kind;
            }
            cachedVeinPos = null;
        }
        for (BlockPos candidate : veinCandidates(pos, state)) {
            OreVeinBlock.OreKind kind = tryMine(level, candidate);
            if (kind != null) {
                cachedVeinPos = candidate.immutable();
                return kind;
            }
        }
        return null;
    }

    /**
     * 脚下是否还有可采的矿脉（只查询，不扣储量）。
     *
     * <p>用于每 tick 的工作前置判定：矿挖完了机器就该停，不该继续空转烧煤。</p>
     */
    private boolean veinAvailable(Level level, BlockPos pos, BlockState state) {
        if (cachedVeinPos != null && hasReserve(level, cachedVeinPos)) {
            return true;
        }
        // 没矿时限制重扫频率：每 20 tick 扫一次就够（玩家搬机器/矿脉消失不会瞬间发现，1 秒延迟可接受）
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_COOLDOWN;
        for (BlockPos candidate : veinCandidates(pos, state)) {
            if (hasReserve(level, candidate)) {
                cachedVeinPos = candidate.immutable();
                return true;
            }
        }
        cachedVeinPos = null;
        return false;
    }

    private static boolean hasReserve(Level level, BlockPos p) {
        if (!level.isLoaded(p)) {
            return false;
        }
        return level.getBlockState(p).getBlock() instanceof OreVeinBlock
                && level.getBlockEntity(p) instanceof OreVeinBlockEntity vein
                && vein.getReserve() > 0;
    }

    /** 尝试挖该位置 1 单位；成功返回矿种，失败返回 null。 */
    private static OreVeinBlock.OreKind tryMine(Level level, BlockPos p) {
        if (!(level instanceof ServerLevel serverLevel) || !level.isLoaded(p)) {
            return null;
        }
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof OreVeinBlock)) {
            return null;
        }
        if (!(level.getBlockEntity(p) instanceof OreVeinBlockEntity vein)) {
            return null;
        }
        if (vein.getReserve() <= 0) {
            return null;
        }
        OreVeinBlock.OreKind kind = st.getValue(OreVeinBlock.KIND);
        // 复用玩家挖掘的扣减 + 贫富度刷新逻辑，保证两条路径行为一致
        OreVeinBlock.mineOne(serverLevel, p, st);
        return kind;
    }

    /**
     * 生成候选矿脉位置：2×2 机器覆盖的每一列，从机器脚下往下 {@link #ORE_SCAN_DEPTH} 格，
     * 水平方向外扩 {@link #ORE_SCAN_MARGIN} 格。
     *
     * <p>顺序：先近后远、先上层后下层，保证靠得最近的矿先被挖到。</p>
     */
    private List<BlockPos> veinCandidates(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(BurnerMinerBlock.FACING);
        List<BlockPos> out = new java.util.ArrayList<>(64);
        // 2×2 覆盖的 dx/dz 范围，再外扩 margin
        int lo = -ORE_SCAN_MARGIN;
        int hi = 1 + ORE_SCAN_MARGIN;
        for (int depth = 1; depth <= ORE_SCAN_DEPTH; depth++) {
            for (int dx = lo; dx <= hi; dx++) {
                for (int dz = lo; dz <= hi; dz++) {
                    BlockPos base = com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                            .cellPos(pos, facing, dx, dz);
                    out.add(base.below(depth));
                }
            }
        }
        return out;
    }

    private int bufferSpace() {
        int space = 0;
        for (ItemStack stack : buffer) {
            if (stack.isEmpty()) {
                space += 64;
            } else {
                space += stack.getMaxStackSize() - stack.getCount();
            }
        }
        return space;
    }

    private void addToBuffer(ItemStack stack) {
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i].isEmpty()) {
                buffer[i] = stack;
                return;
            }
            if (ItemStack.isSameItemSameComponents(buffer[i], stack)
                    && buffer[i].getCount() < buffer[i].getMaxStackSize()) {
                buffer[i].grow(stack.getCount());
                return;
            }
        }
    }

    /**
     * 把缓存推入输出侧。返回是否有变化。
     *
     * <p>2×2 机器的出矿口是 FACING 面上的整条 2 格边；Q3 决定"输出 1 格宽"，
     * 因此按 dx = 0、1 顺序尝试，先试 dx=0（master 那一侧），满了再试 dx=1。</p>
     */
    private boolean flushOutput(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(BurnerMinerBlock.FACING);
        boolean changed = false;

        for (int dx = 0; dx < 2; dx++) {
            // dz = 1 是机器的正面那一排，再沿 FACING 往外一格就是出矿口
            BlockPos outPos = com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                    .cellPos(pos, facing, dx, 1).relative(facing);
            if (!level.isLoaded(outPos)) {
                continue;
            }
            BlockEntity target = level.getBlockEntity(outPos);
            if (target == null) {
                continue;
            }
            for (int i = 0; i < buffer.length; i++) {
                if (buffer[i].isEmpty()) {
                    continue;
                }
                ItemStack one = buffer[i].copyWithCount(1);
                boolean ok = false;
                if (target instanceof TransportBeltBlockEntity belt) {
                    ok = belt.insertAtStart(one);
                } else if (target instanceof Container container) {
                    ok = insertIntoContainer(container, one);
                }
                if (ok) {
                    buffer[i].shrink(1);
                    if (buffer[i].isEmpty()) {
                        buffer[i] = ItemStack.EMPTY;
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean insertIntoContainer(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                container.setChanged();
                return true;
            }
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, stack);
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", progress);
        output.putInt("BurnBudget", power.getBurnBudgetForSave());
        var flist = output.childrenList("Fuel");
        for (ItemStack stack : fuelContainer) {
            if (!stack.isEmpty()) {
                var child = flist.addChild();
                child.store("Stack", ItemStack.CODEC, stack);
            }
        }
        var list = output.childrenList("Buffer");
        for (ItemStack stack : buffer) {
            if (!stack.isEmpty()) {
                var child = list.addChild();
                child.store("Stack", ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getIntOr("Progress", 0);
        power.setBurnBudgetForLoad(input.getIntOr("BurnBudget", 0));
        fuelContainer.clearContent();
        int fi = 0;
        for (ValueInput child : input.childrenListOrEmpty("Fuel")) {
            if (fi >= FUEL_CONTAINER_SIZE) {
                break;
            }
            ItemStack stack = child.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                fuelContainer.setItem(fi++, stack);
            }
        }
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = ItemStack.EMPTY;
        }
        int i = 0;
        for (ValueInput child : input.childrenListOrEmpty("Buffer")) {
            if (i >= buffer.length) {
                break;
            }
            ItemStack stack = child.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                buffer[i++] = stack;
            }
        }
    }
}
