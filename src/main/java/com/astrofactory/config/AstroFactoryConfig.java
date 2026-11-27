package com.astrofactory.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * AstroFactory 通用配置（COMMON 类型，服务端权威）。
 *
 * <p>P0b 起，蓝图基准配平表里标 ⚙ 的数值统一收敛到这里，
 * 改平衡不用重新编译模组，改 {@code config/astrofactory-common.toml} 即可。</p>
 *
 * <p>注意：COMMON 配置在服务端读取，客户端读同一份文件；
 * 若日后需要「服务端改、客户端跟随」，应改用 SERVER 类型（会自动同步）。</p>
 */
public final class AstroFactoryConfig {

    public static final ModConfigSpec SPEC;

    // ---------------------------------------------------------------- 锅炉（US-101）

    /** 锅炉水/蒸汽内部缓冲上限。 */
    public static final ModConfigSpec.IntValue BOILER_FLUID_CAPACITY;
    /** 锅炉每 tick 产汽量。 */
    public static final ModConfigSpec.IntValue BOILER_STEAM_PER_TICK;
    /** 1 煤可燃烧的 tick 数（20 tick = 1 秒，蓝图要求 80 秒 = 1600）。 */
    public static final ModConfigSpec.IntValue BOILER_COAL_BURN_TICKS;

    // ---------------------------------------------------------------- 蒸汽机（US-101）

    /** 蒸汽机内部电缓冲（蓝图：10,000 FE）。 */
    public static final ModConfigSpec.IntValue STEAM_ENGINE_CAPACITY;
    /** 蒸汽机每 tick 发电上限（蓝图：40 FE/t）。 */
    public static final ModConfigSpec.IntValue STEAM_ENGINE_GENERATION_PER_TICK;
    /** 1 单位蒸汽转化多少 FE。 */
    public static final ModConfigSpec.IntValue STEAM_ENGINE_FE_PER_STEAM;
    /** 蒸汽机对外最大输出（FE/t），供电缆抽取。 */
    public static final ModConfigSpec.IntValue STEAM_ENGINE_MAX_EXTRACT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("锅炉（Boiler）—— 烧燃料把水变蒸汽，蒸汽只供相邻蒸汽机")
                .push("boiler");
        BOILER_FLUID_CAPACITY = builder
                .comment("水/蒸汽内部缓冲上限")
                .defineInRange("fluidCapacity", 100, 1, 100_000);
        BOILER_STEAM_PER_TICK = builder
                .comment("每 tick 产汽量。蓝图基准：1 台锅炉供 2 台蒸汽机")
                .defineInRange("steamPerTick", 2, 1, 1_000);
        BOILER_COAL_BURN_TICKS = builder
                .comment("1 块煤燃烧的 tick 数（1600 = 80 秒）")
                .defineInRange("coalBurnTicks", 1600, 1, 200_000);
        builder.pop();

        builder.comment("蒸汽机（Steam Engine）—— 消耗蒸汽发电，供相邻电网抽取")
                .push("steamEngine");
        STEAM_ENGINE_CAPACITY = builder
                .comment("内部电缓冲（FE）。蓝图基准 10,000")
                .defineInRange("capacity", 10_000, 1_000, 10_000_000);
        STEAM_ENGINE_GENERATION_PER_TICK = builder
                .comment("每 tick 发电上限（FE/t）。蓝图基准 40")
                .defineInRange("generationPerTick", 40, 1, 100_000);
        STEAM_ENGINE_FE_PER_STEAM = builder
                .comment("1 单位蒸汽转化多少 FE")
                .defineInRange("fePerSteam", 20, 1, 10_000);
        STEAM_ENGINE_MAX_EXTRACT = builder
                .comment("对外最大输出（FE/t），供电缆抽取")
                .defineInRange("maxExtract", 200, 1, 100_000);
        builder.pop();

        SPEC = builder.build();
    }

    private AstroFactoryConfig() {}
}
