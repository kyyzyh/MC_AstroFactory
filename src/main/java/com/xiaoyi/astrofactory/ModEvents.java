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

    /** /astrofactory recipes —— 打印当前生效的配方数量。 */
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
                        })));
    }
}
