package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 矿脉方块实体：存储当前储量。
 */
public class OreVeinBlockEntity extends BlockEntity {
    private int reserve = OreVeinBlock.DEFAULT_RESERVE;

    public OreVeinBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ORE_VEIN_BE.get(), pos, state);
    }

    /** 当前储量。 */
    public int getReserve() {
        return reserve;
    }

    /**
     * 扣减 1 单位储量，返回剩余值。
     * 调用方会据此更新 richness 或清掉方块。
     */
    public int decrementReserve() {
        reserve--;
        setChanged();
        return reserve;
    }

    /** 设置储量（矿田生成时用）。 */
    public void setReserve(int value) {
        this.reserve = value;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Reserve", reserve);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        reserve = input.getIntOr("Reserve", OreVeinBlock.DEFAULT_RESERVE);
    }
}
