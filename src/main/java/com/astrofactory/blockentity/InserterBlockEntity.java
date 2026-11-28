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
public class InserterBlockEntity extends PoweredBlockEntity implements IMachineInfo {

    // ---- US-103：数值全部走配置（config/astrofactory-common.toml） ----

    /** 电力模式每 tick 耗电（FE/t）。蓝图基准 10。 */
    public static int fePerTick() {
        return com.astrofactory.config.AstroFactoryConfig.INSERTER_FE_PER_TICK.get();
    }

    /** 一次搬运所需 tick 数。蓝图基准 17（约 1.2 次/秒）。 */
    public static int cycleTicks() {
        return com.astrofactory.config.AstroFactoryConfig.INSERTER_CYCLE_TICKS.get();
    }

    /** 内部储能上限（FE）。蓝图基准 10,000。 */
    public static int energyCapacity() {
        return com.astrofactory.config.AstroFactoryConfig.INSERTER_CAPACITY.get();
    }

    /** 最大充能速度（FE/t）。 */
    public static int maxReceive() {
        return com.astrofactory.config.AstroFactoryConfig.INSERTER_MAX_RECEIVE.get();
    }

    private int cooldown = 0;

    public InserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INSERTER.get(), pos, state, energyCapacity(), maxReceive());
    }

    /** 科技速度加成：机械动力 I 时电力机械臂提速（US-108，仅电力模式生效）。 */
    @Override
    protected double techSpeedMultiplier() {
        if (getPowerMode() != PowerMode.ELECTRIC) {
            return 1.0;
        }
        if (this.level == null || this.level.getServer() == null) {
            return 1.0;
        }
        return com.astrofactory.tech.TechTreeData.get(this.level.getServer()).inserterSpeedMultiplier();
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 能源门禁（US-103）：断电/断燃料时直接返回——冷却不倒数、不搬物品，
        // 于是机械臂「停在原处」，手里不会有半截物品（物品是原子地进或出的）。
        if (getPowerMode() == PowerMode.FUEL) {
            tickFuel();
            if (!isBurning()) {
                setLit(false); // 无燃料停工
                return;
            }
        } else {
            // 电力模式：每 tick 扣电，没电立刻停
            if (!tickElectric(fePerTick())) {
                setLit(false); // 断电停工
                return;
            }
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            setLit(true); // 冷却推进中可视作「正在摆臂」
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
            setLit(false); // 无料可取
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

        this.cooldown = techTicks(effectiveTicks(cycleTicks()));
        setLit(true); // 完成一次搬运，进入摆臂冷却
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

    /** 更新方块 LIT 状态（驱动摆臂动画的开关）。 */
    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(com.astrofactory.block.InserterBlock.LIT)
                && state.getValue(com.astrofactory.block.InserterBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(com.astrofactory.block.InserterBlock.LIT, lit), 3);
        }
    }

    /** 当前搬运冷却剩余（tick）。断电时应保持不变（US-103 验收断言用）。 */
    public int getCooldown() {
        return this.cooldown;
    }

    // ------------------------------------------------------------ IMachineInfo 信息面板

    @Override
    public String infoTitle() {
        return com.astrofactory.registry.ModBlocks.INSERTER.get().getName().getString();
    }

    @Override
    public String infoStatusKey() {
        if (this.level == null) {
            return "idle";
        }
        if (getPowerMode() == PowerMode.FUEL) {
            if (!isBurning()) {
                return "no_fuel";
            }
        } else if (!hasEnergy(fePerTick())) {
            return "no_power";
        }
        return this.cooldown > 0 ? "working" : "idle";
    }

    @Override
    public int infoEnergy() {
        return getEnergyStored();
    }

    @Override
    public int infoEnergyCapacity() {
        return energyCapacity();
    }

    @Override
    public int infoProgress() {
        return this.cooldown;
    }

    @Override
    public int infoMaxProgress() {
        return Math.max(1, cycleTicks());
    }

    @Override
    public String infoExtra() {
        // 展示搬运方向
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite().getName()
                + " → " + getBlockState().getValue(HorizontalDirectionalBlock.FACING).getName();
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
