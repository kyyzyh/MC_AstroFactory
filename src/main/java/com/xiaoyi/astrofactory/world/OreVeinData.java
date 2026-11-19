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
 * <p>出生点 64 格内保底 4 片矿田，铁:铜:煤:石 = 3:2:2:2。
 * 矿田 7×7~15×15。首次生成后写入存档，重载不再重复放置。</p>
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

    // ------------------------------------------------------------ 生成

    /** 矿田半径（7×7~15×15 → 半径 3~7）。 */
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 7;

    /** 矿种权重：铁:铜:煤:石 = 3:2:2:2。 */
    private static final int[] KIND_WEIGHTS = {3, 2, 2, 2};
    private static final OreVeinBlock.OreKind[] KINDS = OreVeinBlock.OreKind.values();

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
}
