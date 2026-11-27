package com.astrofactory.blockentity;

import com.astrofactory.power.PowerGrid;
import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 电线杆方块实体（US-102）。
 *
 * <p>本身不存电，只做两件事：</p>
 * <ol>
 *   <li>作为电网的锚点，让 {@link PowerGrid} 能通过它聚合网络；</li>
 *   <li>若是所在网络的 owner（坐标最小的杆），每 tick 触发一次分配。</li>
 * </ol>
 *
 * <p>这样一张网每 tick 只算一次，不是每根杆都算一遍。</p>
 */
public class PowerPoleBlockEntity extends FactoryBlockEntity implements MenuProvider {

    /** 上次分配实际传输的能量（GUI 显示用）。 */
    private int lastTransfer = 0;
    /** 网络内发电设备数量与耗电设备数量（GUI 显示用）。 */
    private int sourceCount = 0;
    private int sinkCount = 0;
    /** 网络内杆数量。 */
    private int poleCount = 0;

    public PowerPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_POLE.get(), pos, state);
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        PowerGrid.Grid grid = PowerGrid.resolve(serverLevel, this.worldPosition);
        if (grid == null) {
            return;
        }
        // 只有 owner 执行分配，其余杆只更新自己的显示数据
        if (grid.owner.equals(this.worldPosition)) {
            this.lastTransfer = PowerGrid.distribute(serverLevel, grid);
        }
        this.sourceCount = grid.sources.size();
        this.sinkCount = grid.sinks.size();
        this.poleCount = grid.poles.size();
    }

    public int getLastTransfer() {
        return this.lastTransfer;
    }

    public int getSourceCount() {
        return this.sourceCount;
    }

    public int getSinkCount() {
        return this.sinkCount;
    }

    public int getPoleCount() {
        return this.poleCount;
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astrofactory.power_pole");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new com.astrofactory.menu.PowerPoleMenu(containerId, playerInv, this);
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("LastTransfer", this.lastTransfer);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        this.lastTransfer = tag.getInt("LastTransfer");
    }
}
