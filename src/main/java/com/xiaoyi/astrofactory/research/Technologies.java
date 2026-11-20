package com.xiaoyi.astrofactory.research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科技树定义（规格 v3.2 §4）。
 *
 * <p>层级（{@code ->} 表示前置）：</p>
 * <pre>
 *   automation_1（自动化 I，10）──┬─> electronics（电子学，10）
 *                                │        └─> steam_power（蒸汽动力，50）─> steel_processing（钢铁，50）
 *                                ├─> mining_1（采矿 I，20）──┐
 *                                └─> assembly_1（组装 I，20）┴─> logistics_1（物流 I，50）
 *                                         │
 *                                         ├─> research_1（科研 I，50）
 *                                         └─> automation_2（自动化 II，40）
 * </pre>
 *
 * <p><b>cost 的含义</b>：需要的科研包（电路板 / 红瓶）数量，1 个包 = 1 点。
 * 数值取规格 §4 的门槛量级（10 / 50 / 40 / 50）。</p>
 *
 * <p><b>列表顺序即「科研台默认研究顺序」</b>：先基础后进阶，
 * 保证 {@code firstResearchable} 回退时给出的是玩家最该做的那一个。</p>
 *
 * <p><b>解锁项</b>（{@code unlocks}）只做展示与判定依据，不做硬门禁 ——
 * 0.1.0 的方块全部已注册、可直接合成，避免破坏既有存档与产线。</p>
 */
public final class Technologies {
    private Technologies() {}

    private static final Map<String, Technology> BY_ID = new LinkedHashMap<>();

    static {
        // ---- 第 1 层：开局基础
        add(new Technology("automation_1", "tech.astrofactory.automation_1",
                List.of(), 10, List.of("conveyor", "robotic_arm")));

        // ---- 第 2 层：规格「触发：10 铜板」
        add(new Technology("electronics", "tech.astrofactory.electronics",
                List.of("automation_1"), 10,
                List.of("electronic_circuit", "electric_arm", "iron_chest", "research_table")));

        add(new Technology("mining_1", "tech.astrofactory.mining_1",
                List.of("automation_1"), 20, List.of("miner")));

        add(new Technology("assembly_1", "tech.astrofactory.assembly_1",
                List.of("automation_1"), 20, List.of("assembler")));

        // ---- 第 3 层：规格「触发：50 铁板」
        add(new Technology("steam_power", "tech.astrofactory.steam_power",
                List.of("electronics"), 50,
                List.of("boiler", "steam_engine", "offshore_pump", "steam_pipe", "power_pole")));

        // ---- 第 4 层：红瓶科技
        add(new Technology("logistics_1", "tech.astrofactory.logistics_1",
                List.of("mining_1", "assembly_1"), 50, List.of("fast_conveyor")));

        add(new Technology("research_1", "tech.astrofactory.research_1",
                List.of("assembly_1"), 50, List.of("research_table")));

        add(new Technology("automation_2", "tech.astrofactory.automation_2",
                List.of("assembly_1"), 40, List.of("assembler_2")));

        add(new Technology("steel_processing", "tech.astrofactory.steel_processing",
                List.of("steam_power"), 50, List.of("steel_furnace", "steel_chest")));
    }

    private static void add(Technology tech) {
        BY_ID.put(tech.id(), tech);
    }

    public static Technology get(String id) {
        return BY_ID.get(id);
    }

    /** 全部科技，顺序即科研台默认研究顺序。 */
    public static List<Technology> all() {
        return new ArrayList<>(BY_ID.values());
    }

    /** 前置是否全部满足。 */
    public static boolean prerequisitesMet(Technology tech, java.util.Set<String> unlocked) {
        for (String pre : tech.prerequisites()) {
            if (!unlocked.contains(pre)) {
                return false;
            }
        }
        return true;
    }

    /** 是否当前可研究：未解锁且前置满足。 */
    public static boolean isResearchable(Technology tech, java.util.Set<String> unlocked) {
        return tech != null && !unlocked.contains(tech.id()) && prerequisitesMet(tech, unlocked);
    }

    /** 当前全部可研究的科技，顺序同 {@link #all()}。 */
    public static List<Technology> researchable(java.util.Set<String> unlocked) {
        List<Technology> out = new ArrayList<>();
        for (Technology tech : BY_ID.values()) {
            if (isResearchable(tech, unlocked)) {
                out.add(tech);
            }
        }
        return out;
    }
}
