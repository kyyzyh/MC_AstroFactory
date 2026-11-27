package com.astrofactory.menu;

import com.astrofactory.blockentity.SteamEngineBlockEntity;
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
 * 蒸汽机信息面板菜单（US-101）。
 *
 * <p>同步：当前储能、储能上限、累计发电量、累计耗汽量。</p>
 */
public class SteamEngineMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final SteamEngineBlockEntity engine;

    private int syncedEnergy;
    private int syncedCapacity;
    private int syncedGenerated;
    private int syncedSteam;

    /** 客户端构造：extraData 只传方块位置。 */
    public SteamEngineMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, beOf(playerInv, buf.readBlockPos()));
    }

    private static SteamEngineBlockEntity beOf(Inventory playerInv, BlockPos pos) {
        return playerInv.player.level().getBlockEntity(pos) instanceof SteamEngineBlockEntity be
                ? be : null;
    }

    /** 服务端构造。 */
    public SteamEngineMenu(int containerId, Inventory playerInv, SteamEngineBlockEntity engine) {
        super(ModMenus.STEAM_ENGINE.get(), containerId);
        this.engine = engine;
        this.access = engine == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(engine.getLevel(), engine.getBlockPos());

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return engine != null ? engine.getEnergyStored() : syncedEnergy;
            }

            @Override
            public void set(int value) {
                syncedEnergy = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return engine != null ? engine.getEnergyCapacity() : syncedCapacity;
            }

            @Override
            public void set(int value) {
                syncedCapacity = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return engine != null ? engine.getGeneratedTotal() : syncedGenerated;
            }

            @Override
            public void set(int value) {
                syncedGenerated = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return engine != null ? engine.getSteamConsumed() : syncedSteam;
            }

            @Override
            public void set(int value) {
                syncedSteam = value;
            }
        });
    }

    public int getEnergyStored() {
        return this.syncedEnergy;
    }

    public int getEnergyCapacity() {
        return Math.max(1, this.syncedCapacity);
    }

    public int getGeneratedTotal() {
        return this.syncedGenerated;
    }

    public int getSteamConsumed() {
        return this.syncedSteam;
    }

    /** 每 tick 发电上限（配置值，两端一致）。 */
    public int getGenerationPerTick() {
        return SteamEngineBlockEntity.generationPerTick();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // 无物品槽
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.engine == null) {
            return true; // 客户端
        }
        return AbstractContainerMenu.stillValid(this.access, player,
                com.astrofactory.registry.ModBlocks.STEAM_ENGINE.get());
    }
}
