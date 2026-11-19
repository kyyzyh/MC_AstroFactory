package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 高速传送带：逻辑与普通传送带一致，只是带速翻倍。
 * 由科技 logistics_1 解锁。
 */
public class FastConveyorBlock extends ConveyorBlock {

    public FastConveyorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FastConveyorBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends ConveyorBlockEntity> getBlockEntityType() {
        return ModRegistry.FAST_CONVEYOR_BE.get();
    }
}
