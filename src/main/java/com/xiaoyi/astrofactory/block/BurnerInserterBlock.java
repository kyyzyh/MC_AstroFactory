package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 逻辑：每 N tick 从"输入面"相邻的容器/传送带取一个物品，
 * 再从"输出面"相邻的容器/传送带放入。
 * 输入面 = 朝向的反面；输出面 = 朝向。
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

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.BURNER_INSERTER_BE.get(), BurnerInserterBlockEntity::serverTick);
    }
}
