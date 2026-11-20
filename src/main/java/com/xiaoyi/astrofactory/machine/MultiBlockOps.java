package com.xiaoyi.astrofactory.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import com.xiaoyi.astrofactory.multiblock.MultiBlockHelper;
import com.xiaoyi.astrofactory.multiblock.MultiBlockMachineBlock;

/**
 * 多方块机器的公共操作（静态工具版）。
 *
 * <p>给<b>不是容器</b>的多方块机器（蒸汽机、离岸泵）复用，
 * 容器机器请直接继承 {@link AbstractMachineEntity}，那里有同样的实例方法。</p>
 */
public final class MultiBlockOps {
    private MultiBlockOps() {}

    /**
     * 区块加载自修复：master 补齐缺失 dummy，被占则整机清掉（规格 §5.1）。
     *
     * <p>在 BE 的 {@code onLoad()} 里调用。</p>
     */
    public static void repairOnLoad(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MultiBlockMachineBlock block)) {
            return;
        }
        if (state.getValue(block.partProperty()) == 0) {
            block.repair(level, be.getBlockPos(), state);
        }
    }

    /**
     * 把「运行中」状态同步到整机所有格子。
     *
     * <p>没有 ACTIVE 属性的机器直接跳过。</p>
     */
    public static void setActive(BlockEntity be, boolean active) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MultiBlockMachineBlock block)) {
            return;
        }
        BooleanProperty activeProp = block.activeProperty();
        if (activeProp == null || state.getValue(activeProp) == active) {
            return;
        }
        for (BlockPos cell : MultiBlockHelper.allCells(be.getBlockPos(),
                state.getValue(MultiBlockMachineBlock.FACING), block.sizeX(), block.sizeZ())) {
            BlockState cellState = level.getBlockState(cell);
            if (cellState.getBlock() == block && cellState.getValue(activeProp) != active) {
                level.setBlock(cell, cellState.setValue(activeProp, active), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 整台机器的所有格子。 */
    public static java.util.List<BlockPos> allCells(BlockEntity be) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MultiBlockMachineBlock block)) {
            return java.util.List.of(be.getBlockPos());
        }
        return MultiBlockHelper.allCells(be.getBlockPos(),
                state.getValue(MultiBlockMachineBlock.FACING), block.sizeX(), block.sizeZ());
    }
}
