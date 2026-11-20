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
 * 电线杆：单格能量节点（规格 §5.5）。5 格范围为电动机器供电。
 *
 * <p>蒸汽机把电灌进电线杆，电动机器（组装机/科研台/电动臂）在范围内取电。
 * 拆掉电线杆 → 范围内机器立刻没电（验收切片「断电线停机」）。</p>
 */
public class PowerPoleBlock extends Block implements EntityBlock {
    /** 电线杆碰撞箱：基座 + 木杆 + 顶部横担（与模型一致，可穿过横担下方）。 */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.0, 0.0, 5.0, 11.0, 3.0, 11.0),
            Block.box(6.0, 3.0, 6.0, 10.0, 16.0, 10.0),
            Block.box(1.0, 12.0, 7.0, 15.0, 14.0, 9.0));

    public PowerPoleBlock(Properties properties) {
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
        return new PowerPoleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        // 电线杆只做能量缓冲，无需 tick（能量由蒸汽机注入、机器取出）。
        return null;
    }
}
