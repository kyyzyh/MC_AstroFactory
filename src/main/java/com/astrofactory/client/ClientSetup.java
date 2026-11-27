package com.astrofactory.client;

import com.astrofactory.AstroFactory;
import com.astrofactory.network.ModNetwork;
import com.astrofactory.registry.ModMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端事件总线：注册 GUI Screen、登录时请求科技进度同步。
 */
@EventBusSubscriber(modid = AstroFactory.MODID, bus = EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TECH_TREE.get(), TechTreeScreen::new);
        event.register(ModMenus.ASSEMBLER.get(), AssemblerScreen::new);
        event.register(ModMenus.BOILER.get(), BoilerScreen::new);
        event.register(ModMenus.STEAM_ENGINE.get(), SteamEngineScreen::new);
        event.register(ModMenus.POWER_POLE.get(), PowerPoleScreen::new);
    }

    /** 电线杆连线可视化（US-102）。 */
    @SubscribeEvent
    public static void registerRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.POWER_POLE.get(),
                ctx -> new PowerPoleRenderer());
    }
}
