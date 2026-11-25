package com.astrofactory.menu;

import com.astrofactory.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * 科技树菜单：纯展示 + 研究台输入槽。
 *
 * <p>菜单本身不持有科技数据——科技进度通过 {@link com.astrofactory.network.ModNetwork}
 * 的 payload 独立同步（SavedData 是世界级的，不属于任何容器）。</p>
 *
 * <p><b>槽位坐标与 {@code TechTreeScreen} 的 480x280 固定面板严格对应</b>：
 * 1.21.1 的 {@link Slot#x}/{@link Slot#y} 是 final，界面无法二次摆放，只能在菜单里定死。</p>
 * <ul>
 *   <li>研究包输入槽：(24,252)、(44,252)——面板左下角；</li>
 *   <li>玩家背包 3x9：从 (159,202) 起，行距 18；</li>
 *   <li>快捷栏：从 (159,258) 起。</li>
 * </ul>
 */
public class TechTreeMenu extends AbstractContainerMenu {

    /** 面板尺寸（与 TechTreeScreen 保持一致）。 */
    public static final int PANEL_W = 480;
    public static final int PANEL_H = 280;

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
                        IItemHandler tableInput) {
        super(ModMenus.TECH_TREE.get(), containerId);
        this.hasTable = hasTable;

        int slotIndex = 0;
        if (hasTable && tableInput != null) {
            this.addSlot(new SlotItemHandler(tableInput, slotIndex++, 24, 252));
            this.addSlot(new SlotItemHandler(tableInput, slotIndex, 44, 252));
        }

        // 玩家背包 3x9
        var playerHandler = new InvWrapper(playerInv);
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new SlotItemHandler(playerHandler,
                        col + row * 9 + 9, 159 + col * 18, 202 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new SlotItemHandler(playerHandler, col, 159 + col * 18, 258));
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
        int tableSlots = this.hasTable ? 2 : 0;
        if (tableSlots > 0 && index < tableSlots) {
            // 从研究包槽移到玩家背包
            if (!this.moveItemStackTo(raw, tableSlots, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (tableSlots > 0) {
            // 从玩家背包移到研究包槽（若匹配）
            if (!this.moveItemStackTo(raw, 0, tableSlots, false)) {
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
