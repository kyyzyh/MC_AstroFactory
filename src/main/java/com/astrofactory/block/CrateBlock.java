package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 储物箱方块：纯容器，27 格存储，无 GUI，靠机械臂或漏斗读写。
 */
public class CrateBlock extends FactoryBlock {

    public static final MapCodec<CrateBlock> CODEC = simpleCodec(CrateBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    public CrateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends CrateBlock> codec() {
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
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.astrofactory.blockentity.CrateBlockEntity(pos, state);
    }

    /**
     * 空手右键打开原版箱子 GUI（v0.7）。
     *
     * <p>之前储物箱完全没有交互方式，只能靠机械臂读写，玩家没法手动存取；
     * 现在复用原版 {@link net.minecraft.world.inventory.ChestMenu}，
     * 行数按容量自动选择（9 格 → 1 行，27 格 → 3 行，54 格 → 6 行）。</p>
     */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hitResult) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.astrofactory.blockentity.CrateBlockEntity crate)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (containerId, playerInv, p) -> openMenuFor(containerId, playerInv, crate),
                    Component.translatable("block.astrofactory." + crateName())));
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** 按容量选原版箱子菜单（1 / 3 / 6 行；其它行数走通用构造器）。 */
    private static net.minecraft.world.inventory.AbstractContainerMenu openMenuFor(
            int containerId, net.minecraft.world.entity.player.Inventory playerInv,
            com.astrofactory.blockentity.CrateBlockEntity crate) {
        int size = crate.getContainerSize();
        int rows = Math.max(1, Math.min(6, size / 9));
        net.minecraft.world.inventory.MenuType<?> type = switch (rows) {
            case 1 -> net.minecraft.world.inventory.MenuType.GENERIC_9x1;
            case 2 -> net.minecraft.world.inventory.MenuType.GENERIC_9x2;
            case 4 -> net.minecraft.world.inventory.MenuType.GENERIC_9x4;
            case 5 -> net.minecraft.world.inventory.MenuType.GENERIC_9x5;
            case 6 -> net.minecraft.world.inventory.MenuType.GENERIC_9x6;
            default -> net.minecraft.world.inventory.MenuType.GENERIC_9x3;
        };
        return new net.minecraft.world.inventory.ChestMenu(type, containerId, playerInv, crate, rows);
    }

    /** 箱子名称的 lang key 后缀（子类覆写）。 */
    protected String crateName() {
        return "crate";
    }
}
