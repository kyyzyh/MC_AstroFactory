package com.astrofactory.blockentity;

import com.astrofactory.config.AstroFactoryConfig;
import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 二型采矿机（v0.8.9）：继承一型 {@link MinerBlockEntity} 的全部区域采集与储量逻辑，
 * 仅覆盖面积（4×4）与数值。P0b 规则：不复制第二套机器逻辑。
 */
public class MinerMk2BlockEntity extends MinerBlockEntity {

    public MinerMk2BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINER_MK2.get(), pos, state,
                AstroFactoryConfig.MINER_MK2_CAPACITY.get(),
                AstroFactoryConfig.MINER_MK2_MAX_RECEIVE.get());
    }

    /** 二型区域采集边长：4×4。 */
    @Override
    protected int areaSize() {
        return AstroFactoryConfig.MINER_MK2_AREA.get();
    }

    @Override
    protected int fePerTick() {
        return AstroFactoryConfig.MINER_MK2_FE_PER_TICK.get();
    }

    @Override
    protected int cycleTicksBase() {
        return AstroFactoryConfig.MINER_MK2_CYCLE_TICKS.get();
    }

    @Override
    protected int energyCapacity() {
        return AstroFactoryConfig.MINER_MK2_CAPACITY.get();
    }

    @Override
    protected int maxReceive() {
        return AstroFactoryConfig.MINER_MK2_MAX_RECEIVE.get();
    }
}
