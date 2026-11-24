package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Map;

/**
 * 燃料发电机：烧可燃物产生 FE，存入内部缓冲，供电缆抽取。
 *
 * <p>燃料表内置而非查询原版燃烧时间——避免 1.21.1 燃烧时间 API 的版本差异，
 * 同时让数值可精确平衡。</p>
 */
public class GeneratorBlockEntity extends FactoryBlockEntity {

    /** 内部储能上限（FE）。 */
    public static final int CAPACITY = 100_000;
    /** 每 tick 发电量（FE/t）。 */
    public static final int GENERATION_PER_TICK = 40;
    /** 对外最大输出（FE/t）。 */
    public static final int MAX_EXTRACT = 200;

    /** 燃料 → 可燃烧 tick 数。数值贴近原版但略作简化。 */
    private static final Map<String, Integer> FUELS = Map.ofEntries(
            Map.entry("minecraft:coal", 1600),
            Map.entry("minecraft:charcoal", 1600),
            Map.entry("minecraft:coal_block", 16000),
            Map.entry("minecraft:blaze_rod", 2400),
            Map.entry("minecraft:lava_bucket", 20000),
            Map.entry("minecraft:dried_kelp_block", 4000),
            Map.entry("minecraft:oak_planks", 300),
            Map.entry("minecraft:oak_log", 300),
            Map.entry("minecraft:stick", 100),
            Map.entry("minecraft:crafting_table", 300),
            Map.entry("minecraft:oak_slab", 150)
    );

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final GeneratorEnergyStorage energy =
            new GeneratorEnergyStorage(CAPACITY, MAX_EXTRACT, this);

    /** 当前这份燃料还能烧多少 tick。 */
    private int burnTime = 0;
    /** 当前燃料的总燃烧时长，用于计算进度。 */
    private int burnTotal = 0;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state);
    }

    /** 只出不进的储能：发电机不接收外部能量，但允许自身发电写入。 */
    public static class GeneratorEnergyStorage extends EnergyStorage {
        private final GeneratorBlockEntity owner;

        GeneratorEnergyStorage(int capacity, int maxExtract, GeneratorBlockEntity owner) {
            super(capacity, 0, maxExtract);
            this.owner = owner;
        }

        /**
         * 发电机内部发电写电：必须绕过 maxReceive=0 的限制，
         * 否则 EnergyStorage 会直接拒绝一切写入。
         */
        void generate(int amount) {
            int space = this.capacity - this.energy;
            this.energy += Math.min(amount, space);
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

    public EnergyStorage getEnergy() {
        return energy;
    }

    public ItemStackHandler getFuelSlot() {
        return fuel;
    }

    public boolean isBurning() {
        return this.burnTime > 0;
    }

    public int getBurnTime() {
        return this.burnTime;
    }

    public int getBurnTotal() {
        return this.burnTotal;
    }

    @Override
    public void serverTick() {
        boolean wasBurning = isBurning();

        // 1) 消耗燃料
        if (this.burnTime > 0) {
            this.burnTime--;
        }

        // 2) 燃料耗尽则尝试点燃下一份
        if (this.burnTime <= 0) {
            tryIgnite();
        }

        // 3) 发电
        if (this.burnTime > 0) {
            energy.generate(GENERATION_PER_TICK);
        }

        if (wasBurning != isBurning()) {
            setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private void tryIgnite() {
        ItemStack stack = fuel.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        String id = stack.getItem().builtInRegistryHolder().key().location().toString();
        Integer ticks = FUELS.get(id);
        if (ticks == null) {
            return;
        }
        fuel.extractItem(0, 1, false);
        this.burnTime = ticks;
        this.burnTotal = ticks;
        setChanged();
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.put("Energy", energy.serializeNBT(registries));
        tag.putInt("BurnTime", this.burnTime);
        tag.putInt("BurnTotal", this.burnTotal);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Fuel")) {
            fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        }
        if (tag.contains("Energy")) {
            energy.deserializeNBT(registries, tag.get("Energy"));
        }
        this.burnTime = tag.getInt("BurnTime");
        this.burnTotal = tag.getInt("BurnTotal");
    }
}
