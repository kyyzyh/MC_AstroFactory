package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import com.astrofactory.worldgen.IcebergOreFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 自定义世界生成 Feature 注册。
 *
 * <p>冰山状矿体不是原版 ore 那样的小团块，而是从地表露头向下深埋
 * 一整片实心矿体，需要自定义 Feature 来实现。</p>
 */
public final class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.FEATURE, AstroFactory.MODID);

    /** 冰山矿体（椭球深埋 + 地表露头）。 */
    public static final Supplier<Feature<IcebergOreFeature.Config>> ICEBERG_ORE =
            FEATURES.register("iceberg_ore", IcebergOreFeature::new);

    private ModFeatures() {}
}
