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
        event.registerBlockEntityRenderer(ModRegistry.TRANSPORT_BELT_BE.get(), TransportBeltRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.FAST_TRANSPORT_BELT_BE.get(), TransportBeltRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.ASSEMBLER_MENU.get(), AssemblerScreen::new);
        event.register(ModRegistry.STONE_FURNACE_MENU.get(), StoneFurnaceScreen::new);
        event.register(ModRegistry.LAB_MENU.get(), LabScreen::new);
        event.register(ModRegistry.BOILER_MENU.get(), BoilerScreen::new);
    }
}
