package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 铁箱：54 格大容量存储（对应异星工厂 steel chest），需「物流学」科技。
 */
public class IronCrateBlockEntity extends CrateBlockEntity {

    public static final int SLOTS = 54;

    public IronCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_CRATE.get(), pos, state, SLOTS);
    }
}
