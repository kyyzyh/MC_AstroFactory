package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import com.astrofactory.power.PowerGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 电线杆（US-102）：覆盖式供电网络的锚点。
 *
 * <p>与电缆不同，电线杆不靠方块相连——范围内的机器自动得电。
 * 杆与杆之间按直线距离 ≤ linkRange（配置项）自动连线组网。</p>
 */
public class PowerPoleBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

    public static final MapCodec<PowerPoleBlock> CODEC = simpleCodec(PowerPoleBlock::new);

    /** 杆身：细柱 + 底部基座。 */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(6.0D, 0.0D, 6.0D, 10.0D, 14.0D, 10.0D),
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 2.0D, 11.0D),
            // 顶部横担
            Block.box(2.0D, 12.0D, 7.0D, 14.0D, 14.0D, 9.0D)
    );

    public PowerPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PowerPoleBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            PowerGrid.invalidate(serverLevel);
            // 每 10 tick 兜底刷新一次拓扑，覆盖「范围内新增机器」这类
            // 不经过杆的事件（机器放置不会通知杆）
            serverLevel.scheduleTick(pos, this, 10);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            PowerGrid.invalidate(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 周期性作废缓存：机器被放/拆不会主动通知电线杆，
        // 靠这个低频刷新保证「拆一根杆→断电范围正确」这类拓扑变化能生效。
        PowerGrid.invalidate(level);
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, 10);
        }
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof com.astrofactory.blockentity.PowerPoleBlockEntity pole) {
            serverPlayer.openMenu(pole, pos);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public com.astrofactory.blockentity.PowerPoleBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.astrofactory.blockentity.PowerPoleBlockEntity(pos, state);
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, p, st, be) -> {
            if (be instanceof com.astrofactory.blockentity.PowerPoleBlockEntity pole) {
                pole.serverTick();
            }
        };
    }
}
