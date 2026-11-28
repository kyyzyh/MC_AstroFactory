package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 抽水机：邻水放置，持续产水推送给相邻的锅炉
 * （对应异星工厂 offshore pump，简化为邻水即抽）。
 *
 * <p>水不作为流体方块存在，只走内部缓冲 + 相邻直连，
 * 保持「可见物理」风格：抽水机必须贴着水放，锅炉必须贴着抽水机放。</p>
 */
public class WaterPumpBlockEntity extends FactoryBlockEntity {

    /** 水缓冲上限。 */
    public static final int WATER_CAPACITY = 100;
    /**
     * 每 tick 产水量。v0.6 改为 2：1 台抽水机刚好供 1 台锅炉满负荷产 2 蒸汽/tick，
     * 再由锅炉均摊给 2 台蒸汽机（异星工厂 1 泵 : 1 锅炉 : 2 蒸汽机）。
     */
    public static final int WATER_PER_TICK = 2;

    private int water = 0;

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_PUMP.get(), pos, state);
    }

    public int getWater() {
        return this.water;
    }

    /** 是否紧邻水源（水平四向或正下方）。 */
    private boolean nearWater() {
        if (this.level == null) {
            return false;
        }
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST,
                Direction.WEST, Direction.DOWN}) {
            if (this.level.getBlockState(this.worldPosition.relative(dir))
                    .getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void serverTick() {
        boolean hadWater = this.water > 0;

        // 1) 邻水则产水
        if (this.water < WATER_CAPACITY && nearWater()) {
            setLit(true); // 邻水抽水 → 活塞上下抽动
            this.water = Math.min(WATER_CAPACITY, this.water + WATER_PER_TICK);
        } else if (!hadWater) {
            setLit(false); // 不邻水且无存水 → 停转
        }

        // 2) 推送给相邻锅炉
        if (this.water > 0 && this.level != null) {
            for (Direction dir : Direction.values()) {
                if (this.water <= 0) {
                    break;
                }
                if (this.level.getBlockEntity(this.worldPosition.relative(dir))
                        instanceof BoilerBlockEntity boiler) {
                    this.water = boiler.receiveWater(this.water);
                }
            }
        }
    }

    /** 更新方块 LIT 状态（驱动抽水活塞动画的开关）。 */
    private void setLit(boolean lit) {
        if (this.level == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (state.hasProperty(com.astrofactory.block.WaterPumpBlock.LIT)
                && state.getValue(com.astrofactory.block.WaterPumpBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(com.astrofactory.block.WaterPumpBlock.LIT, lit), 3);
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Water", this.water);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        this.water = tag.getInt("Water");
    }
}
