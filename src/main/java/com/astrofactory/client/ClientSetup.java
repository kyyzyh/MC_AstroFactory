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
        event.register(ModMenus.LAB.get(), LabScreen::new);
    }

    /** 电线杆连线可视化（US-102）。 */
    @SubscribeEvent
    public static void registerRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.POWER_POLE.get(),
                ctx -> new PowerPoleRenderer());
        // 全机器工作动效（活动部件动画）——统一渲染器
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.MINER.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.MinerBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.INSERTER.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.InserterBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.SMELTER.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.SmelterBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.GENERATOR.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.GeneratorBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.BOILER.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.BoilerBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.STEAM_ENGINE.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.SteamEngineBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.WATER_PUMP.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.WaterPumpBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.ASSEMBLER.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.AssemblerBlockEntity>());
        event.registerBlockEntityRenderer(
                com.astrofactory.registry.ModBlockEntities.LAB.get(),
                ctx -> new MachineAnimationRenderer<com.astrofactory.blockentity.LabBlockEntity>());
    }

    /**
     * US-105：客户端也要读科技 JSON——科技表驱动「哪些配方已解锁」的显示，
     * 客户端没表就会把锁定配方当成可做。
     */
    @SubscribeEvent
    public static void registerClientReloadListeners(
            net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new com.astrofactory.tech.TechRegistry.ReloadListener());
    }
}
