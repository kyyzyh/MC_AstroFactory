package com.xiaoyi.astrofactory.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.xiaoyi.astrofactory.ModEvents;

/**
 * 新手手册：右键（或对空气/方块）直接把 10 步教程推给玩家。
 *
 * <p>规格 §8 出生点包里的「1 手册」。实现上不做 GUI，
 * 复用 {@link ModEvents#sendGuide(CommandSourceStack)} 的文案，
 * 直接往玩家聊天栏逐条发送 —— 和 `/astrofactory guide` 完全同一份内容，
 * 保证命令与手册永远一致。</p>
 */
public class GuideBookItem extends Item {
    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            ModEvents.sendGuideTo(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.level().isClientSide() && player instanceof ServerPlayer sp) {
            ModEvents.sendGuideTo(sp);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
