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

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.block.ResearchTableBlockEntity;
import com.xiaoyi.astrofactory.research.ResearchProgress;
import com.xiaoyi.astrofactory.research.Technologies;
import com.xiaoyi.astrofactory.research.Technology;

import java.util.List;

/**
 * 科研台界面：1 个科研包输入槽 + 科技进度显示 + "投入"按钮。
 *
 * 同步给客户端的数据（ContainerData）：
 *   0 = 当前研究目标在 Technologies.all() 中的下标（-1 表示没有可研究的）
 *   1 = 该科技已投入的科研包数
 *   2 = 该科技需要的总数
 *   3 = 已解锁科技数量
 *
 * 按钮 id 0 = 投入 1 个科研包（服务端 clickMenuButton 处理）。
 */
public class ResearchMenu extends AbstractContainerMenu {
    public static final int BUTTON_CONTRIBUTE = 0;

    public static final MenuType<ResearchMenu> TYPE =
            IMenuTypeExtension.create((windowId, inv, data) ->
                    new ResearchMenu(windowId, inv, new SimpleContainer(
                            ResearchTableBlockEntity.TOTAL_SLOTS), null));

    private static final int INPUT_SLOT = 0;
    private static final int PLAYER_INV_START = 1;

    private final Container container;
    private final Player player;
    private final ContainerData data;

    /** 客户端构造：data 为本地镜像，服务端会同步真实值。 */
    public ResearchMenu(int windowId, Inventory playerInventory, Container container, Player player) {
        this(windowId, playerInventory, container, player,
                new ProgressData(player));
    }

    public ResearchMenu(int windowId, Inventory playerInventory, Container container,
                        Player player, ContainerData data) {
        super(TYPE, windowId);
        this.container = container;
        this.player = player;
        this.data = data;

        // 科研包输入槽（只接受电路板）
        this.addSlot(new Slot(container, INPUT_SLOT, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == ModRegistry.CIRCUIT_BOARD.get();
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

        this.addDataSlots(data);
    }

    // ------------------------------------------------------------ 数据读取（客户端 UI 用）

    /** 当前研究目标；null 表示无可研究科技。 */
    public Technology currentTech() {
        int idx = data.get(0);
        List<Technology> all = Technologies.all();
        if (idx < 0 || idx >= all.size()) {
            return null;
        }
        return all.get(idx);
    }

    public int currentPoints() {
        return data.get(1);
    }

    public int currentCost() {
        return data.get(2);
    }

    public int unlockedCount() {
        return data.get(3);
    }

    public int totalTechs() {
        return Technologies.all().size();
    }

    // ------------------------------------------------------------ 按钮

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CONTRIBUTE || player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        ItemStack stack = container.getItem(INPUT_SLOT);
        // 只有电路板能当科研包：强制走「采矿机 -> 传送带 -> 组装机 -> 科研台」的生产链
        if (stack.isEmpty() || stack.getItem() != ModRegistry.CIRCUIT_BOARD.get()) {
            return false;
        }
        ResearchProgress.Data progress = ResearchProgress.of(player);
        Technology target = firstResearchable(progress);
        if (target == null) {
            return false;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            container.setItem(INPUT_SLOT, ItemStack.EMPTY);
        } else {
            container.setChanged();
        }
        progress.contribute(target.id(), 1);
        this.broadcastChanges();
        return true;
    }

    /** 找第一个"前置满足且未解锁"的科技。 */
    public static Technology firstResearchable(ResearchProgress.Data data) {
        for (Technology tech : Technologies.all()) {
            if (data.isUnlocked(tech.id())) {
                continue;
            }
            if (Technologies.prerequisitesMet(tech, data.unlocked)) {
                return tech;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 同步

    /**
     * 科研进度数据源。
     * 服务端：get() 实时读玩家 attachment，因此 broadcastChanges 时拿到的是最新值。
     * 客户端：get() 读 set() 写入的镜像数组（由服务端同步过来）。
     */
    public static final class ProgressData implements ContainerData {
        private static final int SIZE = 4;
        private final Player player;
        private final int[] mirror = new int[SIZE];

        public ProgressData(Player player) {
            this.player = player;
        }

        private boolean isServer() {
            return player != null && !player.level().isClientSide();
        }

        @Override
        public int get(int index) {
            if (!isServer()) {
                return mirror[index];
            }
            ResearchProgress.Data progress = ResearchProgress.of(player);
            List<Technology> all = Technologies.all();
            Technology target = firstResearchable(progress);
            return switch (index) {
                case 0 -> target == null ? -1 : all.indexOf(target);
                case 1 -> target == null ? 0 : progress.getPoints(target.id());
                case 2 -> target == null ? 0 : target.cost();
                case 3 -> progress.unlocked.size();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < SIZE) {
                mirror[index] = value;
            }
        }

        @Override
        public int getCount() {
            return SIZE;
        }
    }

    // ------------------------------------------------------------ 槽位搬运

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == INPUT_SLOT) {
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
