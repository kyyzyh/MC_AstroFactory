package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 机械臂方块实体。
 *
 * 工作循环（每 ACTION_INTERVAL tick 一次）：
 * 1. 从输入侧（FACING 反向）取物品
 *    - 若相邻是传送带：从带尾 extractReady()
 *    - 若相邻是 Container：取第一个非空槽
 * 2. 把物品放到输出侧（FACING 方向）
 *    - 传送带：insertAtStart()
 *    - Container：尝试合并到已有同类堆或空槽
 * 3. 若输出失败，物品退回输入侧（不丢失）
 */
public class RoboticArmBlockEntity extends BlockEntity {
    /** 每 10 tick（0.5 秒）执行一次搬运。 */
    public static final int ACTION_INTERVAL = 10;

    private int cooldown = 0;
    /** 手上持有的物品（搬运中），用于渲染与容错。 */
    private ItemStack held = ItemStack.EMPTY;

    public RoboticArmBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ROBOTIC_ARM_BE.get(), pos, state);
    }

    public ItemStack getHeld() {
        return held;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RoboticArmBlockEntity be) {
        if (be.cooldown > 0) {
            be.cooldown--;
            return;
        }
        be.cooldown = ACTION_INTERVAL;
        be.doWork(level, pos, state);
    }

    private void doWork(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(RoboticArmBlock.FACING);
        BlockPos inputPos = pos.relative(facing.getOpposite());
        BlockPos outputPos = pos.relative(facing);

        // 手上没东西 -> 去输入侧取
        if (held.isEmpty()) {
            held = takeFrom(level, inputPos, facing);
            if (held.isEmpty()) {
                return;
            }
            setChanged();
        }

        // 尝试放到输出侧
        if (putTo(level, outputPos, facing)) {
            held = ItemStack.EMPTY;
            setChanged();
        }
        // 放不进去就继续拿着，下个周期再试（形成阻塞，不丢物品）
    }

    /** 从指定位置取一个物品。 */
    private ItemStack takeFrom(Level level, BlockPos pos, Direction fromArm) {
        if (!level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ConveyorBlockEntity conveyor) {
            return conveyor.extractReady();
        }
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    ItemStack taken = container.removeItem(i, 1);
                    container.setChanged();
                    return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 往指定位置放一个物品，成功返回 true。 */
    private boolean putTo(Level level, BlockPos pos, Direction fromArm) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ConveyorBlockEntity conveyor) {
            return conveyor.insertAtStart(held);
        }
        if (be instanceof Container container) {
            // 先找同类堆（遵守 canPlaceItem，避免塞进输出槽）
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, held)
                        && slot.getCount() < slot.getMaxStackSize()
                        && container.canPlaceItem(i, held)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int move = Math.min(space, held.getCount());
                    slot.grow(move);
                    held.shrink(move);
                    container.setChanged();
                    return held.isEmpty();
                }
            }
            // 再找空槽
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty() && container.canPlaceItem(i, held)) {
                    container.setItem(i, held.copy());
                    held = ItemStack.EMPTY;
                    container.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Held", ItemStack.CODEC, held);
        output.putInt("Cooldown", cooldown);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held = input.read("Held", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        cooldown = input.getIntOr("Cooldown", 0);
    }
}
