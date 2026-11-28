package com.astrofactory.block;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 二型采矿机（v0.8.9）：继承一型 {@link MinerBlock} 的全部放置校验/面板/朝向逻辑，
 * 仅替换方块实体为 {@link com.astrofactory.blockentity.MinerMk2BlockEntity}（4×4 区域采集）。
 */
public class MinerMk2Block extends MinerBlock {

    public MinerMk2Block(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.astrofactory.blockentity.MinerMk2BlockEntity(pos, state);
    }
}
