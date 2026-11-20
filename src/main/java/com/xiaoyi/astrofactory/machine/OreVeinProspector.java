package com.xiaoyi.astrofactory.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.xiaoyi.astrofactory.block.OreVeinBlock;
import com.xiaoyi.astrofactory.block.OreVeinBlockEntity;
import com.xiaoyi.astrofactory.multiblock.MultiBlockHelper;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 矿脉探矿器：把「找脚下矿脉 + 扣储量」从采矿机 BE 里拆出来。
 *
 * <p>规格 §5.6「矿脉枯竭」：采矿机产出必须来自真实矿脉，挖空就停机。
 * 本类负责三件事：</p>
 * <ul>
 *   <li>{@link #available} —— 只查询脚下是否还有矿（不扣储量），用于每 tick 的工作前置判定；</li>
 *   <li>{@link #mineOne} —— 扣 1 单位储量并返回矿种；</li>
 *   <li>内部缓存上次命中的矿脉位置，避免每 tick 全范围扫描。</li>
 * </ul>
 *
 * <p>扣减复用 {@link OreVeinBlock#mineOne}，与玩家手挖走同一条路径，
 * 保证贫富度刷新、采空变贫矿等行为一致。</p>
 */
public final class OreVeinProspector {
    /** 向下搜索深度。 */
    public static final int SCAN_DEPTH = 4;
    /** 水平外扩格数（容忍机器摆在矿田边缘）。 */
    public static final int SCAN_MARGIN = 1;
    /** 找不到矿时的重扫冷却（tick）。 */
    public static final int SCAN_COOLDOWN = 20;

    private final int sizeX;
    private final int sizeZ;

    /** 上次找到的矿脉位置（缓存，避免每 tick 全范围扫描）。 */
    private @Nullable BlockPos cachedVeinPos;
    /** 重扫冷却计数。 */
    private int scanCooldown;

    public OreVeinProspector(int sizeX, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
    }

    /**
     * 脚下是否还有可采的矿脉（只查询，不扣储量）。
     *
     * <p>矿挖完了机器就该停，不该继续空转烧煤。</p>
     */
    public boolean available(Level level, BlockPos masterPos, Direction facing) {
        if (cachedVeinPos != null && hasReserve(level, cachedVeinPos)) {
            return true;
        }
        // 没矿时限制重扫频率：每 20 tick 扫一次足够（1 秒延迟可接受）
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_COOLDOWN;
        for (BlockPos candidate : candidates(masterPos, facing)) {
            if (hasReserve(level, candidate)) {
                cachedVeinPos = candidate.immutable();
                return true;
            }
        }
        cachedVeinPos = null;
        return false;
    }

    /**
     * 从脚下矿脉扣 1 储量，返回该矿脉的矿种；没矿 / 采空返回 {@code null}。
     *
     * <p>优先用缓存位置（命中率高），缓存失效时重新扫描。</p>
     */
    public OreVeinBlock.@Nullable OreKind mineOne(Level level, BlockPos masterPos, Direction facing) {
        if (cachedVeinPos != null) {
            OreVeinBlock.OreKind kind = tryMine(level, cachedVeinPos);
            if (kind != null) {
                return kind;
            }
            cachedVeinPos = null;
        }
        for (BlockPos candidate : candidates(masterPos, facing)) {
            OreVeinBlock.OreKind kind = tryMine(level, candidate);
            if (kind != null) {
                cachedVeinPos = candidate.immutable();
                return kind;
            }
        }
        return null;
    }

    private static boolean hasReserve(Level level, BlockPos p) {
        if (!level.isLoaded(p)) {
            return false;
        }
        return level.getBlockState(p).getBlock() instanceof OreVeinBlock
                && level.getBlockEntity(p) instanceof OreVeinBlockEntity vein
                && vein.getReserve() > 0;
    }

    /** 尝试挖该位置 1 单位；成功返回矿种，失败返回 null。 */
    private static OreVeinBlock.@Nullable OreKind tryMine(Level level, BlockPos p) {
        if (!(level instanceof ServerLevel serverLevel) || !level.isLoaded(p)) {
            return null;
        }
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof OreVeinBlock)) {
            return null;
        }
        if (!(level.getBlockEntity(p) instanceof OreVeinBlockEntity vein) || vein.getReserve() <= 0) {
            return null;
        }
        OreVeinBlock.OreKind kind = st.getValue(OreVeinBlock.KIND);
        // 复用玩家挖掘的扣减 + 贫富度刷新逻辑，保证两条路径行为一致
        OreVeinBlock.mineOne(serverLevel, p, st);
        return kind;
    }

    /**
     * 生成候选矿脉位置：机器覆盖的每一列，从脚下往下 {@link #SCAN_DEPTH} 格，
     * 水平方向外扩 {@link #SCAN_MARGIN} 格。
     *
     * <p>顺序先近后远、先上层后下层，保证靠得最近的矿先被挖到。</p>
     */
    private List<BlockPos> candidates(BlockPos masterPos, Direction facing) {
        int lo = -SCAN_MARGIN;
        int hi = sizeX - 1 + SCAN_MARGIN;
        int zHi = sizeZ - 1 + SCAN_MARGIN;
        List<BlockPos> out = new ArrayList<>((hi - lo + 1) * (zHi - lo + 1) * SCAN_DEPTH);
        for (int depth = 1; depth <= SCAN_DEPTH; depth++) {
            for (int dx = lo; dx <= hi; dx++) {
                for (int dz = lo; dz <= zHi; dz++) {
                    out.add(MultiBlockHelper.cellPos(masterPos, facing, dx, dz).below(depth));
                }
            }
        }
        return out;
    }
}
