package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 高速传送带方块实体：带速是普通传送带的 2 倍。
 * 其余逻辑（汇流、backpressure、间距约束）完全复用父类。
 */
public class FastTransportBeltBlockEntity extends TransportBeltBlockEntity {
    /** 每 tick 前进比例：0.188 = 普通带 0.094 的 2 倍（Q5 决定）。 */
    public static final float FAST_SPEED = 0.188f;

    public FastTransportBeltBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistry.FAST_TRANSPORT_BELT_BE.get(), pos, state);
    }

    protected FastTransportBeltBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public float getSpeed() {
        return FAST_SPEED;
    }
}
