package com.astrofactory.menu;

import com.astrofactory.blockentity.AssemblerBlockEntity;
import com.astrofactory.recipe.AssemblerRecipe;
import com.astrofactory.recipe.AssemblerRecipes;
import com.astrofactory.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.List;

/**
 * 组装机菜单（US-104 重做）。
 *
 * <p>布局：左侧 3x3 投料区（槽 0-8）+ 右侧输出槽（槽 9）+ 配方选择列表（纯 GUI，
 * 不是物品槽）。配方选择走 {@link #clickMenuButton(Player, int)}：客户端点击列表
 * 第 i 项就发按钮 id = i，服务端用同一份「已解锁配方列表」的下标解析，避免传字符串。</p>
 */
public class AssemblerMenu extends AbstractContainerMenu {

    /** 取消选择配方时客户端发送的按钮 id。 */
    public static final int BUTTON_CLEAR = -1;

    private final ContainerLevelAccess access;
    private final AssemblerBlockEntity assembler;

    /** 客户端侧由 DataSlot 回写的同步值。 */
    private int syncedEnergy = 0;
    private int syncedProgress = 0;
    private int syncedMaxProgress = 1;
    private int syncedSelectedIndex = -1;
    private int syncedUnlockedCount = 0;
    private int syncedFlags = 0;

    /** 状态位：bit0 = 已选配方，bit1 = 缺料，bit2 = 输出满，bit3 = 有电。 */
    private static final int FLAG_SELECTED = 1;
    private static final int FLAG_MISSING = 1 << 1;
    private static final int FLAG_OUTPUT_FULL = 1 << 2;
    private static final int FLAG_POWERED = 1 << 3;

    /** 客户端构造（来自 MenuType，extraData 传方块位置）。 */
    public AssemblerMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, buf.readBlockPos());
    }

    private AssemblerMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, slotsOf(playerInv, pos), beOf(playerInv, pos),
                ContainerLevelAccess.NULL);
    }

    private static IItemHandler slotsOf(Inventory playerInv, BlockPos pos) {
        AssemblerBlockEntity be = beOf(playerInv, pos);
        return be != null ? be.getSlots() : new ItemStackHandler(AssemblerBlockEntity.TOTAL_SLOTS);
    }

    private static AssemblerBlockEntity beOf(Inventory playerInv, BlockPos pos) {
        return playerInv.player.level().getBlockEntity(pos) instanceof AssemblerBlockEntity be
                ? be : null;
    }

    /** 服务端构造。 */
    public AssemblerMenu(int containerId, Inventory playerInv, IItemHandler slots,
                         AssemblerBlockEntity assembler, ContainerLevelAccess access) {
        super(ModMenus.ASSEMBLER.get(), containerId);
        this.access = access;
        this.assembler = assembler;

        // 3x3 投料区（槽 0-8）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new SlotItemHandler(slots, col + row * 3, 30 + col * 18, 30 + row * 18));
            }
        }
        // 输出槽（槽 9）
        this.addSlot(new SlotItemHandler(slots, AssemblerBlockEntity.OUTPUT_SLOT, 124, 48) {
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
                        col + row * 9 + 9, 8 + col * 18, 116 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new SlotItemHandler(playerHandler, col, 8 + col * 18, 174));
        }

        // ---- 数据同步 ----
        addSync(() -> assembler != null ? assembler.getEnergyStored() : syncedEnergy,
                v -> syncedEnergy = v);
        addSync(() -> assembler != null ? assembler.getCraftProgress() : syncedProgress,
                v -> syncedProgress = v);
        addSync(() -> assembler != null ? assembler.getCraftMaxProgress() : syncedMaxProgress,
                v -> syncedMaxProgress = v);
        addSync(this::selectedIndex, v -> syncedSelectedIndex = v);
        addSync(this::unlockedCount, v -> syncedUnlockedCount = v);
        addSync(this::flags, v -> syncedFlags = v);
    }

    /** 注册一个读写绑定的 DataSlot。 */
    private void addSync(java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return getter.getAsInt();
            }

            @Override
            public void set(int value) {
                setter.accept(value);
            }
        });
    }

    // ---------------------------------------------------------------- 服务端状态

    /** 当前已解锁配方列表（服务端用真实科技进度，客户端用同步来的等级）。 */
    public List<RecipeHolder<AssemblerRecipe>> unlockedRecipes() {
        if (this.assembler != null && this.assembler.getLevel() instanceof ServerLevel serverLevel) {
            return AssemblerRecipes.unlocked(serverLevel);
        }
        if (this.assembler != null && this.assembler.getLevel() != null) {
            return AssemblerRecipes.unlockedClient(this.assembler.getLevel());
        }
        return List.of();
    }

    private int selectedIndex() {
        if (this.assembler == null) {
            return this.syncedSelectedIndex;
        }
        String id = this.assembler.getSelectedRecipeId();
        if (id == null || id.isEmpty()) {
            return -1;
        }
        List<RecipeHolder<AssemblerRecipe>> list = unlockedRecipes();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().toString().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int unlockedCount() {
        if (this.assembler == null) {
            return this.syncedUnlockedCount;
        }
        return unlockedRecipes().size();
    }

    private int flags() {
        if (this.assembler == null) {
            return this.syncedFlags;
        }
        int f = 0;
        if (this.assembler.getSelectedRecipe() != null) {
            f |= FLAG_SELECTED;
        }
        if (this.assembler.isMissingInput()) {
            f |= FLAG_MISSING;
        }
        if (this.assembler.isOutputFull()) {
            f |= FLAG_OUTPUT_FULL;
        }
        if (this.assembler.getEnergyStored() > 0) {
            f |= FLAG_POWERED;
        }
        return f;
    }

    // ---------------------------------------------------------------- 客户端读取

    public int getEnergyStored() {
        return this.syncedEnergy;
    }

    public int getCraftProgress() {
        return this.syncedProgress;
    }

    public int getCraftMaxProgress() {
        return Math.max(1, this.syncedMaxProgress);
    }

    public int getSelectedIndex() {
        return this.syncedSelectedIndex;
    }

    public int getUnlockedCount() {
        return this.syncedUnlockedCount;
    }

    public boolean hasSelectedRecipe() {
        return (this.syncedFlags & FLAG_SELECTED) != 0;
    }

    public boolean isMissingInput() {
        return (this.syncedFlags & FLAG_MISSING) != 0;
    }

    public boolean isOutputFull() {
        return (this.syncedFlags & FLAG_OUTPUT_FULL) != 0;
    }

    public boolean isPowered() {
        return (this.syncedFlags & FLAG_POWERED) != 0;
    }

    // ---------------------------------------------------------------- 配方选择

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.assembler == null) {
            return false;
        }
        if (id == BUTTON_CLEAR || id == 65535) {
            return this.assembler.setSelectedRecipe("");
        }
        List<RecipeHolder<AssemblerRecipe>> list = unlockedRecipes();
        if (id < 0 || id >= list.size()) {
            return false;
        }
        return this.assembler.setSelectedRecipe(list.get(id).id().toString());
    }

    // ---------------------------------------------------------------- 槽位逻辑

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        int outputIndex = AssemblerBlockEntity.OUTPUT_SLOT;
        if (index == outputIndex) {
            // 输出槽 → 玩家背包
            if (!this.moveItemStackTo(raw, 10, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < 9) {
            // 投料区 → 玩家背包
            if (!this.moveItemStackTo(raw, 10, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 → 投料区
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
