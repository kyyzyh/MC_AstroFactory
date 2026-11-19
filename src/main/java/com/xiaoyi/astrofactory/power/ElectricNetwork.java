package com.xiaoyi.astrofactory.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * 电力网络工具（最小实现，规格 §5.5）。
 *
 * <p>不做电网拓扑求解。规则很简单：</p>
 * <ul>
 *   <li>电线杆自身是能量缓冲（{@link IEnergyNode}），并作为"供电中心"；</li>
 *   <li>任何电动机器只要在某个电线杆的 {@link #POLE_RANGE} 格曼哈顿/欧氏范围内就能取电；</li>
 *   <li>蒸汽机把发出的电直接灌进范围内的电线杆。</li>
 * </ul>
 *
 * <p>这样"断电线停机"的验收切片成立：拆掉电线杆，范围内的机器立刻没电。</p>
 */
public final class ElectricNetwork {
    private ElectricNetwork() {}

    /** 电线杆供电半径（规格：5 格）。 */
    public static final int POLE_RANGE = 5;

    /**
     * 在 pos 周围 POLE_RANGE 范围内找可用的能量节点，优先取能量最多的。
     *
     * @return 找到的节点，没有则 null
     */
    public static IEnergyNode findNode(Level level, BlockPos pos) {
        if (level == null) {
            return null;
        }
        IEnergyNode best = null;
        int bestEnergy = 0;
        // 逐格扫描立方体范围（5 格半径 = 11×11×11，可接受；机器不常 tick 到这一步）
        for (int dx = -POLE_RANGE; dx <= POLE_RANGE; dx++) {
            for (int dy = -POLE_RANGE; dy <= POLE_RANGE; dy++) {
                for (int dz = -POLE_RANGE; dz <= POLE_RANGE; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > POLE_RANGE) {
                        continue; // 用曼哈顿距离，近似圆形范围
                    }
                    BlockPos np = pos.offset(dx, dy, dz);
                    if (!level.isLoaded(np)) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(np);
                    if (be instanceof IEnergyNode node && node.getEnergy() > bestEnergy) {
                        best = node;
                        bestEnergy = node.getEnergy();
                    }
                }
            }
        }
        return best;
    }

    /** 把电注入范围内所有能量节点（蒸汽机发电用），返回实际注入量。 */
    public static int pushEnergy(Level level, BlockPos pos, int amount) {
        return pushEnergyFromCells(level, java.util.Set.of(pos), amount);
    }

    /**
     * 从整机所有格子向外找能量节点并注入（多方块：master 可能远离电线杆）。
     */
    public static int pushEnergyFromCells(Level level, java.util.Collection<BlockPos> ownCells, int amount) {
        if (level == null || amount <= 0) {
            return 0;
        }
        java.util.Set<BlockPos> own = ownCells instanceof java.util.Set<BlockPos> s
                ? s : new java.util.HashSet<>(ownCells);
        Set<BlockEntity> visited = new HashSet<>();
        int remaining = amount;

        for (BlockPos cell : own) {
            if (remaining <= 0) {
                break;
            }
            for (int dx = -POLE_RANGE; dx <= POLE_RANGE && remaining > 0; dx++) {
                for (int dy = -POLE_RANGE; dy <= POLE_RANGE && remaining > 0; dy++) {
                    for (int dz = -POLE_RANGE; dz <= POLE_RANGE && remaining > 0; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > POLE_RANGE) {
                            continue;
                        }
                        BlockPos np = cell.offset(dx, dy, dz);
                        if (own.contains(np) || !level.isLoaded(np)) {
                            continue;
                        }
                        BlockEntity be = level.getBlockEntity(np);
                        if (be instanceof IEnergyNode node && visited.add(be)) {
                            int moved = node.addEnergy(remaining);
                            if (moved > 0) {
                                be.setChanged();
                                remaining -= moved;
                            }
                        }
                    }
                }
            }
        }
        return amount - remaining;
    }
}
