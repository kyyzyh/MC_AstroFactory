package com.xiaoyi.astrofactory.recipe;

/**
 * 机器类型 —— 决定配方的输入形态。
 *
 * <ul>
 *   <li>{@link #SMELTING} 熔炼炉：单输入槽，原矿 -> 锭</li>
 *   <li>{@link #CRAFTING} 制作台：单输入槽，锭 -> 零件（可一次吃多个）</li>
 *   <li>{@link #ASSEMBLING} 组装机：3x3 输入网格，零件 -> 成品</li>
 * </ul>
 *
 * JSON 里写 {@code "type": "smelting"} 或 {@code "astrofactory:smelting"} 均可。
 */
public enum MachineType {
    SMELTING("smelting", 60),
    CRAFTING("crafting", 40),
    ASSEMBLING("assembling", 30);

    private final String id;
    private final int defaultTicks;

    MachineType(String id, int defaultTicks) {
        this.id = id;
        this.defaultTicks = defaultTicks;
    }

    /** JSON 中使用的短名。 */
    public String id() {
        return id;
    }

    /** 配方未写 time 时使用的耗时（tick）。 */
    public int defaultTicks() {
        return defaultTicks;
    }

    /** 是否使用 3x3 网格输入。 */
    public boolean isGrid() {
        return this == ASSEMBLING;
    }

    /** 解析 JSON 的 type 字段；无法识别返回 null。 */
    public static MachineType byId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        for (MachineType t : values()) {
            if (t.id.equals(s)) {
                return t;
            }
        }
        return null;
    }
}
