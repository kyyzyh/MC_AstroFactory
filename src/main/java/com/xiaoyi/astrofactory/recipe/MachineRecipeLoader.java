package com.xiaoyi.astrofactory.recipe;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * 数据包配方加载器：资源重载（含 {@code /reload}）时扫描
 * {@code data/<ns>/machine_recipes/**.json} 并替换 {@link RecipeRegistry} 的数据包配方集合。
 *
 * <p>解析失败只会让那一条配方失效，不会中断重载，也不会影响内置配方。</p>
 */
public class MachineRecipeLoader extends SimplePreparableReloadListener<Map<Identifier, MachineRecipe>> {

    @Override
    protected Map<Identifier, MachineRecipe> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return RecipeRegistry.parseAll(manager);
    }

    @Override
    protected void apply(Map<Identifier, MachineRecipe> preparations, ResourceManager manager,
                         ProfilerFiller profiler) {
        RecipeRegistry.setDataRecipes(preparations);
    }
}
