package com.astrofactory.blockentity;

import com.astrofactory.block.ConveyorBlock;
import com.astrofactory.registry.ModBlockEntities;
import com.astrofactory.util.ItemTransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 机械臂：从「后方」容器抽取物品，投放到「前方」的容器或传送带上。
 *
 * <p>这是产线的接缝：采矿机 → 传送带 → 机械臂 → 熔炉 → 传送带 → 箱子。
 * 相比 Create 的机械臂，这里保持最简：只有一进一出，无过滤配置。</p>
 */
public class InserterBlockEntity extends PoweredBlockEntity {

    /** 每多少 tick 完成一次搬运。 */
    private static final int CYCLE_TICKS = 10;
    /** 每次搬运消耗的能量（FE）。 */
    public static final int ENERGY_PER_MOVE = 20;
    /** 内部储能上限（FE）。 */
    public static final int ENERGY_CAPACITY = 5_000;
    /** 最大充能速度（FE/t）。 */
    public static final int MAX_RECEIVE = 100;

    private int cooldown = 0;

    public InserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INSERTER.get(), pos, state, ENERGY_CAPACITY, MAX_RECEIVE);
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        // 燃料模式：推进燃烧；无燃料/无电则停工
        if (getPowerMode() == PowerMode.FUEL) {
            tickFuel();
        }
        if (!canWork(ENERGY_PER_MOVE)) {
            return;
        }

        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Direction back = facing.getOpposite();

        // 1) 从后方抽取
        BlockPos sourcePos = this.worldPosition.relative(back);
        ItemStack picked = ItemTransferUtil.extractFrom(serverLevel, sourcePos, facing);
        if (picked.isEmpty()) {
            // 后方是传送带时，从带面上捡起物品实体
            picked = pickupFromConveyor(serverLevel, sourcePos);
        }
        if (picked.isEmpty()) {
            return;
        }

        // 2) 投向前方
        // deliver 返回「没送出去的部分」——必须按剩余堆退回，不能拿原堆退，
        // 否则目标已收下的那部分会被复制一份出来。
        BlockPos targetPos = this.worldPosition.relative(facing);
        ItemStack notDelivered = deliver(serverLevel, targetPos, back, picked);
        if (!notDelivered.isEmpty()) {
            // 投放失败/部分失败：只把剩余退回来（能退则退，否则丢在机器上方）
            ItemStack stillLeft = ItemTransferUtil.insertInto(serverLevel, sourcePos, facing, notDelivered);
            if (!stillLeft.isEmpty()) {
                dropAbove(serverLevel, stillLeft);
            }
        }

        consumeWorkCost(ENERGY_PER_MOVE);
        this.cooldown = effectiveTicks(CYCLE_TICKS);
        serverLevel.playSound(null, this.worldPosition, SoundEvents.PISTON_EXTEND,
                SoundSource.BLOCKS, 0.3F, 1.4F);
    }

    /**
     * 尝试把物品送到目标位置。
     *
     * @return 没能送出去的剩余堆（全部送出返回 {@link ItemStack#EMPTY}）
     */
    private ItemStack deliver(ServerLevel level, BlockPos targetPos, Direction side, ItemStack stack) {
        BlockState targetState = level.getBlockState(targetPos);

        // 目标是传送带：直接生成物品实体，由传送带接管
        if (targetState.getBlock() instanceof ConveyorBlock) {
            Direction beltFacing = targetState.getValue(ConveyorBlock.FACING);
            spawnOnBelt(level, targetPos, beltFacing, stack);
            return ItemStack.EMPTY;
        }

        // 目标是容器：走能力接口，返回真正没塞进去的部分。
        // 用 insertIntoInputSide 是为了避开熔炉/组装机的输出面。
        return ItemTransferUtil.insertIntoInputSide(level, targetPos, side, stack);
    }

    /** 从传送带带面上捡起一个物品实体。 */
    private ItemStack pickupFromConveyor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ConveyorBlock)) {
            return ItemStack.EMPTY;
        }
        var items = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0D, pos.getY() + 0.6D, pos.getZ() + 1.0D));
        for (ItemEntity item : items) {
            if (item.isAlive() && !item.getItem().isEmpty()) {
                ItemStack stack = item.getItem().copy();
                stack.setCount(1);
                ItemStack rest = item.getItem().copy();
                rest.shrink(1);
                if (rest.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(rest);
                }
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 在传送带入口处生成物品实体。 */
    private void spawnOnBelt(ServerLevel level, BlockPos pos, Direction beltFacing, ItemStack stack) {
        // 从带子「上游」一端放下，这样它会被完整地推过整格
        Direction upstream = beltFacing.getOpposite();
        double x = pos.getX() + 0.5D + upstream.getStepX() * 0.35D;
        double z = pos.getZ() + 0.5D + upstream.getStepZ() * 0.35D;
        double y = pos.getY() + ConveyorBlock.BELT_HEIGHT + 0.05D;

        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        entity.setNoGravity(true);
        level.addFreshEntity(entity);
    }

    private void dropAbove(ServerLevel level, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level,
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 1.0D,
                this.worldPosition.getZ() + 0.5D,
                stack);
        level.addFreshEntity(entity);
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveData(tag, registries);
        tag.putInt("Cooldown", this.cooldown);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadData(tag, registries);
        this.cooldown = tag.getInt("Cooldown");
    }
}
