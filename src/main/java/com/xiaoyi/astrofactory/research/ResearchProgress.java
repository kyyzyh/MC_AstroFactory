package com.xiaoyi.astrofactory.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.Astrofactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家科研进度（持久化在玩家身上）。
 *
 * 已解锁科技 + 各科技已投入的科研包数量。
 */
public final class ResearchProgress {
    private ResearchProgress() {}

    /** 数据载体，需可序列化。 */
    public static final class Data {
        public static final com.mojang.serialization.MapCodec<Data> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("unlocked", List.of())
                        .forGetter(d -> new ArrayList<>(d.unlocked)),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("points", java.util.Map.of())
                        .forGetter(d -> new java.util.HashMap<>(d.points))
        ).apply(instance, (unlocked, points) -> {
            Data data = new Data();
            data.unlocked.addAll(unlocked);
            data.points.putAll(points);
            return data;
        }));

        public final Set<String> unlocked = new HashSet<>();
        public final java.util.Map<String, Integer> points = new java.util.HashMap<>();

        public boolean isUnlocked(String techId) {
            return unlocked.contains(techId);
        }

        public int getPoints(String techId) {
            return points.getOrDefault(techId, 0);
        }

        /** 投入科研包；满足前置且集满则解锁，返回是否本次解锁。 */
        public boolean contribute(String techId, int amount) {
            Technology tech = Technologies.get(techId);
            if (tech == null || unlocked.contains(techId)) {
                return false;
            }
            if (!Technologies.prerequisitesMet(tech, unlocked)) {
                return false;
            }
            int now = getPoints(techId) + amount;
            points.put(techId, now);
            if (now >= tech.cost()) {
                unlocked.add(techId);
                return true;
            }
            return false;
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Astrofactory.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Data>> RESEARCH =
            ATTACHMENT_TYPES.register("research",
                    () -> AttachmentType.builder(Data::new)
                            .serialize(Data.MAP_CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    public static Data of(net.minecraft.world.entity.player.Player player) {
        return player.getData(RESEARCH);
    }
}
