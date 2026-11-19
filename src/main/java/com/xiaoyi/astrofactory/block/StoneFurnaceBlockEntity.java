package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.menu.StoneFurnaceMenu;
import com.xiaoyi.astrofactory.recipe.MachineRecipe;
import com.xiaoyi.astrofactory.recipe.MachineType;
import com.xiaoyi.astrofactory.recipe.RecipeRegistry;

/**
 * 熔炼炉方块实体：把原矿熔成锭。
 *
 * 槽位：0 = 输入（原矿），1 = 输出（锭）。
 * 产线位置：采矿机 -> 传送带 -> 【熔炼炉】 -> 制作台 -> 组装机
 */
public class StoneFurnaceBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int TOTAL_SLOTS = 2;

    /** 默认熔炼耗时；实际耗时取配方里的 time 字段。 */
    public static final int SMELT_INTERVAL = MachineType.SMELTING.defaultTicks();

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private int progress = 0;

    public StoneFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.STONE_FURNACE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StoneFurnaceBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        ItemStack input = items.get(INPUT_SLOT);
        MachineRecipe recipe = RecipeRegistry.findSingle(MachineType.SMELTING, input);
        boolean canWork = recipe != null && recipe.fitsInto(items.get(OUTPUT_SLOT));
        int interval = recipe == null ? SMELT_INTERVAL : recipe.ticks();

        if (canWork) {
            progress++;
            if (progress >= interval) {
                progress = 0;
                doSmelt(recipe);
            }
        } else {
            progress = 0;
        }

        boolean active = canWork && progress > 0;
        if (state.getValue(StoneFurnaceBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(StoneFurnaceBlock.ACTIVE, active), 3);
        }
    }

    /** 执行一次熔炼：按配方消耗输入、产出结果。 */
    private void doSmelt(MachineRecipe recipe) {
        ItemStack input = items.get(INPUT_SLOT);
        input.shrink(recipe.inputs().get(0).count());
        if (input.isEmpty()) {
            items.set(INPUT_SLOT, ItemStack.EMPTY);
        }
        ItemStack result = recipe.resultStack();
        ItemStack out = items.get(OUTPUT_SLOT);
        if (out.isEmpty()) {
            items.set(OUTPUT_SLOT, result);
        } else {
            out.grow(result.getCount());
        }
        setChanged();
    }

    /** 进度百分比，供界面显示。 */
    public int getProgressPercent() {
        MachineRecipe recipe = RecipeRegistry.findSingle(MachineType.SMELTING, items.get(INPUT_SLOT));
        int interval = recipe == null ? SMELT_INTERVAL : recipe.ticks();
        return Math.min(100, progress * 100 / Math.max(1, interval));
    }

    // ------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /** 输出槽禁止外部插入；输入槽只接受可熔炼的物品。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == OUTPUT_SLOT) {
            return false;
        }
        return slot == INPUT_SLOT && RecipeRegistry.isSingleInput(MachineType.SMELTING, stack);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ------------------------------------------------------------ MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astrofactory.smelter");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new StoneFurnaceMenu(windowId, inventory, this);
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.list("Items", ItemStack.CODEC);
        for (ItemStack stack : items) {
            list.add(stack);
        }
        output.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        var list = input.listOrEmpty("Items", ItemStack.CODEC);
        int i = 0;
        for (ItemStack stack : list) {
            if (i >= TOTAL_SLOTS) {
                break;
            }
            items.set(i++, stack);
        }
        progress = input.getIntOr("Progress", 0);
    }
}
