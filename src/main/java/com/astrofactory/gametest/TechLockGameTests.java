package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.tech.TechDef;
import com.astrofactory.tech.TechLockManager;
import com.astrofactory.tech.TechRegistry;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * US-105 GameTest：科技树数据框架 + 配方锁定。
 *
 * <p>覆盖清单验收项：</p>
 * <ol>
 *   <li>未研究时被锁的配方在服务端配方表里查不到，研究后能查到（配方条件拦截）；</li>
 *   <li>前置未满足时不能开始研究；</li>
 *   <li>世界级共享数据读写：同一份 {@link TechTreeData} 上写入，另一个读取方（模拟第二名玩家）
 *       看到同一进度；存档版本号正确写入。</li>
 * </ol>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class TechLockGameTests {

    private static final String TECH_A = AstroFactory.MODID + ":test_assembly";
    private static final String TECH_B = AstroFactory.MODID + ":test_research";
    private static final String RECIPE_A = AstroFactory.MODID + ":assembling_test_part";
    private static final String RECIPE_B = AstroFactory.MODID + ":assembling_test_pack";

    /** 数据包里的科技定义确实被读进来了（数据驱动生效）。 */
    @GameTest(template = "pole_flat", batch = "techLock", timeoutTicks = 100)
    public static void techDefsLoadedFromData(GameTestHelper helper) {
        TechDef a = TechRegistry.get(TECH_A);
        TechDef b = TechRegistry.get(TECH_B);
        if (a == null || b == null) {
            helper.fail("数据驱动科技未载入：已载入 " + TechRegistry.size() + " 项，缺 "
                    + (a == null ? TECH_A : TECH_B));
            return;
        }
        if (a.packCount() != 5 || b.packCount() != 8) {
            helper.fail("科技成本与数据不符：A=" + a.packCount() + " B=" + b.packCount());
            return;
        }
        if (!b.parents().contains(TECH_A)) {
            helper.fail("科技 B 的前置应为 A，实际 " + b.parents());
            return;
        }
        helper.succeed();
    }

    /** 配方锁定：未解锁时被拦，解锁后放行。 */
    @GameTest(template = "pole_flat", batch = "techLock", timeoutTicks = 100)
    public static void lockedRecipeBlockedUntilResearched(GameTestHelper helper) {
        TechTreeData data = resetTech(helper);

        RecipeHolder<?> holder = findInSnapshot(RECIPE_A);
        if (holder == null) {
            helper.fail("测试配方不在完整快照里：" + RECIPE_A
                    + "（快照 " + TechLockManager.fullRecipes().size() + " 条，DataGen 产物未进构建？）");
            return;
        }

        // 1) 未研究 → 判定为锁住
        if (TechLockManager.isUnlocked(holder, data)) {
            helper.fail("未研究时配方不应可用：" + RECIPE_A);
            return;
        }
        // 2) 反向索引应指向科技 A（数据声明 → 无需改代码）
        String tech = TechLockManager.requiredTechOf(holder);
        if (!TECH_A.equals(tech)) {
            helper.fail("配方锁归属错误：期望 " + TECH_A + "，实际 " + tech);
            return;
        }
        // 3) 研究科技 A → 放行
        data.unlockDataDriven(TECH_A);
        if (!TechLockManager.isUnlocked(holder, data)) {
            helper.fail("研究科技 A 后配方仍被锁：" + RECIPE_A);
            return;
        }
        // 4) 科技 B 的配方仍应锁着（未研究）
        RecipeHolder<?> holderB = findInSnapshot(RECIPE_B);
        if (holderB != null && TechLockManager.isUnlocked(holderB, data)) {
            helper.fail("科技 B 未研究，其配方不应可用：" + RECIPE_B);
            return;
        }
        helper.succeed();
    }

    /**
     * 真拦截断言：裁剪后的服务端配方表里，锁住的配方<b>查不到</b>；解锁后<b>查得到</b>。
     *
     * <p>这一条才是「工作台与组装机都拦」的直接证据——工作台的合成判定走
     * {@code RecipeManager#byKey/getRecipeFor}，表里没有就做不出来。</p>
     */
    @GameTest(template = "pole_flat", batch = "techLock", timeoutTicks = 100)
    public static void recipeAbsentFromManagerWhileLocked(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        TechTreeData data = resetTech(helper);
        ResourceLocation id = ResourceLocation.parse(RECIPE_A);

        // 清空 + 重新应用裁剪，得到「未研究」状态
        data.setLevelForTest(TECH_A, 0);
        TechLockManager.applyLock(server);
        if (server.getRecipeManager().byKey(id).isPresent()) {
            helper.fail("未研究时配方仍在服务端配方表里：" + RECIPE_A);
            return;
        }

        // 解锁后重新裁剪，配方应回来
        data.unlockDataDriven(TECH_A);
        TechLockManager.applyLock(server);
        if (server.getRecipeManager().byKey(id).isEmpty()) {
            helper.fail("解锁后配方未回到服务端配方表：" + RECIPE_A);
            return;
        }
        helper.succeed();
    }


    /**
     * 把共享科技状态复位到「两个测试科技都没研究」。
     *
     * <p>GameTest 用同一份世界存档，{@link TechTreeData} 是世界级共享数据——
     * 用例之间必须显式复位，否则谁先跑谁影响别人。同批（{@code batch = "techLock"}）
     * 串行执行，避免并行交错。</p>
     */
    private static TechTreeData resetTech(GameTestHelper helper) {
        TechTreeData data = TechTreeData.get(helper.getLevel().getServer());
        data.setCreativeBypass(false);
        data.setLevelForTest(TECH_A, 0);
        data.setLevelForTest(TECH_B, 0);
        data.clearResearch();
        return data;
    }

    /** 从完整快照里按 id 找配方（被锁的配方不在 RecipeManager 里，必须查快照）。 */
    private static RecipeHolder<?> findInSnapshot(String recipeId) {
        ResourceLocation id = ResourceLocation.parse(recipeId);
        for (RecipeHolder<?> holder : TechLockManager.fullRecipes()) {
            if (holder.id().equals(id)) {
                return holder;
            }
        }
        return null;
    }

    /** 前置门禁：前置没解锁不能开研究；解锁后可以。 */
    @GameTest(template = "pole_flat", batch = "techLock", timeoutTicks = 100)
    public static void prerequisiteGatesResearch(GameTestHelper helper) {
        TechTreeData data = resetTech(helper);

        if (data.canResearchDataDriven(TECH_B)) {
            helper.fail("前置未满足时科技 B 不应可研究");
            return;
        }
        if (!data.canResearchDataDriven(TECH_A)) {
            helper.fail("科技 A 无前置，应可研究");
            return;
        }
        // 研究 A 之后 B 才开放
        data.unlockDataDriven(TECH_A);
        if (!data.canResearchDataDriven(TECH_B)) {
            helper.fail("解锁科技 A 后科技 B 应可研究");
            return;
        }
        helper.succeed();
    }

    /** 世界级共享数据：写入方写进度，另一个读取方看到同一份；版本号落盘。 */
    @GameTest(template = "pole_flat", batch = "techLock", timeoutTicks = 100)
    public static void sharedWorldDataReadWrite(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        TechTreeData first = resetTech(helper);
        first.setResearch(TECH_A);
        first.addProgress(3);

        // 「第二名玩家」重新取一次实例：SavedData 按名字取，必须是同一个对象
        TechTreeData second = TechTreeData.get(server);
        if (second != first) {
            helper.fail("TechTreeData 未按世界共享：两次取得不同实例");
            return;
        }
        if (!TECH_A.equals(second.getCurrentResearch()) || second.getResearchProgress() != 3) {
            helper.fail("共享进度不一致：current=" + second.getCurrentResearch()
                    + " progress=" + second.getResearchProgress());
            return;
        }
        // 存档版本号
        var tag = first.save(new net.minecraft.nbt.CompoundTag(),
                server.registryAccess());
        if (tag.getInt("DataVersion") != TechTreeData.SAVE_VERSION) {
            helper.fail("存档版本号未写入，实际 " + tag.getInt("DataVersion"));
            return;
        }
        helper.succeed();
    }

    /** 现场演示「只改数据不改代码」：注入一项新科技，立刻可查可用。 */
    @GameTest(template = "pole_flat", batch = "techLock", timeoutTicks = 100)
    public static void addingTechNeedsOnlyData(GameTestHelper helper) {
        Map<String, TechDef> current = new java.util.LinkedHashMap<>();
        for (TechDef def : TechRegistry.all()) {
            current.put(def.id(), def);
        }
        String newId = AstroFactory.MODID + ":test_injected";
        String newRecipe = AstroFactory.MODID + ":assembling_test_pack";
        current.put(newId, new TechDef(newId, "测试注入科技",
                List.of(), AstroFactory.MODID + ":research_pack", 3, List.of(newRecipe)));

        TechRegistry.replaceAll(current);
        try {
            if (!TechRegistry.exists(newId)) {
                helper.fail("注入后科技不存在");
                return;
            }
            if (!newId.equals(TechRegistry.recipeLock(newRecipe))) {
                helper.fail("注入后配方锁未指向新科技，实际 " + TechRegistry.recipeLock(newRecipe));
                return;
            }
            helper.succeed();
        } finally {
            // 还原，避免污染其他用例
            Map<String, TechDef> restored = new java.util.LinkedHashMap<>(current);
            restored.remove(newId);
            TechRegistry.replaceAll(restored);
        }
    }
}
