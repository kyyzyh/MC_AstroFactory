package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * 蒸汽机：吃锅炉送来的蒸汽发电（对应异星工厂 steam engine）。
 *
 * <p>1 蒸汽 = 20 FE，40 FE/t 输出，储能 100k FE，
 * 供电缆抽取——与燃料发电机同规格，但燃料换成了「水 + 燃料」的蒸汽链。</p>
 */
public class SteamEngineBlockEntity extends FactoryBlockEntity {

    /** 内部储能上限（FE）。 */
    public static final int CAPACITY = 100_000;
    /** 每 tick 发电量（FE/t）。 */
    public static final int GENERATION_PER_TICK = 40;
    /** 对外最大输出（FE/t）。 */
    public static final int MAX_EXTRACT = 200;
    /** 1 蒸汽转化的 FE。 */
    public static final int FE_PER_STEAM = 20;

    private final SteamEnergyStorage energy = new SteamEnergyStorage(CAPACITY, MAX_EXTRACT, this);

    /** 累计发电量（FE）：用于统计与界面显示，不受电缆抽电影响。 */
    private int generatedTotal = 0;

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ENGINE.get(), pos, state);
    }

    /** 锅炉注汽，返回剩余蒸汽量。 */
    public int receiveSteam(int amount) {
        int space = this.energy.getMaxEnergyStored() - this.energy.getEnergyStored();
        int steamSpace = space / FE_PER_STEAM;
        int taken = Math.min(amount, steamSpace);
        if (taken > 0) {
            int fe = taken * FE_PER_STEAM;
            this.energy.generate(fe);
            this.generatedTotal += fe;
            setChanged();
        }
        return amount - taken;
    }

    /** 累计发电量（FE），从放置起算。 */
    public int getGeneratedTotal() {
        return this.generatedTotal;
    }

    public EnergyStorage getEnergy() {
        return this.energy;
    }

    @Override
    public void serverTick() {
        // 蒸汽机本身不主动发电：能量在 receiveSteam 时已转化。
        // 这里留空保持 tick 调度结构一致（电缆按需抽取）。
    }

    /** 只出不进的储能：蒸汽机不接收外部能量，但允许蒸汽转化写入。 */
    public static class SteamEnergyStorage extends EnergyStorage {
        private final SteamEngineBlockEntity owner;

        SteamEnergyStorage(int capacity, int maxExtract, SteamEngineBlockEntity owner) {
            super(capacity, 0, maxExtract);
            this.owner = owner;
        }

        /** 蒸汽转化写电：绕过 maxReceive=0 的限制。 */
        void generate(int amount) {
            this.energy += Math.min(amount, this.capacity - this.energy);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int extracted = super.extractEnergy(toExtract, simulate);
            if (extracted > 0 && !simulate) {
                owner.setChanged();
            }
            return extracted;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Energy", this.energy.serializeNBT(registries));
        tag.putInt("GeneratedTotal", this.generatedTotal);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Energy")) {
            this.energy.deserializeNBT(registries, tag.get("Energy"));
        }
        this.generatedTotal = tag.getInt("GeneratedTotal");
    }
}
