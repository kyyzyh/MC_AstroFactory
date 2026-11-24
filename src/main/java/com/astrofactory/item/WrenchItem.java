package com.astrofactory.item;

import com.astrofactory.blockentity.PoweredBlockEntity;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * 扳手：把燃料时代的机器升级为电动版本。
 *
 * <p>这是科技树里「电力时代」的落地手段——研究出电力后，用扳手点一下机器，
 * 它就换成电动驱动：速度翻倍、不再烧燃料，但必须接上电缆供电。</p>
 *
 * <p>右键机器：燃料模式 → 电力模式。已经是电力模式则提示。</p>
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof PoweredBlockEntity machine)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        TechTreeData tech = TechTreeData.get(serverLevel.getServer());

        // 必须先研究出电力
        if (!tech.isElectricEra()) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.astrofactory.wrench.no_power")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (machine.getPowerMode() == PoweredBlockEntity.PowerMode.ELECTRIC) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.astrofactory.wrench.already_electric")
                                .withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.SUCCESS;
        }

        machine.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(
                    Component.translatable("message.astrofactory.wrench.upgraded")
                            .withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.astrofactory.wrench")
                .withStyle(ChatFormatting.GRAY));
    }
}
