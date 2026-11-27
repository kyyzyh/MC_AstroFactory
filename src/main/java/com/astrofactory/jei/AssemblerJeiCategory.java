package com.astrofactory.jei;

import com.astrofactory.recipe.AssemblerRecipe;
import com.astrofactory.registry.ModBlocks;
import com.astrofactory.tech.TechDef;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;

/**
 * 组装机 JEI 分类（US-107）。
 *
 * <p>展示组装机配方：左侧原料槽（定量投料，每个 {@link SizedIngredient} 一个槽），
 * 右侧输出槽。配方来自 RecipeManager 的 {@code astrofactory:assembling} 类型，
 * JEI 自动收集全部该类型配方。</p>
 *
 * <p>锁定标注：输出槽上挂 {@code IRecipeSlotTooltipCallback}——当该配方被一项
 * 尚未研究的科技锁住时（硬编码 {@code required_tech} 或数据驱动 {@code unlock_recipes}），
 * 悬停产物时提示所需科技；研究完成后提示自动消失（客户端科技等级从网络同步）。</p>
 */
public class AssemblerJeiCategory implements IRecipeCategory<RecipeHolder<AssemblerRecipe>> {

    private final IDrawable background;
    private final IDrawable icon;

    public AssemblerJeiCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(116, 54);
        this.icon = helper.createDrawableItemLike(ModBlocks.ASSEMBLER.get());
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<RecipeHolder<AssemblerRecipe>> getRecipeType() {
        return RecipeTypes.ASSEMBLER;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("screen.astrofactory.jei.assembler");
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AssemblerRecipe> holder,
                          IFocusGroup focuses) {
        AssemblerRecipe recipe = holder.value();
        List<SizedIngredient> ingredients = recipe.ingredients();

        // 原料槽：横向排布，每个定量原料一个槽
        int x = 0;
        for (SizedIngredient ing : ingredients) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, 18)
                    .addIngredients(ing.ingredient())
                    .setSlotName("input");
            x += 18;
        }

        // 输出槽：右侧；若配方被未研究科技锁住，挂锁定 tooltip
        var outputSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, 98, 18)
                .addItemStack(recipe.result())
                .setOutputSlotBackground()
                .setSlotName("output");
        String lockTech = lockedBy(holder);
        if (lockTech != null) {
            outputSlot.addRichTooltipCallback((slotView, tooltip) -> addLockTooltip(lockTech, tooltip));
        }
    }

    @Override
    public void draw(RecipeHolder<AssemblerRecipe> holder, IRecipeSlotsView slots,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 组装时间画在底部
        int ticks = holder.value().craftTicks();
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.translatable("gui.astrofactory.jei.craft_time", ticks / 20),
                0, 44, 0xFF808080, false);
    }

    // ---------------------------------------------------------------- 锁定标注

    /** 该配方被哪项科技锁住；没有返回 null。 */
    private static String lockedBy(RecipeHolder<AssemblerRecipe> holder) {
        String tech = holder.value().requiredTechOrNull();
        if (tech != null) {
            return tech;
        }
        return TechRegistry.recipeLock(holder.id().toString());
    }

    /** 已在 tooltip 追加锁定提示（研究完成后不追加，标注消失）。 */
    private static void addLockTooltip(String techId, mezz.jei.api.gui.builder.ITooltipBuilder tooltip) {
        if (isUnlockedClient(techId)) {
            return;
        }
        tooltip.add(Component.translatable("gui.astrofactory.jei.locked_by",
                displayNameOf(techId)));
    }

    /** 客户端判定该科技是否已解锁（含创造模式旁路）。 */
    private static boolean isUnlockedClient(String techId) {
        if (com.astrofactory.network.ModNetwork.CLIENT_CREATIVE_BYPASS) {
            return true;
        }
        if (com.astrofactory.network.ModNetwork.CLIENT_LEVELS.getOrDefault(techId, 0) >= 1) {
            return true;
        }
        // 数据驱动科技与硬编码科技共用同一等级表；此处兜底判断数据驱动科技
        return false;
    }

    /** 科技显示名（数据驱动科技 name 是 lang key；硬编码节点用 nameKey）。 */
    private static String displayNameOf(String techId) {
        TechDef def = TechRegistry.get(techId);
        if (def != null) {
            return Component.translatable(def.name()).getString();
        }
        var node = TechNodes.get(techId);
        if (node != null) {
            return Component.translatable(node.nameKey()).getString();
        }
        return techId;
    }
}
