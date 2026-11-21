package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.power.FuelPowerSource;
import com.xiaoyi.astrofactory.power.PowerSource;

/**
 * 机械臂方块实体。
 *
 * <h2>工作循环（每 {@link #ACTION_INTERVAL} tick 一次）</h2>
 * <ol>
 *   <li>先自己烧煤（规格红线：燃烧臂自己吃煤）；</li>
 *   <li>手上没东西 → 从输入侧（FACING 反向）按<b>过滤规则</b>挑一个物品；</li>
 *   <li>搬的物品本身是燃料 → 优先烧掉，不送出去（规格红线）；</li>
 *   <li>往输出侧放，<b>放不下就不抓</b>（见下）；</li>
 *   <li>放不进去就继续拿着，下个周期再试（形成阻塞，不丢物品）。</li>
 * </ol>
 *
 * <h2>三个可配置参数（0.2.0 新增）</h2>
 * <ul>
 *   <li><b>过滤</b>：9 格过滤槽 + 白/黑名单。空格 = 不过滤（全收）；</li>
 *   <li><b>数量档</b>：1 / 8 / 64，一次最多搬几个；</li>
 *   <li><b>Keep N</b>：目标容器里该物品数量 <b>低于</b> N 才搬，到 N 就停。</li>
 * </ul>
 *
 * <h2>为什么「放不下就不抓」是关键设计</h2>
 * <p>Mekanism 的物流管道<b>恰恰违反</b>这条：它先抽出、再尝试插入，插不进就让物品
 * 在管道里来回反弹（社区称之为「鬼畜」），是它最被诟病的缺陷。</p>
 * <p>本实现的做法是<b>先模拟再动真格</b>：{@link #simulateInsert} 只做容量计算
 * （不改任何容器），确认放得下才真正抽取。这样物品永远不会「卡在半路」。</p>
 *
 * <h2>为什么 Keep N 让产线可复用</h2>
 * <p>Factorio 的 inserter 有一条隐性规则：燃料只插到 5、弹匣只插到 10 ——
 * 目的是<b>让同一条带上的其他机器也分得到料</b>。没有这条规则，第一台机器
 * 会把整条带的煤全吸走。Keep N 就是这条规则在 Minecraft 里的表达。</p>
 */
public class BurnerInserterBlockEntity extends BlockEntity {
    /** 每 26 tick（1.3 秒）执行一次搬运（规格 §5.2：燃烧臂 26 tick）。 */
    public static final int ACTION_INTERVAL = 26;

    /** 燃料槽下标。 */
    public static final int FUEL_SLOT = 0;
    /** 过滤槽起始下标。 */
    public static final int FILTER_START = 1;
    /** 过滤槽数量。 */
    public static final int FILTER_SLOTS = 9;
    /** 容器总格数 = 燃料 1 + 过滤 9。 */
    public static final int TOTAL_SLOTS = FUEL_SLOT + 1 + FILTER_SLOTS;

    /** 数量档可选值。 */
    public static final int[] MOVE_LIMITS = {1, 8, 64};

    private int cooldown = 0;
    /** 手上持有的物品（搬运中），用于渲染与容错。 */
    private ItemStack held = ItemStack.EMPTY;

    /** 过滤槽 + 燃料槽共用一个容器。 */
    private final SimpleContainer container = new SimpleContainer(TOTAL_SLOTS);
    private final FuelPowerSource power = new FuelPowerSource(container, FUEL_SLOT);

    /** 白名单模式：true = 只搬过滤槽里的物品；false = 只搬不在过滤槽里的。 */
    private boolean whitelist = true;
    /** 数量档在 {@link #MOVE_LIMITS} 中的下标。 */
    private int limitIndex = 0;
    /** Keep N：目标容器里该物品低于此值才搬。0 = 不限制。 */
    private int keepN = 0;

    /**
     * 上次动作的失败原因，供 GUI / 右键诊断读取。
     *
     * <p>只存枚举，不存文字 —— 文案由客户端按 lang 键渲染，
     * 保证服务端不碰本地化。</p>
     */
    public enum ArmStatus {
        /** 空闲（没有可搬的东西，或刚搬完）。 */
        IDLE,
        /** 正常搬运中。 */
        RUNNING,
        /** 没有燃料。 */
        NO_FUEL,
        /** 输入侧没有符合过滤规则的物品。 */
        NO_INPUT,
        /** 输入侧有东西但都被过滤掉了。 */
        FILTERED,
        /** 输出侧放不下（满了 / 不是容器）。 */
        NO_SPACE,
        /** Keep N 已达上限，不需要再搬。 */
        KEEP_SATISFIED
    }

    private ArmStatus status = ArmStatus.IDLE;

    public BurnerInserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BURNER_INSERTER_BE.get(), pos, state);
    }

    // ------------------------------------------------------------ 读取

    public ItemStack getHeld() {
        return held;
    }

    /** 燃料 + 过滤槽容器（供 GUI / 调试 / 自动化测试读取）。 */
    public SimpleContainer getContainer() {
        return container;
    }

    /** 兼容旧调用：燃料槽容器。 */
    public SimpleContainer getFuelContainer() {
        return container;
    }

    public ArmStatus getStatus() {
        return status;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public int getLimitIndex() {
        return limitIndex;
    }

    /** 当前一次最多搬几个。 */
    public int getMoveLimit() {
        return MOVE_LIMITS[Math.max(0, Math.min(MOVE_LIMITS.length - 1, limitIndex))];
    }

    public int getKeepN() {
        return keepN;
    }

    /** 过滤槽是否全空（= 不过滤）。 */
    public boolean filterIsEmpty() {
        for (int i = FILTER_START; i < FILTER_START + FILTER_SLOTS; i++) {
            if (!container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ 配置（服务端）

    public void toggleWhitelist() {
        whitelist = !whitelist;
        setChanged();
    }

    public void cycleLimit() {
        limitIndex = (limitIndex + 1) % MOVE_LIMITS.length;
        setChanged();
    }

    /** Keep N 在 0 / 1 / 4 / 8 / 16 / 64 之间循环。0 = 不限制。 */
    public void cycleKeepN() {
        int[] steps = {0, 1, 4, 8, 16, 64};
        int cur = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == keepN) {
                cur = i;
                break;
            }
        }
        keepN = steps[(cur + 1) % steps.length];
        setChanged();
    }

    // ------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, BurnerInserterBlockEntity be) {
        if (be.cooldown > 0) {
            be.cooldown--;
            return;
        }
        be.cooldown = ACTION_INTERVAL;
        // a) 先自己烧煤（规格红线：燃烧臂自己吃煤）
        if (!be.power.consume(true)) {
            be.setStatus(level, pos, ArmStatus.NO_FUEL);
            return; // 没燃料就停机，不搬东西
        }
        be.power.consume(false);
        be.doWork(level, pos, state);
    }

    private void doWork(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(BurnerInserterBlock.FACING);
        BlockPos inputPos = pos.relative(facing.getOpposite());
        BlockPos outputPos = pos.relative(facing);

        // 手上没东西 -> 去输入侧取
        if (held.isEmpty()) {
            // 先看「如果取到了，放得下吗」—— 放不下就干脆不取（避免物品卡在半路）
            ItemStack candidate = peekFrom(level, inputPos);
            if (candidate.isEmpty()) {
                setStatus(level, pos, hasFilteredOut(level, inputPos)
                        ? ArmStatus.FILTERED : ArmStatus.NO_INPUT);
                return;
            }
            // Keep N：目标容器里已经够了就不再搬
            if (keepN > 0 && countIn(level, outputPos, candidate) >= keepN) {
                setStatus(level, pos, ArmStatus.KEEP_SATISFIED);
                return;
            }
            int want = Math.min(getMoveLimit(), candidate.getCount());
            int fits = simulateInsert(level, outputPos, candidate, want);
            if (fits <= 0) {
                setStatus(level, pos, ArmStatus.NO_SPACE);
                return;
            }
            // 确认放得下，才真正抽取
            held = takeFrom(level, inputPos, fits);
            if (held.isEmpty()) {
                setStatus(level, pos, ArmStatus.NO_INPUT);
                return;
            }
            // 偷燃料（规格红线）：如果搬的物品本身是燃料，优先烧掉，不送出去
            if (power.stealFromCarried(held)) {
                held = ItemStack.EMPTY;
                setStatus(level, pos, ArmStatus.NO_FUEL);
                setChanged();
                return;
            }
            setChanged();
        }

        // 尝试放到输出侧
        if (putTo(level, outputPos, facing)) {
            held = ItemStack.EMPTY;
            setStatus(level, pos, ArmStatus.RUNNING);
            setChanged();
        } else {
            setStatus(level, pos, ArmStatus.NO_SPACE);
        }
        // 放不进去就继续拿着，下个周期再试（形成阻塞，不丢物品）
    }

    /** 只读地看输入侧下一个会取到什么（不修改容器）。 */
    private ItemStack peekFrom(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportBeltBlockEntity conveyor) {
            ItemStack peek = conveyor.peekReady();
            return accepts(peek) ? peek : ItemStack.EMPTY;
        }
        if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (!stack.isEmpty() && accepts(stack)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 输入侧是否有东西但全被过滤掉了（用于给出 FILTERED 而不是 NO_INPUT）。 */
    private boolean hasFilteredOut(Level level, BlockPos pos) {
        if (filterIsEmpty()) {
            return false;
        }
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportBeltBlockEntity conveyor) {
            ItemStack peek = conveyor.peekReady();
            return !peek.isEmpty() && !accepts(peek);
        }
        if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (!stack.isEmpty() && !accepts(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 过滤判定：过滤槽全空 = 全收；否则按白/黑名单比对。 */
    private boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (filterIsEmpty()) {
            return true;
        }
        boolean listed = false;
        for (int i = FILTER_START; i < FILTER_START + FILTER_SLOTS; i++) {
            ItemStack f = container.getItem(i);
            if (!f.isEmpty() && ItemStack.isSameItemSameComponents(f, stack)) {
                listed = true;
                break;
            }
        }
        return whitelist == listed;
    }

    /** 数目标容器里与 sample 同类的物品总数。 */
    private int countIn(Level level, BlockPos pos, ItemStack sample) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container c)) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack stack = c.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sample)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 模拟插入：返回目标容器能收下多少个（0 = 一个都放不下）。
     *
     * <p><b>只读</b>：不修改任何容器，纯容量计算。这是「放不下就不抓」的基础。</p>
     */
    private int simulateInsert(Level level, BlockPos pos, ItemStack sample, int want) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportBeltBlockEntity conveyor) {
            return conveyor.canAcceptAtStart(sample) ? Math.min(want, sample.getMaxStackSize()) : 0;
        }
        if (be instanceof Container c) {
            int room = 0;
            for (int i = 0; i < c.getContainerSize() && room < want; i++) {
                if (!c.canPlaceItem(i, sample)) {
                    continue;
                }
                ItemStack slot = c.getItem(i);
                if (slot.isEmpty()) {
                    room += sample.getMaxStackSize();
                } else if (ItemStack.isSameItemSameComponents(slot, sample)) {
                    room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
                }
            }
            return Math.min(room, want);
        }
        return 0;
    }

    private void setStatus(Level level, BlockPos pos, ArmStatus newStatus) {
        if (status != newStatus) {
            status = newStatus;
            setChanged();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
        }
    }

    /** 从指定位置取最多 {@code want} 个符合过滤的物品。 */
    private ItemStack takeFrom(Level level, BlockPos pos, int want) {
        if (!level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportBeltBlockEntity conveyor) {
            ItemStack peek = conveyor.peekReady();
            return accepts(peek) ? conveyor.extractReady() : ItemStack.EMPTY;
        }
        if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (!stack.isEmpty() && accepts(stack)) {
                    int take = Math.min(want, stack.getCount());
                    ItemStack taken = c.removeItem(i, take);
                    c.setChanged();
                    return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 往指定位置放物品，成功返回 true。 */
    private boolean putTo(Level level, BlockPos pos, Direction fromArm) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportBeltBlockEntity conveyor) {
            return conveyor.insertAtStart(held);
        }
        if (be instanceof Container c) {
            // 先找同类堆（遵守 canPlaceItem，避免塞进输出槽）
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack slot = c.getItem(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, held)
                        && slot.getCount() < slot.getMaxStackSize()
                        && c.canPlaceItem(i, held)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int move = Math.min(space, held.getCount());
                    slot.grow(move);
                    held.shrink(move);
                    c.setChanged();
                    return held.isEmpty();
                }
            }
            // 再找空槽
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (c.getItem(i).isEmpty() && c.canPlaceItem(i, held)) {
                    c.setItem(i, held.copy());
                    held = ItemStack.EMPTY;
                    c.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // 空手不能写进 ItemStack.CODEC（count 必须 1..99 且不能是 air）
        if (!held.isEmpty()) {
            output.store("Held", ItemStack.CODEC, held);
        }
        output.putInt("Cooldown", cooldown);
        output.putInt("BurnBudget", power.getBurnBudgetForSave());
        output.putBoolean("Whitelist", whitelist);
        output.putInt("LimitIndex", limitIndex);
        output.putInt("KeepN", keepN);
        output.putString("Status", status.name());
        var list = output.childrenList("Items");
        for (ItemStack stack : container) {
            if (!stack.isEmpty()) {
                var child = list.addChild();
                child.store("Stack", ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held = input.read("Held", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        cooldown = input.getIntOr("Cooldown", 0);
        power.setBurnBudgetForLoad(input.getIntOr("BurnBudget", 0));
        whitelist = input.getBooleanOr("Whitelist", true);
        limitIndex = Math.max(0, Math.min(MOVE_LIMITS.length - 1, input.getIntOr("LimitIndex", 0)));
        keepN = input.getIntOr("KeepN", 0);
        try {
            status = ArmStatus.valueOf(input.getStringOr("Status", ArmStatus.IDLE.name()));
        } catch (IllegalArgumentException e) {
            status = ArmStatus.IDLE;
        }

        container.clearContent();
        // 新格式：Items（燃料 + 过滤槽）
        int i = 0;
        boolean loadedNew = false;
        for (ValueInput child : input.childrenListOrEmpty("Items")) {
            if (i >= TOTAL_SLOTS) {
                break;
            }
            ItemStack stack = child.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                container.setItem(i++, stack);
                loadedNew = true;
            }
        }
        // 旧格式兼容（0.1.0 存档只有 Fuel）：读不到 Items 才回退
        if (!loadedNew) {
            int fi = 0;
            for (ValueInput child : input.childrenListOrEmpty("Fuel")) {
                if (fi >= TOTAL_SLOTS) {
                    break;
                }
                ItemStack stack = child.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    container.setItem(fi++, stack);
                }
            }
        }
    }
}
