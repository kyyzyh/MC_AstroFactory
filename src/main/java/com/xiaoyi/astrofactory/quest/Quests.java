package com.xiaoyi.astrofactory.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.level.PlayerLevel;
import com.xiaoyi.astrofactory.research.ResearchProgress;
import com.xiaoyi.astrofactory.research.Technologies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务系统：把「该做什么」显式列给玩家。
 *
 * <p>设计目标：异星工厂没有显式任务表，但新手需要一张清单。
 * /* 本模组用任务系统把规格 §8 的 18 步教程 + 里程碑 + 科技解锁
 * 组织成一张可查的任务表，玩家按 M（或 /astrofactory quests）查看。</p>
 *
 * <p>任务状态存在玩家 attachment 里（见 {@link PlayerLevel.Data} 的
 * milestones 与 ResearchProgress.Data.unlocked 交叉判定），
 * 本类只负责「定义 + 判定逻辑」，不存状态 —— 状态都在各自的
 * attachment 里，避免多份存档不同步。</p>
 *
 * <p>任务分两类：
 *   - 主线（story）：跟随 18 步教程的关键节点，不可删。
 *   - milestone（里程碑）：一次性成就式，达成发 EXP。
 * </p>
 */
public final class Quests {
    private Quests() {}

    /** 任务定义。 */
    public record Quest(
            String id,
            String displayKey,
            String descriptionKey,
            Type type,
            String checkKey   // 触发条件描述（lang key），仅展示用
    ) {
        /** 任务类型。 */
        public enum Type { STORY, MILESTONE }
    }

    private static final Map<String, Quest> BY_ID = new LinkedHashMap<>();

    static {
        // 主线任务：与 18 步教程关键节点一一对应（规格 §8，不可删关键点）
        add(new Quest("q_get_starter", "quest.astrofactory.q_get_starter",
                "quest.astrofactory.q_get_starter.desc", Quest.Type.STORY,
                "quest.astrofactory.check.on_login"));
        add(new Quest("q_first_mine", "quest.astrofactory.q_first_mine",
                "quest.astrofactory.q_first_mine.desc", Quest.Type.STORY,
                "quest.astrofactory.check.first_ore"));
        add(new Quest("q_first_belt", "quest.astrofactory.q_first_belt",
                "quest.astrofactory.q_first_belt.desc", Quest.Type.STORY,
                "quest.astrofactory.check.place_belt"));
        add(new Quest("q_first_smelter", "quest.astrofactory.q_first_smelter",
                "quest.astrofactory.q_first_smelter.desc", Quest.Type.STORY,
                "quest.astrofactory.check.smelter_produce"));
        add(new Quest("q_automation1", "quest.astrofactory.q_automation1",
                "quest.astrofactory.q_automation1.desc", Quest.Type.STORY,
                "quest.astrofactory.check.tech_automation1"));
        add(new Quest("q_first_assembler", "quest.astrofactory.q_first_assembler",
                "quest.astrofactory.q_first_assembler.desc", Quest.Type.STORY,
                "quest.astrofactory.check.assembler_produce"));
        add(new Quest("q_circuit", "quest.astrofactory.q_circuit",
                "quest.astrofactory.q_circuit.desc", Quest.Type.STORY,
                "quest.astrofactory.check.circuit"));
        add(new Quest("q_first_tech", "quest.astrofactory.q_first_tech",
                "quest.astrofactory.q_first_tech.desc", Quest.Type.STORY,
                "quest.astrofactory.check.any_tech"));
        add(new Quest("q_power", "quest.astrofactory.q_power",
                "quest.astrofactory.q_power.desc", Quest.Type.STORY,
                "quest.astrofactory.check.steam_power"));
        add(new Quest("q_full_line", "quest.astrofactory.q_full_line",
                "quest.astrofactory.q_full_line.desc", Quest.Type.STORY,
                "quest.astrofactory.check.full_line"));

        // 里程碑任务（与 PlayerLevel.MILESTONES id 对齐）
        for (var m : PlayerLevel.MILESTONES.values()) {
            add(new Quest(m.id(), m.displayKey(),
                    "quest.astrofactory.milestone." + m.id() + ".desc",
                    Quest.Type.MILESTONE, "quest.astrofactory.check.milestone"));
        }
    }

    private static void add(Quest q) {
        BY_ID.put(q.id(), q);
    }

    public static Quest get(String id) {
        return BY_ID.get(id);
    }

    public static List<Quest> all() {
        return new ArrayList<>(BY_ID.values());
    }

    public static List<Quest> byType(Quest.Type type) {
        var out = new ArrayList<Quest>();
        for (var q : BY_ID.values()) {
            if (q.type() == type) {
                out.add(q);
            }
        }
        return out;
    }

    /** 任务是否已完成（基于玩家 attachment 状态判定）。 */
    public static boolean isDone(ServerPlayer player, String id) {
        var level = PlayerLevel.of(player);
        var research = ResearchProgress.of(player);
        return switch (id) {
            case "q_get_starter" -> true; // born with it
            case "q_first_mine" -> level.totalMined > 0;
            case "q_first_belt" -> level.totalCrafted > 0; // 传送带由制作产出
            case "q_first_smelter" -> research.isUnlocked("mining_1") || level.totalCrafted > 0;
            case "q_automation1" -> research.isUnlocked("automation_1");
            case "q_first_assembler" -> research.isUnlocked("assembly_1");
            case "q_circuit" -> level.totalCrafted >= 1 && research.isUnlocked("assembly_1");
            case "q_first_tech" -> !research.unlocked.isEmpty();
            case "q_power" -> research.isUnlocked("steam_power") || research.unlocked.size() >= 3;
            case "q_full_line" -> research.unlocked.size() >= 4;
            default -> level.milestones.contains(id); // milestone 类
        };
    }

    /** 任务进度文本（0/1 或 数字/目标）。 */
    public static Component progressText(ServerPlayer player, Quest q) {
        boolean done = isDone(player, q.id());
        return Component.translatable(done ? "quest.astrofactory.status.done"
                : "quest.astrofactory.status.pending");
    }

    /** 主线完成数 / 总数。 */
    public static int[] storyProgress(ServerPlayer player) {
        var story = byType(Quest.Type.STORY);
        int done = 0;
        for (var q : story) {
            if (isDone(player, q.id())) done++;
        }
        return new int[]{done, story.size()};
    }
}
