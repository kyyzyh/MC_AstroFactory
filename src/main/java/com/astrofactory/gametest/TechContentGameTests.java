package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.recipe.AssemblerRecipe;
import com.astrofactory.tech.TechDef;
import com.astrofactory.tech.TechLockManager;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechRegistry;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * US-108 GameTest：首批科技内容校验。
 *
 * <p>覆盖清单「异常」与「验收 2」：</p>
 * <ol>
 *   <li><b>前置无环</b>：硬编码科技树（{@link TechNodes}）与数据驱动科技
 *       （{@link TechRegistry}）作为一张 DAG 无环——DFS 着色检测，防栈溢出/死循环；</li>
 *   <li><b>开局解锁集</b>：新世界开局默认已解锁 MINING + BASIC_AUTOMATION
 *       （P0a 玩法不被倒锁，采矿机/传送带/机械臂开局可用）；</li>
 *   <li><b>无孤儿配方 / 无悬空归属</b>：数据驱动科技声明的每个 {@code unlock_recipes}
 *       都真实存在于完整配方快照里（不会锁到一条不存在的配方）；组装机的
 *       {@code required_tech} 引用的科技必须真实存在。</li>
 * </ol>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class TechContentGameTests {

    // ---------------------------------------------------------------- 前置无环

    /** 完整科技关系（硬编码 + 数据驱动）作为 DAG 做 DFS，检测环。 */
    @GameTest(template = "pole_flat", batch = "techContent", timeoutTicks = 100)
    public static void techTreeIsAcyclic(GameTestHelper helper) {
        // 所有科技 id（硬编码 + 数据驱动）
        Set<String> ids = new HashSet<>(TechNodes.all().keySet());
        ids.addAll(TechRegistry.all().stream().map(TechDef::id).toList());

        Set<String> visiting = new HashSet<>();   // 当前 DFS 栈
        Set<String> done = new HashSet<>();       // 已完成的节点

        for (String id : ids) {
            if (done.contains(id)) {
                continue;
            }
            if (!visit(id, ids, visiting, done)) {
                helper.fail("科技前置链存在环，涉及 " + id);
                return;
            }
        }
        helper.succeed();
    }

    /** 返回 false 表示发现环。 */
    private static boolean visit(String id, Set<String> ids,
                                 Set<String> visiting, Set<String> done) {
        if (done.contains(id)) {
            return true;
        }
        if (!visiting.add(id)) {
            return false; // 环
        }
        try {
            for (String parent : parentsOf(id, ids)) {
                if (!visit(parent, ids, visiting, done)) {
                    return false;
                }
            }
            return true;
        } finally {
            visiting.remove(id);
            done.add(id);
        }
    }

    /** 取某科技的前置 id（跨硬编码 / 数据驱动）。 */
    private static java.util.List<String> parentsOf(String id, Set<String> ids) {
        TechDef def = TechRegistry.get(id);
        if (def != null) {
            return def.parents();
        }
        TechNodes.Node node = TechNodes.get(id);
        return node == null ? java.util.List.of() : java.util.List.of(node.parents());
    }

    // ---------------------------------------------------------------- 开局解锁集

    /** 新世界开局默认已解锁 MINING + BASIC_AUTOMATION（P0a 玩法不被倒锁）。 */
    @GameTest(template = "pole_flat", batch = "techContent", timeoutTicks = 100)
    public static void openingUnlockSetPresent(GameTestHelper helper) {
        TechTreeData data = TechTreeData.get(helper.getLevel().getServer());
        data.setCreativeBypass(false);
        if (data.getLevel(TechNodes.MINING) < 1) {
            helper.fail("开局未解锁自动采矿 MINING");
            return;
        }
        if (data.getLevel(TechNodes.BASIC_AUTOMATION) < 1) {
            helper.fail("开局未解锁基础自动化 BASIC_AUTOMATION");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 无孤儿配方 / 无悬空归属

    /**
     * 数据驱动科技声明的 {@code unlock_recipes} 必须真实存在于完整配方快照（不悬空），
     * 且归属科技本身存在；组装机 {@code required_tech} 引用的科技必须被 TechNodes 或
     * TechRegistry 认领。
     */
    @GameTest(template = "pole_flat", batch = "techContent", timeoutTicks = 100)
    public static void noDanglingRecipeLockRefererences(GameTestHelper helper) {
        Set<String> snapshotIds = new HashSet<>();
        for (RecipeHolder<?> holder : TechLockManager.fullRecipes()) {
            snapshotIds.add(holder.id().toString());
        }
        if (snapshotIds.isEmpty()) {
            helper.fail("配方快照为空（TechLockManager 未初始化？）");
            return;
        }

        // 1) 数据驱动科技 unlock_recipes 不悬空 + 指向真实存在的 recipe
        for (TechDef def : TechRegistry.all()) {
            for (String recipeId : def.unlockRecipes()) {
                if (!snapshotIds.contains(recipeId)) {
                    helper.fail("科技 " + def.id() + " 声明解锁的配方不存在于快照："
                            + recipeId + "（快照 " + snapshotIds.size() + " 条）");
                    return;
                }
            }
            // 2) 数据驱动科技前置必须真实存在
            for (String parent : def.parents()) {
                if (!TechNodes.exists(parent) && !TechRegistry.exists(parent)) {
                    helper.fail("科技 " + def.id() + " 的前置不存在：" + parent);
                    return;
                }
            }
        }

        // 3) 组装机配方 required_tech 引用的科技必须真实存在
        for (RecipeHolder<?> holder : TechLockManager.fullRecipes()) {
            if (holder.value() instanceof AssemblerRecipe recipe) {
                String tech = recipe.requiredTechOrNull();
                if (tech != null && !TechNodes.exists(tech) && !TechRegistry.exists(tech)) {
                    helper.fail("组装机配方 " + holder.id() + " 引用不存在的科技：" + tech);
                    return;
                }
            }
        }
        helper.succeed();
    }

    /** 机械动力 I 已注册且依赖电力时代（US-108 首个加成科技）。 */
    @GameTest(template = "pole_flat", batch = "techContent", timeoutTicks = 100)
    public static void mechanicalPowerRegistered(GameTestHelper helper) {
        TechNodes.Node node = TechNodes.get(TechNodes.MECHANICAL_POWER);
        if (node == null) {
            helper.fail("机械动力 I 未注册");
            return;
        }
        boolean requiresElectric = java.util.Arrays.asList(node.parents())
                .contains(TechNodes.ELECTRIC_POWER);
        if (!requiresElectric) {
            helper.fail("机械动力 I 应依赖电力时代，实际前置 " + java.util.Arrays.toString(node.parents()));
            return;
        }
        // 创造模式旁路下应视为满级（解锁）
        TechTreeData data = TechTreeData.get(helper.getLevel().getServer());
        data.setCreativeBypass(true);
        if (!data.isUnlocked(TechNodes.MECHANICAL_POWER)) {
            helper.fail("创造模式下机械动力 I 应已解锁");
            return;
        }
        data.setCreativeBypass(false);
        helper.succeed();
    }

    private TechContentGameTests() {}
}
