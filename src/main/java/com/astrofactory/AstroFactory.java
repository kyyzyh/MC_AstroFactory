package com.astrofactory;

import com.mojang.logging.LogUtils;
import com.astrofactory.network.ModNetwork;
import com.astrofactory.registry.ModRegistry;
import com.astrofactory.tech.TechTreeData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * AstroFactory —— 在 Minecraft 里搭建自动化工厂流水线。
 *
 * <p>核心玩法闭环：采矿 → 传送带运输 → 加工 → 再运输。
 * v0.4：异星工厂式科技树（DAG + 等级科技 + 分级研究包 + 可视化 GUI + 组装机）。</p>
 */
@Mod(AstroFactory.MODID)
public class AstroFactory {
    public static final String MODID = "astrofactory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AstroFactory(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModRegistry.register(modEventBus);

        // 玩家登录后同步科技进度（GUI 显示用）
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);

        // v0.6 触发型科技：做出指定物品自动解锁（借鉴异星工厂 2.0）
        com.astrofactory.tech.TriggeredTechListener.install();

        if (com.astrofactory.dev.SelfTest.enabled()) {
            com.astrofactory.dev.SelfTest.install();
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[AstroFactory] common setup complete");
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            TechTreeData data = TechTreeData.get(serverPlayer.serverLevel().getServer());
            ModNetwork.syncTo(serverPlayer, data);
        }
    }
}
