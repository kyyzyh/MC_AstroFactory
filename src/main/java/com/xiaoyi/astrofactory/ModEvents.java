package com.xiaoyi.astrofactory;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.xiaoyi.astrofactory.recipe.MachineRecipeLoader;
import com.xiaoyi.astrofactory.recipe.MachineType;
import com.xiaoyi.astrofactory.recipe.RecipeRegistry;

/**
 * 通用（双端）事件：配方数据包重载监听 + 调试命令。
 */
@EventBusSubscriber(modid = Astrofactory.MODID)
public final class ModEvents {
    private ModEvents() {}

    /** 注册数据包配方加载器（服务端资源重载链）。 */
    @SubscribeEvent
    public static void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(Astrofactory.MODID, "machine_recipes"),
                new MachineRecipeLoader());
    }

    /** /astrofactory —— 子命令：recipes（配方统计）、guide（新手流程）。 */
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("astrofactory")
                        .then(Commands.literal("recipes").executes(ctx -> {
                            var src = ctx.getSource();
                            StringBuilder sb = new StringBuilder();
                            for (MachineType type : MachineType.values()) {
                                if (sb.length() > 0) {
                                    sb.append("  ");
                                }
                                sb.append(type.id()).append('=').append(RecipeRegistry.byType(type).size());
                            }
                            src.sendSuccess(() -> Component.literal(
                                    "[AstroFactory] 配方总数 " + RecipeRegistry.all().size() + "（" + sb + "）"), false);
                            return 1;
                        }))
                        .then(Commands.literal("guide").executes(ctx -> {
                            var src = ctx.getSource();
                            // 逐条发送，避免单条消息过长被截断
                            sendLine(src, "msg.astrofactory.guide.header");
                            for (int i = 1; i <= GUIDE_STEPS; i++) {
                                sendLine(src, "msg.astrofactory.guide.step" + i);
                            }
                            sendLine(src, "msg.astrofactory.guide.footer");
                            return 1;
                        })));
    }

    /** 新手流程步骤数（与语言文件 step1..stepN 对应）。 */
    private static final int GUIDE_STEPS = 10;

    private static void sendLine(net.minecraft.commands.CommandSourceStack src, String key) {
        src.sendSuccess(() -> Component.translatable(key), false);
    }
}
