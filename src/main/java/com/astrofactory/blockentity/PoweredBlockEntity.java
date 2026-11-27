package com.astrofactory.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Map;

/**
 * 机器基类：同时支持「燃料时代」与「电力时代」两种驱动方式。
 *
 * <p>这是科技树的核心：</p>
 * <ul>
 *   <li><b>燃料模式</b>（开局默认）：机器自带一格燃料槽，烧木头或煤驱动，
 *       周期是电力模式的 2 倍慢；</li>
 *   <li><b>电力模式</b>（研究出电力后用扳手升级）：从电缆取电，速度翻倍，
 *       不再需要燃料。</li>
 * </ul>
 *
 * <p>燃料通过「手持可燃物右键机器」投入，无需 GUI——这是无界面时代的自然交互。</p>
 */
public abstract class PoweredBlockEntity extends FactoryBlockEntity {

    /** 驱动方式。 */
    public enum PowerMode {
        /** 烧燃料（木头/煤），速度慢。 */
        FUEL,
        /** 用电，速度正常。 */
        ELECTRIC;

        public static PowerMode byName(String name) {
            return "electric".equals(name) ? ELECTRIC : FUEL;
        }
    }

    /** 燃料 → 可燃烧 tick 数。数值贴近原版。 */
    private static final Map<String, Integer> FUELS = Map.ofEntries(
            Map.entry("minecraft:coal", 1600),
            Map.entry("minecraft:charcoal", 1600),
            Map.entry("minecraft:coal_block", 16000),
            Map.entry("minecraft:oak_planks", 300),
            Map.entry("minecraft:oak_log", 300),
            Map.entry("minecraft:oak_slab", 150),
            Map.entry("minecraft:stick", 100),
            Map.entry("minecraft:spruce_log", 300),
            Map.entry("minecraft:birch_log", 300),
            Map.entry("minecraft:jungle_log", 300),
            Map.entry("minecraft:acacia_log", 300),
            Map.entry("minecraft:dark_oak_log", 300),
            Map.entry("minecraft:crafting_table", 300),
            Map.entry("minecraft:blaze_rod", 2400),
            Map.entry("minecraft:lava_bucket", 20000),
            Map.entry("minecraft:dried_kelp_block", 4000)
    );

    private final MachineEnergyStorage energy;

    /** 燃料槽（仅燃料模式使用）。 */
    private final ItemStackHandler fuelSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private PowerMode powerMode = PowerMode.FUEL;
    /** 当前燃料剩余燃烧 tick。 */
    private int burnTime = 0;
    /** 当前燃料总燃烧 tick，用于进度。 */
    private int burnTotal = 0;

    protected PoweredBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                 int capacity, int maxReceive) {
        super(type, pos, state);
        this.energy = new MachineEnergyStorage(capacity, maxReceive, 0, this);
    }

    /** 机器专用能量缓冲：只进不出（机器自身不对外放电）。 */
    public static class MachineEnergyStorage extends EnergyStorage {
        private final PoweredBlockEntity owner;

        MachineEnergyStorage(int capacity, int maxReceive, int maxExtract, PoweredBlockEntity owner) {
            super(capacity, maxReceive, maxExtract);
            this.owner = owner;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            // 燃料模式的机器不接电：只有升级为电动后才会从电网取能
            if (owner.getPowerMode() == PowerMode.FUEL) {
                return 0;
            }
            int received = super.receiveEnergy(toReceive, simulate);
            if (received > 0 && !simulate) {
                owner.setChanged();
            }
            return received;
        }

        /**
         * 对外不放电（机器不是电源）。注意：这<b>不</b>意味着内部不能消耗——
         * 内部消耗走 {@link #drain(int)}，绕过 {@code canExtract}。
         *
         * <p>历史 bug（US-103 修）：这里返回 false 时，基类 {@code extractEnergy}
         * 会直接返回 0，导致机器<b>从不真正扣电</b>。之前自检只断言「电量 > 0」，
         * 所以没暴露出来。</p>
         */
        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return owner.getPowerMode() == PowerMode.ELECTRIC;
        }

        /**
         * 内部消耗：直接扣自己的 {@code energy}，不受 {@code maxExtract}/{@code canExtract} 限制。
         *
         * @return 实际扣掉的量
         */
        int drain(int amount) {
            if (amount <= 0 || this.energy <= 0) {
                return 0;
            }
            int taken = Math.min(this.energy, amount);
            this.energy -= taken;
            if (taken > 0) {
                owner.setChanged();
            }
            return taken;
        }
    }

    // ---------------------------------------------------------------- 能量

    public IEnergyStorage getEnergyBuffer() {
        return this.energy;
    }

    public int getEnergyStored() {
        return this.energy.getEnergyStored();
    }

    protected boolean consumeEnergy(int amount) {
        if (this.energy.getEnergyStored() < amount) {
            return false;
        }
        // 必须走 drain：extractEnergy 会被 canExtract()=false 拦成 0（见 MachineEnergyStorage）
        return this.energy.drain(amount) > 0;
    }

    protected boolean hasEnergy(int amount) {
        return this.energy.getEnergyStored() >= amount;
    }

    // ---------------------------------------------------------------- 驱动方式

    public PowerMode getPowerMode() {
        return this.powerMode;
    }

    public void setPowerMode(PowerMode mode) {
        if (this.powerMode == mode) {
            return;
        }
        this.powerMode = mode;
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 燃料模式下当前是否有火在烧。 */
    public boolean isBurning() {
        return this.burnTime > 0;
    }

    public int getBurnTime() {
        return this.burnTime;
    }

    public int getBurnTotal() {
        return this.burnTotal;
    }

    public ItemStackHandler getFuelSlot() {
        return this.fuelSlot;
    }

    /** 该物品是否可作为燃料。 */
    public static boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return FUELS.containsKey(stack.getItem().builtInRegistryHolder().key().location().toString());
    }

    /**
     * 尝试把手中的可燃物投入燃料槽。
     *
     * @return 是否投入成功（投入 1 个）
     */
    public boolean tryInsertFuel(ItemStack held) {
        if (this.powerMode != PowerMode.FUEL || !isFuel(held)) {
            return false;
        }
        ItemStack inSlot = this.fuelSlot.getStackInSlot(0);
        if (inSlot.isEmpty()) {
            this.fuelSlot.setStackInSlot(0, held.copyWithCount(1));
            return true;
        }
        if (ItemStack.isSameItemSameComponents(inSlot, held)
                && inSlot.getCount() < inSlot.getMaxStackSize()) {
            inSlot.grow(1);
            this.fuelSlot.setStackInSlot(0, inSlot);
            return true;
        }
        return false;
    }

    /** 每 tick 推进燃料燃烧（燃料模式下由子类在 serverTick 里调用）。 */
    protected void tickFuel() {
        if (this.burnTime > 0) {
            this.burnTime--;
        }
        if (this.burnTime <= 0) {
            igniteNextFuel();
        }
    }

    private void igniteNextFuel() {
        ItemStack stack = this.fuelSlot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        String id = stack.getItem().builtInRegistryHolder().key().location().toString();
        Integer ticks = FUELS.get(id);
        if (ticks == null) {
            return;
        }
        this.fuelSlot.extractItem(0, 1, false);
        this.burnTime = ticks;
        this.burnTotal = ticks;
        setChanged();
    }

    // ---------------------------------------------------------------- 统一工作门禁

    /**
     * 是否可以开始/继续工作。
     *
     * <p>燃料模式看是否有火在烧；电力模式看是否有足够电量。</p>
     */
    protected boolean canWork(int energyCost) {
        if (this.powerMode == PowerMode.ELECTRIC) {
            return hasEnergy(energyCost);
        }
        return this.burnTime > 0;
    }

    /** 扣一次工作成本。燃料模式的消耗已由 {@link #tickFuel()} 按 tick 计，这里不再扣。 */
    protected void consumeWorkCost(int energyCost) {
        if (this.powerMode == PowerMode.ELECTRIC) {
            consumeEnergy(energyCost);
        }
    }

    // ---------------------------------------------------------------- 每 tick 扣电（US-103）

    /**
     * 电力模式的「每 tick 扣电」统一入口（US-103）。
     *
     * <p>清单要求「复用 P0a 基类与预定机制，仅替换能源模块；禁止复制粘贴出第二套机器逻辑」。
     * 采矿机、机械臂（以及后续所有电动设备）都走这一个方法，不各写一份。</p>
     *
     * <p>语义：<b>每 tick</b> 扣 {@code fePerTick}，而不是「做完一次活才扣」。这样断电时
     * 电量立刻见底、设备随即停摆，符合「断电停当前状态、恢复继续」。燃料模式不扣电
     * （由 {@link #tickFuel()} 按 tick 烧燃料），直接返回 true。</p>
     *
     * @param fePerTick 电力模式下每 tick 应扣的电量
     * @return 是否还有电可以继续干活（燃料模式恒为 true）
     */
    protected boolean tickElectric(int fePerTick) {
        if (this.powerMode != PowerMode.ELECTRIC) {
            return true;
        }
        if (fePerTick <= 0) {
            return true;
        }
        if (this.energy.getEnergyStored() < fePerTick) {
            return false;
        }
        return this.energy.drain(fePerTick) > 0;
    }

    /** 当前模式是否处于「可运转」状态（供 GUI / 测试查询）。 */
    public boolean isRunning() {
        if (this.powerMode == PowerMode.ELECTRIC) {
            return this.energy.getEnergyStored() > 0;
        }
        return this.burnTime > 0;
    }

    /** 内部储能上限（GUI / 测试显示用）。 */
    public int getEnergyCapacity() {
        return this.energy.getMaxEnergyStored();
    }

    /** 清空内部电量（断电模拟用，测试与调试）。 */
    public void drainAllEnergy() {
        this.energy.drain(this.energy.getEnergyStored());
    }

    /**
     * 燃料模式的速度惩罚：周期 ×2。
     *
     * @param baseTicks 电力模式下的基准周期
     * @return 当前模式实际使用的周期
     */
    protected int effectiveTicks(int baseTicks) {
        return this.powerMode == PowerMode.ELECTRIC ? baseTicks : baseTicks * 2;
    }

    /**
     * 科技速度加成：子类按机器类型返回对应科技的乘数（1.0 = 无加成）。
     * 采矿机 = 高效采矿等级，熔炉 = 高效冶炼，其余默认 1.0。
     */
    protected double techSpeedMultiplier() {
        return 1.0;
    }

    /** 应用科技加成后的实际周期（向上取整，至少 1 tick）。 */
    protected int techTicks(int baseTicks) {
        return Math.max(1, (int) Math.ceil(baseTicks / techSpeedMultiplier()));
    }

    // ---------------------------------------------------------------- NBT

    /** 拆机器时把燃料槽里的东西一起掉出来。 */
    @Override
    public void addContentDrops(java.util.List<ItemStack> drops) {
        for (int i = 0; i < this.fuelSlot.getSlots(); i++) {
            ItemStack stack = this.fuelSlot.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Energy", this.energy.serializeNBT(registries));
        tag.put("FuelSlot", this.fuelSlot.serializeNBT(registries));
        tag.putString("PowerMode", this.powerMode == PowerMode.ELECTRIC ? "electric" : "fuel");
        tag.putInt("BurnTime", this.burnTime);
        tag.putInt("BurnTotal", this.burnTotal);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Energy")) {
            this.energy.deserializeNBT(registries, tag.get("Energy"));
        }
        if (tag.contains("FuelSlot")) {
            this.fuelSlot.deserializeNBT(registries, tag.getCompound("FuelSlot"));
        }
        if (tag.contains("PowerMode")) {
            this.powerMode = PowerMode.byName(tag.getString("PowerMode"));
        }
        this.burnTime = tag.getInt("BurnTime");
        this.burnTotal = tag.getInt("BurnTotal");
    }
}
