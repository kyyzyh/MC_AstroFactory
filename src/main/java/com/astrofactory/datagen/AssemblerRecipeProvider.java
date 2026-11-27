package com.astrofactory.datagen;

import com.astrofactory.AstroFactory;
import com.astrofactory.registry.ModItems;
import com.astrofactory.recipe.AssemblerRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 组装机配方 DataGen（US-104）。
 *
 * <p>三个初始配方走数据生成而不是手写 JSON：这样配方与物品注册永远同步，
 * 后续加配方也只改这一处。产物文件落在 {@code src/generated/resources}。</p>
 *
 * <p>配方周期（tick）说明：20 tick = 1 秒。</p>
 * <ul>
 *   <li>铁齿轮：2 铁板 → 1 齿轮，40 tick（2 秒）；</li>
 *   <li>铜线材：1 铜板 → 2 线，20 tick（1 秒）；</li>
 *   <li>红瓶：1 齿轮 + 1 铜板 → 1 红瓶，100 tick（5 秒，即 12 瓶/分钟）。</li>
 * </ul>
 */
public class AssemblerRecipeProvider extends RecipeProvider {

    public AssemblerRecipeProvider(PackOutput output,
                                   CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // 铁齿轮：2 铁板 → 1 齿轮
        add(output, "assembling_iron_gear",
                List.of(SizedIngredient.of(ModItems.IRON_PLATE.get(), 2)),
                new ItemStack(ModItems.IRON_GEAR.get()),
                40, null);

        // 铜线材：1 铜板 → 2 线（蓝图 ⚙）
        add(output, "assembling_copper_wire",
                List.of(SizedIngredient.of(ModItems.COPPER_PLATE.get(), 1)),
                new ItemStack(ModItems.COPPER_WIRE.get(), 2),
                20, null);

        // 红瓶：1 齿轮 + 1 铜板 → 1 红瓶，5 秒/个 = 12 瓶/分钟
        add(output, "assembling_red_science_pack",
                List.of(
                        SizedIngredient.of(ModItems.IRON_GEAR.get(), 1),
                        SizedIngredient.of(ModItems.COPPER_PLATE.get(), 1)),
                new ItemStack(ModItems.RED_SCIENCE_PACK.get()),
                100, null);

        // ---- US-105 测试配方：专供「配方锁定」验证，不参与正常玩法 ----
        // 锁定关系由数据驱动科技的 unlock_recipes 声明（不在配方里写 required_tech），
        // 正好验证「新增科技只改数据不改代码」。
        add(output, "assembling_test_part",
                List.of(SizedIngredient.of(ModItems.IRON_PLATE.get(), 1)),
                new ItemStack(ModItems.IRON_GEAR.get()),
                20, null);
        add(output, "assembling_test_pack",
                List.of(SizedIngredient.of(ModItems.IRON_GEAR.get(), 1)),
                new ItemStack(ModItems.RED_SCIENCE_PACK.get()),
                20, null);
    }

    private static void add(RecipeOutput output, String name,
                            List<SizedIngredient> ingredients, ItemStack result,
                            int craftTicks, String requiredTech) {
        AssemblerRecipe recipe = new AssemblerRecipe(
                ingredients, result, craftTicks,
                Optional.ofNullable(requiredTech));
        output.accept(ResourceLocation.fromNamespaceAndPath(AstroFactory.MODID, name),
                recipe, null);
    }

    /** 供外部（如测试）直接拿到原料判定用的 Ingredient 列表，避免重复构造。 */
    public static List<Ingredient> ingredientsOf(AssemblerRecipe recipe) {
        return recipe.getIngredients();
    }
}
