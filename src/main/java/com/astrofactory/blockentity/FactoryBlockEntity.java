package com.astrofactory.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 工厂机器方块实体的公共基类：统一处理 NBT 存读与每 tick 调度。
 */
public abstract class FactoryBlockEntity extends BlockEntity {

    protected FactoryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveData(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadData(tag, registries);
    }

    /** 子类把需要持久化的字段写进 tag。 */
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {}

    /** 子类从 tag 恢复字段。 */
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {}

    /** 服务端每 tick 调用。 */
    public void serverTick() {}
}
