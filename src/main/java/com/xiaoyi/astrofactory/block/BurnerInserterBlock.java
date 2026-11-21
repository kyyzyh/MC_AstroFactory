package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 机械臂：六向抓取/投放。
 *
 * <p>逻辑：每 N tick 从「输入面」相邻的容器/传送带按过滤规则取物品，
 * 再放到「输出面」相邻的容器/传送带。输入面 = 朝向的反面；输出面 = 朝向。</p>
 *
 * <p><b>方向由摆放决定</b>（和 Create 机械臂一样），不在 GUI 里选 ——
 * 摆放即配置，零点击成本。GUI 只放三个真正需要调的东西：
 * 过滤槽 + 白黑名单 + 数量档 + Keep N。</p>
 */
public class BurnerInserterBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** 水平朝向：底座 4..12 / 0..3 + 立柱 6..10 / 3..13。 */
    private static final VoxelShape HORIZONTAL_SHAPE = Shapes.or(
            Block.box(4.0, 0.0, 4.0, 12.0, 3.0, 12.0),
            Block.box(6.0, 3.0, 6.0, 10.0, 13.0, 10.0));

    /** 朝上/朝下：模型被 x 旋转 90 度后压成薄片，给一个扁平碰撞箱。 */
    private static final VoxelShape VERTICAL_SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 6.0, 12.0);

    public BurnerInserterBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 碰撞箱与模型一致：底座 + 立柱（不含伸出的细臂，避免挡路）。
     * 水平朝向时是中间那根 8×13×8 的柱子；竖直朝向时整体压到底座高度。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isHorizontal()) {
            return HORIZONTAL_SHAPE;
        }
        return VERTICAL_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerInserterBlockEntity(pos, state);
    }

    /** 右键打开配置界面（过滤 / 白黑名单 / 数量 / Keep N）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BurnerInserterBlockEntity inserter) {
            player.openMenu(new SimpleMenuProvider(
                    (windowId, inv, p) -> new com.xiaoyi.astrofactory.menu.InserterMenu(
                            windowId, inv, inserter.getContainer()),
                    Component.translatable("block.astrofactory.burner_inserter")));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.BURNER_INSERTER_BE.get(), BurnerInserterBlockEntity::serverTick);
    }
}
