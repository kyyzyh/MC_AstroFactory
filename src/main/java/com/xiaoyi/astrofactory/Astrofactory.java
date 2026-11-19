package com.xiaoyi.astrofactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import com.xiaoyi.astrofactory.world.OreVeinData;

/**
 * AstroFactory —— 异星工厂式自动化模组。
 *
 * 模块：
 * - 传送带（真连续移动 + 汇流 + backpressure）
 * - 机械臂（抓取 / 投放）
 * - 采矿机（无限矿脉）
 * - 组装机（配方自动化）
 * - 科研与科技树（后续）
 */
@Mod(Astrofactory.MODID)
public class Astrofactory {
    public static final String MODID = "astrofactory";

    public Astrofactory(IEventBus modEventBus) {
        ModRegistry.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 配方表依赖 ItemStack 数据组件，改为在首次使用时延迟初始化，
        // 此处不做预加载，避免 "Components not bound yet"。
    }

    /**
     * 服务器启动后幂等放置矿田（规格 §5.6）。
     *
     * <p>用 {@link OreVeinData} SavedData 保证幂等：只在首次启动时放置 4 片矿田，
     * 重载不再重复。</p>
     */
    private void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        var level = server.overworld();
        if (level == null) {
            return;
        }
        var spawn = level.getLevelData().getRespawnData().pos();
        var data = level.getDataStorage().computeIfAbsent(OreVeinData.TYPE);
        data.generateIfAbsent(level, spawn);
    }
}
