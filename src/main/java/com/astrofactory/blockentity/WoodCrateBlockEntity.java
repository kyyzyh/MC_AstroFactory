package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 木箱：9 格，贫瘠开局唯一可用的存储（对应异星工厂 wooden chest）。
 */
public class WoodCrateBlockEntity extends CrateBlockEntity {

    public static final int SLOTS = 9;

    public WoodCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WOOD_CRATE.get(), pos, state, SLOTS);
    }
}
