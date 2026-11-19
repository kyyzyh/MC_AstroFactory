package com.xiaoyi.astrofactory.research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科技树定义。
 *
 * 层级：
 *   automation_1（自动化 I）      —— 解锁传送带、机械臂
 *   mining_1（采矿 I）            —— 依赖 automation_1，解锁采矿机
 *   assembly_1（组装 I）          —— 依赖 automation_1，解锁组装机
 *   logistics_1（物流 I）         —— 依赖 mining_1 + assembly_1，解锁高速传送带
 *   research_1（科研 I）          —— 依赖 assembly_1，解锁科研台
 */
public final class Technologies {
    private Technologies() {}

    private static final Map<String, Technology> BY_ID = new LinkedHashMap<>();

    static {
        add(new Technology("automation_1", "tech.astrofactory.automation_1",
                List.of(), 10, List.of("conveyor", "robotic_arm")));
        add(new Technology("mining_1", "tech.astrofactory.mining_1",
                List.of("automation_1"), 20, List.of("miner")));
        add(new Technology("assembly_1", "tech.astrofactory.assembly_1",
                List.of("automation_1"), 20, List.of("assembler")));
        add(new Technology("logistics_1", "tech.astrofactory.logistics_1",
                List.of("mining_1", "assembly_1"), 50, List.of("fast_conveyor")));
        add(new Technology("research_1", "tech.astrofactory.research_1",
                List.of("assembly_1"), 50, List.of("research_table")));
    }

    private static void add(Technology tech) {
        BY_ID.put(tech.id(), tech);
    }

    public static Technology get(String id) {
        return BY_ID.get(id);
    }

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
}
