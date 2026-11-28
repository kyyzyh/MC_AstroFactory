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
public class SteamEngineBlockEntity extends FactoryBlockEntity
        implements net.minecraft.world.MenuProvider {

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.astrofactory.steam_engine");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory playerInv,
            net.minecraft.world.entity.player.Player player) {
        return new com.astrofactory.menu.SteamEngineMenu(containerId, playerInv, this);
    }

    /** 内部储能上限（FE），配置项 steamEngine.capacity（蓝图基准 10,000）。 */
    public static int capacity() {
        return com.astrofactory.config.AstroFactoryConfig.STEAM_ENGINE_CAPACITY.get();
    }

    /** 每 tick 发电上限（FE/t），配置项 steamEngine.generationPerTick。 */
    public static int generationPerTick() {
        return com.astrofactory.config.AstroFactoryConfig.STEAM_ENGINE_GENERATION_PER_TICK.get();
    }

    /** 对外最大输出（FE/t），配置项 steamEngine.maxExtract。 */
    public static int maxExtract() {
        return com.astrofactory.config.AstroFactoryConfig.STEAM_ENGINE_MAX_EXTRACT.get();
    }

    /** 1 蒸汽转化的 FE，配置项 steamEngine.fePerSteam。 */
    public static int fePerSteam() {
        return com.astrofactory.config.AstroFactoryConfig.STEAM_ENGINE_FE_PER_STEAM.get();
    }

    /** 电缓冲会随配置变化，故每次构造时按当前配置新建。 */
    private final SteamEnergyStorage energy = new SteamEnergyStorage(this);

    /** 累计发电量（FE）：用于统计与界面显示，不受电缆抽电影响。 */
    private int generatedTotal = 0;
    /** 累计消耗的蒸汽量（GUI 显示用）。 */
    private int steamConsumed = 0;

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ENGINE.get(), pos, state);
    }

    /**
     * 锅炉注汽：把蒸汽换算成电能存进内部缓冲。
     *
     * <p>清单要求「缓冲满暂停发电不浪费蒸汽」——所以这里只吃缓冲装得下的蒸汽，
     * 装不下的原样退回锅炉（返回剩余量），锅炉会留着下次再送。</p>
     *
     * @return 装不下的剩余蒸汽
     */
    public int receiveSteam(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int fePer = fePerSteam();
        int space = this.energy.getMaxEnergyStored() - this.energy.getEnergyStored();
        int steamSpace = space / fePer;
        // 单 tick 转换量不超过发电上限对应的蒸汽量，避免一次灌爆
        int perTickSteam = Math.max(1, generationPerTick() / fePer);
        int taken = Math.min(amount, Math.min(steamSpace, perTickSteam));
        if (taken > 0) {
            int fe = taken * fePer;
            this.energy.generate(fe);
            this.generatedTotal += fe;
            this.steamConsumed += taken;
            setChanged();
        }
        return amount - taken;
    }

    /** 累计发电量（FE），从放置起算。 */
    public int getGeneratedTotal() {
        return this.generatedTotal;
    }

    /** 累计消耗蒸汽量。 */
    public int getSteamConsumed() {
        return this.steamConsumed;
    }

    /** 当前储能（FE）。 */
    public int getEnergyStored() {
        return this.energy.getEnergyStored();
    }

    /** 储能上限（FE）。 */
    public int getEnergyCapacity() {
        return this.energy.getMaxEnergyStored();
    }

    public EnergyStorage getEnergy() {
        return this.energy;
    }

    @Override
    public void serverTick() {
        // 蒸汽机本身不主动发电：能量在 receiveSteam 时已转化。
        // 有储能 = 收到过蒸汽正在运转 → 驱动飞轮/活塞动画。
        setLit(getEnergyStored() > 0);
    }

    /** 更新方块 LIT 状态（驱动飞轮/活塞动画的开关）。 */
    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(com.astrofactory.block.SteamEngineBlock.LIT)
                && state.getValue(com.astrofactory.block.SteamEngineBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(com.astrofactory.block.SteamEngineBlock.LIT, lit), 3);
        }
    }

    /** 只出不进的储能：蒸汽机不接收外部能量，但允许蒸汽转化写入。 */
    public static class SteamEnergyStorage extends EnergyStorage {
        private final SteamEngineBlockEntity owner;

        SteamEnergyStorage(SteamEngineBlockEntity owner) {
            // 容量与输出上限都从配置读，构造时定死（改配置需重启世界，属正常预期）
            super(capacity(), 0, maxExtract());
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
        tag.putInt("SteamConsumed", this.steamConsumed);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Energy")) {
            this.energy.deserializeNBT(registries, tag.get("Energy"));
        }
        this.generatedTotal = tag.getInt("GeneratedTotal");
        this.steamConsumed = tag.getInt("SteamConsumed");
    }
}
