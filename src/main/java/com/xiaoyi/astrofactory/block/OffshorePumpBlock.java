package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
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
 * 离岸泵：1×2 多方块（规格 §5.1 / §5.5）。
 *
 * <p>贴水放置时持续抽水供锅炉，是蒸汽链的取水端。最小实现：只要周围有水方块，
 * 就不断把水转成蒸汽注入相邻管道（等价于"抽水 + 汽化"合并，规格不做压力/温度）。</p>
 */
public class OffshorePumpBlock extends MultiBlockMachineBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** 1×2 机器的格索引：0 = master，1 = dummy。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);

    public OffshorePumpBlock(Properties properties) {
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
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public int sizeX() {
        return 1;
    }

    @Override
    public int sizeZ() {
        return 2;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new OffshorePumpBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != 0) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.OFFSHORE_PUMP_BE.get(),
                OffshorePumpBlockEntity::serverTick);
    }
}
