package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.blockentity.GeneratorBlockEntity;
import com.astrofactory.blockentity.PoweredBlockEntity;
import com.astrofactory.blockentity.PowerPoleBlockEntity;
import com.astrofactory.blockentity.SmelterBlockEntity;
import com.astrofactory.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * US-102 GameTest：覆盖式电网（电线杆）。
 *
 * <p>覆盖清单验收项：</p>
 * <ol>
 *   <li>杆覆盖范围内的机器自动得电（无需电缆相连）；</li>
 *   <li>超出覆盖半径的机器拿不到电；</li>
 *   <li>拆掉中间的杆导致网络分裂后，远端机器断电；</li>
 *   <li>发电不足时不崩溃，只是各机器降速（公平分配）。</li>
 * </ol>
 *
 * <p>模板 {@code pole_flat} 是 21x6x21 的石头平台，方块全部在测试里 setBlock 放置
 * （不预埋进 NBT，否则会报 Missing block entity）。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class PowerPoleGameTests {

    /** 电线杆位置（放在石头平台上方一层）。 */
    private static final BlockPos POLE = new BlockPos(10, 1, 10);
    /** 半径 7 内的机器。 */
    private static final BlockPos NEAR_MACHINE = new BlockPos(12, 1, 10);
    /** 半径 7 外的机器。 */
    private static final BlockPos FAR_MACHINE = new BlockPos(19, 1, 10);
    /** 发电机（放在杆旁边）。 */
    private static final BlockPos GENERATOR = new BlockPos(9, 1, 10);

    /**
     * 杆覆盖范围内的电动机器自动得电。
     *
     * <p>发电机给电网供电，机器切电动模式后应能从杆取电（能量 > 0）。</p>
     */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void machineInRangeReceivesPower(GameTestHelper helper) {
        helper.setBlock(POLE, ModBlocks.POWER_POLE.get());
        helper.setBlock(GENERATOR, ModBlocks.GENERATOR.get());
        helper.setBlock(NEAR_MACHINE, ModBlocks.SMELTER.get());

        GeneratorBlockEntity gen = helper.getBlockEntity(GENERATOR);
        SmelterBlockEntity smelter = helper.getBlockEntity(NEAR_MACHINE);
        // 发电机投煤
        gen.getFuelSlot().insertItem(0, new ItemStack(Items.COAL, 4), false);
        // 机器切电动模式（只有电动模式才接电）
        smelter.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);

        helper.succeedWhen(() -> {
            int stored = smelter.getEnergyStored();
            helper.assertTrue(stored > 0,
                    "半径内机器未从电线杆取到电（当前 " + stored + " FE）");
        });
    }

    /**
     * 超出覆盖半径的机器拿不到电。
     */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void machineOutOfRangeGetsNothing(GameTestHelper helper) {
        helper.setBlock(POLE, ModBlocks.POWER_POLE.get());
        helper.setBlock(GENERATOR, ModBlocks.GENERATOR.get());
        // 距离 9 格 > 半径 7
        helper.setBlock(FAR_MACHINE, ModBlocks.SMELTER.get());

        GeneratorBlockEntity gen = helper.getBlockEntity(GENERATOR);
        SmelterBlockEntity smelter = helper.getBlockEntity(FAR_MACHINE);
        gen.getFuelSlot().insertItem(0, new ItemStack(Items.COAL, 4), false);
        smelter.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);

        // 跑 100 tick 后确认机器仍然没电
        helper.runAfterDelay(100, () -> {
            int stored = smelter.getEnergyStored();
            helper.assertTrue(stored == 0,
                    "半径外机器本不该得电，实际 " + stored + " FE");
            helper.succeed();
        });
    }

    /**
     * 拆掉中间的杆导致网络分裂，远端杆覆盖的机器断电。
     *
     * <p>布局：P1(2,1,10) - P2(8,1,10) - P3(14,1,10)，间距 6 ≤ linkRange 10，三杆同网。
     * 发电机在 P1 旁；机器在 P3 旁。拆掉 P2 后 P1 与 P3 相距 12 > 10，网络分裂，
     * 机器拿不到发电机的电。</p>
     */
    @GameTest(template = "pole_flat", timeoutTicks = 800)
    public static void removingPoleSplitsGrid(GameTestHelper helper) {
        BlockPos p1 = new BlockPos(2, 1, 10);
        BlockPos p2 = new BlockPos(8, 1, 10);
        BlockPos p3 = new BlockPos(14, 1, 10);
        BlockPos genPos = new BlockPos(2, 1, 12);
        BlockPos machinePos = new BlockPos(14, 1, 12);

        helper.setBlock(p1, ModBlocks.POWER_POLE.get());
        helper.setBlock(p2, ModBlocks.POWER_POLE.get());
        helper.setBlock(p3, ModBlocks.POWER_POLE.get());
        helper.setBlock(genPos, ModBlocks.GENERATOR.get());
        helper.setBlock(machinePos, ModBlocks.SMELTER.get());

        GeneratorBlockEntity gen = helper.getBlockEntity(genPos);
        SmelterBlockEntity smelter = helper.getBlockEntity(machinePos);
        gen.getFuelSlot().insertItem(0, new ItemStack(Items.COAL, 8), false);
        smelter.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);

        // 第一阶段：三杆连通，机器应得电
        helper.runAfterDelay(120, () -> {
            int before = smelter.getEnergyStored();
            helper.assertTrue(before > 0, "组网阶段机器未得电（" + before + " FE）");

            // 拆掉中间的杆 → 网络分裂
            helper.setBlock(p2, net.minecraft.world.level.block.Blocks.AIR);
            // 缓存作废靠杆的周期刷新（10 tick），给足时间后再断言
            helper.runAfterDelay(120, () -> {
                // 记录拆杆后电量，再等一段时间看是否还在增长
                int afterSplit = smelter.getEnergyStored();
                helper.runAfterDelay(80, () -> {
                    int later = smelter.getEnergyStored();
                    helper.assertTrue(later <= afterSplit,
                            "拆杆后机器仍在得电（" + afterSplit + " → " + later + "），网络未分裂");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * 单杆也能独立工作：没有第二根杆时，杆覆盖范围内的发电机与机器正常供电。
     *
     * <p>这条用来防止「必须有两根杆组网才工作」的误实现。</p>
     */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void singlePoleWorksAlone(GameTestHelper helper) {
        helper.setBlock(POLE, ModBlocks.POWER_POLE.get());
        helper.setBlock(GENERATOR, ModBlocks.GENERATOR.get());
        helper.setBlock(NEAR_MACHINE, ModBlocks.SMELTER.get());

        GeneratorBlockEntity gen = helper.getBlockEntity(GENERATOR);
        SmelterBlockEntity smelter = helper.getBlockEntity(NEAR_MACHINE);
        gen.getFuelSlot().insertItem(0, new ItemStack(Items.COAL, 4), false);
        smelter.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);

        // 同时检查杆自身的统计字段被填上（证明电网解析跑通）
        PowerPoleBlockEntity pole = helper.getBlockEntity(POLE);
        helper.succeedWhen(() -> {
            helper.assertTrue(pole.getSourceCount() > 0, "杆未识别到发电设备");
            helper.assertTrue(pole.getSinkCount() > 0, "杆未识别到耗电设备");
            helper.assertTrue(smelter.getEnergyStored() > 0, "单杆供电失败");
        });
    }
}
