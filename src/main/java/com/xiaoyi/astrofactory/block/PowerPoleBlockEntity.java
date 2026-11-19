package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.power.IEnergyNode;

/**
 * 电线杆方块实体：能量缓冲。
 *
 * <p>容量 200，够一台组装机跑一阵。蒸汽机注入、机器取出。</p>
 */
public class PowerPoleBlockEntity extends BlockEntity implements IEnergyNode {
    public static final int CAPACITY = 200;

    private int energy = 0;

    public PowerPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.POWER_POLE_BE.get(), pos, state);
    }

    @Override
    public int getEnergy() {
        return energy;
    }

    @Override
    public int getEnergyCapacity() {
        return CAPACITY;
    }

    @Override
    public void setEnergy(int amount) {
        this.energy = Math.max(0, Math.min(CAPACITY, amount));
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", energy);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy = input.getIntOr("Energy", 0);
    }
}
