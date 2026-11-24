package com.astrofactory.tech;

import com.astrofactory.AstroFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 触发型科技监听（v0.6，借鉴异星工厂 2.0）。
 *
 * <p>异星工厂 2.0 的最初几项科技不是研究出来的，而是「做出指定物品」
 * 自动完成（50 铁板触发蒸汽动力、10 铜板触发基础电学）。
 * 这里监听玩家合成与取出熔炼产物两个事件，累计到 {@link TechTreeData}，
 * 达到阈值即自动解锁对应科技并全服播报。</p>
 */
public final class TriggeredTechListener {

    private TriggeredTechListener() {}

    public static void install() {
        NeoForge.EVENT_BUS.register(new TriggeredTechListener());
    }

    /** 玩家在合成台 / 组装机取出成品。 */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getCrafting().isEmpty()) {
            return;
        }
        String itemId = event.getCrafting().getItem()
                .builtInRegistryHolder().key().location().toString();
        handle(player, itemId, event.getCrafting().getCount());
    }

    /** 玩家从熔炉类方块取出产物（原版熔炉烧铁锭也计入触发）。 */
    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSmelting().isEmpty()) {
            return;
        }
        String itemId = event.getSmelting().getItem()
                .builtInRegistryHolder().key().location().toString();
        handle(player, itemId, event.getSmelting().getCount());
    }

    private void handle(ServerPlayer player, String itemId, int count) {
        TechTreeData data = TechTreeData.get(player.serverLevel().getServer());
        String unlocked = data.onItemCrafted(itemId, count);
        // v0.7 修复：触发计数与自动解锁都要同步给客户端，否则科技树 GUI 不刷新
        com.astrofactory.network.ModNetwork.syncToAll(data);
        if (unlocked != null) {
            TechNodes.Node node = TechNodes.get(unlocked);
            player.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.astrofactory.tech_triggered",
                            Component.translatable(node.nameKey())), false);
            AstroFactory.LOGGER.info("[Tech] 触发型科技解锁：{}（{} x{}）",
                    unlocked, itemId, count);
        }
    }
}
