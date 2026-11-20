package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.multiblock.MultiBlockMachineBlock;

/**
 * 燃烧采矿机：2×2 多方块（规格 §5.1 / §5.4）。
 *
 * <p>覆盖下方 2×2 的矿脉，烧煤产出原矿；产物从 FACING 面直接推入传送带或相邻容器
 * （规格 §5.3 允许的唯一"自动输出"例外）。</p>
 *
 * <p>多方块结构由 {@link MultiBlockMachineBlock} 统一处理：本类只声明尺寸、BE 与工作状态。</p>
 */
public class BurnerMinerBlock extends MultiBlockMachineBlock {
    /** 是否正在工作（用于切换发光贴图）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** 2×2 机器的格索引：0 = master，1..3 = dummy。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);

    public BurnerMinerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0)
                .setValue(ACTIVE, false));
    }

    @Override
    public IntegerProperty partProperty() {
        return PART;
    }

    @Override
    public BooleanProperty activeProperty() {
        return ACTIVE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerMinerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != 0) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.BURNER_MINER_BE.get(),
                BurnerMinerBlockEntity::serverTick);
    }
}
