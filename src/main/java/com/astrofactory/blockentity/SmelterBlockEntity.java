package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Map;

/**
 * 自动熔炉：从背后/侧面接收矿石，自动烧炼后把成品推出到前方。
 *
 * <p>内置映射表而非走配方系统，好处是不依赖 RecipeManager 的版本差异，
 * 且行为完全可预测。覆盖原版主要矿石。</p>
 */
public class SmelterBlockEntity extends PoweredBlockEntity implements IMachineInfo {

    private static final int SMELT_TICKS = 100;
    /** 每次烧炼消耗的能量（FE）。 */
    public static final int ENERGY_PER_SMELT = 400;
    /** 内部储能上限（FE）。 */
    public static final int ENERGY_CAPACITY = 20_000;
    /** 最大充能速度（FE/t）。 */
    public static final int MAX_RECEIVE = 200;

    /** 输入 → 输出 的烧炼映射。 */
    private static final Map<String, ItemStack> RECIPES = Map.ofEntries(
            Map.entry("minecraft:raw_iron", new ItemStack(Items.IRON_INGOT)),
            Map.entry("minecraft:raw_gold", new ItemStack(Items.GOLD_INGOT)),
            Map.entry("minecraft:raw_copper", new ItemStack(Items.COPPER_INGOT)),
            Map.entry("minecraft:iron_ore", new ItemStack(Items.IRON_INGOT)),
            Map.entry("minecraft:deepslate_iron_ore", new ItemStack(Items.IRON_INGOT)),
            Map.entry("minecraft:gold_ore", new ItemStack(Items.GOLD_INGOT)),
            Map.entry("minecraft:deepslate_gold_ore", new ItemStack(Items.GOLD_INGOT)),
            Map.entry("minecraft:copper_ore", new ItemStack(Items.COPPER_INGOT)),
            Map.entry("minecraft:deepslate_copper_ore", new ItemStack(Items.COPPER_INGOT)),
            Map.entry("minecraft:cobblestone", new ItemStack(Items.STONE)),
            Map.entry("minecraft:cobbled_deepslate", new ItemStack(Items.DEEPSLATE)),
            Map.entry("minecraft:sand", new ItemStack(Items.GLASS)),
            Map.entry("minecraft:clay_ball", new ItemStack(Items.BRICK)),
            Map.entry("minecraft:clay", new ItemStack(Items.TERRACOTTA)),
            Map.entry("minecraft:netherrack", new ItemStack(Items.NETHER_BRICK)),
            Map.entry("minecraft:potato", new ItemStack(Items.BAKED_POTATO)),
            Map.entry("minecraft:beef", new ItemStack(Items.COOKED_BEEF)),
            Map.entry("minecraft:porkchop", new ItemStack(Items.COOKED_PORKCHOP)),
            Map.entry("minecraft:chicken", new ItemStack(Items.COOKED_CHICKEN)),
            Map.entry("minecraft:mutton", new ItemStack(Items.COOKED_MUTTON)),
            Map.entry("minecraft:cod", new ItemStack(Items.COOKED_COD)),
            Map.entry("minecraft:salmon", new ItemStack(Items.COOKED_SALMON)),
            Map.entry("minecraft:kelp", new ItemStack(Items.DRIED_KELP))
    );

    /**
     * v0.6 钢材二次冶炼：铁锭 → 钢板（对应异星工厂 steel processing）。
     * 需「钢材」科技，未解锁时熔炉不接铁锭炼钢。
     */
    private static final String STEEL_INPUT = "minecraft:iron_ingot";

    private final ItemStackHandler input = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int progress = 0;

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER.get(), pos, state, ENERGY_CAPACITY, MAX_RECEIVE);
    }

    public ItemStackHandler getInput() {
        return input;
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    // ------------------------------------------------------------ IMachineInfo 信息面板

    @Override
    public String infoTitle() {
        return com.astrofactory.registry.ModBlocks.SMELTER.get().getName().getString();
    }

    @Override
    public String infoStatusKey() {
        if (this.level == null) {
            return "idle";
        }
        if (getPowerMode() == PowerMode.FUEL) {
            if (!isBurning()) {
                return "no_fuel";
            }
        } else if (!hasEnergy(ENERGY_PER_SMELT)) {
            return "no_power";
        }
        if (input.getStackInSlot(0).isEmpty()) {
            return "idle";
        }
        return "working";
    }

    @Override
    public int infoEnergy() {
        return getEnergyStored();
    }

    @Override
    public int infoEnergyCapacity() {
        return ENERGY_CAPACITY;
    }

    @Override
    public int infoProgress() {
        return this.progress;
    }

    @Override
    public int infoMaxProgress() {
        return techTicks(effectiveTicks(SMELT_TICKS));
    }

    @Override
    public ItemStackHandler infoInput() {
        return input;
    }

    @Override
    public ItemStackHandler infoOutput() {
        return output;
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 先把成品往前推
        pushOutput(serverLevel);

        ItemStack inSlot = input.getStackInSlot(0);
        if (inSlot.isEmpty()) {
            setLit(false);
            this.progress = 0;
            return;
        }

        ItemStack result = RECIPES.get(inSlot.getItem().builtInRegistryHolder().key().location().toString());
        // v0.6 钢材二次冶炼：铁锭 → 钢板（需「钢材」科技）
        if (result == null && STEEL_INPUT.equals(inSlot.getItem().builtInRegistryHolder().key().location().toString())) {
            if (this.level != null && this.level.getServer() != null
                    && com.astrofactory.tech.TechTreeData.get(this.level.getServer())
                            .isUnlocked(com.astrofactory.tech.TechNodes.STEEL)) {
                result = new ItemStack(com.astrofactory.registry.ModItems.STEEL_PLATE.get());
            }
        }
        if (result == null) {
            setLit(false);
            this.progress = 0;
            return;
        }

        // 输出槽被不同物品占满则停止
        ItemStack outSlot = output.getStackInSlot(0);
        if (!outSlot.isEmpty() && !ItemStack.isSameItemSameComponents(outSlot, result)) {
            setLit(false);
            return;
        }
        if (!outSlot.isEmpty() && outSlot.getCount() >= outSlot.getMaxStackSize()) {
            setLit(false);
            return;
        }

        // 燃料模式：推进燃烧；无燃料/无电则不烧
        if (getPowerMode() == PowerMode.FUEL) {
            tickFuel();
        }
        if (!canWork(ENERGY_PER_SMELT)) {
            setLit(false);
            return;
        }

        setLit(true);
        this.progress++;
        if (this.progress >= techTicks(effectiveTicks(SMELT_TICKS))) {
            this.progress = 0;
            consumeWorkCost(ENERGY_PER_SMELT);
            input.extractItem(0, 1, false);
            if (outSlot.isEmpty()) {
                output.setStackInSlot(0, result.copy());
            } else {
                outSlot.grow(1);
                output.setStackInSlot(0, outSlot);
            }
            serverLevel.playSound(null, this.worldPosition, SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.4F, 1.0F);
        }
    }

    /** 高效冶炼科技：熔炉速度 +30%。 */
    @Override
    protected double techSpeedMultiplier() {
        if (this.level == null || this.level.getServer() == null) {
            return 1.0;
        }
        return com.astrofactory.tech.TechTreeData.get(this.level.getServer()).smeltingSpeedMultiplier();
    }

    /** 把成品推出到前方相邻容器。 */
    private void pushOutput(ServerLevel level) {
        ItemStack outSlot = output.getStackInSlot(0);
        if (outSlot.isEmpty()) {
            return;
        }
        Direction facing = getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        BlockPos target = this.worldPosition.relative(facing);
        // 前方是同类熔炉且它的输出面正对着我们时，推过去会塞进它的成品槽；
        // 这种情况交给玩家改朝向，本 tick 不推，避免两个熔炉互相灌成品。
        if (level.getBlockState(target).getBlock() instanceof com.astrofactory.block.SmelterBlock) {
            return;
        }
        ItemStack remaining = com.astrofactory.util.ItemTransferUtil.insertInto(
                level, target, facing.getOpposite(), outSlot.copy());
        if (remaining.getCount() != outSlot.getCount()) {
            int moved = outSlot.getCount() - remaining.getCount();
            output.extractItem(0, moved, false);
        }
    }

    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(com.astrofactory.block.SmelterBlock.LIT)
                && state.getValue(com.astrofactory.block.SmelterBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(com.astrofactory.block.SmelterBlock.LIT, lit), 3);
        }
    }

    /** 拆熔炉时把输入槽与输出槽的成品一起掉出来。 */
    @Override
    public void addContentDrops(java.util.List<ItemStack> drops) {
        super.addContentDrops(drops);
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveData(tag, registries);
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadData(tag, registries);
        if (tag.contains("Input")) {
            input.deserializeNBT(registries, tag.getCompound("Input"));
        }
        if (tag.contains("Output")) {
            output.deserializeNBT(registries, tag.getCompound("Output"));
        }
        this.progress = tag.getInt("Progress");
    }
}
