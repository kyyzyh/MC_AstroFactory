package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 高速传送带方块实体：带速是普通传送带的 2 倍（0.1 / tick，约 10 tick 走一格）。
 * 其余逻辑（汇流、backpressure、间距约束）完全复用父类。
 */
public class FastConveyorBlockEntity extends ConveyorBlockEntity {
    /** 每 tick 前进比例：0.1 => 10 tick 走一格（约 2 格/秒）。 */
    public static final float FAST_SPEED = 0.10f;

    public FastConveyorBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistry.FAST_CONVEYOR_BE.get(), pos, state);
    }

    protected FastConveyorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public float getSpeed() {
        return FAST_SPEED;
    }
}
