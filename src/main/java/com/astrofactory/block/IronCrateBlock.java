package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 铁箱：54 格大容量存储（需「物流学」科技）。
 */
public class IronCrateBlock extends CrateBlock {

    public static final MapCodec<IronCrateBlock> CODEC = simpleCodec(IronCrateBlock::new);

    public IronCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends IronCrateBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.astrofactory.blockentity.IronCrateBlockEntity(pos, state);
    }

    @Override
    protected String crateName() {
        return "iron_crate";
    }
}
