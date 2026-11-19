package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 传送带方块实体：维护带上的"在途物品"列表，每 tick 沿朝向推进。
 *
 * 核心模型（真连续移动）：
 * - 每个 ItemSlot 记录 item、progress(0..1 沿本格前进的比例)、lane(横向偏移，用于多列并行)
 * - 每 tick progress += speed；progress >= 1 时尝试移交给下一格（或本格后续位置）
 * - 汇流：多个上游同时推入时按 lane 分配；下游已满则阻塞（backpressure）
 * - 客户端同步：位置由 progress 插值渲染，无需逐 tick 发包
 */
public class TransportBeltBlockEntity extends BlockEntity {
    /** 每 tick 前进比例：0.094 => 约 10.6 tick 走一格（对齐异星工厂 1.875 格/秒）。 */
    public static final float SPEED = 0.094f;
    /** 并行 lane 数：规格 6.4 定为 2 条。 */
    public static final int LANES = 2;
    /** 同 lane 最小间距 0.25 格 => 每格每 lane 最多 4 个（规格 6.4）。 */
    public static final float MIN_GAP = 0.25f;
    /** 每条 lane 在一格内的容量上限 = 1 / MIN_GAP。 */
    public static final int PER_LANE_CAPACITY = 4;

    /** 在途物品。 */
    public static final class ItemSlot {
        public ItemStack stack;
        /** 0..1，沿 FACING 方向在本格内的前进比例。 */
        public float progress;
        /** 0..(LANES-1)，横向偏移索引。 */
        public int lane;

        public ItemSlot(ItemStack stack, float progress, int lane) {
            this.stack = stack;
            this.progress = progress;
            this.lane = lane;
        }

        public ItemSlot copy() {
            return new ItemSlot(this.stack.copy(), this.progress, this.lane);
        }
    }

    private final List<ItemSlot> items = new ArrayList<>();

    public TransportBeltBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistry.TRANSPORT_BELT_BE.get(), pos, state);
    }

    /** 供子类（高速传送带）指定自己的 BlockEntityType。 */
    protected TransportBeltBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                  BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public List<ItemSlot> getItems() {
        return items;
    }

    /** 每 tick 前进比例；子类可覆写以改变带速。 */
    public float getSpeed() {
        return SPEED;
    }

    // ------------------------------------------------------------ 服务端推进

    public static void serverTick(Level level, BlockPos pos, BlockState state, TransportBeltBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        if (items.isEmpty()) {
            return;
        }
        Direction facing = state.getValue(TransportBeltBlock.FACING);
        boolean changed = false;
        float speed = getSpeed();

        // 1. 推进
        for (ItemSlot slot : items) {
            slot.progress += speed;
            changed = true;
        }

        // 2. 处理到达末端的物品：尝试移交给下一格
        List<ItemSlot> leaving = new ArrayList<>();
        for (ItemSlot slot : items) {
            if (slot.progress >= 1.0f) {
                leaving.add(slot);
            }
        }
        if (!leaving.isEmpty()) {
            BlockPos nextPos = pos.relative(facing);
            BlockState nextState = level.getBlockState(nextPos);
            TransportBeltBlockEntity next = null;
            if (nextState.getBlock() instanceof TransportBeltBlock
                    && level.getBlockEntity(nextPos) instanceof TransportBeltBlockEntity nbe) {
                next = nbe;
            }
            for (ItemSlot slot : leaving) {
                boolean handedOver = false;
                if (next != null) {
                    handedOver = next.tryAcceptFromUpstream(slot.stack, slot.lane);
                }
                if (handedOver) {
                    items.remove(slot);
                } else {
                    // 下游阻塞：停在末端等待（不越过 1.0，避免视觉穿模）
                    slot.progress = 1.0f - speed;
                }
            }
        }

        // 3. 同列防重叠：按 progress 降序，保证前车间距
        enforceSpacing();

        if (changed) {
            setChanged();
        }
    }

    /** 上游移交入口：成功接收返回 true。 */
    public boolean tryAcceptFromUpstream(ItemStack stack, int preferredLane) {
        int lane = Math.max(0, Math.min(LANES - 1, preferredLane));
        if (laneCount(lane) >= PER_LANE_CAPACITY) {
            return false;
        }
        // 检查该列入口是否空闲
        for (ItemSlot slot : items) {
            if (slot.lane == lane && slot.progress < MIN_GAP) {
                return false;
            }
        }
        items.add(new ItemSlot(stack.copy(), 0.0f, lane));
        setChanged();
        return true;
    }

    private int laneCount(int lane) {
        int n = 0;
        for (ItemSlot slot : items) {
            if (slot.lane == lane) {
                n++;
            }
        }
        return n;
    }

    /** 外部（机械臂/采矿机）从带首投放。 */
    public boolean insertAtStart(ItemStack stack) {
        for (int lane = 0; lane < LANES; lane++) {
            if (tryAcceptFromUpstream(stack, lane)) {
                return true;
            }
        }
        return false;
    }

    /** 取走带尾（progress 最大）的物品。 */
    public ItemStack extractFromEnd() {
        ItemSlot best = null;
        for (ItemSlot slot : items) {
            if (best == null || slot.progress > best.progress) {
                best = slot;
            }
        }
        if (best == null) {
            return ItemStack.EMPTY;
        }
        items.remove(best);
        setChanged();
        return best.stack;
    }

    /** 取走最靠前、且已到达末端的物品（供下游机器取用）。 */
    public ItemStack extractReady() {
        ItemSlot best = null;
        for (ItemSlot slot : items) {
            if (slot.progress >= 0.999f && (best == null || slot.progress > best.progress)) {
                best = slot;
            }
        }
        if (best == null) {
            return ItemStack.EMPTY;
        }
        items.remove(best);
        setChanged();
        return best.stack;
    }

    private void enforceSpacing() {
        // 每列独立处理，按 progress 从大到小排列，前车至少领先 MIN_GAP
        for (int lane = 0; lane < LANES; lane++) {
            List<ItemSlot> laneItems = new ArrayList<>();
            for (ItemSlot slot : items) {
                if (slot.lane == lane) {
                    laneItems.add(slot);
                }
            }
            if (laneItems.size() < 2) {
                continue;
            }
            laneItems.sort((a, b) -> Float.compare(b.progress, a.progress));
            for (int i = 1; i < laneItems.size(); i++) {
                ItemSlot front = laneItems.get(i - 1);
                ItemSlot back = laneItems.get(i);
                float maxAllowed = front.progress - MIN_GAP;
                if (back.progress > maxAllowed) {
                    back.progress = Math.max(0.0f, maxAllowed);
                }
            }
        }
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.childrenList("Items");
        for (ItemSlot slot : items) {
            var child = list.addChild();
            child.store("Stack", ItemStack.CODEC, slot.stack);
            child.putFloat("Progress", slot.progress);
            child.putInt("Lane", slot.lane);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        for (ValueInput child : input.childrenListOrEmpty("Items")) {
            ItemStack stack = child.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            float progress = child.getFloatOr("Progress", 0.0f);
            int lane = child.getIntOr("Lane", 0);
            items.add(new ItemSlot(stack, progress, Math.max(0, Math.min(LANES - 1, lane))));
        }
    }
}
