package com.xiaoyi.astrofactory.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 新手手册：右键<b>打开书本界面</b>（规格 §8 出生点包里的「1 手册」）。
 *
 * <h2>为什么这里不 import 任何 client 类</h2>
 * <p>本类在<b>双端</b>都会加载（服务端注册物品时也要加载它）。如果直接
 * import {@code GuideBookScreen}（客户端类），服务端加载本类时会去解析
 * 那个类 → {@code NoClassDefFoundError}。所以界面调用收进
 * {@code com.xiaoyi.astrofactory.client.ClientGuideBook}，只有
 * {@code level.isClientSide()} 为真时才会被 JVM 解析加载。</p>
 *
 * <p>服务端分支<b>什么都不做</b>：书的内容由客户端从玩家 attachment
 * 生成（进度已通过 SYNC_CODEC 同步），不需要服务端参与。</p>
 */
public class GuideBookItem extends Item {
    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // 仅在客户端分支引用 client 包，服务端永远不会解析到
            com.xiaoyi.astrofactory.client.ClientGuideBook.open();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return use(player.level(), player, context.getHand());
    }
}
