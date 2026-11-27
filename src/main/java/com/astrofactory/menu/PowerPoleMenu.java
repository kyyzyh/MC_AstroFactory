package com.astrofactory.menu;

import com.astrofactory.blockentity.PowerPoleBlockEntity;
import com.astrofactory.config.AstroFactoryConfig;
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
 * 电线杆信息面板菜单（US-102）。
 *
 * <p>无物品槽，只同步四项统计：本网杆数、发电设备数、耗电设备数、
 * 上 tick 实际传输量（FE）。客户端方块实体不 tick，必须靠 DataSlot 推送。</p>
 */
public class PowerPoleMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final PowerPoleBlockEntity pole;

    private int syncedPoles;
    private int syncedSources;
    private int syncedSinks;
    private int syncedTransfer;

    /** 客户端构造：extraData 只传方块位置。 */
    public PowerPoleMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, beOf(playerInv, buf.readBlockPos()));
    }

    private static PowerPoleBlockEntity beOf(Inventory playerInv, BlockPos pos) {
        return playerInv.player.level().getBlockEntity(pos) instanceof PowerPoleBlockEntity be ? be : null;
    }

    /** 服务端构造。 */
    public PowerPoleMenu(int containerId, Inventory playerInv, PowerPoleBlockEntity pole) {
        super(ModMenus.POWER_POLE.get(), containerId);
        this.pole = pole;
        this.access = pole == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(pole.getLevel(), pole.getBlockPos());

        this.addDataSlot(slot(() -> pole != null ? pole.getPoleCount() : syncedPoles, v -> syncedPoles = v));
        this.addDataSlot(slot(() -> pole != null ? pole.getSourceCount() : syncedSources, v -> syncedSources = v));
        this.addDataSlot(slot(() -> pole != null ? pole.getSinkCount() : syncedSinks, v -> syncedSinks = v));
        this.addDataSlot(slot(() -> pole != null ? pole.getLastTransfer() : syncedTransfer, v -> syncedTransfer = v));
    }

    private static DataSlot slot(java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return new DataSlot() {
            @Override
            public int get() {
                return getter.getAsInt();
            }

            @Override
            public void set(int value) {
                setter.accept(value);
            }
        };
    }

    public int getPoleCount() {
        return this.syncedPoles;
    }

    public int getSourceCount() {
        return this.syncedSources;
    }

    public int getSinkCount() {
        return this.syncedSinks;
    }

    public int getTransfer() {
        return this.syncedTransfer;
    }

    public int getRadius() {
        return AstroFactoryConfig.POWER_POLE_RADIUS.get();
    }

    public int getLinkRange() {
        return AstroFactoryConfig.POWER_POLE_LINK_RANGE.get();
    }

    public int getMaxTransfer() {
        return AstroFactoryConfig.POWER_POLE_MAX_TRANSFER.get();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.pole == null) {
            return true;
        }
        return AbstractContainerMenu.stillValid(this.access, player,
                com.astrofactory.registry.ModBlocks.POWER_POLE.get());
    }
}
