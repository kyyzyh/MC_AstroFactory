package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.machine.AbstractMachineEntity;
import com.xiaoyi.astrofactory.menu.BoilerMenu;
import com.xiaoyi.astrofactory.power.FuelPowerSource;
import com.xiaoyi.astrofactory.power.ISteamNode;
import com.xiaoyi.astrofactory.power.PowerSource;
import com.xiaoyi.astrofactory.power.SteamNetwork;

/**
 * 锅炉方块实体（规格 §5.5）：烧煤产蒸汽，蒸汽注入相邻管道。
 *
 * <p>燃料槽 1 格（机械臂送煤）+ 自带蒸汽缓冲。
 * 蒸汽通过 {@link SteamNetwork} 均摊给相邻节点。</p>
 */
public class BoilerBlockEntity extends AbstractMachineEntity implements ISteamNode {
    public static final int FUEL_SLOT = 0;
    public static final int TOTAL_SLOTS = 1;

    /** 每 tick 产蒸汽量。 */
    public static final int STEAM_PER_TICK = 1;
    /** 蒸汽缓冲上限。 */
    public static final int STEAM_CAPACITY = 100;

    private final FuelPowerSource power = new FuelPowerSource(this, FUEL_SLOT);
    private int steam = 0;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BOILER_BE.get(), pos, state, TOTAL_SLOTS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        be.tickServer(level, pos, state);
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
        if (SteamNetwork.equalizeFromCells(level, allCells(), this)) {
            setChanged();
        }

        setActive(producing);
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

    // ------------------------------------------------------------ 容器

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FUEL_SLOT && PowerSource.fuelTicks(stack) > 0;
    }

    // ------------------------------------------------------------ MenuProvider

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.astrofactory.boiler");
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory inventory, Player player) {
        return new BoilerMenu(containerId, inventory, this);
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveMachineData(ValueOutput output) {
        output.putInt("Steam", steam);
        output.putInt("BurnBudget", power.getBurnBudgetForSave());
    }

    @Override
    protected void loadMachineData(ValueInput input) {
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
