package com.astrofactory.menu;

import com.astrofactory.blockentity.AssemblerBlockEntity;
import com.astrofactory.registry.ModMenus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.Optional;

/**
 * 组装机菜单：3x3 网格 + 输出槽 + 能量条 + 玩家背包。
 */
public class AssemblerMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final AssemblerBlockEntity assembler;

    /** 客户端构造（来自 MenuType，extraData 传方块位置）。 */
    public AssemblerMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, new net.neoforged.neoforge.items.ItemStackHandler(10),
                playerInv.player.level().getBlockEntity(buf.readBlockPos()) instanceof AssemblerBlockEntity be
                        ? be : null,
                ContainerLevelAccess.NULL);
    }

    /** 服务端构造。 */
    public AssemblerMenu(int containerId, Inventory playerInv, IItemHandler slots,
                         AssemblerBlockEntity assembler, ContainerLevelAccess access) {
        super(ModMenus.ASSEMBLER.get(), containerId);
        this.access = access;
        this.assembler = assembler;

        // 3x3 网格（槽 0-8）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new SlotItemHandler(slots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        // 输出槽（槽 9）
        this.addSlot(new SlotItemHandler(slots, 9, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // 玩家背包
        var playerHandler = new InvWrapper(playerInv);
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new SlotItemHandler(playerHandler,
                        col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new SlotItemHandler(playerHandler, col, 8 + col * 18, 142));
        }

        // 同步能量与进度
        this.addDataSlot(DataSlot.standalone());
        this.addDataSlot(DataSlot.standalone());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        if (index == 9) {
            // 输出槽 → 玩家背包
            if (!this.moveItemStackTo(raw, 10, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < 9) {
            // 网格 → 玩家背包
            if (!this.moveItemStackTo(raw, 10, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 → 网格
            if (!this.moveItemStackTo(raw, 0, 9, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (raw.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, raw);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.assembler == null) {
            return true; // 客户端
        }
        return AbstractContainerMenu.stillValid(this.access, player,
                com.astrofactory.registry.ModBlocks.ASSEMBLER.get());
    }
}
