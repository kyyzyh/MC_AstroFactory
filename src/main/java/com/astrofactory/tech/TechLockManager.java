package com.astrofactory.tech;

import com.astrofactory.AstroFactory;
import com.astrofactory.recipe.AssemblerRecipe;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方锁定（US-105）。
 *
 * <p>科技没研究完，它解锁的配方就不该存在——工作台和组装机都要拦。这里用的
 * 办法是「<b>按当前研究进度裁剪服务端配方表</b>」，再把这个裁剪后的表广播给
 * 客户端：</p>
 *
 * <ul>
 *   <li>服务端：{@code RecipeManager#replaceRecipes(过滤后的表)}——工作台合成判定、
 *       组装机配方查询都走它，所以两边一起被拦；</li>
 *   <li>客户端：发 {@link ClientboundUpdateRecipesPacket}，让客户端配方书 / JEI 看到
 *       同一份表，不会出现「客户端显示能做、服务端不给做」的错位。</li>
 * </ul>
 *
 * <p><b>快照纪律（关键）</b>：裁剪必须从「完整配方表」算起，否则反复裁剪会越裁越少。
 * 完整表只在<b>数据包真正重载后</b>重新抓取：{@code AddReloadListenerEvent} 打标记，
 * 随后的同步事件才抓。玩家登录触发的同步不会重抓，避免把已裁剪的表当成完整表。</p>
 */
public final class TechLockManager {

    /** 数据包重载后抓到的完整配方表快照。空表表示还没初始化好，此时不动配方。 */
    private static volatile List<RecipeHolder<?>> fullRecipes = List.of();

    /** 下次同步时是否需要重新抓快照（由数据包重载置位）。 */
    private static volatile boolean needsRecapture = false;

    private TechLockManager() {}

    /** 数据包重载开始（{@code AddReloadListenerEvent}）：标记待重抓。 */
    public static void markNeedsRecapture() {
        needsRecapture = true;
    }

    /**
     * 数据包同步 / 服务器启动后调用：必要时重抓快照，然后按当前进度裁剪并广播。
     */
    public static void onDatapackSync(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (needsRecapture || fullRecipes.isEmpty()) {
            // 此刻 RecipeManager 已经 apply 完数据包，getRecipes() 是完整的
            fullRecipes = List.copyOf(server.getRecipeManager().getRecipes());
            needsRecapture = false;
            AstroFactory.LOGGER.info("[Tech] 配方快照已更新：{} 条", fullRecipes.size());
        }
        applyLock(server);
    }

    /** 按当前研究进度裁剪服务端配方表并广播。 */
    public static void applyLock(MinecraftServer server) {
        if (server == null || fullRecipes.isEmpty()) {
            return;
        }
        TechTreeData data = TechTreeData.get(server);
        List<RecipeHolder<?>> filtered = filter(data);
        server.getRecipeManager().replaceRecipes(filtered);
        broadcast(server, filtered);
    }

    /** 只发给某个玩家（登录时补一份，覆盖 vanilla 发的完整表）。 */
    public static void syncTo(ServerPlayer player) {
        if (player == null || fullRecipes.isEmpty()) {
            return;
        }
        TechTreeData data = TechTreeData.get(player.serverLevel().getServer());
        player.connection.send(new ClientboundUpdateRecipesPacket(filter(data)));
    }

    /** 当前完整快照（测试与调试用）。 */
    public static List<RecipeHolder<?>> fullRecipes() {
        return fullRecipes;
    }

    /** 测试用：注入完整快照。 */
    public static void setFullRecipesForTest(List<RecipeHolder<?>> recipes) {
        fullRecipes = List.copyOf(recipes);
        needsRecapture = false;
    }

    private static List<RecipeHolder<?>> filter(TechTreeData data) {
        List<RecipeHolder<?>> filtered = new ArrayList<>(fullRecipes.size());
        for (RecipeHolder<?> holder : fullRecipes) {
            if (isUnlocked(holder, data)) {
                filtered.add(holder);
            }
        }
        return filtered;
    }

    // ---------------------------------------------------------------- 判定

    /** 某条配方当前是否可用（未被未解锁科技拦住）。 */
    public static boolean isUnlocked(RecipeHolder<?> holder, TechTreeData data) {
        if (data.isCreativeBypass()) {
            return true;
        }
        String tech = requiredTechOf(holder);
        if (tech == null) {
            return true;
        }
        return data.isUnlocked(tech);
    }

    /**
     * 这条配方需要哪个科技。
     *
     * <p>两个来源：</p>
     * <ol>
     *   <li>组装机配方自带的 {@code required_tech} 字段（US-104 预留的接口）；</li>
     *   <li>数据驱动科技的 {@code unlock_recipes} 反向索引（US-105 主路径，
     *       新增科技只改数据不改代码）。</li>
     * </ol>
     */
    public static String requiredTechOf(RecipeHolder<?> holder) {
        if (holder.value() instanceof AssemblerRecipe assembler) {
            String tech = assembler.requiredTechOrNull();
            if (tech != null) {
                return tech;
            }
        }
        return TechRegistry.recipeLock(holder.id().toString());
    }

    private static void broadcast(MinecraftServer server, List<RecipeHolder<?>> filtered) {
        if (server.getPlayerList() == null) {
            return;
        }
        ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(filtered);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}
