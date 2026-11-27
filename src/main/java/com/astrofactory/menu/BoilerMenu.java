package com.astrofactory.menu;

import com.astrofactory.blockentity.BoilerBlockEntity;
import com.astrofactory.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 锅炉信息面板菜单（US-101）。
 *
 * <p>锅炉没有物品槽——燃料是「手持右键投料」进内部槽的，
 * 所以这里只同步数值给 GUI 看：水量、蒸汽量、燃烧剩余/总时长、缓冲上限。</p>
 *
 * <p>客户端方块实体不 tick，数值不会自己更新，必须靠 DataSlot 推送。</p>
 */
public class BoilerMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    /** 服务端非空；客户端为 null（客户端从 DataSlot 收值）。 */
    private final BoilerBlockEntity boiler;

    // 客户端侧由 DataSlot 回写的同步值
    private int syncedWater;
    private int syncedSteam;
    private int syncedBurnTime;
    private int syncedBurnTotal;

    /** 客户端构造：extraData 只传方块位置。 */
    public BoilerMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, beOf(playerInv, buf.readBlockPos()));
    }

    private static BoilerBlockEntity beOf(Inventory playerInv, BlockPos pos) {
        return playerInv.player.level().getBlockEntity(pos) instanceof BoilerBlockEntity be ? be : null;
    }

    /** 服务端构造。 */
    public BoilerMenu(int containerId, Inventory playerInv, BoilerBlockEntity boiler) {
        super(ModMenus.BOILER.get(), containerId);
        this.boiler = boiler;
        this.access = boiler == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(boiler.getLevel(), boiler.getBlockPos());

        // 水量
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return boiler != null ? boiler.getWater() : syncedWater;
            }

            @Override
            public void set(int value) {
                syncedWater = value;
            }
        });
        // 蒸汽量
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return boiler != null ? boiler.getSteam() : syncedSteam;
            }

            @Override
            public void set(int value) {
                syncedSteam = value;
            }
        });
        // 燃烧剩余 tick
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return boiler != null ? boiler.getBurnTime() : syncedBurnTime;
            }

            @Override
            public void set(int value) {
                syncedBurnTime = value;
            }
        });
        // 燃烧总时长 tick
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return boiler != null ? boiler.getBurnTotal() : syncedBurnTotal;
            }

            @Override
            public void set(int value) {
                syncedBurnTotal = value;
            }
        });
    }

    public int getWater() {
        return this.syncedWater;
    }

    public int getSteam() {
        return this.syncedSteam;
    }

    public int getBurnTime() {
        return this.syncedBurnTime;
    }

    public int getBurnTotal() {
        return this.syncedBurnTotal;
    }

    public int getFluidCapacity() {
        return BoilerBlockEntity.fluidCapacity();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // 无物品槽
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.boiler == null) {
            return true; // 客户端
        }
        return AbstractContainerMenu.stillValid(this.access, player,
                com.astrofactory.registry.ModBlocks.BOILER.get());
    }
}
