package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * US-109 GameTest：旧存档迁移（不毁档）。
 *
 * <p>覆盖清单验收「旧存档升级后已解锁状态迁移正确」「旧档在新版正常加载」。
 * 构造三档存档 NBT 直接喂 {@link TechTreeData#load}：</p>
 * <ul>
 *   <li><b>v0</b>（无 {@code DataVersion}，P0a 前的贫瘠开局）：迁移后补齐开局解锁集；</li>
 *   <li><b>v2</b>（US-105 格式，已有部分科技进度）：已有进度<b>不被覆盖</b>，缺失的开局项补齐；</li>
 *   <li><b>v3</b>（当前格式）：原样读取，不再二次补写；</li>
 *   <li>所有档读完后 {@code DataVersion} 统一标为当前版本（下次保存即写新号）。</li>
 * </ul>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class SaveMigrationGameTests {

    /** v0 老档（P0a 贫瘠开局）：没有任何 DataVersion / Levels。 */
    @GameTest(template = "pole_flat", batch = "saveMigration", timeoutTicks = 100)
    public static void v0SaveGetsOpeningUnlockSet(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag(); // 空 tag = 最老的存档

        TechTreeData data = TechTreeData.load(tag, helper.getLevel().registryAccess());

        if (data.getLevel(TechNodes.MINING) < 1) {
            helper.fail("v0 老档迁移后未补齐自动采矿");
            return;
        }
        if (data.getLevel(TechNodes.BASIC_AUTOMATION) < 1) {
            helper.fail("v0 老档迁移后未补齐基础自动化");
            return;
        }
        if (data.getSaveVersion() != TechTreeData.SAVE_VERSION) {
            helper.fail("v0 老档迁移后版本号应为 " + TechTreeData.SAVE_VERSION
                    + "，实际 " + data.getSaveVersion());
            return;
        }
        helper.succeed();
    }

    /** v2 老档：已有进度保留（不被覆盖），缺失的开局项补齐。 */
    @GameTest(template = "pole_flat", batch = "saveMigration", timeoutTicks = 100)
    public static void v2SaveKeepsProgressAndFillsOpening(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 2);
        CompoundTag levels = new CompoundTag();
        // 玩家已研究过物流学、钢材
        levels.putInt(TechNodes.LOGISTICS, 1);
        levels.putInt(TechNodes.STEEL, 1);
        // 显式研究过自动采矿（等级 1）——迁移不得抹掉
        levels.putInt(TechNodes.MINING, 1);
        tag.put("Levels", levels);

        TechTreeData data = TechTreeData.load(tag, helper.getLevel().registryAccess());

        if (data.getLevel(TechNodes.LOGISTICS) != 1 || data.getLevel(TechNodes.STEEL) != 1) {
            helper.fail("v2 老档已有进度被覆盖：物流=" + data.getLevel(TechNodes.LOGISTICS)
                    + " 钢材=" + data.getLevel(TechNodes.STEEL));
            return;
        }
        if (data.getLevel(TechNodes.MINING) != 1) {
            helper.fail("v2 老档已有采矿等级被改写：" + data.getLevel(TechNodes.MINING));
            return;
        }
        // BASIC_AUTOMATION 老档没写 → 迁移补齐
        if (data.getLevel(TechNodes.BASIC_AUTOMATION) < 1) {
            helper.fail("v2 老档缺失的基础自动化未补齐");
            return;
        }
        if (data.getSaveVersion() != TechTreeData.SAVE_VERSION) {
            helper.fail("v2 老档迁移后版本号应为 " + TechTreeData.SAVE_VERSION
                    + "，实际 " + data.getSaveVersion());
            return;
        }
        helper.succeed();
    }

    /** v3 当前档：原样读取，等级与当前研究完整保留。 */
    @GameTest(template = "pole_flat", batch = "saveMigration", timeoutTicks = 100)
    public static void v3SaveRoundTripsFaithfully(GameTestHelper helper) {
        // 先用真实对象造一份 v3 存档
        TechTreeData source = new TechTreeData();
        source.setLevelForTest(TechNodes.ELECTRIC_POWER, 1);
        source.setLevelForTest(TechNodes.AUTOMATION, 1);
        CompoundTag tag = source.save(new CompoundTag(), helper.getLevel().registryAccess());

        TechTreeData loaded = TechTreeData.load(tag, helper.getLevel().registryAccess());

        if (loaded.getLevel(TechNodes.ELECTRIC_POWER) != 1
                || loaded.getLevel(TechNodes.AUTOMATION) != 1) {
            helper.fail("v3 存档往返后科技等级丢失：电力="
                    + loaded.getLevel(TechNodes.ELECTRIC_POWER)
                    + " 组装=" + loaded.getLevel(TechNodes.AUTOMATION));
            return;
        }
        // 开局解锁集也应在（新建对象自带）
        if (loaded.getLevel(TechNodes.MINING) < 1
                || loaded.getLevel(TechNodes.BASIC_AUTOMATION) < 1) {
            helper.fail("v3 存档往返后开局解锁集丢失");
            return;
        }
        if (loaded.getSaveVersion() != TechTreeData.SAVE_VERSION) {
            helper.fail("v3 存档版本号异常：" + loaded.getSaveVersion());
            return;
        }
        helper.succeed();
    }

    private SaveMigrationGameTests() {}
}
