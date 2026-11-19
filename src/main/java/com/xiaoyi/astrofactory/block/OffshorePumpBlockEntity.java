package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.power.ISteamNode;
import com.xiaoyi.astrofactory.power.SteamNetwork;

/**
 * 离岸泵方块实体（规格 §5.5）：贴水时持续产蒸汽。
 *
 * <p>最小实现：检测周围是否有水方块；有就每 {@link #PUMP_INTERVAL} tick 产 1 蒸汽注入相邻管道。
 * 不消耗燃料（水是无限资源），这样蒸汽链有"锅炉烧煤"和"离岸泵抽水"两种供给方式。</p>
 */
public class OffshorePumpBlockEntity extends BlockEntity implements ISteamNode {
    public static final int STEAM_CAPACITY = 40;
    /** 每多少 tick 产 1 蒸汽。 */
    public static final int PUMP_INTERVAL = 20;

    private int steam = 0;
    private int cooldown = 0;

    public OffshorePumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.OFFSHORE_PUMP_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OffshorePumpBlockEntity be) {
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
        if (state.getBlock() instanceof OffshorePumpBlock blk && state.getValue(OffshorePumpBlock.PART) == 0) {
            blk.repair(level, this.getBlockPos(), state);
        }
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        boolean hasWater = nearWater(level, pos);
        boolean producing = false;

        if (hasWater) {
            if (cooldown > 0) {
                cooldown--;
            } else if (steam < STEAM_CAPACITY) {
                cooldown = PUMP_INTERVAL;
                steam = Math.min(STEAM_CAPACITY, steam + 1);
                producing = true;
            }
        }

        if (SteamNetwork.equalizeFromCells(level,
                com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                        .allCells(pos, state.getValue(OffshorePumpBlock.FACING), 1, 2),
                this)) {
            setChanged();
        }

        if (state.getValue(OffshorePumpBlock.ACTIVE) != producing) {
            for (BlockPos cell : com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                    .allCells(pos, state.getValue(OffshorePumpBlock.FACING), 1, 2)) {
                BlockState cellState = level.getBlockState(cell);
                if (cellState.getBlock() instanceof OffshorePumpBlock
                        && cellState.getValue(OffshorePumpBlock.ACTIVE) != producing) {
                    level.setBlock(cell, cellState.setValue(OffshorePumpBlock.ACTIVE, producing), 3);
                }
            }
        }
    }

    private boolean nearWater(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos np = pos.relative(dir);
            if (!level.isLoaded(np)) {
                continue;
            }
            if (level.getFluidState(np).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
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
        output.putInt("Cooldown", cooldown);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        steam = input.getIntOr("Steam", 0);
        cooldown = input.getIntOr("Cooldown", 0);
    }
}
