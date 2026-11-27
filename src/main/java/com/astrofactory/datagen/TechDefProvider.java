package com.astrofactory.datagen;

import com.astrofactory.AstroFactory;
import com.astrofactory.tech.TechDef;
import com.astrofactory.tech.TechRegistry;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 数据驱动科技 DataGen（US-105）。
 *
 * <p>产物写进 {@code src/generated/resources/data/astrofactory/astrofactory_tech/*.json}，
 * 也就是一个纯数据包——新增科技只改这里（或直接改数据包），代码不用动。</p>
 *
 * <p>本卡只放 2 个测试科技（验收要求「只做框架 + 2 个测试科技」），
 * 正式科技内容在 US-108 填充。</p>
 */
public class TechDefProvider implements DataProvider {

    private final PackOutput.PathProvider pathProvider;

    public TechDefProvider(PackOutput output) {
        this.pathProvider = output.createPathProvider(
                PackOutput.Target.DATA_PACK, TechRegistry.DIRECTORY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (TechDef def : buildTechs()) {
            ResourceLocation id = ResourceLocation.parse(def.id());
            Path path = this.pathProvider.json(id);
            JsonObject json = def.toJson();
            futures.add(DataProvider.saveStable(output, json, path));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /** 两个测试科技：A 无前置，B 依赖 A。 */
    private static List<TechDef> buildTechs() {
        return List.of(
                // 测试科技 A：解锁测试配方 assembling_test_part（默认锁定）
                new TechDef(
                        AstroFactory.MODID + ":test_assembly",
                        "tech.astrofactory.test_assembly",
                        List.of(),
                        AstroFactory.MODID + ":research_pack",
                        5,
                        List.of(AstroFactory.MODID + ":assembling_test_part")
                ),
                // 测试科技 B：依赖 A，解锁测试配方 assembling_test_pack
                new TechDef(
                        AstroFactory.MODID + ":test_research",
                        "tech.astrofactory.test_research",
                        List.of(AstroFactory.MODID + ":test_assembly"),
                        AstroFactory.MODID + ":research_pack",
                        8,
                        List.of(AstroFactory.MODID + ":assembling_test_pack")
                )
        );
    }

    @Override
    public String getName() {
        return "AstroFactory 数据驱动科技";
    }
}
