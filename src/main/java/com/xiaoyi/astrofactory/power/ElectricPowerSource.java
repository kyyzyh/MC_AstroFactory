package com.xiaoyi.astrofactory.power;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 电力动力源（规格 §5.5）：电动机器从范围内的电线杆取电。
 *
 * <p>最小实现：不做电压/损耗。每次 consume 需要固定能量，不够就停机，
 * GUI 显示"无电力"（规格 §5.4）。</p>
 */
public class ElectricPowerSource implements PowerSource {
    /** 每次 consume 需要的能量。 */
    public static final int ENERGY_PER_OP = 1;

    private final BlockEntity host;

    public ElectricPowerSource(BlockEntity host) {
        this.host = host;
    }

    @Override
    public boolean consume(boolean simulate) {
        Level level = host.getLevel();
        if (level == null) {
            return false;
        }
        IEnergyNode node = ElectricNetwork.findNode(level, host.getBlockPos());
        if (node == null || node.getEnergy() < ENERGY_PER_OP) {
            return false;
        }
        if (!simulate) {
            node.takeEnergy(ENERGY_PER_OP);
            if (level.getBlockEntity(host.getBlockPos()) != null) {
                // 节点的 setChanged 由节点自己负责
            }
        }
        return true;
    }

    @Override
    public boolean isPowered() {
        Level level = host.getLevel();
        if (level == null) {
            return false;
        }
        IEnergyNode node = ElectricNetwork.findNode(level, host.getBlockPos());
        return node != null && node.getEnergy() >= ENERGY_PER_OP;
    }

    @Override
    public String statusText() {
        Level level = host.getLevel();
        if (level == null) {
            return "no power";
        }
        IEnergyNode node = ElectricNetwork.findNode(level, host.getBlockPos());
        if (node == null) {
            return "no grid";
        }
        return "energy " + node.getEnergy() + "/" + node.getEnergyCapacity();
    }
}
