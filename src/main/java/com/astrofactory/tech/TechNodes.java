package com.astrofactory.tech;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 科技节点定义（借鉴《异星工厂》的科技树结构与前期流程）。
 *
 * <p>v0.5「贫瘠开局」：开局只有研究台、研究包、扳手、木箱可用，
 * 其余全部锁进科技树——对应异星工厂「徒手伐木 → 第一个红瓶科技」的体验。</p>
 *
 * <p>v0.6「触发型科技」：借鉴异星工厂 2.0——最初几项科技不是研究出来的，
 * 而是「做出指定物品」自动完成（如做出 50 铁板触发蒸汽动力）。
 * {@link #STEAM_POWER} 改为做出 16 个铁锭自动解锁，不消耗研究包。</p>
 *
 * <p>流程主线（对应异星工厂）：</p>
 * <ol>
 *   <li>手挖矿 + 原版熔炉烧板（徒手时代）；</li>
 *   <li>研究「基础自动化」→ 解锁传送带、机械臂、储物箱（红瓶：自动化）；</li>
 *   <li>做出 16 个铁锭 → 触发「蒸汽动力」→ 解锁抽水机、锅炉、蒸汽机；</li>
 *   <li>研究「物流学」→ 解锁分流器、地下传送带、铁箱（物流学）；</li>
 *   <li>研究「钢材」→ 解锁钢板二次冶炼（铁板 → 钢板）；</li>
 *   <li>研究「自动组装」→ 解锁组装机（自动化 2）。</li>
 * </ol>
 */
public final class TechNodes {

    // ---------------------------------------------------------------- 节点 id

    /** 基础自动化：传送带、机械臂、储物箱（需研究，10 点，贫瘠开局第一科技）。 */
    public static final String BASIC_AUTOMATION = "basic_automation";

    /** 自动采矿：燃料采矿机。 */
    public static final String MINING = "mining";

    /** 二型采矿机（v0.8.9）：4×4 区域采集，更高效。依赖自动采矿。 */
    public static final String MINER_MK2 = "miner_mk2";

    /** 自动冶炼：燃料熔炉。 */
    public static final String SMELTING = "smelting";

    /**
     * 蒸汽动力：抽水机、锅炉、蒸汽机（电力时代入口）。
     * v0.6 改为触发型：做出 16 个铁锭自动解锁（对应异星工厂 50 铁板触发）。
     */
    public static final String STEAM_POWER = "steam_power";

    /** 物流学：分流器、地下传送带、铁箱。 */
    public static final String LOGISTICS = "logistics";

    /** 钢材：解锁钢板二次冶炼（铁锭 → 钢板）。 */
    public static final String STEEL = "steel";

    /** 电力时代：发电机、电缆、扳手升级（蒸汽机的替代/补充）。 */
    public static final String ELECTRIC_POWER = "electric_power";

    /** 高效采矿 I-III：每级采矿速度 +20%（等级科技）。 */
    public static final String EFFICIENT_MINING = "efficient_mining";

    /** 高效冶炼：熔炉速度 +30%。 */
    public static final String EFFICIENT_SMELTING = "efficient_smelting";

    /** 高级研究：解锁高级研究包的合成。 */
    public static final String ADVANCED_PACKS = "advanced_packs";

    /** 自动组装：解锁组装机（电力自动合成）。 */
    public static final String AUTOMATION = "automation";

    /** 机械动力 I（US-108）：电力机械臂提速 20% ⚙（首个加成科技）。 */
    public static final String MECHANICAL_POWER = "mechanical_power";

    // ---------------------------------------------------------------- 定义

    /** 研究包等级：对应异星工厂的红瓶 / 绿瓶。 */
    public enum PackTier {
        /** 初级研究包：铁板 + 铜板。 */
        BASIC,
        /** 高级研究包：钢板 + 传送带 + 研究包（需「高级研究」科技）。 */
        ADVANCED
    }

    /** 时代。 */
    public enum Era {
        /** 手动时代：手挖矿、原版熔炉。 */
        MANUAL,
        /** 燃料时代：烧木头和煤。 */
        FUEL,
        /** 电力时代：蒸汽发电、电缆、电动机器。 */
        ELECTRIC
    }

    /**
     * 一个科技节点。
     *
     * @param id            唯一 id
     * @param costPerLevel  每级研究成本（科技点）；触发型节点不使用
     * @param parents       前置科技 id 数组（DAG，可多个）
     * @param nameKey       语言键
     * @param era           所属时代，仅用于界面分组与提示
     * @param pack          消耗的研究包等级
     * @param maxLevel      最大等级（1 = 一次性科技，&gt;1 = 等级科技）
     * @param triggerItem   触发物品 id（非 null = 触发型科技，做出即解锁）
     * @param triggerCount  触发所需的累计物品数
     */
    public record Node(String id, int costPerLevel, String[] parents, String nameKey,
                       Era era, PackTier pack, int maxLevel,
                       String triggerItem, int triggerCount) {}

    private static final Map<String, Node> NODES = new LinkedHashMap<>();

    static {
        register(new Node(BASIC_AUTOMATION, 10, new String[0],
                "tech.astrofactory.basic_automation", Era.MANUAL, PackTier.BASIC, 1, null, 0));
        register(new Node(MINING, 20, new String[]{BASIC_AUTOMATION},
                "tech.astrofactory.mining", Era.FUEL, PackTier.BASIC, 1, null, 0));
        // v0.8.9：二型采矿机（4×4 区域采集）
        register(new Node(MINER_MK2, 60, new String[]{MINING},
                "tech.astrofactory.miner_mk2", Era.FUEL, PackTier.BASIC, 1, null, 0));
        register(new Node(SMELTING, 30, new String[]{MINING},
                "tech.astrofactory.smelting", Era.FUEL, PackTier.BASIC, 1, null, 0));
        // v0.6 触发型：做出 16 个铁锭自动解锁（对应异星工厂「50 铁板触发蒸汽动力」）
        register(new Node(STEAM_POWER, 0, new String[]{SMELTING},
                "tech.astrofactory.steam_power", Era.FUEL, PackTier.BASIC, 1,
                "minecraft:iron_ingot", 16));
        register(new Node(LOGISTICS, 40, new String[]{BASIC_AUTOMATION},
                "tech.astrofactory.logistics", Era.FUEL, PackTier.BASIC, 1, null, 0));
        // v0.6 钢材：解锁熔炉的钢板二次冶炼配方
        register(new Node(STEEL, 40, new String[]{SMELTING},
                "tech.astrofactory.steel", Era.FUEL, PackTier.BASIC, 1, null, 0));
        register(new Node(EFFICIENT_MINING, 40, new String[]{MINING},
                "tech.astrofactory.efficient_mining", Era.FUEL, PackTier.BASIC, 3, null, 0));
        register(new Node(EFFICIENT_SMELTING, 60, new String[]{SMELTING},
                "tech.astrofactory.efficient_smelting", Era.FUEL, PackTier.BASIC, 1, null, 0));
        register(new Node(ELECTRIC_POWER, 80, new String[]{STEAM_POWER, LOGISTICS},
                "tech.astrofactory.electric_power", Era.ELECTRIC, PackTier.BASIC, 1, null, 0));
        register(new Node(ADVANCED_PACKS, 100, new String[]{ELECTRIC_POWER},
                "tech.astrofactory.advanced_packs", Era.ELECTRIC, PackTier.BASIC, 1, null, 0));
        register(new Node(AUTOMATION, 150, new String[]{ELECTRIC_POWER},
                "tech.astrofactory.automation", Era.ELECTRIC, PackTier.ADVANCED, 1, null, 0));
        // US-108 机械动力 I：电力机械臂提速 20%（数值进 config），依赖电力时代
        register(new Node(MECHANICAL_POWER, 50, new String[]{ELECTRIC_POWER},
                "tech.astrofactory.mechanical_power", Era.ELECTRIC, PackTier.BASIC, 1, null, 0));
    }

    private static void register(Node node) {
        NODES.put(node.id(), node);
    }

    private TechNodes() {}

    // ---------------------------------------------------------------- 查询

    public static boolean exists(String id) {
        return NODES.containsKey(id);
    }

    public static Node get(String id) {
        return NODES.get(id);
    }

    /** 按注册顺序返回全部节点（GUI 按此顺序绘制）。 */
    public static Map<String, Node> all() {
        return NODES;
    }

    public static int costOf(String id) {
        Node node = NODES.get(id);
        return node == null ? 0 : node.costPerLevel();
    }

    /** 是否为触发型科技（做出指定物品自动解锁，不消耗研究包）。 */
    public static boolean isTriggered(String id) {
        Node node = NODES.get(id);
        return node != null && node.triggerItem() != null;
    }

    /** 前置是否全部满足且未满级。触发型科技不可主动研究，只能等动作触发。 */
    public static boolean canResearch(String id, TechTreeData data) {
        Node node = NODES.get(id);
        if (node == null || node.triggerItem() != null
                || data.getLevel(id) >= node.maxLevel()) {
            return false;
        }
        // 创造模式下所有科技都已满级（getLevel 返回 maxLevel），上面那条已拦掉，
        // 这里再兜一层，防止将来改判定逻辑时漏掉。
        if (data.isCreativeBypass()) {
            return false;
        }
        for (String parent : node.parents()) {
            if (data.getLevel(parent) < 1) {
                return false;
            }
        }
        return true;
    }

    /** 某科技的全部直接后续（GUI 画连线用）。 */
    public static java.util.List<Node> childrenOf(String id) {
        return NODES.values().stream()
                .filter(n -> Arrays.asList(n.parents()).contains(id))
                .toList();
    }
}
