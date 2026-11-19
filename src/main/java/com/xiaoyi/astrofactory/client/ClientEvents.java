package com.xiaoyi.astrofactory.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 客户端注册：方块实体渲染器 + 界面。
 */
@EventBusSubscriber(modid = Astrofactory.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.CONVEYOR_BE.get(), ConveyorRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.FAST_CONVEYOR_BE.get(), ConveyorRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.ASSEMBLER_MENU.get(), AssemblerScreen::new);
        event.register(ModRegistry.SMELTER_MENU.get(), SmelterScreen::new);
        event.register(ModRegistry.CRAFTER_MENU.get(), CrafterScreen::new);
        event.register(ModRegistry.RESEARCH_MENU.get(), ResearchScreen::new);
    }
}
