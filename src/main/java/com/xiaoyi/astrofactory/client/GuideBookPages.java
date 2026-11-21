package com.xiaoyi.astrofactory.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.xiaoyi.astrofactory.tutorial.Tutorial;

import java.util.ArrayList;
import java.util.List;

/**
 * 手册书页生成器：把 18 步教程转成「一本书」的页面内容。
 *
 * <p><b>为什么单独一个类</b>：书页文本是纯数据，跟渲染无关。把它从
 * {@link GuideBookScreen} 里拆出来，方便探针在服务端直接验证页数/文案，
 * 不需要开客户端。</p>
 *
 * <p>页面结构：</p>
 * <ol>
 *   <li>封面：标题 + 完成进度</li>
 *   <li>每步一页：序号 + 标题 + 说明 + 状态（已完成 / 进行中 / 未开始）</li>
 *   <li>末页：完整流程一览（原 guide step1-10 的浓缩版）</li>
 * </ol>
 */
public final class GuideBookPages {
    private GuideBookPages() {}

    /** 颜色：已完成 / 当前 / 未开始 / 正文。 */
    private static final ChatFormatting DONE = ChatFormatting.DARK_GREEN;
    private static final ChatFormatting CURRENT = ChatFormatting.GOLD;
    private static final ChatFormatting TODO = ChatFormatting.GRAY;
    private static final ChatFormatting BODY = ChatFormatting.BLACK;

    /**
     * 生成全部书页。
     *
     * @param currentStep 玩家当前步骤下标（0 基）；&gt;= count 表示全部完成
     * @param done        是否已全部完成
     */
    public static List<Component> build(int currentStep, boolean done) {
        List<Component> pages = new ArrayList<>();
        pages.add(cover(currentStep, done));

        int total = Tutorial.count();
        for (int i = 0; i < total; i++) {
            pages.add(stepPage(i, currentStep, done));
        }

        pages.add(summaryPage());

        // ---- 参考章节（不属于教程进度，是查得到的字典）----
        pages.add(chapterTitle("machines"));
        for (String id : MACHINE_ENTRIES) {
            pages.add(referencePage("machine." + id));
        }
        pages.add(chapterTitle("items"));
        for (String id : ITEM_ENTRIES) {
            pages.add(referencePage("item." + id));
        }
        pages.add(chapterTitle("trouble"));
        for (String id : TROUBLE_ENTRIES) {
            pages.add(referencePage("trouble." + id));
        }
        return pages;
    }

    // ------------------------------------------------------------ 参考章节数据

    /**
     * 机器速查条目（顺序 = 玩家遇到顺序）。
     *
     * <p>只列「玩家会去翻书问它干什么」的方块。传送带、漏斗这种一看就懂的
     * 不占篇幅 —— 手册的价值在解释，不在复述名字。</p>
     */
    private static final String[] MACHINE_ENTRIES = {
            "burner_miner", "transport_belt", "funnel", "burner_inserter",
            "stone_furnace", "assembler", "lab",
            "boiler", "steam_engine", "offshore_pump", "steam_pipe", "power_pole",
            "ore_vein"
    };

    /** 物品链条目：从原料到成品，让玩家能倒推「我现在缺哪一环」。 */
    private static final String[] ITEM_ENTRIES = {
            "iron_gear", "copper_cable", "electronic_circuit",
            "automation_pack", "steel_ingot"
    };

    /** 常见故障：每条都是「症状 → 原因 → 怎么办」。 */
    private static final String[] TROUBLE_ENTRIES = {
            "machine_stopped", "no_power", "arm_stuck", "belt_backup", "vein_depleted"
    };

    /** 章节标题页。 */
    private static Component chapterTitle(String chapter) {
        MutableComponent page = Component.empty();
        page.append(Component.translatable("book.astrofactory.ref." + chapter + ".title")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
        page.append("\n\n");
        page.append(Component.translatable("book.astrofactory.ref." + chapter + ".intro")
                .withStyle(ChatFormatting.DARK_GRAY));
        return page;
    }

    /**
     * 单条参考页：标题 + 正文。
     *
     * <p>机器条目额外给一行「怎么用」和一行「什么时候解锁」——
     * 前者是操作，后者是路线图，玩家最常问的就是这两件事。</p>
     */
    private static Component referencePage(String key) {
        String base = "book.astrofactory.ref." + key;

        MutableComponent page = Component.empty();
        page.append(Component.translatable(base + ".name")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_AQUA));
        page.append("\n\n");
        page.append(Component.translatable(base + ".body").withStyle(BODY));

        // 有 .usage 才追加（不是所有条目都需要）
        Component usage = Component.translatable(base + ".usage");
        if (!usage.getString().startsWith("book.astrofactory.ref.")) {
            page.append("\n\n");
            page.append(Component.translatable("book.astrofactory.ref.usage_label")
                    .withStyle(ChatFormatting.DARK_GRAY));
            page.append(usage.copy().withStyle(BODY));
        }
        return page;
    }

    /** 封面：标题 + 进度。 */
    private static Component cover(int currentStep, boolean done) {
        MutableComponent page = Component.empty();
        page.append(Component.translatable("book.astrofactory.guide.cover.title")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
        page.append("\n\n");
        page.append(Component.translatable("book.astrofactory.guide.cover.subtitle")
                .withStyle(ChatFormatting.DARK_GRAY));
        page.append("\n\n");

        int total = Tutorial.count();
        int shown = done ? total : Math.min(currentStep, total);
        page.append(Component.translatable("book.astrofactory.guide.cover.progress", shown, total)
                .withStyle(done ? DONE : CURRENT));

        page.append("\n\n");
        page.append(Component.translatable("book.astrofactory.guide.cover.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        return page;
    }

    /** 单个步骤页。 */
    private static Component stepPage(int index, int currentStep, boolean done) {
        Tutorial.Step step = Tutorial.stepAt(index);
        boolean isDone = done || index < currentStep;
        boolean isCurrent = !done && index == currentStep;

        ChatFormatting color = isDone ? DONE : (isCurrent ? CURRENT : TODO);
        String mark = isDone ? "[v] " : (isCurrent ? "[>] " : "[ ] ");

        MutableComponent page = Component.empty();
        page.append(Component.literal(mark).withStyle(color, ChatFormatting.BOLD));
        page.append(Component.translatable(step.titleKey()).withStyle(color, ChatFormatting.BOLD));

        page.append("\n\n");
        page.append(Component.translatable(step.descKey()).withStyle(BODY));

        page.append("\n\n");
        if (step.expReward() > 0) {
            page.append(Component.translatable("book.astrofactory.guide.exp", step.expReward())
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        return page;
    }

    /** 末页：完整流程一览。 */
    private static Component summaryPage() {
        MutableComponent page = Component.empty();
        page.append(Component.translatable("book.astrofactory.guide.summary.title")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
        page.append("\n\n");
        page.append(Component.translatable("book.astrofactory.guide.summary.body")
                .withStyle(BODY));
        return page;
    }
}
