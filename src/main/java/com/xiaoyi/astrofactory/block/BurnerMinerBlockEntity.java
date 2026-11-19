package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;

import java.util.List;

/**
 * 采矿机方块实体：无限矿脉。
 *
 * 设计（异星工厂式）：
 * - 不依赖地形矿脉，固定周期产出一个物品
 * - 产物池按"矿种"配置，默认给铁矿/铜矿/煤/石头
 * - 产出后尝试推入 FACING 面相邻的容器或传送带；推不进去就积压在内部缓存
 * - 内部缓存有上限，满了就停机（可观测的 backpressure）
 */
public class BurnerMinerBlockEntity extends BlockEntity {
    /** 每 40 tick（2 秒）产出一个。 */
    public static final int PRODUCE_INTERVAL = 40;
    /** 内部缓存上限。 */
    public static final int BUFFER_SIZE = 16;

    /** 默认产物池：原矿，交给熔炼炉处理。 */
    private static final List<Item> ORE_POOL = List.of(
            Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD, Items.COAL, Items.COBBLESTONE);

    private int progress = 0;
    private int poolIndex = 0;
    private final ItemStack[] buffer = new ItemStack[BUFFER_SIZE];

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
        // 1. 推进生产进度
        if (progress < PRODUCE_INTERVAL) {
            progress++;
            if (progress >= PRODUCE_INTERVAL) {
                progress = 0;
                produce();
            }
        }

        // 2. 尝试把缓存推出去
        boolean pushed = flushOutput(level, pos, state);

        // 3. 更新 ACTIVE 状态（有产出 = 亮）；整机 4 格一起切，保证外观一致
        boolean active = hasBufferItems();
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

    private void produce() {
        if (bufferSpace() <= 0) {
            return; // 缓存满，停机
        }
        Item ore = ORE_POOL.get(poolIndex % ORE_POOL.size());
        poolIndex++;
        addToBuffer(new ItemStack(ore, 1));
        setChanged();
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

    private boolean hasBufferItems() {
        for (ItemStack stack : buffer) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
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
        output.putInt("PoolIndex", poolIndex);
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
        poolIndex = input.getIntOr("PoolIndex", 0);
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
