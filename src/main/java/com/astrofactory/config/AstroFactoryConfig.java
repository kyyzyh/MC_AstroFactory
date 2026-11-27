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

    // ---------------------------------------------------------------- 组装机（US-104）

    /** 组装机每 tick 耗电（FE/t）。蓝图基准 30。 */
    public static final ModConfigSpec.IntValue ASSEMBLER_ENERGY_PER_TICK;
    /** 组装速度乘数（1.0 = 配方标称速度；>1 更快）。蓝图基准 1.0。 */
    public static final ModConfigSpec.DoubleValue ASSEMBLER_SPEED_MULTIPLIER;
    /** 组装机内部储能上限（FE）。 */
    public static final ModConfigSpec.IntValue ASSEMBLER_CAPACITY;
    /** 组装机最大充能速度（FE/t）。 */
    public static final ModConfigSpec.IntValue ASSEMBLER_MAX_RECEIVE;
    /** 换配方时，新配方用不上的已投入原料是否退回（推出到朝向容器/掉落）。 */
    public static final ModConfigSpec.BooleanValue ASSEMBLER_RETURN_EXTRA_ON_RECIPE_CHANGE;
    /** 缺料等待时是否仍耗电（false = 不耗电，符合「缺料等待不耗电」）。 */
    public static final ModConfigSpec.BooleanValue ASSEMBLER_IDLE_DRAINS_ENERGY;

    // ---------------------------------------------------------------- 电线杆（US-102）

    /** 供电半径（格，球形）。 */
    public static final ModConfigSpec.IntValue POWER_POLE_RADIUS;
    /** 杆与杆自动连线的最远距离（格）。 */
    public static final ModConfigSpec.IntValue POWER_POLE_LINK_RANGE;
    /** 单个电网每 tick 最大传输量（FE/t）。 */
    public static final ModConfigSpec.IntValue POWER_POLE_MAX_TRANSFER;
    /** 单张电网最多聚合多少根杆。 */
    public static final ModConfigSpec.IntValue POWER_POLE_MAX_POLES;

    // ---------------------------------------------------------------- 电力采矿机（US-103）

    /** 电力采矿机每 tick 耗电（蓝图：20 FE/t）。 */
    public static final ModConfigSpec.IntValue MINER_FE_PER_TICK;
    /** 电力采矿机产出一个物品所需 tick 数（蓝图：20 = 1 矿/秒）。 */
    public static final ModConfigSpec.IntValue MINER_CYCLE_TICKS;
    /** 电力采矿机内部电缓冲（蓝图：10,000 FE）。 */
    public static final ModConfigSpec.IntValue MINER_CAPACITY;
    /** 电力采矿机最大充能速度（FE/t）。 */
    public static final ModConfigSpec.IntValue MINER_MAX_RECEIVE;

    // ---------------------------------------------------------------- 电力机械臂（US-103）

    /** 电力机械臂每 tick 耗电（蓝图：10 FE/t）。 */
    public static final ModConfigSpec.IntValue INSERTER_FE_PER_TICK;
    /** 电力机械臂一次搬运所需 tick 数（蓝图：17 ≈ 1.2 次/秒）。 */
    public static final ModConfigSpec.IntValue INSERTER_CYCLE_TICKS;
    /** 电力机械臂内部电缓冲（蓝图：10,000 FE）。 */
    public static final ModConfigSpec.IntValue INSERTER_CAPACITY;
    /** 电力机械臂最大充能速度（FE/t）。 */
    public static final ModConfigSpec.IntValue INSERTER_MAX_RECEIVE;

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

        builder.comment("组装机（Assembler）—— 选定配方自动组装，US-104")
                .push("assembler");
        ASSEMBLER_ENERGY_PER_TICK = builder
                .comment("每 tick 耗电（FE/t）。蓝图基准 30")
                .defineInRange("energyPerTick", 30, 0, 100_000);
        ASSEMBLER_SPEED_MULTIPLIER = builder
                .comment("组装速度乘数（1.0 = 配方标称速度）。蓝图基准 1.0")
                .defineInRange("speedMultiplier", 1.0D, 0.05D, 100.0D);
        ASSEMBLER_CAPACITY = builder
                .comment("内部储能上限（FE）")
                .defineInRange("capacity", 20_000, 1_000, 100_000_000);
        ASSEMBLER_MAX_RECEIVE = builder
                .comment("最大充能速度（FE/t）")
                .defineInRange("maxReceive", 500, 1, 1_000_000);
        ASSEMBLER_RETURN_EXTRA_ON_RECIPE_CHANGE = builder
                .comment("换配方时把新配方用不上的已投入原料退回（推出到朝向容器，推不动则掉在机器上方）")
                .define("returnExtraOnRecipeChange", true);
        ASSEMBLER_IDLE_DRAINS_ENERGY = builder
                .comment("缺料等待时是否仍按 tick 耗电。false（默认）= 不耗电，符合「缺料等待不耗电」")
                .define("idleDrainsEnergy", false);
        builder.pop();

        builder.comment("电线杆（Power Pole）—— 覆盖式无线电网，US-102")
                .push("powerPole");
        POWER_POLE_RADIUS = builder
                .comment("供电半径（格，球形）。蓝图基准 7")
                .defineInRange("radius", 7, 1, 32);
        POWER_POLE_LINK_RANGE = builder
                .comment("杆与杆自动连线的最远距离（格）。蓝图基准 10")
                .defineInRange("linkRange", 10, 1, 64);
        POWER_POLE_MAX_TRANSFER = builder
                .comment("单个电网每 tick 最大传输量（FE/t）")
                .defineInRange("maxTransfer", 2000, 1, 1_000_000);
        POWER_POLE_MAX_POLES = builder
                .comment("单张电网最多聚合多少根杆，防止跨度失控卡服")
                .defineInRange("maxPoles", 256, 1, 4096);
        builder.pop();

        builder.comment("电力采矿机（Electric Miner）—— US-103：每 tick 扣电，断电暂停不清零进度")
                .push("miner");
        MINER_FE_PER_TICK = builder
                .comment("每 tick 耗电（FE/t）。蓝图基准 20")
                .defineInRange("fePerTick", 20, 0, 10_000);
        MINER_CYCLE_TICKS = builder
                .comment("产出一个物品所需 tick 数。蓝图基准 20（1 矿/秒），是热能版的 2 倍速")
                .defineInRange("cycleTicks", 20, 1, 20_000);
        MINER_CAPACITY = builder
                .comment("内部电缓冲（FE）。蓝图基准 10,000")
                .defineInRange("capacity", 10_000, 100, 10_000_000);
        MINER_MAX_RECEIVE = builder
                .comment("最大充能速度（FE/t），需 ≥ fePerTick 才能持续运转")
                .defineInRange("maxReceive", 200, 1, 100_000);
        builder.pop();

        builder.comment("电力机械臂（Electric Inserter）—— US-103：每 tick 扣电，断电时停在原处不丢物品")
                .push("inserter");
        INSERTER_FE_PER_TICK = builder
                .comment("每 tick 耗电（FE/t）。蓝图基准 10")
                .defineInRange("fePerTick", 10, 0, 10_000);
        INSERTER_CYCLE_TICKS = builder
                .comment("一次搬运所需 tick 数。蓝图基准 17（约 1.2 次/秒）")
                .defineInRange("cycleTicks", 17, 1, 20_000);
        INSERTER_CAPACITY = builder
                .comment("内部电缓冲（FE）。蓝图基准 10,000")
                .defineInRange("capacity", 10_000, 100, 10_000_000);
        INSERTER_MAX_RECEIVE = builder
                .comment("最大充能速度（FE/t），需 ≥ fePerTick 才能持续运转")
                .defineInRange("maxReceive", 200, 1, 100_000);
        builder.pop();

        SPEC = builder.build();
    }

    private AstroFactoryConfig() {}
}
