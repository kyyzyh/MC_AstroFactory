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

import com.xiaoyi.astrofactory.block.CrafterBlockEntity;

/**
 * 制作台界面：1 输入 + 1 输出 + 玩家背包。
 * 同步数据：0 = 制作进度百分比。
 */
public class CrafterMenu extends AbstractContainerMenu {
    public static final MenuType<CrafterMenu> TYPE =
            IMenuTypeExtension.create((windowId, inv, data) ->
                    new CrafterMenu(windowId, inv, new SimpleContainer(
                            CrafterBlockEntity.TOTAL_SLOTS)));

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int PLAYER_INV_START = 2;

    private final Container container;
    private final ContainerData data;

    public CrafterMenu(int windowId, Inventory playerInventory, Container container) {
        this(windowId, playerInventory, container,
                new ContainerData() {
                    @Override
                    public int get(int index) {
                        return container instanceof CrafterBlockEntity be ? be.getProgressPercent() : 0;
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

    public CrafterMenu(int windowId, Inventory playerInventory, Container container, ContainerData data) {
        super(TYPE, windowId);
        this.container = container;
        this.data = data;

        this.addSlot(new Slot(container, INPUT_SLOT, 56, 35));
        this.addSlot(new Slot(container, OUTPUT_SLOT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

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

    public int getProgressPercent() {
        return data.get(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == OUTPUT_SLOT || index == INPUT_SLOT) {
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
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
