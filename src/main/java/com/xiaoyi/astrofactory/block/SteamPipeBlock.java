package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 蒸汽管道：单格方块 + 蒸汽节点（规格 §5.5）。
 *
 * <p>不做管网拓扑：每 tick 与相邻节点均摊蒸汽，锅炉产蒸汽 → 管道逐格传递 → 蒸汽机取用。</p>
 */
public class SteamPipeBlock extends Block implements EntityBlock {
    /** 十字管道碰撞箱：中心接头 + 六个方向管段（与模型一致）。 */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0),
            Block.box(0.0, 6.0, 6.0, 5.0, 10.0, 10.0),
            Block.box(11.0, 6.0, 6.0, 16.0, 10.0, 10.0),
            Block.box(6.0, 6.0, 0.0, 10.0, 10.0, 5.0),
            Block.box(6.0, 6.0, 11.0, 10.0, 10.0, 16.0),
            Block.box(6.0, 0.0, 6.0, 10.0, 5.0, 10.0),
            Block.box(6.0, 11.0, 6.0, 10.0, 16.0, 10.0));

    public SteamPipeBlock(Properties properties) {
        super(properties);
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.STEAM_PIPE_BE.get(),
                SteamPipeBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> given, BlockEntityType<E> expected,
            BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
