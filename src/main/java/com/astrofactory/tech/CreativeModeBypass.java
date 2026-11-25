package com.astrofactory.tech;

import com.astrofactory.network.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * 创造模式科技旁路（v0.8）。
 *
 * <p>创造模式是「沙盒试玩」场景：玩家应该能直接摆出任何机器看效果，
 * 不该被科技树卡住。这里在服务端维护一个「是否有人处于创造模式」的标志，
 * 写进 {@link TechTreeData} 的会话态旁路开关。</p>
 *
 * <p>判定规则：<b>只要有任意一个在线玩家处于创造模式，全服视为已全解锁</b>。
 * 这样多人服务器里管理员切创造调试时，产线立刻可用；切回生存后按真实进度恢复。
 * 单机里就是「切创造 → 全解锁，切生存 → 恢复」。</p>
 *
 * <p>旁路状态只存在内存，不写存档（见 {@code TechTreeData#setCreativeBypass}）。</p>
 */
public final class CreativeModeBypass {

    private CreativeModeBypass() {}

    /**
     * 重新计算并应用旁路状态。
     *
     * @return 状态是否发生了变化（变化时需要把新进度同步给客户端）
     */
    public static boolean refresh(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        TechTreeData data = TechTreeData.get(server);

        boolean anyCreative = false;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                anyCreative = true;
                break;
            }
        }

        boolean before = data.isCreativeBypass();
        data.setCreativeBypass(anyCreative);
        if (before != anyCreative) {
            // 界面上的等级/状态全部来自 payload，必须重新广播
            ModNetwork.syncToAll(data);
            com.astrofactory.AstroFactory.LOGGER.info(
                    "[Tech] 创造模式旁路 {}（全科技视为已解锁）",
                    anyCreative ? "开启" : "关闭");
            return true;
        }
        return false;
    }

    /** 玩家加入/离开/切换游戏模式时调用。 */
    public static void refreshFor(ServerPlayer player) {
        if (player != null) {
            refresh(player.serverLevel().getServer());
        }
    }
}
