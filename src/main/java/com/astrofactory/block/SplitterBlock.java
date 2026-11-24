package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 分流器：把传送带送来的物品交替导向左前 / 右前两个方向
 * （对应异星工厂 splitter 的分拣功能）。
 *
 * <p>实现：与传送带同为计划刻自驱方块。物品进入本格中心区后，
 * 按 NEXT_LEFT 布尔属性交替把物品横向弹向左前或右前，
 * 由该处的传送带（或容器）接管。</p>
 */
public class SplitterBlock extends Block {

    public static final MapCodec<SplitterBlock> CODEC = simpleCodec(SplitterBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 下一个物品走左侧（false = 走右侧）。 */
    public static final BooleanProperty NEXT_LEFT = BooleanProperty.create("next_left");

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);
    private static final double BELT_HEIGHT = 4.0D / 16.0D;
    private static final double GRAB_HEIGHT = 0.6D;
    /** 物品越过此前进偏移后开始分拣。 */
    private static final double SPLIT_OFFSET = 0.30D;
    /** 横向弹出速度。 */
    private static final double SIDE_SPEED = 0.10D;
    /** 前进速度。 */
    private static final double SPEED = 0.06D;

    public SplitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(NEXT_LEFT, true));
    }

    @Override
    protected MapCodec<? extends SplitterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NEXT_LEFT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        transport(level, pos, state);
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    private void transport(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean nextLeft = state.getValue(NEXT_LEFT);

        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();

        AABB search = new AABB(minX, minY, minZ, minX + 1.0D, minY + BELT_HEIGHT + GRAB_HEIGHT, minZ + 1.0D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, search);
        if (items.isEmpty()) {
            return;
        }

        double dx = facing.getStepX();
        double dz = facing.getStepZ();
        // 垂直向量：左 = (dz, -dx)
        double lx = dz;
        double lz = -dx;

        boolean flipped = false;
        for (ItemEntity item : items) {
            double cx = item.getX();
            double cz = item.getZ();
            if (cx < minX || cx >= minX + 1.0D || cz < minZ || cz >= minZ + 1.0D) {
                continue;
            }

            double centerX = minX + 0.5D;
            double centerZ = minZ + 0.5D;
            double forwardOffset = (cx - centerX) * dx + (cz - centerZ) * dz;

            if (forwardOffset > SPLIT_OFFSET) {
                // 到达分拣点：直接把物品放到左前/右前的相邻方块中心
                // （对应异星工厂分流器的侧面出口；物品离开本格后由该处的传送带/容器接管）
                double side = nextLeft ? 1.0D : -1.0D;
                double nx = centerX + lx * side;
                double nz = centerZ + dz + lz * side * 0.0D;
                // 前方一格 + 侧向一格的对角位置
                nx = centerX + dx + lx * side;
                nz = centerZ + dz + lz * side;
                item.setNoGravity(true);
                item.setPos(nx, minY + BELT_HEIGHT, nz);
                item.setDeltaMovement(0.0D, 0.0D, 0.0D);
                item.hasImpulse = true;
                item.fallDistance = 0.0F;
                flipped = true;
                nextLeft = !nextLeft; // 本 tick 内也交替
            } else {
                // 还在汇聚段：向前推进
                double nx = cx + dx * SPEED;
                double nz = cz + dz * SPEED;
                item.setNoGravity(true);
                item.setPos(nx, minY + BELT_HEIGHT, nz);
                item.setDeltaMovement(0.0D, 0.0D, 0.0D);
                item.hasImpulse = true;
                item.fallDistance = 0.0F;
            }
        }

        if (flipped) {
            // 把交替状态写回方块（以最后一个被分拣物品之后的方向为准）
            level.setBlock(pos, state.setValue(NEXT_LEFT, nextLeft), Block.UPDATE_CLIENTS);
        }
    }
}
