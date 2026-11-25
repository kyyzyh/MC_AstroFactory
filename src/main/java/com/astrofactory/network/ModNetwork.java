package com.astrofactory.network;

import com.astrofactory.AstroFactory;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;

/**
 * 网络层：科技进度同步 + 客户端「选定研究」请求。
 *
 * <p>科技树 GUI 需要实时显示服务端的 SavedData 进度；玩家在 GUI 里点击
 * 节点选定研究时，请求要发回服务端校验（防作弊、防前置不满足）。</p>
 */
public final class ModNetwork {

    private ModNetwork() {}

    /**
     * 服务端 → 客户端：全量科技进度（等级表 + 在研项目 + 进度 + 触发计数）。
     *
     * <p>v0.7.1：加入 {@code triggerCounts}，让 GUI 能显示「已做出 5/16 铁锭」这类
     * 触发型科技的真实进度，而不是只显示阈值。</p>
     */
    public record TechSyncPayload(Map<String, Integer> levels, String current, int progress,
                                  Map<String, Integer> triggerCounts)
            implements CustomPacketPayload {

        public static final TechSyncPayload EMPTY =
                new TechSyncPayload(Map.of(), null, 0, Map.of());

        public static final CustomPacketPayload.Type<TechSyncPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        AstroFactory.MODID, "tech_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, TechSyncPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                        TechSyncPayload::levels,
                        ByteBufCodecs.STRING_UTF8,
                        p -> p.current() == null ? "" : p.current(),
                        ByteBufCodecs.VAR_INT,
                        TechSyncPayload::progress,
                        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                        TechSyncPayload::triggerCounts,
                        (levels, current, progress, triggerCounts) -> new TechSyncPayload(
                                levels, current.isEmpty() ? null : current, progress, triggerCounts)
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：请求把某科技设为在研项目。 */
    public record SelectResearchPayload(String techId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SelectResearchPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        AstroFactory.MODID, "select_research"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SelectResearchPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8,
                        SelectResearchPayload::techId,
                        SelectResearchPayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端缓存的最新科技进度（由 TechSyncPayload 刷新）。 */
    public static final Map<String, Integer> CLIENT_LEVELS = new HashMap<>();
    public static volatile String CLIENT_CURRENT = null;
    public static volatile int CLIENT_PROGRESS = 0;
    /** 触发型科技的累计物品数（物品 id → 已做出数量）。 */
    public static final Map<String, Integer> CLIENT_TRIGGER_COUNTS = new HashMap<>();

    /** 把服务端进度打包成 payload。 */
    public static TechSyncPayload snapshot(TechTreeData data) {
        Map<String, Integer> levels = new HashMap<>();
        TechNodes.all().forEach((id, node) -> levels.put(id, data.getLevel(id)));
        return new TechSyncPayload(levels, data.getCurrentResearch(), data.getResearchProgress(),
                new HashMap<>(data.triggerCountsView()));
    }

    /** 服务端：把进度广播给所有玩家。 */
    public static void syncToAll(TechTreeData data) {
        PacketDistributor.sendToAllPlayers(snapshot(data));
    }

    /** 服务端：把进度发给单个玩家。 */
    public static void syncTo(ServerPlayer player, TechTreeData data) {
        PacketDistributor.sendToPlayer(player, snapshot(data));
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TechSyncPayload.TYPE, TechSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    CLIENT_LEVELS.clear();
                    CLIENT_LEVELS.putAll(payload.levels());
                    CLIENT_CURRENT = payload.current();
                    CLIENT_PROGRESS = payload.progress();
                    CLIENT_TRIGGER_COUNTS.clear();
                    CLIENT_TRIGGER_COUNTS.putAll(payload.triggerCounts());
                }));
        registrar.playToServer(SelectResearchPayload.TYPE, SelectResearchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        TechTreeData data = TechTreeData.get(serverPlayer.serverLevel().getServer());
                        data.setResearch(payload.techId());
                        syncTo(serverPlayer, data);
                    }
                }));
    }
}
