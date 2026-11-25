package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import com.astrofactory.blockentity.ResearchTableBlockEntity;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 研究台方块。
 *
 * <p>交互：手持研究包右键 = 投入；空手右键 = 查看科技树进度并自动选定下一个可研究的科技。</p>
 */
public class ResearchTableBlock extends FactoryBlock {

    public static final MapCodec<ResearchTableBlock> CODEC = simpleCodec(ResearchTableBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);

    public ResearchTableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends ResearchTableBlock> codec() {
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
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof ResearchTableBlockEntity table) {
                table.serverTick();
            }
        };
    }

    /** 手持研究包 → 投入；空手 → 查看/选定研究目标。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                             BlockPos pos, Player player, InteractionHand hand,
                                             BlockHitResult hitResult) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ResearchTableBlockEntity table)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 投入研究包：初级包进槽 0，高级包进槽 1
        boolean isBasic = stack.is(com.astrofactory.registry.ModItems.RESEARCH_PACK.get());
        boolean isAdvanced = stack.is(com.astrofactory.registry.ModItems.ADVANCED_RESEARCH_PACK.get());
        if (isBasic || isAdvanced) {
            int slot = isAdvanced ? 1 : 0;
            ItemStack inSlot = table.getInput().getStackInSlot(slot);
            if (inSlot.isEmpty()) {
                table.getInput().setStackInSlot(slot, stack.copyWithCount(1));
            } else if (ItemStack.isSameItemSameComponents(inSlot, stack)
                    && inSlot.getCount() < inSlot.getMaxStackSize()) {
                inSlot.grow(1);
                table.getInput().setStackInSlot(slot, inSlot);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.astrofactory.research.input_full")
                                .withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.SUCCESS;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(
                    Component.translatable("message.astrofactory.research.pack_added")
                            .withStyle(ChatFormatting.AQUA), true);
            return ItemInteractionResult.SUCCESS;
        }

        // 空手：打开科技树界面（带研究台的两个输入槽），并在聊天栏提示当前进度
        TechTreeData tech = TechTreeData.get(serverLevel.getServer());
        boolean autoSelected = false;
        if (tech.getCurrentResearch() == null) {
            for (var entry : TechNodes.all().entrySet()) {
                if (TechNodes.canResearch(entry.getKey(), tech)) {
                    tech.setResearch(entry.getKey());
                    autoSelected = true;
                    break;
                }
            }
        }
        if (autoSelected) {
            // 自动选定研究目标后同步给客户端，否则科技树 GUI 显示不出「在研」
            com.astrofactory.network.ModNetwork.syncToAll(tech);
        }

        String current = tech.getCurrentResearch();
        if (current == null) {
            player.displayClientMessage(
                    Component.translatable("message.astrofactory.research.all_done")
                            .withStyle(ChatFormatting.GREEN), true);
        } else {
            var node = TechNodes.get(current);
            player.displayClientMessage(
                    Component.translatable("message.astrofactory.research.status",
                                    Component.translatable(node.nameKey()),
                                    node.costPerLevel() - tech.getResearchProgress(),
                                    node.costPerLevel())
                            .withStyle(ChatFormatting.AQUA), false);
        }

        // 打开科技树 GUI：hasTable=true 时界面里会多出两个研究包输入槽，
        // 玩家可以直接往槽里放包，不必每次手持右键投入。
        // extraData 必须写 true，客户端靠它决定是否加槽位（否则两边索引错位）。
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (containerId, playerInv, p) -> new com.astrofactory.menu.TechTreeMenu(
                            containerId, playerInv, true, table.getInput()),
                    Component.translatable("screen.astrofactory.tech_tree")),
                    buf -> buf.writeBoolean(true));
        }
        return ItemInteractionResult.SUCCESS;
    }
}
