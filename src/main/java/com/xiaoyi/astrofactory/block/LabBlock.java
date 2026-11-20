package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import com.xiaoyi.astrofactory.multiblock.MultiBlockMachineBlock;

/**
 * 科研台：3×3 多方块（规格 §5.1 / §5.4）。
 *
 * <p>电力驱动，投入科研包推进科技树。进度挂在玩家身上（ResearchProgress）。</p>
 *
 * <p>无 ACTIVE 状态（不发光），所以 blockstate 只有 facing × part。</p>
 */
public class LabBlock extends MultiBlockMachineBlock {
    /** 3×3 机器的格索引：0 = master，1..8 = dummy。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);

    public LabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0));
    }

    @Override
    public IntegerProperty partProperty() {
        return PART;
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LabBlockEntity(pos, state);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level,
                                                                   BlockPos pos, Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide()) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof LabBlockEntity be) {
            player.openMenu(be);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}
