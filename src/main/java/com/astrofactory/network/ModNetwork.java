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
                                  Map<String, Integer> triggerCounts, boolean creativeBypass,
                                  java.util.List<String> queue)
            implements CustomPacketPayload {

        public static final TechSyncPayload EMPTY =
                new TechSyncPayload(Map.of(), null, 0, Map.of(), false, java.util.List.of());

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
                        ByteBufCodecs.BOOL,
                        TechSyncPayload::creativeBypass,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        TechSyncPayload::queue,
                        (levels, current, progress, triggerCounts, creativeBypass, queue)
                                -> new TechSyncPayload(levels, current.isEmpty() ? null : current,
                                        progress, triggerCounts, creativeBypass,
                                        queue == null ? java.util.List.of() : queue)
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

    /** 客户端 → 服务端：把某科技加入研究队列。 */
    public record EnqueueResearchPayload(String techId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<EnqueueResearchPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        AstroFactory.MODID, "enqueue_research"));

        public static final StreamCodec<RegistryFriendlyByteBuf, EnqueueResearchPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8,
                        EnqueueResearchPayload::techId,
                        EnqueueResearchPayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：把某科技移出研究队列。 */
    public record DequeueResearchPayload(String techId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<DequeueResearchPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        AstroFactory.MODID, "dequeue_research"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DequeueResearchPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8,
                        DequeueResearchPayload::techId,
                        DequeueResearchPayload::new
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
    /** 创造模式旁路是否生效（界面据此显示「创造模式：已全解锁」）。 */
    public static volatile boolean CLIENT_CREATIVE_BYPASS = false;
    /** US-106 研究队列（客户端缓存）。 */
    public static volatile java.util.List<String> CLIENT_QUEUE = java.util.List.of();

    /** 把服务端进度打包成 payload。 */
    public static TechSyncPayload snapshot(TechTreeData data) {
        Map<String, Integer> levels = new HashMap<>();
        TechNodes.all().forEach((id, node) -> levels.put(id, data.getLevel(id)));
        return new TechSyncPayload(levels, data.getCurrentResearch(), data.getResearchProgress(),
                new HashMap<>(data.triggerCountsView()), data.isCreativeBypass(),
                data.researchQueueView());
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
                    CLIENT_CREATIVE_BYPASS = payload.creativeBypass();
                    CLIENT_QUEUE = payload.queue();
                }));
        registrar.playToServer(SelectResearchPayload.TYPE, SelectResearchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        TechTreeData data = TechTreeData.get(serverPlayer.serverLevel().getServer());
                        data.setResearch(payload.techId());
                        syncTo(serverPlayer, data);
                    }
                }));
        registrar.playToServer(EnqueueResearchPayload.TYPE, EnqueueResearchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        TechTreeData data = TechTreeData.get(serverPlayer.serverLevel().getServer());
                        // 无在研时直接开研，否则入队
                        if (data.getCurrentResearch() == null) {
                            data.setResearch(payload.techId());
                        } else {
                            data.enqueueResearch(payload.techId());
                        }
                        syncToAll(data);
                    }
                }));
        registrar.playToServer(DequeueResearchPayload.TYPE, DequeueResearchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        TechTreeData data = TechTreeData.get(serverPlayer.serverLevel().getServer());
                        data.removeFromQueue(payload.techId());
                        syncToAll(data);
                    }
                }));
    }
}
