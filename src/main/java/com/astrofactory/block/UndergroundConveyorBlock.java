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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 地下传送带：入口把物品「吞入地下」，沿朝向直线搜索配对的出口，
 * 从出口正前方吐出（对应异星工厂 underground belt，最长 8 格）。
 *
 * <p>配对规则：同朝向的入口→出口，中间允许穿过任意方块（包括地形），
 * 距离上限 {@link #MAX_LENGTH}。没有配对出口时物品滞留在入口上方不吞。</p>
 */
public class UndergroundConveyorBlock extends Block {

    public static final MapCodec<UndergroundConveyorBlock> CODEC = simpleCodec(UndergroundConveyorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** true = 入口（吞物品），false = 出口（吐物品）。 */
    public static final BooleanProperty ENTRANCE = BooleanProperty.create("entrance");

    /** 入口到出口的最大距离（格）。 */
    public static final int MAX_LENGTH = 8;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);
    private static final double TOP_HEIGHT = 3.0D / 16.0D;
    private static final double GRAB_HEIGHT = 0.6D;

    public UndergroundConveyorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ENTRANCE, true));
    }

    @Override
    protected MapCodec<? extends UndergroundConveyorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENTRANCE);
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
        // 默认入口；潜行放置 = 出口
        boolean entrance = !context.getPlayer().isShiftKeyDown();
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ENTRANCE, entrance);
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
        if (state.getValue(ENTRANCE)) {
            intake(level, pos, state.getValue(FACING));
        }
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** 入口：把上方物品吞入并传送到出口前方。 */
    private void intake(ServerLevel level, BlockPos pos, Direction facing) {
        BlockPos exit = findExit(level, pos, facing);
        if (exit == null) {
            return; // 没有配对出口，不吞
        }

        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        AABB search = new AABB(minX, minY, minZ, minX + 1.0D, minY + TOP_HEIGHT + GRAB_HEIGHT, minZ + 1.0D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, search);
        if (items.isEmpty()) {
            return;
        }

        BlockPos dropPos = exit.above();
        double dropX = dropPos.getX() + 0.5D + facing.getStepX() * 0.6D;
        double dropZ = dropPos.getZ() + 0.5D + facing.getStepZ() * 0.6D;

        for (ItemEntity item : items) {
            double cx = item.getX();
            double cz = item.getZ();
            if (cx < minX || cx >= minX + 1.0D || cz < minZ || cz >= minZ + 1.0D) {
                continue;
            }
            // 先尝试直接塞进出口前方的容器（无缝衔接传送带/箱子）
            if (tryInsertContainer(level, exit, facing, item)) {
                continue;
            }
            // 否则作为物品实体吐在出口前方
            item.setPos(dropX, dropPos.getY(), dropZ);
            item.setDeltaMovement(0.0D, 0.0D, 0.0D);
            item.hasImpulse = true;
        }
    }

    /** 沿朝向搜索配对出口（同方块、同朝向、ENTRANCE=false）。 */
    private BlockPos findExit(ServerLevel level, BlockPos entrance, Direction facing) {
        for (int i = 1; i <= MAX_LENGTH; i++) {
            BlockPos p = entrance.relative(facing, i);
            BlockState s = level.getBlockState(p);
            if (s.is(this) && !s.getValue(ENTRANCE) && s.getValue(FACING) == facing) {
                return p;
            }
        }
        return null;
    }

    /** 出口前方若是容器，直接入库。 */
    private boolean tryInsertContainer(ServerLevel level, BlockPos exit, Direction facing, ItemEntity item) {
        BlockPos front = exit.relative(facing);
        var handler = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
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
