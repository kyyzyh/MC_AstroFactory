package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import com.xiaoyi.astrofactory.power.ElectricPowerSource;
import com.xiaoyi.astrofactory.recipe.MachineRecipe;
import com.xiaoyi.astrofactory.recipe.MachineType;
import com.xiaoyi.astrofactory.recipe.RecipeRegistry;

/**
 * 组装机方块实体。
 *
 * 槽位布局：0..8 = 3x3 输入，9 = 输出。
 * 每 CRAFT_INTERVAL tick 检查一次输入是否匹配某条内置配方；
 * 匹配则消耗输入、产出结果（结果槽满则等待）。
 */
public class AssemblerBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOT = 9;
    public static final int TOTAL_SLOTS = 10;
    /** 默认合成耗时；实际耗时取配方里的 time 字段。 */
    public static final int CRAFT_INTERVAL = MachineType.ASSEMBLING.defaultTicks();

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private int craftProgress = 0;
    /** 电力驱动（规格 §5.4：组装机是电动的，断电停机）。 */
    private final ElectricPowerSource power = new ElectricPowerSource(this);

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ASSEMBLER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblerBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    /**
     * 区块加载时自修复多方块结构（规格 §5.1）。
     */
    @Override
    public void onLoad() {
        super.onLoad();
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof AssemblerBlock blk && state.getValue(AssemblerBlock.PART) == 0) {
            blk.repair(level, this.getBlockPos(), state);
        }
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        // 输入全空时无需匹配配方（也避免无谓的配方表初始化）
        MachineRecipe match = hasAnyInput() ? RecipeRegistry.findGrid(items) : null;
        // 断电停机（规格 §5.4）：没有电力则无法合成
        boolean powered = power.consume(true);
        boolean active = match != null && powered;

        if (active) {
            power.consume(false);
            int interval = match.ticks();
            craftProgress++;
            if (craftProgress >= interval) {
                craftProgress = 0;
                if (craft(match)) {
                    setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        } else {
            craftProgress = 0;
        }

        if (state.getValue(AssemblerBlock.ACTIVE) != active) {
            // ACTIVE 必须整机 9 格一起切，保证外观一致
            for (BlockPos cell : com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                    .allCells(pos, state.getValue(AssemblerBlock.FACING), 3)) {
                BlockState cellState = level.getBlockState(cell);
                if (cellState.getBlock() instanceof AssemblerBlock
                        && cellState.getValue(AssemblerBlock.ACTIVE) != active) {
                    level.setBlock(cell, cellState.setValue(AssemblerBlock.ACTIVE, active), 3);
                }
            }
        }
    }

    private boolean hasAnyInput() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 执行合成；成功返回 true。 */
    private boolean craft(MachineRecipe recipe) {
        ItemStack result = recipe.resultStack();
        // 输出槽能否容纳
        ItemStack out = items.get(OUTPUT_SLOT);
        if (!recipe.fitsInto(out)) {
            return false;
        }
        // 只消耗配方要求的格子，多余物品原样留在输入槽
        for (int i = 0; i < INPUT_SLOTS; i++) {
            MachineRecipe.Ingredient need = recipe.inputs().get(i);
            if (need == null) {
                continue;
            }
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                return false;
            }
            stack.shrink(need.count());
            if (stack.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
            }
        }
        // 产出
        if (out.isEmpty()) {
            items.set(OUTPUT_SLOT, result);
        } else {
            out.grow(result.getCount());
        }
        return true;
    }

    /** 尝试从外部插入（机械臂/传送带）。 */
    public boolean insertItem(ItemStack stack) {
        // 先合并
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                setChanged();
                return true;
            }
        }
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.copyWithCount(1));
                setChanged();
                return true;
            }
        }
        return false;
    }

    /** 当前合成进度百分比，供界面显示。 */
    public int getProgressPercent() {
        MachineRecipe recipe = hasAnyInput() ? RecipeRegistry.findGrid(items) : null;
        int interval = recipe == null ? CRAFT_INTERVAL : recipe.ticks();
        return Math.min(100, craftProgress * 100 / Math.max(1, interval));
    }

    /** 是否通电（供界面显示"无电力"）。 */
    public boolean isPowered() {
        return power.isPowered();
    }

    /** 电力状态文本。 */
    public String getPowerStatus() {
        return power.statusText();
    }

    public ItemStack extractResult() {
        ItemStack out = items.get(OUTPUT_SLOT);
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(OUTPUT_SLOT, ItemStack.EMPTY);
        setChanged();
        return out;
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

    /** 输出槽禁止外部插入（只能通过内部合成产出）。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != OUTPUT_SLOT;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ------------------------------------------------------------ MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astrofactory.assembler");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new com.xiaoyi.astrofactory.menu.AssemblerMenu(windowId, inventory, this);
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.list("Items", ItemStack.CODEC);
        for (ItemStack stack : items) {
            list.add(stack);
        }
        output.putInt("CraftProgress", craftProgress);
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
        craftProgress = input.getIntOr("CraftProgress", 0);
    }
}
