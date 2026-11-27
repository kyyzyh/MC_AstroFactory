package com.astrofactory.tech;

import com.astrofactory.AstroFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 科技框架的服务端接线（US-105）。
 *
 * <p>四件事：</p>
 * <ol>
 *   <li><b>注册重载监听器</b>：{@code AddReloadListenerEvent} 里挂
 *       {@link TechRegistry.ReloadListener}，数据包一重载就读科技 JSON；</li>
 *   <li><b>重载后裁剪配方</b>：{@code OnDatapackSyncEvent} 在配方发给客户端之前触发，
 *       在这里抓快照 + 裁剪 + 广播；</li>
 *   <li><b>服务器启动兜底</b>：{@code ServerStartedEvent} 保证单人/专用服都有一次裁剪；</li>
 *   <li><b>登录补发</b>：玩家登录时 vanilla 先发完整配方表，这里补一份裁剪后的覆盖掉。</li>
 * </ol>
 */
public final class TechFrameworkEvents {

    private static boolean installed = false;

    private TechFrameworkEvents() {}

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        NeoForge.EVENT_BUS.register(new TechFrameworkEvents());
        // 科技解锁后立刻重算配方锁定（否则要等下一次数据包重载才生效）
        TechTreeData.setUnlockListener(data -> {
            MinecraftServer server = currentServer;
            if (server != null) {
                TechLockManager.applyLock(server);
            }
        });
        AstroFactory.LOGGER.info("[Tech] 科技框架事件已安装（US-105）");
    }

    /** 当前服务器引用（供解锁回调使用；TechTreeData 是 SavedData 拿不到 server）。 */
    private static volatile MinecraftServer currentServer;

    /** 数据包重载：挂上科技 JSON 读取器，并标记配方快照需要重抓。 */
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new TechRegistry.ReloadListener());
        TechLockManager.markNeedsRecapture();
    }

    /** 数据包同步（玩家加入 / {@code /reload}）：抓快照 + 裁剪 + 定向补发。 */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        currentServer = server;
        TechLockManager.onDatapackSync(server);
        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            // 该玩家的登录流程里 vanilla 已经发过完整表，这里再补一份裁剪后的
            TechLockManager.syncTo(joining);
        }
    }

    /** 服务器启动完成：确保配方锁定按当前存档进度生效。 */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        currentServer = event.getServer();
        TechLockManager.onDatapackSync(event.getServer());
    }

    /** 玩家登录：补发裁剪后的配方表（科技进度由 {@link AstroFactory} 的登录监听器同步）。 */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TechLockManager.syncTo(player);
    }
}
