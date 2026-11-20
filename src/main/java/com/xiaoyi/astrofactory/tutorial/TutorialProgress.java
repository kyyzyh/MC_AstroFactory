package com.xiaoyi.astrofactory.tutorial;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.level.PlayerLevel;
import com.xiaoyi.astrofactory.research.ResearchProgress;

/**
 * 教程推进：判定「当前这一步是否满足完成条件」并前进。
 *
 * <h2>设计</h2>
 * <p>不用「每个事件里写一遍 if 判断当前是哪一步」，而是把所有判定集中到
 * {@link #isSatisfied} 的 switch 里，事件处理只需调 {@link #tryAdvance}。
 * 这样加步骤只改一处。</p>
 *
 * <p>判定全部基于<b>已有状态</b>（背包、等级统计、科技解锁），不额外存标记 ——
 * 少一份状态就少一处不同步的可能。唯一例外是「组装机放下但没转」这种
 * 瞬时事件，它需要真正的事件监听（见 {@code TutorialEvents}）。</p>
 */
public final class TutorialProgress {
    private TutorialProgress() {}

    /**
     * 当前步骤是否已满足完成条件。
     *
     * @param flag 事件带来的瞬时信号（如「刚放下组装机」），没有则为 null
     */
    public static boolean isSatisfied(ServerPlayer player, Tutorial.Data data, String flag) {
        var inv = player.getInventory();
        var level = PlayerLevel.of(player);
        var research = ResearchProgress.of(player);
        String id = data.current().id();

        return switch (id) {
            // ---- 阶段 1：让采矿机跑起来 ----
            // 放下采矿机：瞬时信号
            case "place_miner" -> "place_miner".equals(flag);
            // 加燃料：采出过东西即说明燃料进去了
            case "miner_fuel" -> level.totalMined > 0;
            case "first_ore" -> level.totalMined > 0;

            // ---- 阶段 2：把矿运出来 ----
            case "place_belt" -> "place_belt".equals(flag);
            // 传送带把矿送到位：靠拾取事件打标（见 TutorialEvents）
            case "belt_deliver" -> "belt_deliver".equals(flag);

            // ---- 阶段 3：熔炼 ----
            case "place_furnace" -> "place_furnace".equals(flag);
            // 原矿进了熔炉（打开过熔炉界面并塞了东西）
            case "belt_to_furnace" -> "belt_to_furnace".equals(flag);
            // 熔炉产出过（totalCrafted 由熔炉累加）
            case "get_ingot" -> level.totalCrafted > 0;

            // ---- 阶段 4：做出第一批零件 ----
            case "craft_gear" -> hasItem(player, ModRegistry.IRON_GEAR.get());
            case "craft_inserter" -> hasItem(player, ModRegistry.BURNER_INSERTER_ITEM.get());
            case "place_inserter" -> "place_inserter".equals(flag);

            // ---- 阶段 5：科研 ----
            case "craft_lab" -> hasItem(player, ModRegistry.LAB_ITEM.get());

            // ---- 阶段 6：撞墙（13-15）★ 顺序不许改 ----
            case "craft_assembler" -> hasItem(player, ModRegistry.ASSEMBLER_ITEM.get());
            // 组装机放下但没转：打开界面看到「无电力」
            case "assembler_stuck" -> "assembler_stuck".equals(flag);
            // 知道需要发电
            case "need_power" -> "need_power".equals(flag);

            // ---- 阶段 7：发电 ----
            case "craft_boiler" -> hasItem(player, ModRegistry.BOILER_ITEM.get());
            case "steam_running" -> research.isUnlocked("steam_power")
                    || hasItem(player, ModRegistry.STEAM_ENGINE_ITEM.get());

            // ---- 阶段 8：自动化闭环 ----
            case "auto_line" -> !research.unlocked.isEmpty();

            default -> false;
        };
    }

    private static boolean hasItem(Player player, net.minecraft.world.item.Item item) {
        for (ItemStack stack : player.getInventory()) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 尝试推进教程。
     *
     * <p>会连续推进：如果玩家一口气满足了多步（例如直接从创造模式拿齐东西），
     * 循环把所有能过的步骤都过掉，每步都发奖励。这样不会出现「卡在第 1 步
     * 但背包里已经什么都有」的尴尬。</p>
     *
     * @param flag 瞬时信号，可为 null
     * @return 是否有步骤被推进
     */
    public static boolean tryAdvance(ServerPlayer player, String flag) {
        Tutorial.Data data = Tutorial.of(player);
        if (data.done) {
            return false;
        }
        boolean advanced = false;
        // 上限 18 次，防死循环
        for (int guard = 0; guard < Tutorial.count() && !data.done; guard++) {
            Tutorial.Step cur = data.current();
            if (!isSatisfied(player, data, flag)) {
                break;
            }
            // 完成当前步骤
            data.completed.add(cur.id());
            PlayerLevel.of(player).addExp(cur.expReward());
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.astrofactory.tutorial.step_done", cur.titleKey()));

            data.step++;
            if (data.step >= Tutorial.count()) {
                data.done = true;
                data.step = Tutorial.count() - 1;
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.astrofactory.tutorial.all_done"));
                PlayerLevel.awardMilestone(player, "full_line");
            }
            advanced = true;
            // 瞬时信号只对一步有效，用掉即清
            flag = null;
        }
        if (advanced) {
            syncTo(player);
        }
        return advanced;
    }

    /** 玩家主动折叠/展开任务卡（客户端按钮 → 服务端 → 同步回）。 */
    public static void toggleCollapsed(ServerPlayer player) {
        Tutorial.Data data = Tutorial.of(player);
        data.collapsed = !data.collapsed;
        syncTo(player);
    }

    /**
     * 把教程进度同步给客户端。
     *
     * <p>{@code ServerPlayer.connection} 在「玩家还没真正接进网络」时是 null
     * （例如登录事件早期、以及服务端自检里造的假玩家）。这时发数据包会 NPE，
     * 而进度本身已经写进 attachment，玩家下次登录会由 initial sync 带上，
     * 所以直接跳过即可。</p>
     */
    private static void syncTo(ServerPlayer player) {
        if (player.connection == null) {
            return;
        }
        player.syncData(Tutorial.TUTORIAL);
    }

    /** 当前步骤要高亮的方块；null 表示不高亮。 */
    public static Block highlightBlock(Player player) {
        Tutorial.Data data = Tutorial.of(player);
        if (data.done) {
            return null;
        }
        String id = data.current().id();
        return switch (id) {
            case "place_miner" -> ModRegistry.BURNER_MINER.get();
            case "miner_fuel" -> ModRegistry.BURNER_MINER.get();
            case "first_ore" -> ModRegistry.BURNER_MINER.get();
            case "place_belt" -> ModRegistry.TRANSPORT_BELT.get();
            case "belt_deliver" -> ModRegistry.TRANSPORT_BELT.get();
            case "place_furnace" -> ModRegistry.STONE_FURNACE.get();
            case "belt_to_furnace" -> ModRegistry.STONE_FURNACE.get();
            case "place_inserter" -> ModRegistry.BURNER_INSERTER.get();
            case "assembler_stuck" -> ModRegistry.ASSEMBLER.get();
            case "need_power" -> ModRegistry.ASSEMBLER.get();
            case "craft_boiler" -> ModRegistry.BOILER.get();
            case "steam_running" -> ModRegistry.STEAM_ENGINE.get();
            default -> null;
        };
    }
}
