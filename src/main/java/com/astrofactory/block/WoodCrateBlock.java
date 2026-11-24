package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 木箱：9 格，贫瘠开局唯一可用的存储（不加科技锁）。
 */
public class WoodCrateBlock extends CrateBlock {

    public static final MapCodec<WoodCrateBlock> CODEC = simpleCodec(WoodCrateBlock::new);

    public WoodCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WoodCrateBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.astrofactory.blockentity.WoodCrateBlockEntity(pos, state);
    }
}
