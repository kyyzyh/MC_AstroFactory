package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.power.ElectricNetwork;
import com.xiaoyi.astrofactory.power.ISteamNode;
import com.xiaoyi.astrofactory.power.SteamNetwork;

/**
 * 蒸汽机方块实体（规格 §5.5）：消耗蒸汽 → 发电 → 灌入范围内电线杆。
 *
 * <p>最小实现：每 tick 消耗 1 蒸汽产出 {@link #ENERGY_PER_STEAM} 能量。</p>
 */
public class SteamEngineBlockEntity extends BlockEntity implements ISteamNode {
    /** 蒸汽缓冲上限（蒸汽机自己不存太多，靠管道供给）。 */
    public static final int STEAM_CAPACITY = 40;
    /** 每消耗 1 蒸汽产出能量。 */
    public static final int ENERGY_PER_STEAM = 2;

    private int steam = 0;

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.STEAM_ENGINE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SteamEngineBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof SteamEngineBlock blk && state.getValue(SteamEngineBlock.PART) == 0) {
            blk.repair(level, this.getBlockPos(), state);
        }
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        // 先从整机边缘补充蒸汽（多方块：master 邻居全是自己的 dummy）
        SteamNetwork.equalizeFromCells(level,
                com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                        .allCells(pos, state.getValue(SteamEngineBlock.FACING),
                                engineSizeX(state), engineSizeZ(state)),
                this);

        boolean active = false;
        if (steam > 0) {
            steam--;
            int energy = ENERGY_PER_STEAM;
            int pushed = ElectricNetwork.pushEnergyFromCells(level,
                    com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                            .allCells(pos, state.getValue(SteamEngineBlock.FACING),
                                    engineSizeX(state), engineSizeZ(state)),
                    energy);
            active = pushed > 0;
            if (pushed > 0) {
                setChanged();
            }
        }

        if (state.getValue(SteamEngineBlock.ACTIVE) != active) {
            for (BlockPos cell : com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                    .allCells(pos, state.getValue(SteamEngineBlock.FACING),
                            engineSizeX(state), engineSizeZ(state))) {
                BlockState cellState = level.getBlockState(cell);
                if (cellState.getBlock() instanceof SteamEngineBlock
                        && cellState.getValue(SteamEngineBlock.ACTIVE) != active) {
                    level.setBlock(cell, cellState.setValue(SteamEngineBlock.ACTIVE, active), 3);
                }
            }
        }
    }

    private static int engineSizeX(BlockState state) {
        return state.getBlock() instanceof SteamEngineBlock b ? b.sizeX() : 3;
    }

    private static int engineSizeZ(BlockState state) {
        return state.getBlock() instanceof SteamEngineBlock b ? b.sizeZ() : 5;
    }

    // ------------------------------------------------------------ ISteamNode

    @Override
    public int getSteam() {
        return steam;
    }

    @Override
    public int getSteamCapacity() {
        return STEAM_CAPACITY;
    }

    @Override
    public void setSteam(int amount) {
        this.steam = Math.max(0, Math.min(STEAM_CAPACITY, amount));
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Steam", steam);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        steam = input.getIntOr("Steam", 0);
    }
}
