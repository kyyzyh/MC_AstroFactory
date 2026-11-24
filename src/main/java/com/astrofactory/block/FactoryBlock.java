package com.astrofactory.block;

import com.astrofactory.blockentity.PoweredBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * 所有工厂方块的公共基类：自带方块实体、掉落自身、使用普通模型渲染。
 */
public abstract class FactoryBlock extends Block implements EntityBlock {

    protected FactoryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 简化掉落逻辑：直接掉落自身，避免额外维护 loot table 数据包。 */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this.asItem()));
    }

    /** 读取水平朝向（方块必须带 FACING 属性）。 */
    protected static Direction facingOf(BlockState state) {
        return state.getValue(HorizontalDirectionalBlock.FACING);
    }

    /**
     * 手持可燃物右键机器 = 投入燃料（燃料时代的自然交互，无需 GUI）。
     *
     * <p>只有燃料模式且手持物可作燃料时才拦截，其余情况交还给原版逻辑
     * （例如手持扳手时由扳手自己处理）。</p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                             BlockPos pos, Player player, InteractionHand hand,
                                             BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PoweredBlockEntity machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!PoweredBlockEntity.isFuel(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (machine.getPowerMode() != PoweredBlockEntity.PowerMode.FUEL) {
            // 已经是电动版本，不再吃燃料
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.astrofactory.no_fuel_needed")
                                .withStyle(ChatFormatting.GRAY), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (machine.tryInsertFuel(stack)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(
                    Component.translatable("message.astrofactory.fuel_added")
                            .withStyle(ChatFormatting.GOLD), true);
        } else {
            player.displayClientMessage(
                    Component.translatable("message.astrofactory.fuel_full")
                            .withStyle(ChatFormatting.RED), true);
        }
        return ItemInteractionResult.SUCCESS;
    }
}
