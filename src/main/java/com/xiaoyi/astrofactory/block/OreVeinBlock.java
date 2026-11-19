package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 矿脉方块：真实带储量的可枯竭矿点（规格 §5.6）。
 *
 * <p>每块 300 储量，四态 richness 切贴图（rich / normal / poor / depleted）。
 * 采空变贫矿，最终耗尽。矿田由 {@link com.xiaoyi.astrofactory.world.OreVeinData}
 * 在出生点附近幂等放置。</p>
 *
 * <p>不是多方块，是单格方块 + BlockEntity 存储量。</p>
 */
public class OreVeinBlock extends Block implements EntityBlock {
    /** 矿种：铁 / 铜 / 煤 / 石。 */
    public static final EnumProperty<OreKind> KIND = EnumProperty.create("kind", OreKind.class);
    /** 贫富度：4 态切贴图。 */
    public static final EnumProperty<Richness> RICHNESS = EnumProperty.create("richness", Richness.class);

    /** 每块初始储量（规格 §5.6：300）。 */
    public static final int DEFAULT_RESERVE = 300;

    public OreVeinBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(KIND, OreKind.IRON)
                .setValue(RICHNESS, Richness.NORMAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, RICHNESS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreVeinBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        // 矿脉不需要 tick，储量由采矿机/玩家挖掘时扣减。
        return null;
    }

    /** 矿种对应的原矿 Item。 */
    public static ItemStack oreDrop(OreKind kind) {
        return switch (kind) {
            case IRON -> new ItemStack(net.minecraft.world.item.Items.RAW_IRON);
            case COPPER -> new ItemStack(net.minecraft.world.item.Items.RAW_COPPER);
            case COAL -> new ItemStack(net.minecraft.world.item.Items.COAL);
            case STONE -> new ItemStack(net.minecraft.world.item.Items.COBBLESTONE);
        };
    }

    /**
     * 挖掘一格矿脉：扣减储量，返回是否耗尽。
     *
     * <p>调用方负责：找到 BE、扣减储量、更新 richness 状态、掉 1 个原矿。</p>
     */
    public static boolean mineOne(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof OreVeinBlockEntity vein)) {
            return true; // 无 BE 直接清掉
        }
        int remaining = vein.decrementReserve();
        if (remaining <= 0) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            return true;
        }
        Richness newRichness = richnessFor(remaining);
        if (state.getValue(RICHNESS) != newRichness) {
            level.setBlock(pos, state.setValue(RICHNESS, newRichness),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        return false;
    }

    /** 储量 → 贫富度（300→rich, 200→normal, 100→poor, 0→depleted 视觉）。 */
    public static Richness richnessFor(int remaining) {
        if (remaining > 200) return Richness.RICH;
        if (remaining > 100) return Richness.NORMAL;
        if (remaining > 0) return Richness.POOR;
        return Richness.DEPLETED;
    }

    /** 玩家挖掘：掉 1 个原矿 + 扣减储量。 */
    @Override
    public void playerDestroy(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.server.level.ServerPlayer player,
                              BlockPos pos, BlockState state, BlockEntity be,
                              ItemStack tool) {
        super.playerDestroy(level, player, pos, state, be, tool);
        popResource(level, pos, oreDrop(state.getValue(KIND)));
        mineOne(level, pos, state);
    }

    // 矿种枚举（StringRepresentable 供 EnumProperty 使用）
    public enum OreKind implements StringRepresentable {
        IRON, COPPER, COAL, STONE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    // 贫富度枚举
    public enum Richness implements StringRepresentable {
        RICH, NORMAL, POOR, DEPLETED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }
}
