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
 * 组装机：3×3 多方块（规格 §5.1 / §5.4）。
 *
 * <p>电力驱动，按配方无序匹配 3×3 输入。右键打开界面（9 输入 + 1 输出）。</p>
 */
public class AssemblerBlock extends MultiBlockMachineBlock {
    /** 是否正在组装（切换发光贴图）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** 3×3 机器的格索引：0 = master，1..8 = dummy。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);

    public AssemblerBlock(Properties properties) {
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != 0) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.ASSEMBLER_BE.get(),
                AssemblerBlockEntity::serverTick);
    }
}
