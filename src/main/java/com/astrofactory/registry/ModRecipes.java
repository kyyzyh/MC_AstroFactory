package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import com.astrofactory.recipe.AssemblerRecipe;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 配方类型 / 序列化器注册（US-104）。
 *
 * <p>组装机需要自己的配方类型：它不是工作台配方，没有 3x3 摆位，
 * 而是「定量投料」。自定义配方类型也让 JEI 之类的查看器能单独归类。</p>
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AstroFactory.MODID);

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, AstroFactory.MODID);

    /** 组装机配方序列化器。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblerRecipe>>
            ASSEMBLER_SERIALIZER = SERIALIZERS.register("assembling",
                    () -> new RecipeSerializer<AssemblerRecipe>() {
                        @Override
                        public MapCodec<AssemblerRecipe> codec() {
                            return AssemblerRecipe.CODEC;
                        }

                        @Override
                        public StreamCodec<RegistryFriendlyByteBuf, AssemblerRecipe> streamCodec() {
                            return AssemblerRecipe.STREAM_CODEC;
                        }
                    });

    /** 组装机配方类型（按 {@code astrofactory:assembling} 归类）。 */
    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblerRecipe>> ASSEMBLER_TYPE =
            TYPES.register("assembling", () -> RecipeType.simple(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            AstroFactory.MODID, "assembling")));

    private ModRecipes() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        SERIALIZERS.register(bus);
        TYPES.register(bus);
    }
}
