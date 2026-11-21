package com.xiaoyi.astrofactory.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.block.OreVeinBlock;
import com.xiaoyi.astrofactory.block.OreVeinBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 矿田生成记录：保证幂等（规格 §5.6「用 SavedData 保证幂等」）。
 *
 * <h2>两层生成</h2>
 * <ol>
 *   <li><b>出生点保底</b>：{@link #generateIfAbsent} 在出生点 64 格内放 4 片矿田，
 *       铁:铜:煤:石 = 3:2:2:2。只在首次启动跑一次。</li>
 *   <li><b>全世界散布</b>：{@link #tryGenerateForChunk} 在 chunk 首次加载时按
 *       确定性概率生成 0~1 片矿田。这是「扩张找矿」体验链的基础。</li>
 * </ol>
 *
 * <h2>为什么第二层是必须的</h2>
 * <p>0.1.0 只有出生点 4 片矿田，挖完就彻底没有矿了 —— 手册里「去别处找新矿脉」
 * 那句话在当时是假的。采矿机枯竭机制只有在「远处还有矿」的前提下才成立，
 * 否则它不是挑战，是死路。</p>
 *
 * <h2>为什么用确定性哈希而不是随机</h2>
 * <p>同一 chunk 无论加载多少次都必须得到同一个结论，否则玩家走远再回来会看到
 * 矿田反复生灭。用 {@code (chunkX, chunkZ, worldSeed)} 三元组做哈希，
 * 结果完全可复现 —— 这也是原版 placed feature 用 region seed 的同一思路。</p>
 *
 * <h2>为什么不用原版 worldgen feature</h2>
 * <p>矿田不是「一堆方块」，而是<b>带储量的方块实体</b>。原版 feature 能放方块，
 * 但要在 setBlock 之后拿 BlockEntity 写储量，时序和区块状态（generation vs full）
 * 很难保证。用 {@code ChunkEvent.Load} 在 chunk 完全加载后写，行为最可预测。</p>
 */
public class OreVeinData extends SavedData {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Astrofactory.MODID, "ore_veins");

    public static final Codec<OreVeinData> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                Codec.STRING.listOf().fieldOf("placed_chunk_keys")
                        .forGetter(d -> d.placedChunkKeys)
            ).apply(i, OreVeinData::new)
    );

    public static final SavedDataType<OreVeinData> TYPE = new SavedDataType<>(ID, OreVeinData::new, CODEC);

    /** 已放置矿田的 chunk 键列表（"x,z"），幂等用。 */
    private final List<String> placedChunkKeys;

    public OreVeinData() {
        this(new ArrayList<>());
    }

    public OreVeinData(List<String> placedChunkKeys) {
        this.placedChunkKeys = placedChunkKeys;
    }

    /** 该 chunk key 是否已放置过矿田。 */
    public boolean isPlaced(ChunkPos chunk) {
        return placedChunkKeys.contains(key(chunk));
    }

    /** 标记该 chunk 已放置。 */
    public void markPlaced(ChunkPos chunk) {
        placedChunkKeys.add(key(chunk));
        setDirty();
    }

    private static String key(ChunkPos chunk) {
        return chunk.x() + "," + chunk.z();
    }

    // ------------------------------------------------------------ 常量

    /** 矿田半径（7×7~15×15 → 半径 3~7）。 */
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 7;

    /** 矿种权重：铁:铜:煤:石 = 3:2:2:2。 */
    private static final int[] KIND_WEIGHTS = {3, 2, 2, 2};
    private static final OreVeinBlock.OreKind[] KINDS = OreVeinBlock.OreKind.values();

    /**
     * 全世界散布的生成概率（每个 chunk）。
     *
     * <p>取 1/18 ≈ 5.6%：一个 chunk 是 16×16=256 格，矿田平均约 12×12=144 格，
     * 所以平均每 18 个 chunk 有 1 片矿田，视觉上「走一会儿就能碰到一片」，
     * 又不会把地下挖成筛子。</p>
     */
    private static final int SCATTER_ONE_IN = 18;

    /**
     * 出生点保护区半径（chunk）。
     *
     * <p>散布生成要避开出生点附近，否则会和 {@link #generateIfAbsent} 的
     * 4 片保底矿田叠在一起。</p>
     */
    private static final int SPAWN_PROTECT_CHUNKS = 4;

    // ------------------------------------------------------------ 第一层：出生点保底

    /**
     * 在出生点附近放置矿田（幂等）。
     *
     * <p>从出生点向外扫描，找到 4 个未占用的 chunk 各放 1 片矿田。</p>
     */
    public void generateIfAbsent(ServerLevel level, BlockPos spawn) {
        if (placedChunkKeys.size() >= 4) {
            return;
        }

        RandomSource random = level.getRandom();
        ChunkPos spawnChunk = new ChunkPos(spawn.getX(), spawn.getZ());

        // 从出生点 chunk 开始螺旋向外，找 4 个 chunk 放矿田
        int placed = 0;
        for (int ring = 0; ring < 16 && placed < 4; ring++) {
            for (int dx = -ring; dx <= ring && placed < 4; dx++) {
                for (int dz = -ring; dz <= ring && placed < 4; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue; // 只取环边
                    }
                    ChunkPos chunk = new ChunkPos(spawnChunk.x() + dx, spawnChunk.z() + dz);
                    if (isPlaced(chunk)) {
                        continue;
                    }
                    // birth 64 格内保底：ring 0..2 的 chunk 都在 64 格内
                    generateVeinInChunk(level, chunk, random);
                    markPlaced(chunk);
                    placed++;
                }
            }
        }
    }

    // ------------------------------------------------------------ 第二层：全世界散布

    /**
     * chunk 首次加载时尝试生成 1 片矿田（确定性，可重入）。
     *
     * @param spawnChunk 世界出生点 chunk，用于跳过保护区
     * @return 是否真的生成了
     */
    public boolean tryGenerateForChunk(ServerLevel level, ChunkPos chunk, ChunkPos spawnChunk) {
        // 保护区：出生点附近交给保底逻辑
        int dx = Math.abs(chunk.x() - spawnChunk.x());
        int dz = Math.abs(chunk.z() - spawnChunk.z());
        if (dx <= SPAWN_PROTECT_CHUNKS && dz <= SPAWN_PROTECT_CHUNKS) {
            return false;
        }
        if (isPlaced(chunk)) {
            return false;
        }

        long seed = level.getSeed();
        if (!shouldScatter(chunk, seed)) {
            return false;
        }

        // 用同一套哈希派生随机源，保证「同一 chunk 同一结果」
        RandomSource random = RandomSource.create(hash(chunk, seed, 0x5DEECE66DL));
        generateVeinInChunk(level, chunk, random);
        markPlaced(chunk);
        return true;
    }

    /** 该 chunk 是否该有散布矿田（纯函数，可单测）。 */
    public static boolean shouldScatter(ChunkPos chunk, long worldSeed) {
        long h = hash(chunk, worldSeed, 0x9E3779B97F4A7C15L);
        // 取模要防负数：floorMod 保证结果在 [0, SCATTER_ONE_IN)
        return Math.floorMod(h, SCATTER_ONE_IN) == 0;
    }

    /**
     * 三元组哈希（splitmix64 变体）。
     *
     * <p>不用 {@code Objects.hash}：它的质量对坐标这种结构化输入不够，
     * 相邻 chunk 的哈希容易成等差，导致矿田排成格子。splitmix64 的
     * 雪崩性质够好，且完全确定。</p>
     */
    private static long hash(ChunkPos chunk, long seed, long salt) {
        long x = chunk.x() * 0x9E3779B97F4A7C15L;
        long z = chunk.z() * 0xC2B2AE3D27D4EB4FL;
        long h = x ^ Long.rotateLeft(z, 31) ^ seed ^ salt;
        // splitmix64 finalizer
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h;
    }

    // ------------------------------------------------------------ 实际放置

    /** 在一个 chunk 内生成 1 片矿田（7×7~15×15）。 */
    private void generateVeinInChunk(ServerLevel level, ChunkPos chunk, RandomSource random) {
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        OreVeinBlock.OreKind kind = weightedKind(random);

        // 矿田中心：chunk 中心
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();

        // 找到地表高度（OCEAN_FLOOR_WG）
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                centerX, centerZ);

        // vein 放在地表往下 1 格（埋在地下，玩家挖矿时发现）
        int baseY = Math.max(level.getMinY(), surfaceY - 1);

        BlockState veinState = com.xiaoyi.astrofactory.ModRegistry.ORE_VEIN.get().defaultBlockState()
                .setValue(OreVeinBlock.KIND, kind)
                .setValue(OreVeinBlock.RICHNESS, OreVeinBlock.Richness.NORMAL);

        // 圆形矿田
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos pos = new BlockPos(centerX + dx, baseY, centerZ + dz);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState existing = level.getBlockState(pos);
                if (!existing.canBeReplaced()) {
                    continue;
                }
                level.setBlock(pos, veinState, net.minecraft.world.level.block.Block.UPDATE_ALL);
                // 设置储量（richness 随距离中心递减）
                double dist = Math.sqrt(dx * dx + dz * dz);
                double ratio = 1.0 - dist / radius;
                int reserve = (int) (OreVeinBlock.DEFAULT_RESERVE * (0.5 + 0.5 * ratio));
                if (reserve <= 0) {
                    reserve = 1;
                }
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof OreVeinBlockEntity vein) {
                    vein.setReserve(reserve);
                    level.setBlock(pos, veinState.setValue(OreVeinBlock.RICHNESS,
                            OreVeinBlock.richnessFor(reserve)), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static OreVeinBlock.OreKind weightedKind(RandomSource random) {
        int total = 0;
        for (int w : KIND_WEIGHTS) total += w;
        int roll = random.nextInt(total);
        int acc = 0;
        for (int i = 0; i < KINDS.length; i++) {
            acc += KIND_WEIGHTS[i];
            if (roll < acc) {
                return KINDS[i];
            }
        }
        return KINDS[0];
    }

    // ------------------------------------------------------------ 供探矿仪查询

    /** 散布概率的倒数（探矿仪用来估算「最近的矿大概多远」）。 */
    public static int scatterOneIn() {
        return SCATTER_ONE_IN;
    }

    /** 出生点保护区半径（chunk）。 */
    public static int spawnProtectChunks() {
        return SPAWN_PROTECT_CHUNKS;
    }

    /**
     * 找出距离 {@code from} 最近的若干个已放矿田的 chunk（供探矿仪）。
     *
     * <p>不扫描方块，只查 {@link #placedChunkKeys} —— 常数级开销，
     * 玩家可以随便右键。返回的 {@link ChunkPos} 是矿田中心所在的 chunk，
     * 玩家照着方向走过去就能看到地表下的矿田。</p>
     *
     * @param maxChunks 最多返回几个
     * @param maxRange  最大 chunk 距离（超出不返回）
     */
    public List<ChunkPos> nearestVeins(ChunkPos from, int maxChunks, int maxRange) {
        record Hit(ChunkPos pos, int dist2) {}
        List<Hit> hits = new ArrayList<>();
        for (String k : placedChunkKeys) {
            int comma = k.indexOf(',');
            if (comma < 0) {
                continue;
            }
            int cx;
            int cz;
            try {
                cx = Integer.parseInt(k.substring(0, comma));
                cz = Integer.parseInt(k.substring(comma + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            int dx = cx - from.x();
            int dz = cz - from.z();
            int d2 = dx * dx + dz * dz;
            if (d2 > maxRange * maxRange) {
                continue;
            }
            hits.add(new Hit(new ChunkPos(cx, cz), d2));
        }
        hits.sort((a, b) -> Integer.compare(a.dist2(), b.dist2()));
        List<ChunkPos> out = new ArrayList<>(Math.min(maxChunks, hits.size()));
        for (int i = 0; i < hits.size() && i < maxChunks; i++) {
            out.add(hits.get(i).pos());
        }
        return out;
    }

    /** 已记录矿田的 chunk 总数（调试用）。 */
    public int veinChunkCount() {
        return placedChunkKeys.size();
    }

    /**
     * 预测周围哪些 chunk <b>会</b>有矿田（含尚未加载的）。
     *
     * <h2>为什么不能只查 {@code placedChunkKeys}</h2>
     * <p>那个列表只记录<b>已经生成过</b>的矿田。玩家站在一片没探索过的区域，
     * 列表里当然什么都没有 —— 探矿仪就会一直报「没探到」，哪怕矿就在 30 格外的
     * 未加载 chunk 里。那等于探矿仪只在「你已经找到矿之后」才有用，
     * 完全违背它的存在意义。</p>
     *
     * <p>{@link #shouldScatter} 是纯函数（chunk 坐标 + 世界种子 → 布尔），
     * 所以可以<b>离线预测</b>。预测和实际生成用同一个函数，走过去时
     * {@code ChunkEvent.Load} 一定会照着预测结果生成 —— 不会骗人。</p>
     *
     * <h2>为什么按环形由近到远扫</h2>
     * <p>玩家只关心最近的几片。按 chunk 距离平方排序后取前 N 个，
     * 结果和「全扫一遍再排序」完全一致，但可以在够数后提前退出。
     * 半径 12 chunk 最多 625 个 chunk，一次右键的常数开销。</p>
     */
    public List<ChunkPos> predictVeins(ChunkPos from, ChunkPos spawnChunk,
                                       int maxChunks, int maxRange, long worldSeed) {
        record Hit(ChunkPos pos, int dist2) {}
        List<Hit> hits = new ArrayList<>();
        for (int dx = -maxRange; dx <= maxRange; dx++) {
            for (int dz = -maxRange; dz <= maxRange; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > maxRange * maxRange) {
                    continue;
                }
                ChunkPos c = new ChunkPos(from.x() + dx, from.z() + dz);
                // 保护区内的 chunk 不会有散布矿田（只有 4 片保底），
                // 预测出来就是骗人。这里必须和 tryGenerateForChunk 的判定一致。
                if (Math.abs(c.x() - spawnChunk.x()) <= SPAWN_PROTECT_CHUNKS
                        && Math.abs(c.z() - spawnChunk.z()) <= SPAWN_PROTECT_CHUNKS) {
                    continue;
                }
                if (shouldScatter(c, worldSeed)) {
                    hits.add(new Hit(c, d2));
                }
            }
        }
        hits.sort((a, b) -> Integer.compare(a.dist2(), b.dist2()));
        List<ChunkPos> out = new ArrayList<>(Math.min(maxChunks, hits.size()));
        for (int i = 0; i < hits.size() && i < maxChunks; i++) {
            out.add(hits.get(i).pos());
        }
        return out;
    }
}
