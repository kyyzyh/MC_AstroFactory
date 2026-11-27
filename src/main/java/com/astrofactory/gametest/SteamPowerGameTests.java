package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.blockentity.BoilerBlockEntity;
import com.astrofactory.blockentity.SteamEngineBlockEntity;
import com.astrofactory.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * US-101 GameTest：锅炉 + 蒸汽机发电对。
 *
 * <p>覆盖清单验收里可自动化的三项：</p>
 * <ol>
 *   <li>相邻摆放喂煤 → 产电，电量落在预期区间；</li>
 *   <li>非相邻摆放 → 不发电；</li>
 *   <li>电缓冲满 → 蒸汽机不再吃蒸汽（不浪费）。</li>
 * </ol>
 *
 * <p>场景用 {@code steam_flat}（9x5x9 石头平地）作底，方块由测试自己
 * {@code setBlock} 放上去——比在 NBT 里预埋方块更可控，也避免结构文件
 * 版本兼容问题。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class SteamPowerGameTests {

    /** 相邻：锅炉与蒸汽机紧贴（相对坐标，y=1 站在地面 stone 上）。 */
    private static final BlockPos BOILER_NEAR = new BlockPos(2, 1, 2);
    private static final BlockPos ENGINE_NEAR = new BlockPos(3, 1, 2);

    /** 非相邻：蒸汽机放到 6 格开外。 */
    private static final BlockPos BOILER_FAR = new BlockPos(1, 1, 1);
    private static final BlockPos ENGINE_FAR = new BlockPos(7, 1, 7);

    /**
     * 相邻摆放喂 1 块煤：断言蒸汽机产生电量。
     *
     * <p>1 煤 = 1600 tick 燃烧；蒸汽机受发电上限（40 FE/t ÷ 20 FE/蒸汽 = 2 蒸汽/t）
     * 约束，每秒约 40 FE。给足时间后累计电量应明显 &gt; 0 且达到合理量级。</p>
     */
    @GameTest(template = "steam_flat", timeoutTicks = 500)
    public static void adjacentBoilerGeneratesPower(GameTestHelper helper) {
        helper.setBlock(BOILER_NEAR, ModBlocks.BOILER.get());
        helper.setBlock(ENGINE_NEAR, ModBlocks.STEAM_ENGINE.get());

        BoilerBlockEntity boiler = helper.getBlockEntity(BOILER_NEAR);
        SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE_NEAR);
        helper.assertTrue(boiler != null, "锅炉方块实体未创建");
        helper.assertTrue(engine != null, "蒸汽机方块实体未创建");

        // 注满水（不依赖抽水机，直接喂内部缓冲）
        boiler.receiveWater(BoilerBlockEntity.fluidCapacity());
        helper.assertTrue(boiler.getWater() > 0, "锅炉未进水");
        // 投 1 块煤
        helper.assertTrue(boiler.tryInsertFuel(new ItemStack(Items.COAL, 1)),
                "锅炉拒绝投煤，燃料表或投料逻辑异常");

        // 等待若干秒后断言发电量
        helper.succeedWhen(() -> {
            int generated = engine.getGeneratedTotal();
            helper.assertTrue(generated > 0, "蒸汽机未发电（累计 " + generated + " FE）");
            helper.assertTrue(generated >= 200,
                    "发电量偏低（累计 " + generated + " FE，预期 ≥200）");
        });
    }

    /**
     * 非相邻摆放：锅炉烧煤产汽，但蒸汽机拿不到蒸汽，不发电。
     */
    @GameTest(template = "steam_flat", timeoutTicks = 300)
    public static void separatedBoilerDoesNotGenerate(GameTestHelper helper) {
        helper.setBlock(BOILER_FAR, ModBlocks.BOILER.get());
        helper.setBlock(ENGINE_FAR, ModBlocks.STEAM_ENGINE.get());

        BoilerBlockEntity boiler = helper.getBlockEntity(BOILER_FAR);
        SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE_FAR);
        helper.assertTrue(boiler != null && engine != null, "方块实体未创建");

        boiler.receiveWater(BoilerBlockEntity.fluidCapacity());
        boiler.tryInsertFuel(new ItemStack(Items.COAL, 1));

        helper.runAfterDelay(150, () -> {
            int generated = engine.getGeneratedTotal();
            helper.assertTrue(generated == 0,
                    "非相邻蒸汽机不应发电，实际 " + generated + " FE");
            // 锅炉自己应在燃烧，证明「不发电」不是因为锅炉没工作
            helper.assertTrue(boiler.isBurning(), "锅炉应在燃烧（非相邻测试前提）");
            helper.succeed();
        });
    }

    /**
     * 电缓冲满 → 蒸汽机不再吃蒸汽（不浪费），蒸汽留在锅炉里。
     */
    @GameTest(template = "steam_flat", timeoutTicks = 300)
    public static void fullBufferStopsSteamConsumption(GameTestHelper helper) {
        helper.setBlock(BOILER_NEAR, ModBlocks.BOILER.get());
        helper.setBlock(ENGINE_NEAR, ModBlocks.STEAM_ENGINE.get());

        BoilerBlockEntity boiler = helper.getBlockEntity(BOILER_NEAR);
        SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE_NEAR);
        helper.assertTrue(boiler != null && engine != null, "方块实体未创建");

        // 先把蒸汽机缓冲灌满
        for (int i = 0; i < 500 && engine.getEnergyStored() < engine.getEnergyCapacity(); i++) {
            engine.receiveSteam(64);
        }
        helper.assertTrue(engine.getEnergyStored() >= engine.getEnergyCapacity(),
                "蒸汽机缓冲未满（" + engine.getEnergyStored() + "/" + engine.getEnergyCapacity() + "）");

        int steamBefore = engine.getSteamConsumed();

        // 锅炉持续产汽
        boiler.receiveWater(BoilerBlockEntity.fluidCapacity());
        boiler.tryInsertFuel(new ItemStack(Items.COAL, 1));

        helper.runAfterDelay(100, () -> {
            int steamAfter = engine.getSteamConsumed();
            helper.assertTrue(steamAfter == steamBefore,
                    "缓冲满时蒸汽机仍在耗汽（" + steamBefore + " → " + steamAfter + "），浪费蒸汽");
            helper.assertTrue(boiler.getSteam() > 0,
                    "锅炉蒸汽未积压，说明蒸汽凭空消失");
            helper.succeed();
        });
    }
}
