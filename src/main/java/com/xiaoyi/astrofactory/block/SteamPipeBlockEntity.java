package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.power.ISteamNode;
import com.xiaoyi.astrofactory.power.SteamNetwork;

/**
 * 蒸汽管道方块实体：一个纯蒸汽缓冲节点。
 */
public class SteamPipeBlockEntity extends BlockEntity implements ISteamNode {
    public static final int CAPACITY = 20;

    private int steam = 0;

    public SteamPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.STEAM_PIPE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SteamPipeBlockEntity be) {
        // 空管道不 tick，省性能（规格 §6 性能：空带不 tick）
        if (be.steam == 0 && !be.hasSteamNeighbor(level, pos)) {
            return;
        }
        if (SteamNetwork.equalize(level, pos, be)) {
            be.setChanged();
        }
    }

    private boolean hasSteamNeighbor(Level level, BlockPos pos) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos np = pos.relative(dir);
            if (!level.isLoaded(np)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(np);
            if (be instanceof ISteamNode node && node.getSteam() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getSteam() {
        return steam;
    }

    @Override
    public int getSteamCapacity() {
        return CAPACITY;
    }

    @Override
    public void setSteam(int amount) {
        this.steam = Math.max(0, Math.min(CAPACITY, amount));
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
