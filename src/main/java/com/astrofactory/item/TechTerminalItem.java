package com.astrofactory.item;

import com.astrofactory.registry.ModMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 科技终端：随身携带的科技树查看器。
 *
 * <p>右键打开科技树 GUI——不依赖研究台，随时查看进度、选定研究。
 * 对应《异星工厂》里按 T 打开科技树的体验。</p>
 */
public class TechTerminalItem extends Item {

    public TechTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 必须显式写 extraData：NeoForge 只有带了 extraData 才给客户端 buffer，
            // 否则客户端 TechTreeMenu 收到 null buf（曾导致「网络协议错误」断连）。
            // 科技终端不打开研究台槽位，写 false 与服务端 TechTreeMenu(hasTable=false) 对齐。
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (containerId, playerInv, p) -> new com.astrofactory.menu.TechTreeMenu(
                            containerId, playerInv, false, null),
                    Component.translatable("screen.astrofactory.tech_tree")),
                    buf -> buf.writeBoolean(false));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.astrofactory.tech_terminal"));
    }
}
