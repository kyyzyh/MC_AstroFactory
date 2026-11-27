package com.astrofactory.jei;

import com.astrofactory.AstroFactory;
import com.astrofactory.recipe.AssemblerRecipe;
import com.astrofactory.registry.ModRecipes;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI 配方类型（US-107）。
 *
 * <p>两个分类页：</p>
 * <ul>
 *   <li>{@link #ASSEMBLER}：组装机配方。配方来自 {@link com.astrofactory.registry.ModRecipes#ASSEMBLER_TYPE}
 *       （RecipeManager 里的 {@code astrofactory:assembling} 类型），JEI 自动收集该类型全部配方。</li>
 *   <li>{@link #LAB}：实验室研究消耗。实验室不产配方，这里用一个轻量 {@link LabResearch} 条目展示
 *       「红瓶 → 研究推进」的消耗关系，让玩家在 JEI 里能查到「某项研究要多少红瓶」。</li>
 * </ul>
 */
public final class RecipeTypes {

    private RecipeTypes() {}

    /** 组装机配方分类（RecipeHolder&lt;AssemblerRecipe&gt;，来自 RecipeManager）。 */
    public static final RecipeType<RecipeHolder<AssemblerRecipe>> ASSEMBLER =
            RecipeType.createRecipeHolderType(ModRecipes.ASSEMBLER_TYPE.getId());

    /** 实验室研究消耗分类。 */
    public static final RecipeType<LabResearch> LAB =
            RecipeType.create(AstroFactory.MODID, "lab_research", LabResearch.class);

    /** 命名空间 id 前缀，供 {@link ResourceLocation} 构造用。 */
    public static final String NS = AstroFactory.MODID;
}
