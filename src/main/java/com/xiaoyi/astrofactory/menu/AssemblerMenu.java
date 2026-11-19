package com.xiaoyi.astrofactory.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import com.xiaoyi.astrofactory.block.AssemblerBlockEntity;

/**
 * 组装机界面：3x3 输入 + 1 输出 + 玩家背包。
 */
public class AssemblerMenu extends AbstractContainerMenu {
    public static final MenuType<AssemblerMenu> TYPE =
            IMenuTypeExtension.create((windowId, inv, data) ->
                    new AssemblerMenu(windowId, inv));

    private static final int INPUT_START = 0;
    private static final int OUTPUT_SLOT = 9;
    private static final int PLAYER_INV_START = 10;

    private final Container container;
    private final net.minecraft.world.inventory.ContainerData powerData;

    /** 客户端构造：仅用于界面，容器为空壳。 */
    public AssemblerMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new net.minecraft.world.SimpleContainer(
                AssemblerBlockEntity.TOTAL_SLOTS));
    }

    public AssemblerMenu(int windowId, Inventory playerInventory, Container container) {
        super(TYPE, windowId);
        this.container = container;

        // 3x3 输入
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                this.addSlot(new Slot(container, index, 30 + col * 18, 17 + row * 18));
            }
        }
        // 输出槽（只出不进）
        this.addSlot(new Slot(container, OUTPUT_SLOT, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // 玩家背包 3x9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // 电力状态同步（规格 §5.4：断电 GUI 显示「无电力」）
        this.powerData = new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                return container instanceof AssemblerBlockEntity be && be.isPowered() ? 1 : 0;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        this.addDataSlots(this.powerData);
    }

    /** 是否通电（客户端读同步值）。 */
    public boolean isPowered() {
        return this.powerData.get(0) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == OUTPUT_SLOT) {
                // 输出 -> 背包
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < OUTPUT_SLOT) {
                // 输入 -> 背包
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 背包 -> 输入
                if (!this.moveItemStackTo(stack, INPUT_START, OUTPUT_SLOT, false)) {
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
