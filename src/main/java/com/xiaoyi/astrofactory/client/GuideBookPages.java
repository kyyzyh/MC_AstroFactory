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
        return pages;
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
