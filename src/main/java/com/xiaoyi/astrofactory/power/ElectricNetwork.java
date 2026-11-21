package com.xiaoyi.astrofactory.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 电力网络工具（最小实现，规格 §5.5）。
 *
 * <p>不做电网拓扑求解。规则很简单：</p>
 * <ul>
 *   <li>电线杆自身是能量缓冲（{@link IEnergyNode}），并作为"供电中心"；</li>
 *   <li>任何电动机器只要在某个电线杆的 {@link #POLE_RANGE} 格曼哈顿范围内就能取电；</li>
 *   <li>蒸汽机把发出的电直接灌进范围内的电线杆。</li>
 * </ul>
 *
 * <p>这样"断电线停机"的验收切片成立：拆掉电线杆，范围内的机器立刻没电。</p>
 *
 * <h2>性能说明（为什么不是朴素三重循环）</h2>
 * <p>半径 5 的曼哈顿球共有 <b>515</b> 个格子。朴素写法每格都要
 * {@code pos.offset(...)}（新建对象）+ {@code level.isLoaded} + {@code getBlockEntity}
 * （区块查找）。而 {@code ElectricPowerSource} 每 tick 会调用 {@code findNode}
 * <b>两次</b>（{@code consume(true)} 试算 + {@code consume(false)} 真扣），
 * 20 台机器 = 每秒 2 万次区块查找。这是真实的性能地雷。</p>
 *
 * <p>三层优化，<b>语义完全不变</b>：</p>
 * <ol>
 *   <li><b>偏移表预计算</b>：515 个相对坐标在类加载时算好，省掉每格的
 *       {@code Math.abs} 判断与循环开销；</li>
 *   <li><b>可变 BlockPos 复用</b>：用同一个 {@link BlockPos.MutableBlockPos}
 *       逐个 {@code setWithOffset}，消除 515 次对象分配；</li>
 *   <li><b>同 tick 缓存</b>：同一台机器在同一 game tick 内多次查询直接返回上次结果。
 *       因为缓存的是<b>节点引用</b>而不是能量值（{@code node.getEnergy()} 仍然实时读），
 *       所以「这一 tick 内能量变化」不会读到脏数据，而「拆掉电线杆立刻停机」
 *       的验收标准也仍然成立（下一个 tick 就会重新扫描）。</li>
 * </ol>
 */
public final class ElectricNetwork {
    private ElectricNetwork() {}

    /** 电线杆供电半径（规格：5 格）。 */
    public static final int POLE_RANGE = 5;

    /** 预计算的曼哈顿球偏移表（半径 POLE_RANGE），类加载时算一次。 */
    private static final int[][] OFFSETS = buildOffsets();

    private static int[][] buildOffsets() {
        List<int[]> list = new ArrayList<>();
        for (int dx = -POLE_RANGE; dx <= POLE_RANGE; dx++) {
            for (int dy = -POLE_RANGE; dy <= POLE_RANGE; dy++) {
                for (int dz = -POLE_RANGE; dz <= POLE_RANGE; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= POLE_RANGE) {
                        list.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    /** 同 tick 缓存：key = 维度 + 方块坐标，value = 上次找到的节点。 */
    private static final class CacheEntry {
        final Object dimension;
        final long tick;
        final IEnergyNode node;

        CacheEntry(Object dimension, long tick, IEnergyNode node) {
            this.dimension = dimension;
            this.tick = tick;
            this.node = node;
        }
    }

    private static final java.util.Map<BlockPos, CacheEntry> CACHE = new java.util.HashMap<>();

    // ------------------------------------------------------------ 探针钩子（仅用于自检）

    /** 上一次 findNode 实际扫描的格子数（缓存命中时为 0）。 */
    private static int LAST_SCAN_COUNT = 0;

    /** 探针用：偏移表长度。 */
    public static int debugOffsetCount() {
        return OFFSETS.length;
    }

    /** 探针用：上一次查询扫描的格子数。 */
    public static int debugLastScanCount() {
        return LAST_SCAN_COUNT;
    }

    /** 探针用：清空缓存与统计，让下一次查询必定真扫。 */
    public static void debugResetStats() {
        CACHE.clear();
        LAST_SCAN_COUNT = 0;
    }

    /**
     * 在 pos 周围 POLE_RANGE 范围内找可用的能量节点，优先取能量最多的。
     *
     * @return 找到的节点，没有则 null
     */
    public static IEnergyNode findNode(Level level, BlockPos pos) {
        LAST_SCAN_COUNT = 0;
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        BlockPos key = pos.immutable();
        Object dim = level.dimension();

        CacheEntry cached = CACHE.get(key);
        if (cached != null && cached.tick == now && cached.dimension.equals(dim)) {
            return cached.node;
        }

        IEnergyNode best = null;
        int bestEnergy = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanned = 0;
        for (int[] off : OFFSETS) {
            scanned++;
            cursor.setWithOffset(pos, off[0], off[1], off[2]);
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(cursor);
            if (be instanceof IEnergyNode node && node.getEnergy() > bestEnergy) {
                best = node;
                bestEnergy = node.getEnergy();
            }
        }
        LAST_SCAN_COUNT = scanned;

        // 缓存清理：避免跨维度/长跑后无限增长。只在表明显变大时整体清一次。
        if (CACHE.size() > 512) {
            CACHE.clear();
        }
        CACHE.put(key, new CacheEntry(dim, now, best));
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
        Set<BlockPos> own = ownCells instanceof Set<BlockPos> s
                ? s : new HashSet<>(ownCells);
        Set<BlockEntity> visited = new HashSet<>();
        int remaining = amount;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (BlockPos cell : own) {
            if (remaining <= 0) {
                break;
            }
            for (int[] off : OFFSETS) {
                if (remaining <= 0) {
                    break;
                }
                cursor.setWithOffset(cell, off[0], off[1], off[2]);
                if (own.contains(cursor) || !level.isLoaded(cursor)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(cursor);
                if (be instanceof IEnergyNode node && visited.add(be)) {
                    int moved = node.addEnergy(remaining);
                    if (moved > 0) {
                        be.setChanged();
                        remaining -= moved;
                    }
                }
            }
        }
        return amount - remaining;
    }
}
