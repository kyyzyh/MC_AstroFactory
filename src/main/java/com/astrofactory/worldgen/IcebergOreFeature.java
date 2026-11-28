package com.astrofactory.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * 冰山状矿体特性（v0.8.9 矿体改造）。
 *
 * <p>像冰山一样：地表只露一小块，下方深埋一大片实心矿体。
 * 世界生成时以一个锚点为椭球中心，水平方向半径 {@link #radius()}，
 * 垂直向下延伸 {@link #depth()} 格，形成深埋椭球矿体。
 * 这样一片矿区用采矿机要挖很久才能挖完（玩家构想的核心）。</p>
 *
 * <p>仅替换 {@link #target}（默认为石头/深板岩），不覆盖空气、
 * 流体或其它方块，避免破坏地形。</p>
 */
public class IcebergOreFeature extends Feature<IcebergOreFeature.Config> {

    public IcebergOreFeature() {
        super(Config.CODEC.codec());
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config config = context.config();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        // 水平半径 + 垂直向下深度
        int r = config.radius();
        int depth = config.depth();

        // 椭球中心在锚点下方 depth/2 处，使大部分矿体埋在地下，顶部贴近地表露头
        int cy = origin.getY() - depth / 2;

        boolean placed = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= depth; dy++) {
                    int y = origin.getY() - dy;
                    BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    // 椭球归一化距离；dy 从锚点向下
                    double nx = (double) dx / (r == 0 ? 1 : r);
                    double nz = (double) dz / (r == 0 ? 1 : r);
                    double ny = (double) (dy - depth / 2.0) / (depth / 2.0 == 0 ? 1 : depth / 2.0);
                    if (nx * nx + nz * nz + ny * ny > 1.0) {
                        continue;
                    }
                    BlockState current = level.getBlockState(pos);
                    if (current.is(config.target().getBlock())) {
                        level.setBlock(pos, config.ore(), 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    /** 冰山矿体配置。 */
    public record Config(BlockState target, BlockState ore, int radius, int depth)
            implements FeatureConfiguration {
        public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                BlockState.CODEC.fieldOf("target").forGetter(Config::target),
                BlockState.CODEC.fieldOf("ore").forGetter(Config::ore),
                Codec.INT.fieldOf("radius").forGetter(Config::radius),
                Codec.INT.fieldOf("depth").forGetter(Config::depth)
        ).apply(inst, Config::new));
    }
}
