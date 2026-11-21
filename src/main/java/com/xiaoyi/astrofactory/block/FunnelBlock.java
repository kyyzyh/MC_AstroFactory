package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 漏斗 / 溜槽：1 格被动转移，<b>无 GUI、无电、无燃料</b>。
 *
 * <h2>为什么需要它</h2>
 * <p>没有漏斗时，玩家为了「机器出口掉进下方箱子」这个最简单的连接，
 * 被迫摆一个机械臂并给它喂煤 —— 荒谬。漏斗补上这个最低成本的连接。</p>
 *
 * <h2>和机械臂的分工</h2>
 * <ul>
 *   <li><b>漏斗</b>：紧邻、无过滤、被动、免费。解决「1 格落差」；</li>
 *   <li><b>机械臂</b>：跨格、可过滤、可限量、要烧煤。解决「策略性搬运」。</li>
 * </ul>
 *
 * <p>这和 Factorio 的「分流器 vs inserter」、Create 的「溜槽 vs 机械臂」
 * 是同一个分工：<b>免费的做简单事，要资源的做复杂事</b>。</p>
 *
 * <h2>为什么用 {@link Direction} 而不是 6 向</h2>
 * <p>漏斗朝向决定「往哪吐」，从「上方 + 朝向的反面」吸。这样单格漏斗
 * 就能表达「从机器侧面接、往下吐进箱子」这种最常见的产线收尾。</p>
 */
public class FunnelBlock extends Block {
    /** 吐出口朝向。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4.0, 10.0, 4.0, 12.0, 16.0, 12.0),
            Block.box(5.0, 4.0, 5.0, 11.0, 10.0, 11.0),
            Block.box(6.0, 0.0, 6.0, 10.0, 4.0, 10.0));

    /** 每次转移的物品数（漏斗是「小口慢倒」，不是整堆瞬移）。 */
    public static final int MOVE_PER_TICK = 1;
    /** 每多少 tick 尝试一次（4 tick = 每秒 5 次，肉眼可见的流动感）。 */
    public static final int INTERVAL = 4;

    public FunnelBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 默认朝玩家看的方向：站在箱子旁放漏斗，它自然朝下/朝向箱子
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * 被动转移逻辑（由 {@link #neighborChanged} 驱动，不需要 ticker）。
     *
     * <p><b>为什么不注册 BlockEntity</b>：漏斗没有状态（没有缓存、没有进度、
     * 没有燃料），纯函数式转移。Pipez 的核心性能设计就是「只有主动抽取的
     * 方块才需要 BE」—— 漏斗是被动的，所以零 BE 开销。</p>
     */
    public static void tryTransfer(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return;
        }
        Direction out = state.getValue(FACING);
        BlockPos outPos = pos.relative(out);

        // 1) 找可吐的目标
        Container target = containerAt(level, outPos);
        if (target == null) {
            return;
        }

        // 2) 从「上方」和「朝向的反面」找来源（不包含吐出口，避免自环）
        for (Direction src : new Direction[]{Direction.UP, out.getOpposite()}) {
            if (src == out) {
                continue;
            }
            Container source = containerAt(level, pos.relative(src));
            if (source == null) {
                continue;
            }
            if (moveOne(source, target)) {
                return;
            }
        }
    }

    private static Container containerAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        var be = level.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }

    /** 从 source 搬一个到 target，成功返回 true。 */
    private static boolean moveOne(Container source, Container target) {
        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (stack.isEmpty() || !source.canTakeItem(target, i, stack)) {
                continue;
            }
            int want = Math.min(MOVE_PER_TICK, stack.getCount());
            // 先确认放得下再取（和机械臂同一条铁律：不放就不抓）
            int fits = simulateInsert(target, stack, want);
            if (fits <= 0) {
                continue;
            }
            ItemStack taken = source.removeItem(i, fits);
            if (taken.isEmpty()) {
                continue;
            }
            int moved = insertInto(target, taken);
            if (moved < taken.getCount()) {
                // 理论上不会发生（上面已模拟过），兜底把剩下的塞回去
                ItemStack leftover = taken.copyWithCount(taken.getCount() - moved);
                insertInto(source, leftover);
            }
            source.setChanged();
            target.setChanged();
            return moved > 0;
        }
        return false;
    }

    private static int simulateInsert(Container target, ItemStack sample, int want) {
        int room = 0;
        for (int i = 0; i < target.getContainerSize() && room < want; i++) {
            if (!target.canPlaceItem(i, sample)) {
                continue;
            }
            ItemStack slot = target.getItem(i);
            if (slot.isEmpty()) {
                room += sample.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slot, sample)) {
                room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
        }
        return Math.min(room, want);
    }

    private static int insertInto(Container target, ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = 0; i < target.getContainerSize() && remaining > 0; i++) {
            if (!target.canPlaceItem(i, stack)) {
                continue;
            }
            ItemStack slot = target.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                int move = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                slot.grow(move);
                remaining -= move;
                target.setChanged();
            }
        }
        for (int i = 0; i < target.getContainerSize() && remaining > 0; i++) {
            if (!target.getItem(i).isEmpty() || !target.canPlaceItem(i, stack)) {
                continue;
            }
            int move = Math.min(remaining, stack.getMaxStackSize());
            target.setItem(i, stack.copyWithCount(move));
            remaining -= move;
            target.setChanged();
        }
        return stack.getCount() - remaining;
    }

    // ------------------------------------------------------------ 触发

    /**
     * 邻居变化时尝试转移。
     *
     * <p>26.3 的 {@code neighborChanged} 签名含 {@code Orientation}，
     * 且只对 6 个正交邻居触发。漏斗只需要「旁边放了箱子/机器」这一个信号。</p>
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        scheduleSelf(level, pos);
    }

    /** 被放置时也试一次，避免「放好但旁边没人变化」导致不工作。 */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        tryTransfer(level, pos, state);
        scheduleSelf(level, pos);
    }

    /**
     * 自调度下一次检查。
     *
     * <p><b>为什么用 scheduled tick 而不是 BlockEntity ticker</b>：
     * 机器产出物品时<b>不会</b>触发邻居变化（它只改自己的容器），
     * 所以纯事件驱动会漏掉「机器里出了新东西」这种情况。
     * 而 scheduled tick 让漏斗<b>零 BlockEntity 开销</b>地定期自检 ——
     * 这是 Pipez「只有主动抽取端才需要 BE」思路的变体：
     * 漏斗是被动的，所以它不需要 BE，只需要一个定时器。</p>
     */
    private static void scheduleSelf(Level level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, ModRegistry.FUNNEL.get())) {
            level.scheduleTick(pos, ModRegistry.FUNNEL.get(), INTERVAL);
        }
    }

    /** scheduled tick 回调：搬一次，然后排下一次。 */
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                        net.minecraft.util.RandomSource random) {
        tryTransfer(level, pos, state);
        // 只要漏斗还在，就一直自调度（无 BE，无内存泄漏风险）
        level.scheduleTick(pos, this, INTERVAL);
    }
}
