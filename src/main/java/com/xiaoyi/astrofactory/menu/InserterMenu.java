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

import com.xiaoyi.astrofactory.block.BurnerInserterBlockEntity;

/**
 * 机械臂界面：1 燃料槽 + 9 过滤槽 + 4 个配置按钮 + 玩家背包。
 *
 * <h2>为什么参数控制在 4 个按钮以内</h2>
 * <p>XNet 被认为「强大但吓人」，就是因为它的控制器 GUI 一屏能塞 10 个参数。
 * 本界面只暴露新手必须理解的东西：</p>
 * <ul>
 *   <li><b>白/黑名单</b>：过滤槽里的东西是「只要」还是「不要」；</li>
 *   <li><b>数量档</b>：1 / 8 / 64；</li>
 *   <li><b>Keep N</b>：目标里有多少就够，到量就停；</li>
 *   <li><b>方向</b>：由摆放决定（和 Create 机械臂一样），不在 GUI 里选。</li>
 * </ul>
 *
 * <p>「从哪抓/往哪放」故意不做成 GUI 下拉框 —— 摆放即配置，零点击成本。
 * Create 的机械臂已验证这条可行。</p>
 */
public class InserterMenu extends AbstractContainerMenu {
    public static final int BUTTON_WHITELIST = 0;
    public static final int BUTTON_LIMIT = 1;
    public static final int BUTTON_KEEP_N = 2;

    public static final MenuType<InserterMenu> TYPE =
            IMenuTypeExtension.create((windowId, inv, data) ->
                    new InserterMenu(windowId, inv, new SimpleContainer(
                            BurnerInserterBlockEntity.TOTAL_SLOTS)));

    private static final int PLAYER_INV_START = BurnerInserterBlockEntity.TOTAL_SLOTS;

    private final Container container;
    private final ContainerData data;

    /** 客户端构造。 */
    public InserterMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new SimpleContainer(BurnerInserterBlockEntity.TOTAL_SLOTS));
    }

    public InserterMenu(int windowId, Inventory playerInventory, Container container) {
        super(TYPE, windowId);
        this.container = container;

        // 燃料槽（左，单独一格）
        this.addSlot(new Slot(container, BurnerInserterBlockEntity.FUEL_SLOT, 8, 20));

        // 9 格过滤槽（3×3）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = BurnerInserterBlockEntity.FILTER_START + row * 3 + col;
                this.addSlot(new Slot(container, index, 44 + col * 18, 14 + row * 18));
            }
        }

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // 同步：0 = 白名单(0/1)，1 = 数量档下标，2 = KeepN，3 = 状态序号
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (!(container instanceof BurnerInserterBlockEntity be)) {
                    return 0;
                }
                return switch (index) {
                    case 0 -> be.isWhitelist() ? 1 : 0;
                    case 1 -> be.getLimitIndex();
                    case 2 -> be.getKeepN();
                    case 3 -> be.getStatus().ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // 客户端只读镜像；真实状态由服务端 broadcastChanges 推送
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        this.addDataSlots(this.data);
    }

    // ------------------------------------------------------------ 读取（客户端可用）

    public boolean isWhitelist() {
        return this.data.get(0) != 0;
    }

    public int getLimitIndex() {
        return this.data.get(1);
    }

    public int getMoveLimit() {
        int idx = Math.max(0, Math.min(BurnerInserterBlockEntity.MOVE_LIMITS.length - 1, this.data.get(1)));
        return BurnerInserterBlockEntity.MOVE_LIMITS[idx];
    }

    public int getKeepN() {
        return this.data.get(2);
    }

    public BurnerInserterBlockEntity.ArmStatus getStatus() {
        int ord = this.data.get(3);
        var values = BurnerInserterBlockEntity.ArmStatus.values();
        return values[Math.max(0, Math.min(values.length - 1, ord))];
    }

    // ------------------------------------------------------------ 按钮

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true; // 客户端只发包，服务端处理
        }
        if (!(container instanceof BurnerInserterBlockEntity be)) {
            return false;
        }
        switch (id) {
            case BUTTON_WHITELIST -> be.toggleWhitelist();
            case BUTTON_LIMIT -> be.cycleLimit();
            case BUTTON_KEEP_N -> be.cycleKeepN();
            default -> {
                return false;
            }
        }
        this.broadcastChanges();
        return true;
    }

    // ------------------------------------------------------------ 快速移动

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            int machineEnd = BurnerInserterBlockEntity.TOTAL_SLOTS;
            if (index < machineEnd) {
                // 机器 -> 背包
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 背包 -> 过滤槽（燃料槽要玩家手动放，避免误把煤当过滤条件）
                if (!this.moveItemStackTo(stack, BurnerInserterBlockEntity.FILTER_START, machineEnd, false)) {
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
