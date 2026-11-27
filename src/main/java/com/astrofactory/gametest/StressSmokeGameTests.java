package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.blockentity.GeneratorBlockEntity;
import com.astrofactory.blockentity.PoweredBlockEntity;
import com.astrofactory.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * US-109 GameTest：规模化压力冒烟（GameTest 模板容量内可自动化部分）。
 *
 * <p>完整压力场景（200 格带 + 20 台设备 + 电网 + 3 台实验室 + spark 报告）
 * 需要真客户端长时间运行，超出单模板能力，列为人工项。这里做可自动化部分：
 * 一根电线杆 + 一台发电机 + 一批电动熔炉，连续运行 240 tick，断言
 * 不崩、全部设备持续得电运转——验证电网聚合在多设备下的稳定性。</p>
 *
 * <p>刻意不碰 TechTreeData 的全局研究状态：GameTest 各 batch 并行，
 * 改写共享 currentResearch 会污染 LabGameTests 的瓶尽/并联断言（US-106 已踩过的坑）。
 * 本用例只依赖电网供电，与科技进度无关。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class StressSmokeGameTests {

    @GameTest(template = "pole_flat", batch = "stress", timeoutTicks = 800)
    public static void multiMachineGridStaysStable(GameTestHelper helper) {
        BlockPos pole = new BlockPos(10, 1, 10);
        BlockPos gen = new BlockPos(9, 1, 10);
        helper.setBlock(pole, ModBlocks.POWER_POLE.get());
        helper.setBlock(gen, ModBlocks.GENERATOR.get());
        if (helper.getBlockEntity(gen) instanceof GeneratorBlockEntity g) {
            g.getFuelSlot().insertItem(0, new ItemStack(Items.COAL, 32), false);
        } else {
            helper.fail("压力场景发电机未创建");
            return;
        }

        int placed = 0;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 4) {
                BlockPos p = new BlockPos(10 + dx, 1, 10 + dz);
                if (p.equals(pole) || p.equals(gen)) {
                    continue;
                }
                helper.setBlock(p, ModBlocks.SMELTER.get());
                if (helper.getBlockEntity(p) instanceof PoweredBlockEntity be) {
                    be.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
                    placed++;
                }
            }
        }
        if (placed < 8) {
            helper.fail("压力场景设备放置不足，仅 " + placed + " 台（期望 >= 8）");
            return;
        }

        final int expected = placed;
        helper.runAfterDelay(240, () -> {
            int powered = 0;
            for (int dx = -4; dx <= 4; dx += 2) {
                for (int dz = -4; dz <= 4; dz += 4) {
                    BlockPos p = new BlockPos(10 + dx, 1, 10 + dz);
                    if (helper.getBlockEntity(p) instanceof PoweredBlockEntity be
                            && be.getEnergyStored() > 0) {
                        powered++;
                    }
                }
            }
            if (powered < expected) {
                helper.fail("多设备电网供电不稳定：" + powered + " / " + expected + " 台得电");
                return;
            }
            helper.succeed();
        });
    }

    private StressSmokeGameTests() {}
}
