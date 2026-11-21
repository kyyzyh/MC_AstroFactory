package com.xiaoyi.astrofactory.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.xiaoyi.astrofactory.multiblock.MultiBlockHelper;
import com.xiaoyi.astrofactory.multiblock.MultiBlockMachineBlock;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 机器方块实体基类：容器样板 + 多方块公共行为。
 *
 * <p><b>容器部分</b>：基于官方 {@link BaseContainerBlockEntity}，它已实现
 * {@code Container} 的 {@code isEmpty/getItem/removeItem/setItem/stillValid/clearContent}
 * 与 {@code MenuProvider.getDisplayName}。子类只提供槽位数量、默认名、开哪个菜单。</p>
 *
 * <p><b>多方块部分</b>：把 5 台机器里逐字重复的两件事收拢进来 ——</p>
 * <ol>
 *   <li>区块加载自修复：master 加载时补齐缺失 dummy，被占则整机清掉（规格 §5.1「不留幽灵」）；</li>
 *   <li>{@link #setActive} 把运行状态一次性刷到整机所有格子，避免一格亮一格暗。</li>
 * </ol>
 *
 * <p><b>存档</b>：统一走 {@link MachineStorage}，与 0.1.0 早期存档双向兼容。</p>
 */
public abstract class AbstractMachineEntity extends BaseContainerBlockEntity {
    private final NonNullList<ItemStack> items;

    protected AbstractMachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slotCount) {
        super(type, pos, state);
        this.items = NonNullList.withSize(slotCount, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------ 容器

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        for (int i = 0; i < this.items.size() && i < items.size(); i++) {
            this.items.set(i, items.get(i));
        }
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    /** 只读物品列表视图（供配方匹配等工具方法使用）。 */
    protected List<ItemStack> slotView() {
        return items;
    }

    // ------------------------------------------------------------ 菜单

    /**
     * 子类实现：创建界面。
     *
     * <p>参数带 {@code player}，因为部分 GUI 需要玩家上下文
     * （例如科研台要从玩家身上读研究进度）。不需要时可忽略。</p>
     */
    protected abstract AbstractContainerMenu createMachineMenu(int containerId, Inventory inventory, @Nullable Player player);

    /** 官方抽象方法：转调子类实现。 */
    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return createMachineMenu(containerId, inventory, null);
    }

    /** 官方三参入口：保留锁定检查语义后转调子类实现。 */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (!canOpen(player)) {
            sendChestLockedNotifications(Vec3.atCenterOf(getBlockPos()), player, getDisplayName());
            return null;
        }
        return createMachineMenu(containerId, inventory, player);
    }

    // ------------------------------------------------------------ 多方块

    /** 该机器对应的多方块方块；不是多方块时返回 {@code null}。 */
    protected MultiBlockMachineBlock machineBlock() {
        return getBlockState().getBlock() instanceof MultiBlockMachineBlock b ? b : null;
    }

    /** 本格是否 master（只有 master 跑逻辑）。 */
    protected boolean isMaster() {
        MultiBlockMachineBlock block = machineBlock();
        return block != null && getBlockState().getValue(block.partProperty()) == 0;
    }

    /** 整台机器的所有格子；非多方块返回只含自身的一格。 */
    protected List<BlockPos> allCells() {
        MultiBlockMachineBlock block = machineBlock();
        BlockState state = getBlockState();
        if (block == null) {
            return List.of(getBlockPos());
        }
        return MultiBlockHelper.allCells(getBlockPos(),
                state.getValue(MultiBlockMachineBlock.FACING),
                block.sizeX(), block.sizeZ());
    }

    /** 面向方向（多方块机器的 FACING）。 */
    protected Direction facing() {
        return getBlockState().getValue(MultiBlockMachineBlock.FACING);
    }

    /** 区块加载自修复（子类不需要再覆写 onLoad）。 */
    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        MultiBlockMachineBlock block = machineBlock();
        if (block != null && isMaster()) {
            block.repair(level, getBlockPos(), getBlockState());
        }
    }

    /**
     * 把「运行中」状态同步到整机所有格子。
     *
     * <p>没有 ACTIVE 属性的机器（如科研台）直接跳过。</p>
     */
    protected void setActive(boolean active) {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        MultiBlockMachineBlock block = machineBlock();
        if (block == null) {
            return;
        }
        BooleanProperty activeProp = block.activeProperty();
        if (activeProp == null || getBlockState().getValue(activeProp) == active) {
            return;
        }
        for (BlockPos cell : allCells()) {
            BlockState cellState = level.getBlockState(cell);
            if (cellState.getBlock() == block && cellState.getValue(activeProp) != active) {
                level.setBlock(cell, cellState.setValue(activeProp, active), Block.UPDATE_CLIENTS);
            }
        }
        // 状态切换的音效：启动一声闷响、停机一声轻响。
        // 放在这里是因为它是所有多方块机器的唯一收口点 —— 加一次全部生效。
        // 只在真正的状态翻转时响（上面已经 return 掉了「状态没变」的情况）。
        if (active) {
            MachineFeedback.playStart(level, getBlockPos());
        } else {
            MachineFeedback.playStop(level, getBlockPos());
        }
    }

    // ------------------------------------------------------------ 诊断（供扳手右键）

    /**
     * 动力状态的一句话描述。
     *
     * <p>子类覆写并转调自己的 {@code PowerSource.statusText()}。默认返回
     * 「无动力」是因为基类不持有 PowerSource —— 由子类组合持有（见
     * {@code PowerSource} 的注释：不通过继承区分动力类型）。</p>
     */
    public String getPowerStatusText() {
        return "no power";
    }

    /**
     * 缺什么输入，返回<b>物品的语言键</b>（不是成品文案）。
     *
     * <p>返回 null 或空表示「不缺东西」（要么在正常跑，要么问题不在原料）。
     * 具体到物品是关键：告诉玩家「缺 2 个铁齿轮」比「原料不足」有用得多。</p>
     */
    public String describeMissingInput() {
        return null;
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        MachineStorage.saveItems(output, items);
        saveMachineData(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        MachineStorage.loadItems(input, items);
        loadMachineData(input);
    }

    /** 子类在这里写自己的额外字段（进度、燃料预算等）。 */
    protected void saveMachineData(ValueOutput output) {
        // 默认无额外数据
    }

    /** 子类在这里读自己的额外字段。 */
    protected void loadMachineData(ValueInput input) {
        // 默认无额外数据
    }
}
