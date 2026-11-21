package com.xiaoyi.astrofactory.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 客户端注册：方块实体渲染器 + 界面 + 按键。
 */
@EventBusSubscriber(modid = Astrofactory.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /**
     * 折叠/展开教程任务卡的按键。
     *
     * <p>用 {@code InputConstants.getKey("key.keyboard.k")} 而不是 GLFW 常量：
     * 字符串 API 是 26.3 推荐的写法，也不依赖 lwjgl 在编译期可见。</p>
     */
    public static final KeyMapping TOGGLE_TUTORIAL = new KeyMapping(
            "key.astrofactory.toggle_tutorial",
            InputConstants.Type.KEYBOARD,
            InputConstants.getKey("key.keyboard.k").getValue(),
            KeyMapping.Category.MISC);

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_TUTORIAL);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // consumeClick() 保证按住不放只触发一次
        while (TOGGLE_TUTORIAL.consumeClick()) {
            TutorialHud.toggleCollapsed();
        }
    }

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
        event.register(ModRegistry.INSERTER_MENU.get(), InserterScreen::new);
    }
}
