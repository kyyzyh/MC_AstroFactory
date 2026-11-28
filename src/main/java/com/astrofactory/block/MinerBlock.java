package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import com.astrofactory.blockentity.MinerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 采矿机方块。
 */
public class MinerBlock extends FactoryBlock {

    public static final MapCodec<MinerBlock> CODEC = simpleCodec(MinerBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 正在采矿（驱动下钻动画）。 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public MinerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends MinerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 采矿机允许放置的下方方块集合：本模组矿区 + 原版主世界矿石（含深板岩/粗矿石变体）。 */
    public static boolean isMineableGround(BlockState below) {
        net.minecraft.world.level.block.Block b = below.getBlock();
        if (b instanceof OreFieldBlock) {
            return true;
        }
        return b == net.minecraft.world.level.block.Blocks.IRON_ORE
                || b == net.minecraft.world.level.block.Blocks.COPPER_ORE
                || b == net.minecraft.world.level.block.Blocks.GOLD_ORE
                || b == net.minecraft.world.level.block.Blocks.COAL_ORE
                || b == net.minecraft.world.level.block.Blocks.DIAMOND_ORE
                || b == net.minecraft.world.level.block.Blocks.REDSTONE_ORE
                || b == net.minecraft.world.level.block.Blocks.LAPIS_ORE
                || b == net.minecraft.world.level.block.Blocks.EMERALD_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_COPPER_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_COAL_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_REDSTONE_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_LAPIS_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE
                || b == net.minecraft.world.level.block.Blocks.RAW_IRON_BLOCK
                || b == net.minecraft.world.level.block.Blocks.RAW_COPPER_BLOCK
                || b == net.minecraft.world.level.block.Blocks.RAW_GOLD_BLOCK;
    }

    /** 玩家放置校验：下方必须可开采（矿区或原版矿石），否则拒放并提示。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        Player player = context.getPlayer();
        boolean creative = player != null && player.getAbilities().instabuild;
        if (!creative && !isMineableGround(below)) {
            if (player != null && context.getLevel() instanceof net.minecraft.server.level.ServerLevel) {
                player.displayClientMessage(
                        Component.translatable("message.astrofactory.miner.bad_ground")
                                .withStyle(ChatFormatting.RED), true);
            }
            return null; // 放置失败
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 空手右键打开信息面板（v0.8.9：通用信息面板）。 */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof com.astrofactory.blockentity.IMachineInfo info) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.astrofactory.menu.InfoPanelMenu(id, inv, info),
                    Component.literal(info.infoTitle())), pos);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 支撑方块被挖掉后自动破坏采矿机（v0.8 放置约束一致性）。 */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // 创造模式（沙盒试玩）不作支撑约束，避免调试时被卡住
        if (level instanceof Level lvl && lvl.getServer() != null
                && com.astrofactory.tech.TechTreeData.get(lvl.getServer()).isCreativeBypass()) {
            return true;
        }
        return isMineableGround(level.getBlockState(pos.below()));
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MinerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof MinerBlockEntity miner) {
                miner.serverTick();
            }
        };
    }
}
