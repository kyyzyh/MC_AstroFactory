package com.astrofactory.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 物品在方块之间的搬运工具。
 */
public final class ItemTransferUtil {

    private ItemTransferUtil() {}

    /** 取出目标位置的物品容器，没有则返回 null。 */
    public static IItemHandler getHandler(ServerLevel level, BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }

    /**
     * 把物品塞进相邻方块，返回塞不进去的剩余堆。
     *
     * @param side 从目标方块的哪一面接入（用于区分面敏感容器）
     */
    public static ItemStack insertInto(ServerLevel level, BlockPos pos, Direction side, ItemStack stack) {
        IItemHandler handler = getHandler(level, pos, side);
        if (handler == null) {
            return stack;
        }
        ItemStack remaining = stack;
        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            remaining = handler.insertItem(i, remaining, false);
        }
        return remaining;
    }

    /** 从相邻方块抽取一个物品，抽不到返回空堆。 */
    public static ItemStack extractFrom(ServerLevel level, BlockPos pos, Direction side) {
        IItemHandler handler = getHandler(level, pos, side);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack inSlot = handler.getStackInSlot(i);
            if (!inSlot.isEmpty()) {
                ItemStack extracted = handler.extractItem(i, 1, false);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
