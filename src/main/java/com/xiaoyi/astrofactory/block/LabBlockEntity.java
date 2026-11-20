package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.machine.AbstractMachineEntity;
import com.xiaoyi.astrofactory.menu.LabMenu;
import com.xiaoyi.astrofactory.power.ElectricPowerSource;

/**
 * 科研台方块实体。
 *
 * <p>只存 1 个槽位：科研包投入口。
 * 科技解锁进度不在这里，而是挂在玩家身上（ResearchProgress），
 * 这样换一台科研台也能继续研究。</p>
 */
public class LabBlockEntity extends AbstractMachineEntity {
    /** 科研包输入槽。 */
    public static final int INPUT_SLOT = 0;
    public static final int TOTAL_SLOTS = 1;

    /** 电力驱动（规格 §5.4：科研台是电动的，断电停机）。 */
    private final ElectricPowerSource power = new ElectricPowerSource(this);

    public LabBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.LAB_BE.get(), pos, state, TOTAL_SLOTS);
    }

    /** 是否通电（供 GUI 与投入校验）。 */
    public boolean isPowered() {
        return power.isPowered();
    }

    /** 尝试消费一次电（投入科研包时调用）。 */
    public boolean tryConsumePower() {
        return power.consume(false);
    }

    /** 科研包槽只接受单个物品（投入即被消耗）。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT;
    }

    // ------------------------------------------------------------ MenuProvider

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.astrofactory.lab");
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory inventory, Player player) {
        return new LabMenu(containerId, inventory, this, player);
    }
}
