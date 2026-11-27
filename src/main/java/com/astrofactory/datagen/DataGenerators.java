package com.astrofactory.datagen;

import com.astrofactory.AstroFactory;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

/**
 * DataGen 总入口（US-104）。
 *
 * <p>{@code gradlew runData} 时触发，产物写入 {@code src/generated/resources}，
 * 该目录已在 {@code build.gradle} 里挂到 main 资源集上，因此会被打进 jar。</p>
 *
 * <p>注意：手写 JSON（{@code src/main/resources/data/astrofactory/recipe/*.json}）
 * 与 DataGen 产物会冲突（同名配方两处定义）。本卡采用 DataGen 作为唯一来源，
 * 已把手写的 assembling_*.json 删除。</p>
 */
@EventBusSubscriber(modid = AstroFactory.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper helper = event.getExistingFileHelper();

        // 服务端数据：组装机配方
        generator.addProvider(event.includeServer(),
                new AssemblerRecipeProvider(output, lookup));

        // 服务端数据：物品 tag（配方引用的通用 tag）
        TagsProvider<Block> blockTags = new TagsProvider<>(
                output, net.minecraft.core.registries.Registries.BLOCK, lookup,
                AstroFactory.MODID, helper) {
            @Override
            protected void addTags(HolderLookup.Provider provider) {
                // 本卡暂无方块 tag 需求
            }

            @Override
            public String getName() {
                return "AstroFactory 方块 tag（占位）";
            }
        };
        generator.addProvider(event.includeServer(), blockTags);
        generator.addProvider(event.includeServer(),
                new ModItemTagsProvider(output, lookup, blockTags.contentsGetter(), helper));
    }
}
