package com.astrofactory.blockentity;

import com.astrofactory.block.AssemblerBlock;
import com.astrofactory.registry.ModBlockEntities;
import com.astrofactory.util.ItemTransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * 组装机方块实体：电力自动合成（对应《异星工厂》的 assembling machine）。
 *
 * <p>工作方式：</p>
 * <ul>
 *   <li>3x3 输入网格 + 1 输出槽，玩家或机械臂放入原料；</li>
 *   <li>每 tick 检查网格是否匹配某个工作台配方（有输出空间时）；</li>
 *   <li>匹配后按周期推进进度，完成后消耗原料、产出成品——需要耗电；</li>
 *   <li>成品优先进入输出槽，输出槽满时尝试推给朝向的容器。</li>
 * </ul>
 */
public class AssemblerBlockEntity extends PoweredBlockEntity
        implements net.minecraft.world.MenuProvider {

    /** 每次合成消耗的能量（FE）。 */
    public static final int ENERGY_PER_CRAFT = 300;
    /** 内部储能上限（FE）。 */
    public static final int ENERGY_CAPACITY = 20_000;
    /** 最大充能速度（FE/t）。 */
    public static final int MAX_RECEIVE = 200;
    /** 基准合成周期（tick）。 */
    public static final int CRAFT_TICKS = 40;

    /** 槽 0-8 = 3x3 网格，槽 9 = 输出。 */
    private final ItemStackHandler slots = new ItemStackHandler(10) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int progress = 0;
    /** 当前在做的配方 id（锁定用），空串 = 自动匹配。 */
    private String lockedRecipe = "";

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLER.get(), pos, state, ENERGY_CAPACITY, MAX_RECEIVE);
        // 组装机是纯电力机器：没有燃料模式，直接进入电力模式（canReceive 才会放行）
        setPowerMode(PowerMode.ELECTRIC);
    }

    public ItemStackHandler getSlots() {
        return this.slots;
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astrofactory.assembler");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new com.astrofactory.menu.AssemblerMenu(containerId, playerInv, this.slots, this,
                net.minecraft.world.inventory.ContainerLevelAccess.create(this.level, this.worldPosition));
    }

    // ---------------------------------------------------------------- 逻辑

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 组装机是纯电力机器：无电不工作
        if (!hasEnergy(ENERGY_PER_CRAFT)) {
            setLit(false);
            return;
        }

        // 输出槽满则先尝试外推
        if (!this.slots.getStackInSlot(9).isEmpty() && !pushOutput()) {
            setLit(false);
            return;
        }

        // 匹配配方
        RecipeHolder<CraftingRecipe> holder = findRecipe(serverLevel);
        if (holder == null) {
            this.progress = 0;
            setLit(false);
            return;
        }

        setLit(true);
        this.progress++;
        if (this.progress < CRAFT_TICKS) {
            return;
        }
        this.progress = 0;

        // 产出
        CraftingInput input = makeInput();
        ItemStack result = holder.value().assemble(input, serverLevel.registryAccess());
        if (result.isEmpty()) {
            return;
        }

        // 消耗原料（每格 1 个）
        for (int i = 0; i < 9; i++) {
            this.slots.extractItem(i, 1, false);
        }

        // 扣电
        consumeEnergy(ENERGY_PER_CRAFT);

        // 产出进输出槽或外推
        ItemStack remaining = this.slots.insertItem(9, result, false);
        if (!remaining.isEmpty()) {
            remaining = pushToNeighbors(serverLevel, remaining);
        }
        if (!remaining.isEmpty()) {
            // 实在放不下就丢弃（与采矿机策略一致，避免卡死）
        }
        setChanged();
    }

    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (state.getValue(AssemblerBlock.LIT) != lit && this.level != null) {
            this.level.setBlock(this.worldPosition, state.setValue(AssemblerBlock.LIT, lit), 3);
        }
    }

    private CraftingInput makeInput() {
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            items.set(i, this.slots.getStackInSlot(i));
        }
        return CraftingInput.of(3, 3, items);
    }

    private RecipeHolder<CraftingRecipe> findRecipe(ServerLevel level) {
        // 输出槽有空间才匹配
        ItemStack out = this.slots.getStackInSlot(9);
        if (!out.isEmpty() && out.getCount() >= out.getMaxStackSize()) {
            return null;
        }
        CraftingInput input = makeInput();
        if (input.isEmpty()) {
            return null;
        }
        Optional<RecipeHolder<CraftingRecipe>> optional =
                level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (optional.isEmpty()) {
            return null;
        }
        // 锁定了配方则只做该配方
        if (!this.lockedRecipe.isEmpty()
                && !optional.get().id().toString().equals(this.lockedRecipe)) {
            return null;
        }
        return optional.get();
    }

    /** 输出槽内容推给朝向的容器。 */
    private boolean pushOutput() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Direction facing = getBlockState().getValue(AssemblerBlock.FACING);
        ItemStack out = this.slots.getStackInSlot(9);
        if (out.isEmpty()) {
            return true;
        }
        ItemStack remaining = ItemTransferUtil.insertInto(serverLevel,
                this.worldPosition.relative(facing), facing.getOpposite(), out);
        if (remaining.isEmpty()) {
            this.slots.setStackInSlot(9, ItemStack.EMPTY);
            setChanged();
            return true;
        }
        this.slots.setStackInSlot(9, remaining);
        return false;
    }

    private ItemStack pushToNeighbors(ServerLevel level, ItemStack stack) {
        Direction facing = getBlockState().getValue(AssemblerBlock.FACING);
        return ItemTransferUtil.insertInto(level,
                this.worldPosition.relative(facing), facing.getOpposite(), stack);
    }

    // ---------------------------------------------------------------- NBT

    /** 拆组装机时把 3x3 网格与输出槽的东西一起掉出来。 */
    @Override
    public void addContentDrops(java.util.List<ItemStack> drops) {
        super.addContentDrops(drops);
        for (int i = 0; i < this.slots.getSlots(); i++) {
            ItemStack stack = this.slots.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveData(tag, registries);
        tag.put("Slots", this.slots.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
        tag.putString("LockedRecipe", this.lockedRecipe);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadData(tag, registries);
        if (tag.contains("Slots")) {
            this.slots.deserializeNBT(registries, tag.getCompound("Slots"));
        }
        this.progress = tag.getInt("Progress");
        this.lockedRecipe = tag.getString("LockedRecipe");
    }
}
