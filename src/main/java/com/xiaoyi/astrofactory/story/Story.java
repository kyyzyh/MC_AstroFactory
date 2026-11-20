package com.xiaoyi.astrofactory.story;

import net.minecraft.network.chat.Component;

import com.xiaoyi.astrofactory.Astrofactory;

/**
 * 世界观与故事背景（纯文案，无状态）。
 *
 * <p>玩家扮演的是一名被派往未知星球「赫斯珀里亚（Hesperia）」的
 * 自动化工程师。母船在降落时失联，你只有一台采矿机、一座熔炉、
 * 二十节传送带和一本手册。星球上没有现成的补给，只有埋在地下的
 * 矿脉。你的任务：把矿脉变成产线，把产线变成工厂，把工厂变成
 * 能发出信号让母船找到你的文明。</p>
 *
 * <p>所有文案通过 {@link #line(int)} 按编号取用，命令 /astrofactory story
 * 与出生点包提示共用同一套文案，保证一致。</p>
 */
public final class Story {
    private Story() {}

    /** 故事总行数（与语言文件 story.astrofactory.lineN 对应）。 */
    public static final int LINE_COUNT = 8;

    /** 取第 n 行故事文案（1-based）。越界返回空组件。 */
    public static Component line(int n) {
        if (n < 1 || n > LINE_COUNT) {
            return Component.empty();
        }
        return Component.translatable("story.astrofactory.line" + n);
    }

    /** 全部故事行（按顺序）。 */
    public static Component[] all() {
        var out = new Component[LINE_COUNT];
        for (int i = 0; i < LINE_COUNT; i++) {
            out[i] = line(i + 1);
        }
        return out;
    }

    /** 模组名/世界观简称，用于日志与标题。 */
    public static String worldName() {
        return "赫斯珀里亚";
    }
}
