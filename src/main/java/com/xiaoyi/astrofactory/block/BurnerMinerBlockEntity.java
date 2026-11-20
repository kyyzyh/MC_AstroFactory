package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.machine.MachineOutput;
import com.xiaoyi.astrofactory.machine.OreVeinProspector;
import com.xiaoyi.astrofactory.multiblock.MultiBlockHelper;
import com.xiaoyi.astrofactory.power.FuelPowerSource;

/**
 * 采矿机方块实体：烧煤产出原矿。
 *
 * <p>设计（异星工厂式）：</p>
 * <ul>
 *   <li>燃料槽 1 格：燃烧臂从 FACING 背面送煤进来（规格 §5.3 唯一自动输出例外是矿口，
 *       燃料必须经机械臂）；</li>
 *   <li>固定周期产出一个物品，产物按脚下矿脉的矿种决定（规格 §5.6「矿脉枯竭」）；</li>
 *   <li>产出后尝试推入 FACING 面相邻的传送带或容器；推不进去就积压在内部缓存；</li>
 *   <li>缓存满了就停机（可观测的 backpressure）；</li>
 *   <li>烧煤：没有燃料预算就停机（规格红线：燃烧期吃煤）；</li>
 *   <li>脚下没有矿脉 / 矿脉已采空 → 停机，不再凭空产矿。</li>
 * </ul>
 *
 * <p>矿脉查找与扣减委托给 {@link OreVeinProspector}，输出推送委托给 {@link MachineOutput}。</p>
 */
public class BurnerMinerBlockEntity extends BlockEntity implements Container {
    /** 每 80 tick（4 秒）产出一个（规格 §5.2）。 */
    public static final int PRODUCE_INTERVAL = 80;
    /** 内部缓存上限。 */
    public static final int BUFFER_SIZE = 16;
    /** 燃料槽大小。 */
    public static final int FUEL_SLOT = 0;
    public static final int FUEL_CONTAINER_SIZE = 1;
    /** 机器边长（2×2 多方块）。 */
    private static final int SIZE = 2;

    private int progress = 0;
    private final ItemStack[] buffer = new ItemStack[BUFFER_SIZE];
    /** 燃料槽：燃烧臂往这里塞煤。 */
    private final SimpleContainer fuelContainer = new SimpleContainer(FUEL_CONTAINER_SIZE);
    private final FuelPowerSource power = new FuelPowerSource(fuelContainer, FUEL_SLOT);
    /** 矿脉查找器（自带位置缓存与重扫冷却）。 */
    private final OreVeinProspector prospector = new OreVeinProspector(SIZE, SIZE);

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
        Direction facing = state.getValue(BurnerMinerBlock.FACING);

        // 0. 前置条件：脚下要有矿脉、缓存没满。任一不满足就停机且不烧燃料
        //    （异星工厂式：钻机被背压堵住 / 矿挖完了，不该继续吃煤）。
        boolean canWork = bufferSpace() > 0 && prospector.available(level, pos, facing);

        // 1. 燃料检查：没燃料就停机（规格红线：燃烧期吃煤）
        boolean hasFuel = canWork && power.consume(true);

        // 2. 推进生产进度（有燃料才推进）
        if (hasFuel) {
            power.consume(false); // 实际消费 1 tick
            if (progress < PRODUCE_INTERVAL) {
                progress++;
                if (progress >= PRODUCE_INTERVAL) {
                    progress = 0;
                    produce(level, pos, facing);
                }
            }
        } else {
            progress = Math.max(0, progress - 1); // 缓慢回退，避免瞬间重启
        }

        // 3. 尝试把缓存推出去
        boolean pushed = flushOutput(level, pos, facing);

        // 4. 更新 ACTIVE 状态（正在挖 = 亮）；整机 4 格一起切，保证外观一致
        setActive(level, pos, state, hasFuel);

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

    // ------------------------------------------------------------ 生产

    /**
     * 尝试产出一个原矿。
     *
     * <p>扣减顺序：先扣矿脉储量再入缓存。缓存满时提前返回，不扣储量，
     * 避免背压状态下白白吃掉矿石。</p>
     */
    private void produce(Level level, BlockPos pos, Direction facing) {
        if (bufferSpace() <= 0) {
            return; // 缓存满，停机（不扣矿脉储量）
        }
        OreVeinBlock.OreKind kind = prospector.mineOne(level, pos, facing);
        if (kind == null) {
            return; // 脚下没矿或已采空
        }
        addToBuffer(OreVeinBlock.oreDrop(kind));
        setChanged();
        if (!level.isClientSide()) {
            awardMiningExp(level, pos);
        }
    }

    /** 给操作者（最近使用该机器的玩家）加采矿 EXP。 */
    private void awardMiningExp(Level level, BlockPos pos) {
        // 采矿机无直接操作者，按「最近 8 格内玩家」判定，找不到则不发
        // （避免无人值守时凭空涨经验）
        var players = level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(8.0));
        if (players.isEmpty()) {
            return;
        }
        var data = com.xiaoyi.astrofactory.level.PlayerLevel.of(players.get(0));
        data.totalMined++;
        data.addExp(1);
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
    private boolean flushOutput(Level level, BlockPos pos, Direction facing) {
        boolean changed = false;
        for (int dx = 0; dx < SIZE; dx++) {
            // dz = 1 是机器的正面那一排，再沿 FACING 往外一格就是出矿口
            BlockPos outPos = MultiBlockHelper.cellPos(pos, facing, dx, 1).relative(facing);
            for (int i = 0; i < buffer.length; i++) {
                if (buffer[i].isEmpty()) {
                    continue;
                }
                if (MachineOutput.pushOne(level, outPos, buffer[i].copyWithCount(1))) {
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

    /** ACTIVE 整机同步。 */
    private static void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.getValue(BurnerMinerBlock.ACTIVE) == active) {
            return;
        }
        for (BlockPos cell : MultiBlockHelper.allCells(pos, state.getValue(BurnerMinerBlock.FACING), SIZE)) {
            BlockState cellState = level.getBlockState(cell);
            if (cellState.getBlock() instanceof BurnerMinerBlock
                    && cellState.getValue(BurnerMinerBlock.ACTIVE) != active) {
                level.setBlock(cell, cellState.setValue(BurnerMinerBlock.ACTIVE, active),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
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
                flist.addChild().store("Stack", ItemStack.CODEC, stack);
            }
        }
        var list = output.childrenList("Buffer");
        for (ItemStack stack : buffer) {
            if (!stack.isEmpty()) {
                list.addChild().store("Stack", ItemStack.CODEC, stack);
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
