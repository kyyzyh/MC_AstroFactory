package com.astrofactory.recipe;

import com.astrofactory.registry.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 组装机配方的查询与「已解锁」过滤（US-104）。
 *
 * <p>服务端用 {@link com.astrofactory.tech.TechTreeData} 判断解锁；客户端没有
 * SavedData，改用 {@link com.astrofactory.network.ModNetwork#CLIENT_LEVELS} 里
 * 已同步的科技等级（创造模式旁路为 true 时全部视为解锁）。这样 GUI 不用为了
 * 一份配方列表再开一条网络通道——配方本身客户端也有。</p>
 */
public final class AssemblerRecipes {

    private AssemblerRecipes() {}

    /** 按配方 id 取配方（类型不符返回 empty）。 */
    public static Optional<RecipeHolder<AssemblerRecipe>> byId(Level level, String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) {
            return Optional.empty();
        }
        return level.getRecipeManager().byKey(id)
                .filter(holder -> holder.value() instanceof AssemblerRecipe)
                .map(holder -> new RecipeHolder<>(holder.id(), (AssemblerRecipe) holder.value()));
    }

    /** 全部组装机配方（按 id 排序，保证客户端与服务端顺序一致）。 */
    public static List<RecipeHolder<AssemblerRecipe>> all(Level level) {
        List<RecipeHolder<AssemblerRecipe>> list = new ArrayList<>(
                level.getRecipeManager().getAllRecipesFor(ModRecipes.ASSEMBLER_TYPE.get()));
        list.sort(Comparator.comparing(holder -> holder.id().toString()));
        return list;
    }

    /** 服务端：该配方是否已解锁。 */
    public static boolean isUnlocked(ServerLevel level, AssemblerRecipe recipe) {
        String tech = recipe.requiredTechOrNull();
        if (tech == null) {
            return true;
        }
        return com.astrofactory.tech.TechTreeData.get(level.getServer()).isUnlocked(tech);
    }

    /**
     * 客户端：该配方是否已解锁。
     *
     * <p>科技等级由 {@code TechSyncPayload} 同步过来；创造模式旁路开启时全解锁。</p>
     */
    public static boolean isUnlockedClient(AssemblerRecipe recipe) {
        String tech = recipe.requiredTechOrNull();
        if (tech == null) {
            return true;
        }
        if (com.astrofactory.network.ModNetwork.CLIENT_CREATIVE_BYPASS) {
            return true;
        }
        return com.astrofactory.network.ModNetwork.CLIENT_LEVELS.getOrDefault(tech, 0) >= 1;
    }

    /** 服务端：已解锁的配方列表（供校验玩家选择）。 */
    public static List<RecipeHolder<AssemblerRecipe>> unlocked(ServerLevel level) {
        List<RecipeHolder<AssemblerRecipe>> list = new ArrayList<>();
        for (RecipeHolder<AssemblerRecipe> holder : all(level)) {
            if (isUnlocked(level, holder.value())) {
                list.add(holder);
            }
        }
        return list;
    }

    /** 客户端：已解锁的配方列表（供 GUI 显示）。 */
    public static List<RecipeHolder<AssemblerRecipe>> unlockedClient(Level level) {
        List<RecipeHolder<AssemblerRecipe>> list = new ArrayList<>();
        for (RecipeHolder<AssemblerRecipe> holder : all(level)) {
            if (isUnlockedClient(holder.value())) {
                list.add(holder);
            }
        }
        return list;
    }

    /** 配方的显示堆（GUI 列表用）：直接取产物。 */
    public static ItemStack displayStack(AssemblerRecipe recipe) {
        return recipe.result();
    }
}
