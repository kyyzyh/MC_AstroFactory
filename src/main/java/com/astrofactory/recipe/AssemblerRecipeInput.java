package com.astrofactory.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/**
 * 组装机的配方输入（US-104）。
 *
 * <p>与工作台的 3x3 网格不同，组装机是「扁平的多槽输入」：不关心摆位，只关心
 * <b>每种原料够不够数</b>。所以这里按槽位数组包装，并提供按 {@link Ingredient}
 * 计数、扣除的辅助方法。</p>
 */
public final class AssemblerRecipeInput implements RecipeInput {

    private final List<ItemStack> stacks;

    public AssemblerRecipeInput(List<ItemStack> stacks) {
        this.stacks = stacks;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < this.stacks.size() ? this.stacks.get(index) : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return this.stacks.size();
    }

    /** 某个原料（含 tag 匹配）在输入里总共有多少个。 */
    public int countOf(Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : this.stacks) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 该原料是否够 {@code needed} 个。 */
    public boolean has(Ingredient ingredient, int needed) {
        return countOf(ingredient) >= needed;
    }
}
