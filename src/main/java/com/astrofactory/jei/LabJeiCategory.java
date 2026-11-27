package com.astrofactory.jei;

import com.astrofactory.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;

/**
 * 实验室研究 JEI 分类（US-107）。
 *
 * <p>展示「某项研究消耗多少红瓶」：左侧是科技包（红瓶）输入，右侧是研究进度里程碑。
 * 数据来自 {@link com.astrofactory.tech.TechRegistry} 的数据驱动科技定义。</p>
 */
public class LabJeiCategory implements IRecipeCategory<LabResearch> {

    private final IDrawable background;
    private final IDrawable icon;

    public LabJeiCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(116, 54);
        this.icon = helper.createDrawableItemLike(ModBlocks.LAB.get());
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<LabResearch> getRecipeType() {
        return RecipeTypes.LAB;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("screen.astrofactory.jei.lab");
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LabResearch research, IFocusGroup focuses) {
        // 输入端：需要的科技包（通常 1 种）
        int x = 0;
        for (var stack : research.inputs()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, 18)
                    .addItemStack(stack)
                    .setSlotName("input");
            x += 18;
        }

        // 输出端：研究里程碑（展示所用科技包对应的研究产物）
        builder.addSlot(RecipeIngredientRole.OUTPUT, 98, 18)
                .addItemStack(research.result())
                .setOutputSlotBackground()
                .setSlotName("output");
    }
}
