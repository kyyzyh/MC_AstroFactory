package com.astrofactory.jei;

import com.astrofactory.AstroFactory;
import com.astrofactory.registry.ModItems;
import com.astrofactory.registry.ModRecipes;
import com.astrofactory.tech.TechDef;
import com.astrofactory.tech.TechRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * AstroFactory 的 JEI 集成（US-107）。
 *
 * <p>依赖声明为 optional：本类只在 {@code jei} 装载且位于客户端时才会被 JEI 实例化。
 * 集成代码只 import {@code mezz.jei.api}（compileOnly），产物不内嵌 JEI 类，
 * 玩家没装 JEI 时游戏照常运行。</p>
 *
 * <p>展示内容：</p>
 * <ul>
 *   <li>组装机分类——从 RecipeManager 收集 {@code astrofactory:assembling} 配方；</li>
 *   <li>实验室分类——按数据驱动科技定义展示「红瓶 xN → 研究」消耗；</li>
 *   <li>锁定配方标注——锁定 tooltip 由 {@link AssemblerJeiCategory} 挂到输出槽上。</li>
 * </ul>
 */
@JeiPlugin
public class AstroJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AstroFactory.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new AssemblerJeiCategory(helper),
                new LabJeiCategory(helper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // 组装机配方：从 RecipeManager 直接收集 astrofactory:assembling 类型
        List<net.minecraft.world.item.crafting.RecipeHolder<com.astrofactory.recipe.AssemblerRecipe>>
                assemblerRecipes = new java.util.ArrayList<>(level.getRecipeManager()
                        .getAllRecipesFor(ModRecipes.ASSEMBLER_TYPE.get()));
        registration.addRecipes(RecipeTypes.ASSEMBLER, assemblerRecipes);

        // 实验室研究消耗：按数据驱动科技定义生成条目（每项研究 = 红瓶 × 数量）
        List<LabResearch> labRecipes = new ArrayList<>();
        for (TechDef def : TechRegistry.all()) {
            labRecipes.add(toLabResearch(def));
        }
        registration.addRecipes(RecipeTypes.LAB, labRecipes);
    }

    /** 把数据驱动科技定义转成实验室条目。 */
    private static LabResearch toLabResearch(TechDef def) {
        ItemStack pack = new ItemStack(loadPackItem(def.packItem()), def.packCount());
        return new LabResearch(def.id(), List.of(pack), new ItemStack(ModItems.RED_SCIENCE_PACK.get()));
    }

    /** 按物品 id 找物品；找不到退回红瓶。 */
    private static net.minecraft.world.item.Item loadPackItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id != null) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            if (item != null) {
                return item;
            }
        }
        return ModItems.RED_SCIENCE_PACK.get();
    }
}
