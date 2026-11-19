package com.xiaoyi.astrofactory.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * 多方块几何工具：把"master + 局部坐标"换算成世界坐标。
 *
 * <p>坐标约定（规格 §5.1）：</p>
 * <ul>
 *   <li>{@code dx} 沿机器的"右手方向"（{@code FACING.getClockWise()}）展开，0..N-1；</li>
 *   <li>{@code dz} 沿机器的"正面方向"（{@code FACING}）展开，0..N-1；</li>
 *   <li>master 恒占局部坐标 (0,0)，其余格为 dummy。</li>
 * </ul>
 *
 * <p>{@code part} 是 dummy 在方块状态里记录的索引，等于 {@code dz * N + dx}。
 * 因为 master 固定是 part 0，dummy 的 part 取值是 1..N*N-1。</p>
 */
public final class MultiBlockHelper {
    private MultiBlockHelper() {}

    /** 局部坐标 (dx, dz) 对应的世界坐标。 */
    public static BlockPos cellPos(BlockPos masterPos, Direction facing, int dx, int dz) {
        Direction right = facing.getClockWise();
        return masterPos.offset(
                right.getStepX() * dx + facing.getStepX() * dz,
                0,
                right.getStepZ() * dx + facing.getStepZ() * dz);
    }

    /** 局部坐标 -> part 索引。 */
    public static int partOf(int dx, int dz, int size) {
        return dz * size + dx;
    }

    public static int dxOf(int part, int size) {
        return part % size;
    }

    public static int dzOf(int part, int size) {
        return part / size;
    }

    /** 整台机器的全部格子（含 master 格），顺序为 dz 外层、dx 内层。 */
    public static List<BlockPos> allCells(BlockPos masterPos, Direction facing, int size) {
        List<BlockPos> cells = new ArrayList<>(size * size);
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                cells.add(cellPos(masterPos, facing, dx, dz));
            }
        }
        return cells;
    }

    /** 由 dummy 的世界坐标 + part 反推 master 的世界坐标。 */
    public static BlockPos masterOf(BlockPos dummyPos, Direction facing, int size, int part) {
        int dx = dxOf(part, size);
        int dz = dzOf(part, size);
        Direction right = facing.getClockWise();
        return dummyPos.offset(
                -(right.getStepX() * dx + facing.getStepX() * dz),
                0,
                -(right.getStepZ() * dx + facing.getStepZ() * dz));
    }

    /** 局部坐标 (dx, dz) 对应的 part（用于判断某格是不是 master）。 */
    public static boolean isMasterPart(int part) {
        return part == 0;
    }
}
