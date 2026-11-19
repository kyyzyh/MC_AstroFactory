package com.xiaoyi.astrofactory.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 燃料动力源：从机器的燃料槽取燃烧物，烧成 tick 预算。
 *
 * <p>燃烧机器（采矿机 / 石熔炉 / 燃烧臂）共用此实现。
 * 机器持有一个燃料槽（Container 的某个 slot），本类负责：
 * - 检查预算是否耗尽，耗尽则从燃料槽取一个可燃物补充预算
 * - 每 tick 扣减 1 预算
 * - 燃烧臂的"偷燃料"逻辑：当搬运的物品本身是燃料时，优先烧它</p>
 *
 * <p>规格红线：燃烧期吃煤、燃烧臂偷燃料。</p>
 */
public class FuelPowerSource implements PowerSource {
    /** 持有燃料槽的容器（机器 BE）。 */
    private final Container fuelSlot;
    /** 燃料槽的 slot 索引。 */
    private final int fuelSlotIndex;
    /** 剩余燃烧 tick 预算。 */
    private int burnBudget = 0;
    /** 当前燃烧中的物品（用于渲染，可选）。 */
    private ItemStack burning = ItemStack.EMPTY;

    public FuelPowerSource(Container fuelSlot, int fuelSlotIndex) {
        this.fuelSlot = fuelSlot;
        this.fuelSlotIndex = fuelSlotIndex;
    }

    @Override
    public boolean consume(boolean simulate) {
        // 预算够：直接扣
        if (burnBudget > 0) {
            if (!simulate) {
                burnBudget--;
            }
            return true;
        }
        // 预算耗尽：尝试从燃料槽取一个可燃物
        ItemStack stack = fuelSlot.getItem(fuelSlotIndex);
        int ticks = PowerSource.fuelTicks(stack);
        if (ticks <= 0) {
            return false; // 没燃料了
        }
        if (simulate) {
            return true; // 模拟时假设能烧
        }
        // 实际消费：取走一个燃料，加预算
        fuelSlot.removeItem(fuelSlotIndex, 1);
        burnBudget = ticks - 1; // 本 tick 已用 1
        burning = stack.copyWithCount(1);
        if (fuelSlot instanceof BlockEntity be) {
            be.setChanged();
        }
        return true;
    }

    @Override
    public boolean isPowered() {
        return burnBudget > 0 || PowerSource.fuelTicks(fuelSlot.getItem(fuelSlotIndex)) > 0;
    }

    @Override
    public String statusText() {
        if (burnBudget > 0) {
            return "burning " + burnBudget + " tick";
        }
        ItemStack stack = fuelSlot.getItem(fuelSlotIndex);
        int ticks = PowerSource.fuelTicks(stack);
        if (ticks > 0) {
            return "fuel ready (" + stack.getCount() + ")";
        }
        return "no fuel";
    }

    /** 剩余燃烧预算（供 GUI 显示）。 */
    public int getBurnBudget() {
        return burnBudget;
    }

    /**
     * 燃烧臂偷燃料：当手上搬的物品本身是燃料时，
     * 直接烧它而不烧燃料槽里的煤。
     *
     * @param carried 手上搬运的物品（可能已被部分消耗）
     * @return 是否成功偷到燃料并补充了预算
     */
    public boolean stealFromCarried(ItemStack carried) {
        if (burnBudget > 0) {
            return false; // 还有预算，不用偷
        }
        int ticks = PowerSource.fuelTicks(carried);
        if (ticks <= 0) {
            return false;
        }
        carried.shrink(1);
        burnBudget = ticks - 1;
        burning = carried.copyWithCount(1);
        return true;
    }

    /** 存档。 */
    public int getBurnBudgetForSave() {
        return burnBudget;
    }

    /** 读档。 */
    public void setBurnBudgetForLoad(int value) {
        this.burnBudget = value;
    }
}
