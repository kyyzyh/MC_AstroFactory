package com.astrofactory.menu;

import com.astrofactory.blockentity.LabBlockEntity;
import com.astrofactory.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * 实验室菜单（US-106）。
 *
 * <p>槽位：红瓶输入（1 个）+ 玩家背包 + 快捷栏。科技列表 / 在研 / 进度是<b>世界级
 * 数据</b>，不属于任何容器，由 {@link com.astrofactory.network.ModNetwork} 的独立
 * payload 同步；玩家在界面点「选择 / 入队」走 Enqueue/Dequeue/Select 网络包，
 * 服务端校验后写回 {@link com.astrofactory.tech.TechTreeData}。</p>
 *
 * <p>本菜单只负责：红瓶槽 + 同步实验室自身的能量 / 周期进度，供界面显示。</p>
 */
public class LabMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final LabBlockEntity lab;

    private int syncedEnergy = 0;
    private int syncedProgress = 0;
    private int syncedCycle = 1;

    /** 客户端构造（来自 MenuType，extraData 传方块位置）。 */
    public LabMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, buf.readBlockPos());
    }

    private LabMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, pos, redInputOf(playerInv, pos), playerInv.player.level());
    }

    private static IItemHandler redInputOf(Inventory playerInv, BlockPos pos) {
        LabBlockEntity be = labOf(playerInv, pos);
        return be != null ? be.getRedInput() : new ItemStackHandler(LabBlockEntity.RED_SLOTS);
    }

    private static LabBlockEntity labOf(Inventory playerInv, BlockPos pos) {
        return playerInv.player.level().getBlockEntity(pos) instanceof LabBlockEntity be ? be : null;
    }

    /** 服务端构造。 */
    public LabMenu(int containerId, Inventory playerInv, BlockPos pos,
                   IItemHandler redInput, Level level) {
        super(ModMenus.LAB.get(), containerId);
        this.access = ContainerLevelAccess.create(level, pos);
        this.lab = labOf(playerInv, pos);

        // 红瓶输入（槽 0）
        this.addSlot(new SlotItemHandler(redInput, 0, 152, 30));

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

        // 数据同步：能量 + 周期进度
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return lab != null ? lab.getEnergyStored() : syncedEnergy;
            }

            @Override
            public void set(int v) {
                syncedEnergy = v;
            }
        });
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return lab != null ? lab.getProgress() : syncedProgress;
            }

            @Override
            public void set(int v) {
                syncedProgress = v;
            }
        });
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return lab != null ? lab.getCycleTicks() : syncedCycle;
            }

            @Override
            public void set(int v) {
                syncedCycle = v;
            }
        });
    }

    public int getEnergyStored() {
        return this.syncedEnergy;
    }

    public int getProgress() {
        return this.syncedProgress;
    }

    public int getMaxProgress() {
        return this.lab != null ? this.lab.getCycleTicks() : this.syncedCycle;
    }

    public boolean isLab() {
        return this.lab != null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        if (index == 0) {
            // 红瓶槽 → 玩家背包
            if (!this.moveItemStackTo(raw, 1, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 → 红瓶槽（仅红瓶）
            if (raw.is(com.astrofactory.registry.ModItems.RED_SCIENCE_PACK.get())) {
                if (!this.moveItemStackTo(raw, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (raw.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((lvl, pos) -> lvl.getBlockEntity(pos) instanceof LabBlockEntity, true);
    }
}
