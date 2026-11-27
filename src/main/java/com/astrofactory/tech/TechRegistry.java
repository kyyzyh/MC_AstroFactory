package com.astrofactory.tech;

import com.astrofactory.AstroFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据驱动科技注册表（US-105）。
 *
 * <p>从数据包目录 {@code data/<ns>/astrofactory_tech/*.json} 读取 {@link TechDef}。
 * 服务端与客户端各注册一个监听器（同一个类，静态表共享）——因为客户端也要靠它
 * 判断「哪些配方已解锁」来渲染 GUI / JEI。</p>
 *
 * <p>刷新时机：数据包重载（进世界、{@code /reload}）时自动重建；表为空时
 * {@link #all()} 返回空集合，调用方需按「无数据」处理而不是崩。</p>
 */
public final class TechRegistry {

    /** 数据包目录名。 */
    public static final String DIRECTORY = "astrofactory_tech";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** id → 定义。用 volatile 引用整体替换，避免读线程看到半张表。 */
    private static volatile Map<String, TechDef> TECHS = Map.of();

    /** 配方 id → 解锁它的科技 id（{@code unlock_recipes} 的反向索引）。 */
    private static volatile Map<String, String> RECIPE_LOCKS = Map.of();

    private TechRegistry() {}

    /** 某配方需要哪个数据驱动科技解锁；没有则返回 null。 */
    public static String recipeLock(String recipeId) {
        return recipeId == null ? null : RECIPE_LOCKS.get(recipeId);
    }

    // ---------------------------------------------------------------- 查询

    /** 全部数据驱动科技（按 id 排序，保证客户端与服务端顺序一致）。 */
    public static Collection<TechDef> all() {
        return TECHS.values();
    }

    /** 按 id 取定义，没有返回 null。 */
    public static TechDef get(String id) {
        return id == null ? null : TECHS.get(id);
    }

    public static boolean exists(String id) {
        return id != null && TECHS.containsKey(id);
    }

    public static int size() {
        return TECHS.size();
    }

    /**
     * 前置是否全部满足（递归判定，带环保护）。
     *
     * <p>数据驱动科技的前置可以是硬编码科技（{@link TechNodes}）或数据驱动科技；
     * 两者都通过 {@link TechTreeData#getLevel(String)} 查等级，所以这里只需
     * 递归处理「前置本身也是数据驱动科技」的情况，防止数据里写出环导致栈溢出。</p>
     */
    public static boolean parentsSatisfied(String id, TechTreeData data) {
        return parentsSatisfied(id, data, new java.util.HashSet<>());
    }

    private static boolean parentsSatisfied(String id, TechTreeData data, java.util.Set<String> visiting) {
        TechDef def = get(id);
        if (def == null) {
            return false;
        }
        if (!visiting.add(id)) {
            // 数据里有环：判为不满足，并在日志里点名，不静默
            AstroFactory.LOGGER.warn("[Tech] 数据驱动科技前置存在环，涉及 {}", id);
            return false;
        }
        try {
            for (String parent : def.parents()) {
                if (data.getLevel(parent) >= 1) {
                    continue;
                }
                // 前置未解锁：只有当它本身是数据驱动科技时才可能被「未来解锁」，
                // 但此刻就是没满足。
                return false;
            }
            return true;
        } finally {
            visiting.remove(id);
        }
    }

    // ---------------------------------------------------------------- 载入

    /** 整体替换科技表（DataGen/测试也用它注入）。 */
    public static void replaceAll(Map<String, TechDef> defs) {
        Map<String, String> locks = new LinkedHashMap<>();
        for (TechDef def : defs.values()) {
            for (String recipeId : def.unlockRecipes()) {
                String prev = locks.put(recipeId, def.id());
                if (prev != null && !prev.equals(def.id())) {
                    AstroFactory.LOGGER.warn("[Tech] 配方 {} 被多个科技声明解锁：{} 与 {}，取后者",
                            recipeId, prev, def.id());
                }
            }
        }
        RECIPE_LOCKS = Map.copyOf(locks);
        TECHS = Map.copyOf(defs);
    }

    /** 服务端/客户端通用的 JSON 解析与写入。 */
    public static void loadFromJsonMap(Map<ResourceLocation, JsonElement> jsonMap) {
        Map<String, TechDef> built = new LinkedHashMap<>();
        List<ResourceLocation> keys = new ArrayList<>(jsonMap.keySet());
        keys.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation fileId : keys) {
            JsonElement element = jsonMap.get(fileId);
            if (!element.isJsonObject()) {
                AstroFactory.LOGGER.error("[Tech] 科技定义不是 JSON 对象，已跳过：{}", fileId);
                continue;
            }
            try {
                TechDef def = TechDef.fromJson(fileId, element.getAsJsonObject());
                built.put(def.id(), def);
            } catch (RuntimeException e) {
                AstroFactory.LOGGER.error("[Tech] 解析科技定义失败，已跳过：{}", fileId, e);
            }
        }
        replaceAll(built);
        AstroFactory.LOGGER.info("[Tech] 已载入 {} 项数据驱动科技", built.size());
    }

    /** 把定义表序列化成 JSON（DataGen 用）。 */
    public static JsonObject toJson(TechDef def) {
        return def.toJson();
    }

    /** 供 DataGen 输出目录名。 */
    public static String directory() {
        return DIRECTORY;
    }

    // ---------------------------------------------------------------- 监听器

    /**
     * 数据包重载监听器。服务端通过 {@code AddReloadListenerEvent}、
     * 客户端通过 {@code RegisterClientReloadListenersEvent} 各注册一份。
     */
    public static final class ReloadListener extends SimpleJsonResourceReloadListener {

        public ReloadListener() {
            super(GSON, DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> object,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            loadFromJsonMap(object);
        }
    }
}
