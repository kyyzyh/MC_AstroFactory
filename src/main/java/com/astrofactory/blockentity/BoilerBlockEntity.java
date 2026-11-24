package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Map;

/**
 * 锅炉：烧燃料把抽水机送来的水变成蒸汽，推送给相邻的蒸汽机
 * （对应异星工厂 boiler）。
 *
 * <p>燃料表与发电机一致；水/蒸汽只走内部缓冲 + 相邻直连。</p>
 */
public class BoilerBlockEntity extends FactoryBlockEntity {

    /** 水/蒸汽缓冲上限。 */
    public static final int FLUID_CAPACITY = 100;
    /**
     * 每 tick 产汽量。v0.6 调整为 2：1 台锅炉恰好供 2 台蒸汽机
     * （对应异星工厂 1 锅炉 60 蒸汽/秒配 2 蒸汽机各 30 蒸汽/秒）。
     */
    public static final int STEAM_PER_TICK = 2;

    private static final Map<String, Integer> FUELS = Map.ofEntries(
            Map.entry("minecraft:coal", 1600),
            Map.entry("minecraft:charcoal", 1600),
            Map.entry("minecraft:coal_block", 16000),
            Map.entry("minecraft:oak_planks", 300),
            Map.entry("minecraft:oak_log", 300),
            Map.entry("minecraft:stick", 100)
    );

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int water = 0;
    private int steam = 0;
    private int burnTime = 0;
    private int burnTotal = 0;
    /** 均摊蒸汽的轮转起点：产汽量小于蒸汽机台数时轮流先分，保证每台都能吃到。 */
    private int pushTurn = 0;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER.get(), pos, state);
    }

    // ---------------------------------------------------------------- 读写

    /** 抽水机注水，返回剩余水量（锅炉装不下的部分留在抽水机里）。 */
    public int receiveWater(int amount) {
        int space = FLUID_CAPACITY - this.water;
        int taken = Math.min(amount, space);
        if (taken > 0) {
            this.water += taken;
            setChanged();
        }
        return amount - taken;
    }

    public int getWater() {
        return this.water;
    }

    public int getSteam() {
        return this.steam;
    }

    public ItemStackHandler getFuelSlot() {
        return this.fuel;
    }

    public boolean isBurning() {
        return this.burnTime > 0;
    }

    /** 手持燃料右键投料。 */
    public boolean tryInsertFuel(ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        String id = held.getItem().builtInRegistryHolder().key().location().toString();
        if (!FUELS.containsKey(id)) {
            return false;
        }
        ItemStack inSlot = this.fuel.getStackInSlot(0);
        if (inSlot.isEmpty()) {
            this.fuel.setStackInSlot(0, held.copyWithCount(1));
            setChanged();
            return true;
        }
        if (ItemStack.isSameItemSameComponents(inSlot, held) && inSlot.getCount() < inSlot.getMaxStackSize()) {
            inSlot.grow(1);
            this.fuel.setStackInSlot(0, inSlot);
            setChanged();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void serverTick() {
        boolean wasBurning = isBurning();

        // 1) 燃烧计时
        if (this.burnTime > 0) {
            this.burnTime--;
        }
        if (this.burnTime <= 0) {
            igniteNextFuel();
        }

        // 2) 烧水成汽：有火且有水才产汽
        if (this.burnTime > 0 && this.water > 0 && this.steam < FLUID_CAPACITY) {
            int produced = Math.min(STEAM_PER_TICK, this.water);
            this.water -= produced;
            this.steam = Math.min(FLUID_CAPACITY, this.steam + produced);
        }

        // 3) 把蒸汽均摊给相邻蒸汽机（1 锅炉 : 2 蒸汽机时两台各得一半，对应异星工厂配比）
        if (this.steam > 0 && this.level != null) {
            java.util.List<SteamEngineBlockEntity> engines = new java.util.ArrayList<>(2);
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                if (this.level.getBlockEntity(this.worldPosition.relative(dir))
                        instanceof SteamEngineBlockEntity engine) {
                    engines.add(engine);
                }
            }
            if (!engines.isEmpty()) {
                // 从轮转起点开始均摊：产汽量可能小于蒸汽机台数（水 1/tick），
                // 若不轮转，第一台会永远把蒸汽吃光，第二台一台都分不到。
                int start = this.pushTurn % engines.size();
                int remaining = this.steam;
                for (int i = 0; i < engines.size() && remaining > 0; i++) {
                    SteamEngineBlockEntity engine = engines.get((start + i) % engines.size());
                    int share = Math.max(1, remaining / (engines.size() - i));
                    int leftover = engine.receiveSteam(share);
                    remaining -= share - leftover;
                }
                this.steam = remaining;
                this.pushTurn++;
            }
        }

        if (wasBurning != isBurning()) {
            setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private void igniteNextFuel() {
        ItemStack stack = this.fuel.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        String id = stack.getItem().builtInRegistryHolder().key().location().toString();
        Integer ticks = FUELS.get(id);
        if (ticks == null) {
            return;
        }
        this.fuel.extractItem(0, 1, false);
        this.burnTime = ticks;
        this.burnTotal = ticks;
        setChanged();
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Fuel", this.fuel.serializeNBT(registries));
        tag.putInt("Water", this.water);
        tag.putInt("Steam", this.steam);
        tag.putInt("BurnTime", this.burnTime);
        tag.putInt("BurnTotal", this.burnTotal);
        tag.putInt("PushTurn", this.pushTurn);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Fuel")) {
            this.fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        }
        this.water = tag.getInt("Water");
        this.steam = tag.getInt("Steam");
        this.burnTime = tag.getInt("BurnTime");
        this.burnTotal = tag.getInt("BurnTotal");
        this.pushTurn = tag.getInt("PushTurn");
    }
}
