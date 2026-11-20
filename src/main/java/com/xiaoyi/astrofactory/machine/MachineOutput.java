package com.xiaoyi.astrofactory.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.xiaoyi.astrofactory.block.TransportBeltBlockEntity;

/**
 * 机器输出推送工具：把物品塞进传送带或容器。
 *
 * <p>规格 §5.3 规定机器不自动吸附，唯一例外是采矿机出矿口。
 * 这里的推送语义是「只往指定格子推，推不进去就返回 false」，由调用方决定是否积压。</p>
 */
public final class MachineOutput {
    private MachineOutput() {}

    /**
     * 往某个位置推 1 个物品。
     *
     * @return 成功推入返回 {@code true}
     */
    public static boolean pushOne(Level level, BlockPos targetPos, ItemStack stack) {
        if (!level.isLoaded(targetPos)) {
            return false;
        }
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) {
            return false;
        }
        if (target instanceof TransportBeltBlockEntity belt) {
            return belt.insertAtStart(stack);
        }
        if (target instanceof Container container) {
            return insertIntoContainer(container, stack);
        }
        return false;
    }

    /** 先合并到同类堆，再找空槽。 */
    public static boolean insertIntoContainer(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                container.setChanged();
                return true;
            }
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, stack.copyWithCount(1));
                container.setChanged();
                return true;
            }
        }
        return false;
    }
}
