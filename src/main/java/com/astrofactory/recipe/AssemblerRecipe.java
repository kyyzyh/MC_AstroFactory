package com.astrofactory.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.astrofactory.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
import java.util.Optional;

/**
 * 组装机配方（US-104）。
 *
 * <p>与工作台配方（有 3x3 摆位）不同，组装机配方是「定量投料」：只看每种原料
 * 够不够数，不看摆在哪一格。这既符合《异星工厂》的组装机模型，也让机械臂
 * 可以从任意面把料塞进任意输入槽。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code ingredients}：定量原料列表（{@link SizedIngredient} 带 count，可匹配 tag）；</li>
 *   <li>{@code result}：产物；</li>
 *   <li>{@code craftTicks}：标称组装周期（tick），实际周期再乘配置里的速度乘数；</li>
 *   <li>{@code requiredTech}：<b>配方锁定预留接口</b>——为空表示人人可做，
 *       非空表示需要该科技解锁。US-105 接入科技树后由本字段驱动解锁，
 *       本卡只把字段与判定方法准备好，不改现有行为。</li>
 * </ul>
 */
public record AssemblerRecipe(
        List<SizedIngredient> ingredients,
        ItemStack result,
        int craftTicks,
        Optional<String> requiredTech
) implements Recipe<AssemblerRecipeInput> {

    /** 未指定周期时的默认值（2 秒）。 */
    public static final int DEFAULT_CRAFT_TICKS = 40;

    public static final MapCodec<AssemblerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SizedIngredient.FLAT_CODEC.listOf()
                    .fieldOf("ingredients").forGetter(AssemblerRecipe::ingredients),
            ItemStack.STRICT_CODEC
                    .fieldOf("result").forGetter(AssemblerRecipe::result),
            Codec.INT.optionalFieldOf("craft_ticks", DEFAULT_CRAFT_TICKS)
                    .forGetter(AssemblerRecipe::craftTicks),
            Codec.STRING.optionalFieldOf("required_tech")
                    .forGetter(AssemblerRecipe::requiredTech)
    ).apply(instance, AssemblerRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    AssemblerRecipe::ingredients,
                    ItemStack.STREAM_CODEC,
                    AssemblerRecipe::result,
                    ByteBufCodecs.VAR_INT,
                    AssemblerRecipe::craftTicks,
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
                    AssemblerRecipe::requiredTech,
                    AssemblerRecipe::new
            );

    // ---------------------------------------------------------------- Recipe

    /** 原料是否全部足量（不看摆位）。 */
    @Override
    public boolean matches(AssemblerRecipeInput input, Level level) {
        for (SizedIngredient ingredient : this.ingredients) {
            if (!input.has(ingredient.ingredient(), ingredient.count())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(AssemblerRecipeInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (SizedIngredient ingredient : this.ingredients) {
            list.add(ingredient.ingredient());
        }
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ASSEMBLER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ASSEMBLER_TYPE.get();
    }

    // ---------------------------------------------------------------- 配方锁定（US-105 预留）

    /** 该配方是否被科技锁住（{@code requiredTech} 为空 = 不锁）。 */
    public boolean isTechLocked() {
        return this.requiredTech.isPresent() && !this.requiredTech.get().isEmpty();
    }

    /** 需要的科技 id，没有则返回 null。 */
    public String requiredTechOrNull() {
        return this.requiredTech.filter(s -> !s.isEmpty()).orElse(null);
    }
}
