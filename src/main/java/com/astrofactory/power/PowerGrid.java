package com.astrofactory.power;

import com.astrofactory.blockentity.PowerPoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 覆盖式电网（US-102）。
 *
 * <p><b>与电缆的区别：</b>电缆靠方块物理相连传导；电线杆是「覆盖式」——
 * 只要机器落在某根杆的半径内，就算接入该杆所在的网络，不需要方块挨着。</p>
 *
 * <p><b>性能红线（清单明确要求「禁止每 tick 逐机器遍历找电源」）：</b></p>
 * <ul>
 *   <li>网络拓扑（哪些杆连成一张网、每张网覆盖哪些机器）只在<b>结构变化</b>时
 *       重算一次，结果缓存在 {@link #GRIDS}；</li>
 *   <li>每 tick 只做「汇总发电 → 按需求分配」这一遍，且每张网只由
 *       {@code owner} 杆执行一次，不重复；</li>
 *   <li>机器不需要主动找电源，是杆主动把电推进去。</li>
 * </ul>
 *
 * <p>缓存按维度存放（WeakHashMap，维度卸载自动回收），结构变化调
 * {@link #invalidate} 整块作废。</p>
 */
public final class PowerGrid {

    /** 每张网每 tick 的分配预算上限（配置项可覆盖）。 */
    private static final int DEFAULT_MAX_TRANSFER = 2000;

    /** 维度 → (杆坐标 → 所属网络快照)。 */
    private static final Map<ServerLevel, Map<BlockPos, Grid>> GRIDS = new WeakHashMap<>();

    /** 一张电网的拓扑快照。 */
    public static final class Grid {
        /** 本网全部杆。 */
        public final Set<BlockPos> poles;
        /** 负责执行分配的杆（坐标最小，确定性，无需同步）。 */
        public final BlockPos owner;
        /** 本网覆盖到的发电设备（可 extract 的能量能力）。 */
        public final List<IEnergyStorage> sources;
        /** 本网覆盖到的耗电设备（可 receive 的能量能力）。 */
        public final List<IEnergyStorage> sinks;

        Grid(Set<BlockPos> poles, BlockPos owner,
             List<IEnergyStorage> sources, List<IEnergyStorage> sinks) {
            this.poles = poles;
            this.owner = owner;
            this.sources = sources;
            this.sinks = sinks;
        }
    }

    private PowerGrid() {}

    /** 结构变化（杆/机器放置移除）时作废该维度缓存。 */
    public static void invalidate(ServerLevel level) {
        GRIDS.remove(level);
    }

    /**
     * 取得 {@code origin} 所在电网（带缓存）。
     *
     * @return 电网快照；该位置不是杆则返回 null
     */
    public static Grid resolve(ServerLevel level, BlockPos origin) {
        Map<BlockPos, Grid> byPos = GRIDS.computeIfAbsent(level, k -> new HashMap<>());
        Grid cached = byPos.get(origin);
        if (cached != null) {
            return cached;
        }
        Grid built = build(level, origin);
        if (built == null) {
            return null;
        }
        for (BlockPos pole : built.poles) {
            byPos.put(pole, built);
        }
        return built;
    }

    /**
     * 从 {@code origin} 出发 BFS 聚合整张网。
     *
     * <p>连边条件：两根杆的直线距离 ≤ linkRange（配置项，默认 10）。
     * 用直线距离而非方块相邻，这就是「杆间距 ≤10 自动连线」。</p>
     */
    private static Grid build(ServerLevel level, BlockPos origin) {
        if (!(level.getBlockEntity(origin) instanceof PowerPoleBlockEntity)) {
            return null;
        }
        int linkRange = com.astrofactory.config.AstroFactoryConfig.POWER_POLE_LINK_RANGE.get();
        int maxPoles = com.astrofactory.config.AstroFactoryConfig.POWER_POLE_MAX_POLES.get();
        int radius = com.astrofactory.config.AstroFactoryConfig.POWER_POLE_RADIUS.get();
        long linkSq = (long) linkRange * linkRange;

        // 1) BFS 收集连通的所有杆
        Set<BlockPos> poles = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        poles.add(origin);
        while (!queue.isEmpty() && poles.size() <= maxPoles) {
            BlockPos current = queue.poll();
            for (BlockPos candidate : nearbyPoles(level, current, linkRange)) {
                if (poles.add(candidate)) {
                    queue.add(candidate);
                }
            }
        }

        // 2) owner = 坐标最小的杆
        BlockPos owner = origin;
        for (BlockPos pole : poles) {
            if (comparePos(pole, owner) < 0) {
                owner = pole;
            }
        }

        // 3) 扫描覆盖范围，收集发电与耗电设备
        //    用 Set 去重：多根杆覆盖范围重叠时，同一台机器只算一次
        Set<BlockPos> sourcePos = new HashSet<>();
        Set<BlockPos> sinkPos = new HashSet<>();
        for (BlockPos pole : poles) {
            scanCoverage(level, pole, radius, sourcePos, sinkPos);
        }

        List<IEnergyStorage> sources = new ArrayList<>();
        for (BlockPos pos : sourcePos) {
            IEnergyStorage st = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            if (st != null && st.canExtract()) {
                sources.add(st);
            }
        }
        List<IEnergyStorage> sinks = new ArrayList<>();
        for (BlockPos pos : sinkPos) {
            IEnergyStorage st = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            if (st != null && st.canReceive()) {
                sinks.add(st);
            }
        }

        return new Grid(poles, owner, sources, sinks);
    }

    /** 坐标字典序比较（x, y, z）。 */
    private static int comparePos(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    /** 找 current 周围 linkRange 内的其它杆（用方块距离筛选，避免全维度扫描）。 */
    private static List<BlockPos> nearbyPoles(ServerLevel level, BlockPos current, int linkRange) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -linkRange; dx <= linkRange; dx++) {
            for (int dy = -linkRange; dy <= linkRange; dy++) {
                for (int dz = -linkRange; dz <= linkRange; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    m.set(current.getX() + dx, current.getY() + dy, current.getZ() + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    if (level.getBlockEntity(m) instanceof PowerPoleBlockEntity) {
                        result.add(m.immutable());
                    }
                }
            }
        }
        return result;
    }

    /**
     * 扫描一根杆的球形覆盖范围，把范围内的能量设备分到发电/耗电两侧。
     *
     * <p>按 {@code radius} 做球形判定（dx²+dy²+dz² ≤ r²），
     * 只查已加载区块，跨卸载区块的边界自然跳过（冻结语义一致）。</p>
     */
    private static void scanCoverage(ServerLevel level, BlockPos pole, int radius,
                                     Set<BlockPos> sourcePos, Set<BlockPos> sinkPos) {
        long rSq = (long) radius * radius;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((long) dx * dx + (long) dy * dy + (long) dz * dz > rSq) {
                        continue;
                    }
                    m.set(pole.getX() + dx, pole.getY() + dy, pole.getZ() + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    // 杆自身不算设备
                    BlockEntity be = level.getBlockEntity(m);
                    if (be == null || be instanceof PowerPoleBlockEntity) {
                        continue;
                    }
                    IEnergyStorage st = level.getCapability(Capabilities.EnergyStorage.BLOCK, m, null);
                    if (st == null) {
                        continue;
                    }
                    if (st.canExtract()) {
                        sourcePos.add(m.immutable());
                    }
                    if (st.canReceive()) {
                        sinkPos.add(m.immutable());
                    }
                }
            }
        }
    }

    /**
     * 执行一次分配（只由 owner 杆每 tick 调用一次）。
     *
     * <p>公平策略（写入配置说明）：发电量先汇总，再按「各耗电设备的空余容量」
     * 比例分配。这样发电不足时所有机器一起降速，而不是先到的吃满、后来的饿死。</p>
     *
     * @return 本次实际传输的能量（FE）
     */
    public static int distribute(ServerLevel level, Grid grid) {
        if (grid == null || grid.sources.isEmpty() || grid.sinks.isEmpty()) {
            return 0;
        }
        int budget = com.astrofactory.config.AstroFactoryConfig.POWER_POLE_MAX_TRANSFER.get();

        // 只统计还有空余的耗电设备
        List<IEnergyStorage> activeSinks = new ArrayList<>();
        long totalSpace = 0;
        for (IEnergyStorage sink : grid.sinks) {
            int space = sink.getMaxEnergyStored() - sink.getEnergyStored();
            if (space > 0) {
                activeSinks.add(sink);
                totalSpace += space;
            }
        }
        if (activeSinks.isEmpty() || totalSpace <= 0) {
            return 0;
        }

        // 汇总可用发电量（受预算限制）
        long available = 0;
        for (IEnergyStorage source : grid.sources) {
            available += source.extractEnergy(budget, true);
            if (available >= budget) {
                available = budget;
                break;
            }
        }
        if (available <= 0) {
            return 0;
        }

        long toMove = Math.min(available, totalSpace);
        int moved = 0;

        // 按空余容量比例分配：每台机器分到 toMove * (自己的空余 / 总空余)
        for (IEnergyStorage sink : activeSinks) {
            int space = sink.getMaxEnergyStored() - sink.getEnergyStored();
            int share = (int) (toMove * space / totalSpace);
            if (share <= 0) {
                continue;
            }
            moved += pullFromSources(grid.sources, sink, share);
        }

        // 比例取整会剩一点零头，补给还能装的机器，避免长期累积误差
        int leftover = (int) toMove - moved;
        if (leftover > 0) {
            for (IEnergyStorage sink : activeSinks) {
                if (leftover <= 0) {
                    break;
                }
                int got = pullFromSources(grid.sources, sink, leftover);
                moved += got;
                leftover -= got;
            }
        }
        return moved;
    }

    /** 从各电源依次取电灌进 sink，最多 amount。 */
    private static int pullFromSources(List<IEnergyStorage> sources, IEnergyStorage sink, int amount) {
        int remaining = amount;
        int moved = 0;
        for (IEnergyStorage source : sources) {
            if (remaining <= 0) {
                break;
            }
            int canGive = source.extractEnergy(remaining, true);
            if (canGive <= 0) {
                continue;
            }
            int accepted = sink.receiveEnergy(canGive, true);
            if (accepted <= 0) {
                continue;
            }
            int extracted = source.extractEnergy(accepted, false);
            if (extracted <= 0) {
                continue;
            }
            int received = sink.receiveEnergy(extracted, false);
            if (received < extracted) {
                // 理论不会发生（已模拟），保险起见退回差额
                source.receiveEnergy(extracted - received, false);
            }
            moved += received;
            remaining -= received;
        }
        return moved;
    }
}
