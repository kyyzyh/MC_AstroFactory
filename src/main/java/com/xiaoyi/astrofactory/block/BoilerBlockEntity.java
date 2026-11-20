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
import com.xiaoyi.astrofactory.power.FuelPowerSource;
import com.xiaoyi.astrofactory.power.ISteamNode;
import com.xiaoyi.astrofactory.power.SteamNetwork;

/**
 * 锅炉方块实体（规格 §5.5）：烧煤产蒸汽，蒸汽注入相邻管道。
 *
 * <p>燃料槽 1 格（机械臂送煤）+ 自带蒸汽缓冲。
 * 蒸汽通过 {@link SteamNetwork} 均摊给相邻节点。</p>
 */
public class BoilerBlockEntity extends BlockEntity implements Container, MenuProvider, ISteamNode {
    public static final int FUEL_SLOT = 0;
    public static final int TOTAL_SLOTS = 1;

    /** 每 tick 产蒸汽量。 */
    public static final int STEAM_PER_TICK = 1;
    /** 蒸汽缓冲上限。 */
    public static final int STEAM_CAPACITY = 100;

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private final FuelPowerSource power = new FuelPowerSource(this, FUEL_SLOT);
    private int steam = 0;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BOILER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof BoilerBlock blk && state.getValue(BoilerBlock.PART) == 0) {
            blk.repair(level, this.getBlockPos(), state);
        }
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        boolean hasFuel = power.consume(true);
        boolean producing = false;

        if (hasFuel && steam < STEAM_CAPACITY) {
            power.consume(false);
            steam = Math.min(STEAM_CAPACITY, steam + STEAM_PER_TICK);
            producing = true;
        }

        // 蒸汽从整机边缘向外扩散（多方块：master 邻居全是自己的 dummy）
        if (SteamNetwork.equalizeFromCells(level,
                com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                        .allCells(pos, state.getValue(BoilerBlock.FACING),
                                blkSizeX(state), blkSizeZ(state)),
                this)) {
            setChanged();
        }

        boolean active = producing;
        if (state.getValue(BoilerBlock.ACTIVE) != active) {
            for (BlockPos cell : com.xiaoyi.astrofactory.multiblock.MultiBlockHelper
                    .allCells(pos, state.getValue(BoilerBlock.FACING),
                            blkSizeX(state), blkSizeZ(state))) {
                BlockState cellState = level.getBlockState(cell);
                if (cellState.getBlock() instanceof BoilerBlock
                        && cellState.getValue(BoilerBlock.ACTIVE) != active) {
                    level.setBlock(cell, cellState.setValue(BoilerBlock.ACTIVE, active), 3);
                }
            }
        }
    }

    private static int blkSizeX(BlockState state) {
        return state.getBlock() instanceof BoilerBlock b ? b.sizeX() : 2;
    }

    private static int blkSizeZ(BlockState state) {
        return state.getBlock() instanceof BoilerBlock b ? b.sizeZ() : 3;
    }

    // ------------------------------------------------------------ ISteamNode

    @Override
    public int getSteam() {
        return steam;
    }

    @Override
    public int getSteamCapacity() {
        return STEAM_CAPACITY;
    }

    @Override
    public void setSteam(int amount) {
        this.steam = Math.max(0, Math.min(STEAM_CAPACITY, amount));
        setChanged();
    }

    // ------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.get(FUEL_SLOT).isEmpty();
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

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FUEL_SLOT && com.xiaoyi.astrofactory.power.PowerSource.fuelTicks(stack) > 0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ------------------------------------------------------------ MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astrofactory.boiler");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new com.xiaoyi.astrofactory.menu.BoilerMenu(windowId, inventory, this);
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.list("Items", ItemStack.CODEC);
        for (ItemStack stack : items) {
            // 空槽不能写进 ItemStack.CODEC（count 必须 1..99 且不能是 air），
            // 否则每次存档都会刷 "Failed to append value '0 minecraft:air'" 警告。
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        output.putInt("Steam", steam);
        output.putInt("BurnBudget", power.getBurnBudgetForSave());
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
        steam = input.getIntOr("Steam", 0);
        power.setBurnBudgetForLoad(input.getIntOr("BurnBudget", 0));
    }

    /** 燃料状态（供 GUI）。 */
    public String getFuelStatus() {
        return power.statusText();
    }

    public int getSteamAmount() {
        return steam;
    }
}
