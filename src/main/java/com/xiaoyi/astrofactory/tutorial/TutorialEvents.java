package com.xiaoyi.astrofactory.tutorial;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 教程事件：把游戏里的动作喂给 {@link TutorialProgress#tryAdvance}。
 *
 * <h2>为什么分两类触发</h2>
 * <ul>
 *   <li><b>状态型</b>（背包里有齿轮 / 采过矿 / 解锁科技）：走
 *       {@link PlayerTickEvent.Post} 定期检查。用 tick 而不是每类事件各写一遍，
 *       是为了「玩家在别处拿到物品也能过」—— 例如从箱子里取出齿轮。</li>
 *   <li><b>瞬时型</b>（放下方块、打开某个 GUI）：必须靠事件，因为状态里
 *       看不出来。这类信号只对当前一步有效。</li>
 * </ul>
 *
 * <p>tick 检查有节流：每 20 tick（1 秒）一次，避免每 tick 遍历背包。</p>
 */
@EventBusSubscriber(modid = Astrofactory.MODID)
public final class TutorialEvents {
    private TutorialEvents() {}

    /** tick 节流计数（玩家 id 无关，用取模即可）。 */
    private static final int CHECK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (sp.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        TutorialProgress.tryAdvance(sp, null);
    }

    /** 玩家首次进服：立即检查一次，不用等 1 秒。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TutorialProgress.tryAdvance(sp, null);
        }
    }

    /** 放下方块：place_miner / place_belt / place_furnace / place_inserter。 */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        var block = event.getPlacedBlock().getBlock();
        String flag = null;
        if (block == ModRegistry.BURNER_MINER.get()) {
            flag = "place_miner";
        } else if (block == ModRegistry.TRANSPORT_BELT.get()
                || block == ModRegistry.FAST_TRANSPORT_BELT.get()) {
            flag = "place_belt";
        } else if (block == ModRegistry.STONE_FURNACE.get()) {
            flag = "place_furnace";
        } else if (block == ModRegistry.BURNER_INSERTER.get()) {
            flag = "place_inserter";
        }
        if (flag != null) {
            TutorialProgress.tryAdvance(sp, flag);
        }
    }

    /**
     * 玩家捡起物品：belt_deliver（传送带把矿送到玩家面前）。
     *
     * <p>这里不判断「是不是被传送带送来的」—— 只要玩家捡到了矿石/原矿，
     * 就说明物流这一段通了。判定过宽但无害：教程是引导，不是考试。</p>
     */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack stack = event.getOriginalStack();
        if (stack.isEmpty()) {
            return;
        }
        if (stack.is(Items.RAW_IRON) || stack.is(Items.RAW_COPPER)
                || stack.is(Items.RAW_GOLD) || stack.is(Items.COAL)) {
            TutorialProgress.tryAdvance(sp, "belt_deliver");
        }
    }

    /**
     * 打开容器：组装机界面 = 撞墙（第 14 步）+ 知道要发电（第 15 步）。
     *
     * <p>这两步的语义：玩家放下组装机后打开它，看到「无电力」，才意识到
     * 需要发电。所以「打开组装机界面」就是这两步的完成信号。规格 §5.7
     * 要求「先撞墙再给梯子」，这里严格按这个顺序推进（第 14 步先过，第 15
     * 步再告诉原因）。</p>
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (event.getContainer() instanceof com.xiaoyi.astrofactory.menu.AssemblerMenu) {
            TutorialProgress.tryAdvance(sp, "assembler_stuck");
            TutorialProgress.tryAdvance(sp, "need_power");
        } else if (event.getContainer() instanceof com.xiaoyi.astrofactory.menu.StoneFurnaceMenu) {
            // 打开熔炉 = 玩家在看熔炉（下一步是「把矿送进熔炉」）
            TutorialProgress.tryAdvance(sp, "belt_to_furnace");
        }
    }
}
