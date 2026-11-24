package com.astrofactory.menu;

import com.astrofactory.registry.ModMenus;
import com.astrofactory.tech.TechNodes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * 科技树菜单：纯展示 + 研究台输入槽。
 *
 * <p>菜单本身不持有科技数据——科技进度通过 {@link com.astrofactory.network.ModNetwork}
 * 的 payload 独立同步（SavedData 是世界级的，不属于任何容器）。
 * 菜单只负责：研究台的两个研究包输入槽 + 玩家背包。</p>
 */
public class TechTreeMenu extends AbstractContainerMenu {

    private final boolean hasTable;

    /** 客户端构造（来自 MenuType，IContainerFactory 三参签名）。 */
    public TechTreeMenu(int containerId, Inventory playerInv,
                        net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, false, null);
    }

    /** 客户端构造（无额外数据）。 */
    public TechTreeMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, false, null);
    }

    /** 服务端构造（研究台打开时带输入槽）。 */
    public TechTreeMenu(int containerId, Inventory playerInv, boolean hasTable,
                        net.neoforged.neoforge.items.IItemHandler tableInput) {
        super(ModMenus.TECH_TREE.get(), containerId);
        this.hasTable = hasTable;

        int slotIndex = 0;
        if (hasTable && tableInput != null) {
            // 两个研究包槽放在界面上方中央
            this.addSlot(new SlotItemHandler(tableInput, slotIndex++, 124, 24));
            this.addSlot(new SlotItemHandler(tableInput, slotIndex, 142, 24));
        }

        // 玩家背包 3x9
        var playerHandler = new InvWrapper(playerInv);
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new SlotItemHandler(playerHandler,
                        col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new SlotItemHandler(playerHandler, col, 8 + col * 18, 142));
        }
    }

    public boolean hasTable() {
        return this.hasTable;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        if (this.hasTable && index < 2) {
            // 从研究包槽移到玩家背包
            if (!this.moveItemStackTo(raw, 2, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 从玩家背包移到研究包槽（若匹配）
            if (this.hasTable && !this.moveItemStackTo(raw, 0, 2, false)) {
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
        return true;
    }
}
