package com.xiaoyi.astrofactory.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 蒸汽动力源（规格 §5.5）：机器从相邻蒸汽节点抽取蒸汽驱动自己。
 *
 * <p>最小实现：不做压力/温度/损耗。每次 consume 需要固定蒸汽量，
 * 不够就停机。蒸汽机自身也用这个类来"消耗蒸汽"——它在消耗的同时向电网注入能量。</p>
 */
public class SteamPowerSource implements PowerSource {
    /** 每次 consume 需要的蒸汽量。 */
    public static final int STEAM_PER_OP = 1;

    private final BlockEntity host;

    public SteamPowerSource(BlockEntity host) {
        this.host = host;
    }

    @Override
    public boolean consume(boolean simulate) {
        Level level = host.getLevel();
        if (level == null) {
            return false;
        }
        BlockPos pos = host.getBlockPos();
        ISteamNode node = adjacentNode(level, pos);
        if (node == null || node.getSteam() < STEAM_PER_OP) {
            return false;
        }
        if (!simulate) {
            node.takeSteam(STEAM_PER_OP);
            if (level.getBlockEntity(pos) instanceof BlockEntity) {
                // 相邻节点的 setChanged 由调用方扩散处理
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
        ISteamNode node = adjacentNode(level, host.getBlockPos());
        return node != null && node.getSteam() >= STEAM_PER_OP;
    }

    @Override
    public String statusText() {
        Level level = host.getLevel();
        if (level == null) {
            return "no steam";
        }
        ISteamNode node = adjacentNode(level, host.getBlockPos());
        if (node == null) {
            return "no pipe";
        }
        return "steam " + node.getSteam() + "/" + node.getSteamCapacity();
    }

    private ISteamNode adjacentNode(Level level, BlockPos pos) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos np = pos.relative(dir);
            if (!level.isLoaded(np)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(np);
            if (be instanceof ISteamNode node && node.getSteam() > 0) {
                return node;
            }
        }
        return null;
    }
}
