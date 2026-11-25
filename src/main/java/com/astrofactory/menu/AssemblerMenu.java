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
        this(containerId, playerInv, buf.readBlockPos());
    }

    /**
     * 客户端构造的实际实现。
     *
     * <p>必须拿到真实的方块实体：之前这里传的是 {@code new ItemStackHandler(10)}，
     * 客户端看到的是一个空壳容器，组装机 GUI 里的原料和成品永远是空的、
     * 也塞不进去东西。客户端本来就持有 BlockEntity，直接用它的槽位即可。</p>
     */
    private AssemblerMenu(int containerId, Inventory playerInv,
                          net.minecraft.core.BlockPos pos) {
        this(containerId, playerInv, slotsOf(playerInv, pos), beOf(playerInv, pos),
                ContainerLevelAccess.NULL);
    }

    private static IItemHandler slotsOf(Inventory playerInv, net.minecraft.core.BlockPos pos) {
        AssemblerBlockEntity be = beOf(playerInv, pos);
        return be != null ? be.getSlots() : new net.neoforged.neoforge.items.ItemStackHandler(10);
    }

    private static AssemblerBlockEntity beOf(Inventory playerInv, net.minecraft.core.BlockPos pos) {
        return playerInv.player.level().getBlockEntity(pos) instanceof AssemblerBlockEntity be
                ? be : null;
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

        // 玩家背包（v0.8：面板加高到 186 以容纳进度条，背包整体下移到 104 起）
        var playerHandler = new InvWrapper(playerInv);
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new SlotItemHandler(playerHandler,
                        col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new SlotItemHandler(playerHandler, col, 8 + col * 18, 162));
        }

        // 同步能量与进度：之前用 DataSlot.standalone() 只是占位，读出来永远是 0，
        // GUI 的能量条/进度条等于纯装饰。这里绑定真实数据：
        // 服务端 get() 从方块实体读，客户端 set() 把服务端推来的值存进本地字段。
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return assembler != null ? assembler.getEnergyStored() : syncedEnergy;
            }

            @Override
            public void set(int value) {
                syncedEnergy = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return assembler != null ? assembler.getCraftProgress() : syncedProgress;
            }

            @Override
            public void set(int value) {
                syncedProgress = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return assembler != null ? assembler.getCraftMaxProgress() : syncedMaxProgress;
            }

            @Override
            public void set(int value) {
                syncedMaxProgress = value;
            }
        });
    }

    /** 客户端侧由 DataSlot 回写的同步值。 */
    private int syncedEnergy = 0;
    private int syncedProgress = 0;
    private int syncedMaxProgress = AssemblerBlockEntity.CRAFT_TICKS;

    /** 供 GUI 读取：当前储能（FE）。 */
    public int getEnergyStored() {
        return this.syncedEnergy;
    }

    /** 供 GUI 读取：当前合成进度（tick）。 */
    public int getCraftProgress() {
        return this.syncedProgress;
    }

    /** 供 GUI 读取：本次合成需要的总 tick。 */
    public int getCraftMaxProgress() {
        return Math.max(1, this.syncedMaxProgress);
    }

    /** 供 GUI 读取：储能上限（FE）。 */
    public int getEnergyCapacity() {
        return AssemblerBlockEntity.ENERGY_CAPACITY;
    }

    /** 供 GUI 读取：合成周期（tick）。 */
    public int getCraftTicks() {
        return AssemblerBlockEntity.CRAFT_TICKS;
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
