package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 矿脉储量方块实体（v0.8.9 矿体改造）。
 *
 * <p>每个矿脉方块格记录<b>剩余储量</b> {@code reserve}。一片矿体由若干矿脉格组成
 * （世界生成成冰山状），采矿机从覆盖区域内整片矿体的总储量池开采；某格储量
 * 扣到 0 后塌陷为空气（矿洞）。手挖一格矿脉则整格破坏并掉落对应矿。</p>
 */
public class OreFieldBlockEntity extends BlockEntity {

    /** 单格默认初始储量（config 可调；见 {@link com.astrofactory.config.AstroFactoryConfig}）。 */
    public static final int DEFAULT_RESERVE = com.astrofactory.config.AstroFactoryConfig.ORE_FIELD_RESERVE.get();

    private int reserve = DEFAULT_RESERVE;

    public OreFieldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_FIELD.get(), pos, state);
    }

    /** 剩余储量（单位：可采物品数）。 */
    public int getReserve() {
        return this.reserve;
    }

    /** 直接设置储量（测试用，或世界加载时回填）。 */
    public void setReserve(int value) {
        this.reserve = Math.max(0, value);
        setChanged();
    }

    /**
     * 尝试扣 {@code amount} 储量。返回是否耗尽（true = 剩余已 ≤0）。
     * 采矿机每个产出周期调用一次。
     */
    public boolean tryConsume(int amount) {
        if (amount <= 0) {
            return false;
        }
        this.reserve = Math.max(0, this.reserve - amount);
        setChanged();
        return this.reserve <= 0;
    }

    /**
     * 服务端 tick：若本格储量已耗尽，则塌陷为空气（矿洞）。
     * 采矿机扣空某一非支撑格后，由这里在下个 tick 把该格清除，
     * 让玩家看到矿被一步步掏空的矿洞效果。
     *
     * <p>紧邻采矿机下方的那格（支撑格）<b>不塌陷</b>：否则采矿机会悬空掉落。
     * 整片矿采空后，采矿机停在最后一块耗尽矿格上并进入 no_ore 待机。</p>
     */
    public void serverTick() {
        if (this.level == null || this.level.isClientSide || !(this.level instanceof ServerLevel))
            return;
        if (this.level.getGameTime() % 20L != (this.worldPosition.getY() & 0xF)) {
            return; // 错峰，避免大面积矿体同一 tick 一起塌陷造成卡顿
        }
        if (!isExhausted()) {
            return;
        }
        // 支撑格不塌陷
        net.minecraft.world.level.block.state.BlockState above =
                this.level.getBlockState(this.worldPosition.above());
        if (above.getBlock() instanceof com.astrofactory.block.MinerBlock) {
            return;
        }
        if (this.level.getBlockState(this.worldPosition)
                .getBlock() instanceof com.astrofactory.block.OreFieldBlock) {
            this.level.setBlock(this.worldPosition,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** 是否已采空。采空后上方方块应塌陷为空气。 */
    public boolean isExhausted() {
        return this.reserve <= 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Reserve", this.reserve);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Reserve")) {
            this.reserve = tag.getInt("Reserve");
        }
    }
}
