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
 * 蒸汽机：3×5 多方块（规格 §5.1 / §5.5）。消耗蒸汽发电，向电线杆注入能量。
 *
 * <p>sizeX = 3（右手方向），sizeZ = 5（正面方向），共 15 格。</p>
 */
public class SteamEngineBlock extends MultiBlockMachineBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** 3×5 机器的格索引：0 = master，1..14 = dummy。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 14);

    public SteamEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0)
                .setValue(ACTIVE, false));
    }

    @Override
    protected IntegerProperty partProperty() {
        return PART;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public int sizeX() {
        return 3;
    }

    @Override
    public int sizeZ() {
        return 5;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SteamEngineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != 0) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.STEAM_ENGINE_BE.get(),
                SteamEngineBlockEntity::serverTick);
    }
}
