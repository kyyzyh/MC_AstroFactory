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
import com.xiaoyi.astrofactory.block.LabBlockEntity;
import com.xiaoyi.astrofactory.research.ResearchProgress;
import com.xiaoyi.astrofactory.research.Technologies;
import com.xiaoyi.astrofactory.research.Technology;

import java.util.List;

/**
 * 科研台界面：1 个科研包输入槽 + 科技进度显示 + 「上一个 / 投入 / 下一个」三个按钮。
 *
 * <p><b>为什么要能切换目标</b>：科技树补齐到 9 个节点后，同一时刻可能有多个
 * 可研究项（例如 automation_1 解锁后，electronics / mining_1 / assembly_1 同时开放）。
 * 只取「第一个」会让玩家无法选择先点哪条线，因此加了前后切换。</p>
 *
 * <p>同步给客户端的数据（ContainerData）：</p>
 * <pre>
 *   0 = 当前目标在 Technologies.all() 中的下标（-1 表示没有可研究的）
 *   1 = 该科技已投入的科研包数
 *   2 = 该科技需要的总数
 *   3 = 已解锁科技数量
 *   4 = 科技总数
 *   5 = 当前可研究项数量
 *   6 = 当前目标在「可研究列表」中的序号（0 基，无目标时 0）
 * </pre>
 *
 * <p>按钮 id：0 = 投入 1 个科研包，1 = 上一个目标，2 = 下一个目标
 * （服务端 {@link #clickMenuButton} 处理）。</p>
 */
public class LabMenu extends AbstractContainerMenu {
    public static final int BUTTON_CONTRIBUTE = 0;
    public static final int BUTTON_PREV = 1;
    public static final int BUTTON_NEXT = 2;

    /** ContainerData 槽位数量。 */
    private static final int DATA_SIZE = 7;

    public static final MenuType<LabMenu> TYPE =
            IMenuTypeExtension.create((windowId, inv, data) ->
                    new LabMenu(windowId, inv, new SimpleContainer(
                            LabBlockEntity.TOTAL_SLOTS), null));

    private static final int INPUT_SLOT = 0;
    private static final int PLAYER_INV_START = 1;

    private final Container container;
    private final Player player;
    private final ContainerData data;

    /**
     * 当前选中的目标在「可研究列表」中的序号（0 基）。
     *
     * <p>只由服务端维护：客户端点按钮 → 服务端改这里 → broadcastChanges 把
     * 新的 index 同步回去。客户端自己不存状态，避免两端不一致。</p>
     */
    private int selectedOrdinal = 0;

    /** 客户端构造：data 为本地镜像，服务端会同步真实值。 */
    public LabMenu(int windowId, Inventory playerInventory, Container container, Player player) {
        this(windowId, playerInventory, container, player, null);
    }

    public LabMenu(int windowId, Inventory playerInventory, Container container,
                   Player player, ContainerData data) {
        super(TYPE, windowId);
        this.container = container;
        this.player = player;
        this.data = data != null ? data : new ProgressData(player, () -> this.selectedOrdinal);

        // 科研包输入槽：电路板 或 红瓶（自动化科技包）都行
        this.addSlot(new Slot(container, INPUT_SLOT, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isResearchPack(stack);
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

        this.addDataSlots(this.data);
    }

    // ------------------------------------------------------------ 科研包判定

    /**
     * 该物品能否作为科研包投入。
     *
     * <p>两种都算：电路板（主科技包）与红瓶（自动化科技包，规格 §4）。
     * 两者都提供 1 点进度 —— 保持「1 个包 = 1 点」的简单规则。</p>
     */
    public static boolean isResearchPack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() == ModRegistry.ELECTRONIC_CIRCUIT.get()
                || stack.getItem() == ModRegistry.AUTOMATION_PACK.get();
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
        return data.get(4);
    }

    /** 当前可研究项数量（>1 时显示切换按钮）。 */
    public int researchableCount() {
        return data.get(5);
    }

    /** 当前目标在可研究列表中的序号（0 基）。 */
    public int selectedOrdinal() {
        return data.get(6);
    }

    // ------------------------------------------------------------ 按钮

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            // 客户端只负责发包，真实处理在服务端
            return true;
        }
        return switch (id) {
            case BUTTON_CONTRIBUTE -> contribute(player);
            case BUTTON_PREV -> cycle(player, -1);
            case BUTTON_NEXT -> cycle(player, 1);
            default -> false;
        };
    }

    /** 投入 1 个科研包到当前目标。 */
    private boolean contribute(Player player) {
        ItemStack stack = container.getItem(INPUT_SLOT);
        // 只有科研包能投入：强制走「采矿机 -> 传送带 -> 组装机 -> 科研台」的生产链
        if (!isResearchPack(stack)) {
            return false;
        }
        // 断电停机（规格 §5.4）：科研台没电不能投入科研包
        if (container instanceof LabBlockEntity lab && !lab.tryConsumePower()) {
            return false;
        }
        ResearchProgress.Data progress = ResearchProgress.of(player);
        Technology target = currentTarget(progress);
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
        // story 系统：首次研究科技 → 里程碑 first_tech + EXP
        if (progress.unlocked.contains(target.id())) {
            com.xiaoyi.astrofactory.level.PlayerLevel.awardMilestone(player, "first_tech");
            // 解锁后目标列表变了，把选择夹回合法范围
            clampSelection(progress);
        }
        this.broadcastChanges();
        return true;
    }

    /** 前后切换研究目标（循环）。 */
    private boolean cycle(Player player, int delta) {
        ResearchProgress.Data progress = ResearchProgress.of(player);
        List<Technology> list = Technologies.researchable(progress.unlocked);
        if (list.size() <= 1) {
            return false;
        }
        selectedOrdinal = Math.floorMod(selectedOrdinal + delta, list.size());
        this.broadcastChanges();
        return true;
    }

    /** 当前选中的目标；超出范围时回退到第一个。 */
    private Technology currentTarget(ResearchProgress.Data progress) {
        List<Technology> list = Technologies.researchable(progress.unlocked);
        if (list.isEmpty()) {
            return null;
        }
        int idx = selectedOrdinal;
        if (idx < 0 || idx >= list.size()) {
            idx = 0;
            selectedOrdinal = 0;
        }
        return list.get(idx);
    }

    /** 把选择夹到当前可研究列表的合法范围。 */
    private void clampSelection(ResearchProgress.Data progress) {
        int n = Technologies.researchable(progress.unlocked).size();
        if (n == 0) {
            selectedOrdinal = 0;
        } else if (selectedOrdinal >= n) {
            selectedOrdinal = n - 1;
        }
    }

    /** 找第一个「前置满足且未解锁」的科技（无选择时的默认值）。 */
    public static Technology firstResearchable(ResearchProgress.Data data) {
        List<Technology> list = Technologies.researchable(data.unlocked);
        return list.isEmpty() ? null : list.get(0);
    }

    // ------------------------------------------------------------ 同步

    /**
     * 科研进度数据源。
     *
     * <p>服务端：{@code get()} 实时读玩家 attachment + 菜单当前选择，
     * 因此 {@code broadcastChanges} 时拿到的是最新值。</p>
     * <p>客户端：{@code get()} 读 {@code set()} 写入的镜像数组（由服务端同步过来）。</p>
     */
    public static final class ProgressData implements ContainerData {
        private final Player player;
        private final java.util.function.IntSupplier ordinal;
        private final int[] mirror = new int[DATA_SIZE];

        public ProgressData(Player player, java.util.function.IntSupplier ordinal) {
            this.player = player;
            this.ordinal = ordinal;
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
            List<Technology> researchable = Technologies.researchable(progress.unlocked);

            int sel = ordinal.getAsInt();
            if (sel < 0 || sel >= researchable.size()) {
                sel = 0;
            }
            Technology target = researchable.isEmpty() ? null : researchable.get(sel);

            return switch (index) {
                case 0 -> target == null ? -1 : all.indexOf(target);
                case 1 -> target == null ? 0 : progress.getPoints(target.id());
                case 2 -> target == null ? 0 : target.cost();
                case 3 -> progress.unlocked.size();
                case 4 -> all.size();
                case 5 -> researchable.size();
                case 6 -> sel;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < DATA_SIZE) {
                mirror[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
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
