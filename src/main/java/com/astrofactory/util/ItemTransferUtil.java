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
     * 从 {@code accessSide} 这一侧往 {@code pos} 塞物品，但若该侧是
     * 熔炉 / 组装机的「输出面」则拒绝。
     *
     * <p>这两类机器的朝向面暴露输出槽，原料必须从其它面进入；否则原料会被
     * 塞进成品槽，把产物污染成原料。</p>
     *
     * @return 没能塞进去的剩余堆
     */
    public static ItemStack insertIntoInputSide(ServerLevel level, BlockPos pos,
                                                Direction accessSide, ItemStack stack) {
        if (isOutputFace(level, pos, accessSide)) {
            return stack;
        }
        return insertInto(level, pos, accessSide, stack);
    }

    /** 判断 {@code accessSide} 是不是该方块的输出面。 */
    public static boolean isOutputFace(ServerLevel level, BlockPos pos, Direction accessSide) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.astrofactory.block.SmelterBlock
                && state.hasProperty(com.astrofactory.block.SmelterBlock.FACING)) {
            return state.getValue(com.astrofactory.block.SmelterBlock.FACING) == accessSide;
        }
        if (state.getBlock() instanceof com.astrofactory.block.AssemblerBlock
                && state.hasProperty(com.astrofactory.block.AssemblerBlock.FACING)) {
            return state.getValue(com.astrofactory.block.AssemblerBlock.FACING) == accessSide;
        }
        return false;
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
