package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 传送带：把落在带面上的物品沿朝向推动。
 *
 * <p>实现要点：不使用方块实体，靠「方块计划刻」自我续期，每 tick 处理一次，
 * 内存开销极低。转向由 FACING 属性天然支持——两条朝向不同的带子相接，
 * 物品进入下一条时就被改向，无需额外逻辑。</p>
 */
public class ConveyorBlock extends Block {

    public static final MapCodec<ConveyorBlock> CODEC = simpleCodec(ConveyorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** 带面高度（格）：2/16。 */
    public static final double BELT_HEIGHT = 2.0D / 16.0D;
    /** 每 tick 前进距离（格），0.08 → 1.6 格/秒。 */
    private static final double SPEED = 0.08D;
    /** 每 tick 向中线靠拢的最大距离。 */
    private static final double CENTER_SPEED = 0.05D;
    /** 横向最大偏移，超出则贴边。 */
    private static final double MAX_LATERAL = 0.40D;
    /** 物品被判定为「在带上」的最大高度。 */
    private static final double GRAB_HEIGHT = 0.6D;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public ConveyorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends ConveyorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
        // 朝向玩家的反方向，符合「从脚边往前送」的直觉
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        transport(level, pos, state.getValue(FACING));
        // 自我续期：只要方块还在，就继续每 tick 处理
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    private void transport(ServerLevel level, BlockPos pos, Direction facing) {
        // 物流优化科技：传送带速度 +50%
        double speed = SPEED * com.astrofactory.tech.TechTreeData.get(level.getServer())
                .conveyorSpeedMultiplier();

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
        // 垂直于前进方向的向量
        double px = -dz;
        double pz = dx;

        for (ItemEntity item : items) {
            // 只处理「中心在本格内」的物品，避免相邻带子重复推动
            double cx = item.getX();
            double cz = item.getZ();
            if (cx < minX || cx >= minX + 1.0D || cz < minZ || cz >= minZ + 1.0D) {
                continue;
            }

            double centerX = minX + 0.5D;
            double centerZ = minZ + 0.5D;

            // 计算相对中线的横向偏移，并逐步收敛到中线
            double lateral = (cx - centerX) * px + (cz - centerZ) * pz;
            if (lateral > MAX_LATERAL) {
                lateral = MAX_LATERAL;
            } else if (lateral < -MAX_LATERAL) {
                lateral = -MAX_LATERAL;
            }
            double correction = Math.max(-CENTER_SPEED, Math.min(CENTER_SPEED, -lateral));

            double newX = cx + dx * speed + px * correction;
            double newZ = cz + dz * speed + pz * correction;

            // 横向仍然夹紧，防止物品从侧面滑出带子
            double newLateral = (newX - centerX) * px + (newZ - centerZ) * pz;
            if (Math.abs(newLateral) > MAX_LATERAL) {
                double excess = newLateral - Math.signum(newLateral) * MAX_LATERAL;
                newX -= px * excess;
                newZ -= pz * excess;
            }

            double targetY = minY + BELT_HEIGHT;

            // 物品走到带子末端时，若前方是容器就直接卸货，形成完整闭环
            double forwardOffset = (newX - centerX) * dx + (newZ - centerZ) * dz;
            if (forwardOffset > 0.42D) {
                if (tryUnload(level, pos, facing, item)) {
                    continue;
                }
            }

            // 每 tick 重新压制重力与速度，避免物品实体在两次处理之间下落或被弹开
            item.setNoGravity(true);
            item.setPos(newX, targetY, newZ);
            item.setDeltaMovement(0.0D, 0.0D, 0.0D);
            item.hasImpulse = true;
            item.fallDistance = 0.0F;
        }
    }

    /**
     * 带子末端向正前方容器卸货。
     *
     * @return true 表示物品已被容器接收（调用方应跳过本 tick 的位移）
     */
    private boolean tryUnload(ServerLevel level, BlockPos pos, Direction facing, ItemEntity item) {
        BlockPos front = pos.relative(facing);
        // 前方仍是同类传送带时不做卸货，交给下一格继续输送
        if (level.getBlockState(front).is(this)) {
            return false;
        }

        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                front, facing.getOpposite());
        if (handler == null) {
            return false;
        }

        ItemStack stack = item.getItem();
        ItemStack remaining = stack;
        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            remaining = handler.insertItem(i, remaining, false);
        }
        if (remaining.getCount() == stack.getCount()) {
            return false;
        }
        if (remaining.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remaining);
        }
        return true;
    }
}
