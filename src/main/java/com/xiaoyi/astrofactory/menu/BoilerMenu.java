package com.xiaoyi.astrofactory.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import com.xiaoyi.astrofactory.block.BoilerBlockEntity;
import com.xiaoyi.astrofactory.power.PowerSource;

/**
 * 锅炉界面：1 燃料槽 + 玩家背包。
 * 同步数据：0 = 蒸汽量。
 */
public class BoilerMenu extends AbstractContainerMenu {
    public static final MenuType<BoilerMenu> TYPE =
            IMenuTypeExtension.create((windowId, inv, data) ->
                    new BoilerMenu(windowId, inv, new SimpleContainer(
                            BoilerBlockEntity.TOTAL_SLOTS)));

    private static final int FUEL_SLOT = 0;
    private static final int PLAYER_INV_START = 1;

    private final Container container;
    private final ContainerData data;

    public BoilerMenu(int windowId, Inventory playerInventory, Container container) {
        this(windowId, playerInventory, container,
                new ContainerData() {
                    @Override
                    public int get(int index) {
                        return container instanceof BoilerBlockEntity be ? be.getSteamAmount() : 0;
                    }

                    @Override
                    public void set(int index, int value) {
                    }

                    @Override
                    public int getCount() {
                        return 1;
                    }
                });
    }

    public BoilerMenu(int windowId, Inventory playerInventory, Container container, ContainerData data) {
        super(TYPE, windowId);
        this.container = container;
        this.data = data;

        // 燃料槽（只接受燃料）
        this.addSlot(new Slot(container, FUEL_SLOT, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PowerSource.fuelTicks(stack) > 0;
            }
        });

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    public int getSteam() {
        return data.get(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == FUEL_SLOT) {
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
