package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.machine.MultiBlockOps;
import com.xiaoyi.astrofactory.power.ElectricNetwork;
import com.xiaoyi.astrofactory.power.ISteamNode;
import com.xiaoyi.astrofactory.power.SteamNetwork;

import java.util.List;

/**
 * 蒸汽机方块实体（规格 §5.5）：消耗蒸汽 → 发电 → 灌入范围内电线杆。
 *
 * <p>每 tick 消耗 1 蒸汽产出 {@link #ENERGY_PER_STEAM} 能量。</p>
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
        MultiBlockOps.repairOnLoad(this);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        List<BlockPos> cells = MultiBlockOps.allCells(this);
        // 先从整机边缘补充蒸汽（多方块：master 邻居全是自己的 dummy）
        SteamNetwork.equalizeFromCells(level, cells, this);

        boolean active = false;
        if (steam > 0) {
            steam--;
            int pushed = ElectricNetwork.pushEnergyFromCells(level, cells, ENERGY_PER_STEAM);
            active = pushed > 0;
            if (pushed > 0) {
                setChanged();
            }
        }

        MultiBlockOps.setActive(this, active);
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
