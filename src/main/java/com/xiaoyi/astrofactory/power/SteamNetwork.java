package com.xiaoyi.astrofactory.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 蒸汽网络扩散工具（最小实现）。
 *
 * <p>不做管网拓扑、不做压力。每 tick 让每个节点与 6 个相邻节点均摊蒸汽，
 * 形成"水往低处流"的效果：锅炉不断产蒸汽 → 管道逐格传递 → 蒸汽机取用发电。</p>
 *
 * <p>为了防止蒸汽在管道里来回震荡，只把蒸汽从"更满"推向"更空"，且每次最多推一半差值。</p>
 *
 * <p><b>多方块</b>：锅炉/蒸汽机是多格机器，蒸汽必须从"整机边缘格"往外扩散，
 * 否则 master 的邻居全是自己的 dummy，蒸汽永远出不去。</p>
 */
public final class SteamNetwork {
    private SteamNetwork() {}

    private static final Direction[] DIRS = Direction.values();

    /** 单格节点扩散（管道）。 */
    public static boolean equalize(Level level, BlockPos pos, ISteamNode self) {
        return equalizeFromCells(level, Set.of(pos), self);
    }

    /**
     * 多方块节点扩散：从整机所有格子的外边界向外均摊蒸汽。
     *
     * @param ownCells 整台机器占据的全部格子（含 dummy）
     */
    public static boolean equalizeFromCells(Level level, Collection<BlockPos> ownCells, ISteamNode self) {
        Set<BlockPos> own = ownCells instanceof Set<BlockPos> s ? s : new HashSet<>(ownCells);
        boolean changed = false;
        if (self.getSteam() <= 0) {
            return false;
        }

        for (BlockPos cell : own) {
            if (self.getSteam() <= 0) {
                break;
            }
            for (Direction dir : DIRS) {
                BlockPos np = cell.relative(dir);
                if (own.contains(np)) {
                    continue; // 自己的格子，跳过
                }
                if (!level.isLoaded(np)) {
                    continue;
                }
                BlockEntity nbe = level.getBlockEntity(np);
                if (!(nbe instanceof ISteamNode other)) {
                    continue;
                }
                int mine = self.getSteam();
                int theirs = other.getSteam();
                if (mine <= theirs) {
                    continue; // 只从更满的推向更空的
                }
                int move = (mine - theirs) / 2;
                if (move <= 0) {
                    continue;
                }
                int moved = other.addSteam(move);
                if (moved > 0) {
                    self.setSteam(mine - moved);
                    if (nbe instanceof BlockEntity be) {
                        be.setChanged();
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }
}
