package com.astrofactory.blockentity;

import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 机器信息面板的统一视图接口（v0.8.9）。
 *
 * <p>为满足「所有机器右键都可查看面板，展示运行情况、输入、输出」，
 * 给没有独立面板的机器（自动采矿机/自动熔炉/机械臂/抽水机/燃料发电机）
 * 提供通用信息面板。各机器 BE 实现本接口暴露状态与输入/输出槽，
 * 面板 {@code InfoPanelMenu/InfoPanelScreen} 统一消费，避免每台机器复制一套。</p>
 */
public interface IMachineInfo {

    /** 机器显示名（标题），例如「自动采矿机」。 */
    String infoTitle();

    /**
     * 当前运行状态对应的翻译键后缀（见 {@code gui.astrofactory.info.status.*}）。
     * 例如 {@code "working"}、{@code "idle"}、{@code "no_fuel"}、{@code "no_power"}、
     * {@code "no_ore"}、{@code "no_water"}。
     */
    String infoStatusKey();

    // ------------------------------------------------------------ 能量条（无则返回 -1）

    /** 当前储能（FE）。无能量条返回 -1。 */
    default int infoEnergy() {
        return -1;
    }

    /** 储能上限（FE）。无能量条返回 -1。 */
    default int infoEnergyCapacity() {
        return -1;
    }

    // ------------------------------------------------------------ 进度条（无则返回 -1）

    /** 当前进度（tick）。无进度条返回 -1。 */
    default int infoProgress() {
        return -1;
    }

    /** 进度上限（tick）。无进度条返回 -1。 */
    default int infoMaxProgress() {
        return -1;
    }

    // ------------------------------------------------------------ 输入/输出槽（可为空 handler）

    /** 输入槽集合。无输入槽返回空 handler（非 null）。 */
    default ItemStackHandler infoInput() {
        return new ItemStackHandler(0);
    }

    /** 输出槽集合。无输出槽返回空 handler（非 null）。 */
    default ItemStackHandler infoOutput() {
        return new ItemStackHandler(0);
    }

    /** 是否有输入槽区（决定面板是否绘制「输入」区块）。 */
    default boolean hasInfoInput() {
        return infoInput().getSlots() > 0;
    }

    /** 是否有输出槽区（决定面板是否绘制「输出」区块）。 */
    default boolean hasInfoOutput() {
        return infoOutput().getSlots() > 0;
    }

    // ------------------------------------------------------------ 额外统计行（可为 null）

    /** 额外统计行文案（原样显示在状态区下方），可为 null。 */
    default String infoExtra() {
        return null;
    }
}
