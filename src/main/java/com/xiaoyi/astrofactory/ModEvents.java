package com.xiaoyi.astrofactory;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.xiaoyi.astrofactory.item.GuideBookItem;
import com.xiaoyi.astrofactory.recipe.MachineRecipeLoader;
import com.xiaoyi.astrofactory.recipe.MachineType;
import com.xiaoyi.astrofactory.recipe.RecipeRegistry;

/**
 * 通用（双端）事件：配方数据包重载监听 + 调试命令 + 出生点包发放。
 */
@EventBusSubscriber(modid = Astrofactory.MODID)
public final class ModEvents {
    private ModEvents() {}

    /** 出生点包标记：写在玩家持久化数据里，跨死亡/重连保留，只发一次。 */
    private static final String STARTER_TAG = "astrofactory:starter_received";

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
                            sendGuideTo(ctx.getSource());
                            return 1;
                        })));
    }

    /**
     * 出生点包（规格 §8 验收切片：手搓开局）。
     *
     * <p>1 采矿机 + 1 石熔炉 + 20 传送带 + 1 手册，每个玩家只发一次。
     * 用 {@code getPersistentData()} 打标，跨死亡/重连保留，不会重复发。
     * 发不下的部分（满包）掉在脚边，不丢物品。</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        var tag = sp.getPersistentData();
        if (tag.getBooleanOr(STARTER_TAG, false)) {
            return;
        }
        tag.putBoolean(STARTER_TAG, true);

        giveOnce(sp, new ItemStack(ModRegistry.BURNER_MINER_ITEM.get()));
        giveOnce(sp, new ItemStack(ModRegistry.STONE_FURNACE_ITEM.get()));
        giveOnce(sp, new ItemStack(ModRegistry.TRANSPORT_BELT_ITEM.get(), 20));
        giveOnce(sp, new ItemStack(ModRegistry.GUIDE_BOOK.get()));

        sp.sendSystemMessage(Component.translatable("msg.astrofactory.starter.given"));
    }

    /** 尝试把物品塞进背包，塞不下则掉在脚边（不丢物品）。 */
    private static void giveOnce(ServerPlayer sp, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!sp.getInventory().add(stack.copy())) {
            sp.drop(stack.copy(), true, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    // ------------------------------------------------------------ 教程文案

    /** 新手流程步骤数（与语言文件 step1..stepN 对应）。 */
    public static final int GUIDE_STEPS = 10;

    /** 向命令源逐条发送 10 步教程（命令与手册共用同一份文案）。 */
    public static void sendGuideTo(net.minecraft.commands.CommandSourceStack src) {
        sendLine(src, "msg.astrofactory.guide.header");
        for (int i = 1; i <= GUIDE_STEPS; i++) {
            sendLine(src, "msg.astrofactory.guide.step" + i);
        }
        sendLine(src, "msg.astrofactory.guide.footer");
    }

    /** 向玩家逐条发送 10 步教程（手册右键用）。 */
    public static void sendGuideTo(ServerPlayer sp) {
        sendGuideTo(sp.createCommandSourceStack());
    }

    private static void sendLine(net.minecraft.commands.CommandSourceStack src, String key) {
        src.sendSuccess(() -> Component.translatable(key), false);
    }
}
