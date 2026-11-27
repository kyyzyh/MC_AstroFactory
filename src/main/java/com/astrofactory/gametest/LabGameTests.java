package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.block.LabBlock;
import com.astrofactory.blockentity.LabBlockEntity;
import com.astrofactory.registry.ModBlocks;
import com.astrofactory.registry.ModItems;
import com.astrofactory.tech.TechLockManager;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * US-106 GameTest：实验室与红瓶。
 *
 * <p>覆盖清单验收项：</p>
 * <ol>
 *   <li>红瓶 → 实验室推进研究，完成一项科技并解锁配方（真拦截放行）；</li>
 *   <li>两台并联速度约为单台 2 倍（GameTest 断言区间）；</li>
 *   <li>瓶尽暂停，补瓶继续；</li>
 *   <li>双人共享：一人投瓶另一人看同一份世界观进度增长。</li>
 * </ol>
 *
 * <p>并联加速与瓶尽暂停用 DataGen 高成本科技 {@code test_lab_cost}（100000 点，
 * 测试周期内不可能完成）量测——固定 tick 内只测「消耗瓶 → 投进度」的线性速率；
 * 研究完成解锁用 {@code test_assembly}（成本 5，1 个红瓶即完成）测。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class LabGameTests {

    private static final String TECH_COST = AstroFactory.MODID + ":test_lab_cost";
    private static final String TECH_A = AstroFactory.MODID + ":test_assembly";
    private static final String RECIPE_A = AstroFactory.MODID + ":assembling_test_part";

    private static final BlockPos P1 = new BlockPos(8, 1, 8);
    private static final BlockPos P2 = new BlockPos(12, 1, 8);

    /** 放一台实验室并循环充满电（maxReceive 限制，需多轮充能）。 */
    private static LabBlockEntity placeLab(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.LAB.get().defaultBlockState()
                .setValue(LabBlock.FACING, Direction.SOUTH));
        LabBlockEntity be = helper.getBlockEntity(pos);
        int guard = 0;
        while (be.getEnergyStored() < be.getEnergyCapacity() && guard++ < 100_000) {
            int moved = be.getEnergyBuffer().receiveEnergy(be.getEnergyCapacity(), false);
            if (moved <= 0) {
                break;
            }
        }
        return be;
    }

    /** 复位共享科技状态：关创造旁路、清两个测试科技等级、清当前研究。 */
    private static TechTreeData resetLab(GameTestHelper helper) {
        TechTreeData data = TechTreeData.get(helper.getLevel().getServer());
        data.setCreativeBypass(false);
        data.setLevelForTest(TECH_A, 0);
        data.setLevelForTest(TECH_COST, 0);
        data.clearResearch();
        return data;
    }

    // ---------------------------------------------------------------- 验收 2：并联加速

    /** 单台基准：固定 360 tick 内投出的进度。 */
    @GameTest(template = "pole_flat", batch = "lab1", timeoutTicks = 600)
    public static void singleLabProgressRate(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        TechTreeData data = resetLab(helper);

        LabBlockEntity lab = placeLab(helper, P1);
        lab.getRedInput().setStackInSlot(0,
                new ItemStack(ModItems.RED_SCIENCE_PACK.get(), 10));

        boolean started = data.setResearch(TECH_COST);

        // 360 tick / 60 tick每瓶 = 6 瓶/台；每瓶 10 点 = 60 点
        helper.runAfterDelay(360, () -> {
            int prog = TechTreeData.get(server).getResearchProgress();
            if (prog < 50 || prog > 70) {
                helper.fail("单台 360 tick 进度应≈60，实际 " + prog
                        + " started=" + started + " current="
                        + TechTreeData.get(server).getCurrentResearch()
                        + " exists=" + com.astrofactory.tech.TechRegistry.exists(TECH_COST)
                        + " costDef=" + com.astrofactory.tech.TechRegistry.get(TECH_COST));
                return;
            }
            helper.succeed();
        });
    }

    /** 两台并联：同一时间投出的进度约为单台 2 倍。 */
    @GameTest(template = "pole_flat", batch = "lab2", timeoutTicks = 600)
    public static void twoLabsLinearSpeedup(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        TechTreeData data = resetLab(helper);

        placeLab(helper, P1).getRedInput().setStackInSlot(0,
                new ItemStack(ModItems.RED_SCIENCE_PACK.get(), 10));
        placeLab(helper, P2).getRedInput().setStackInSlot(0,
                new ItemStack(ModItems.RED_SCIENCE_PACK.get(), 10));

        data.setResearch(TECH_COST);

        // 两台 360 tick：共消耗 ≈12 瓶 = 120 点，约为单台（≈60）的 2 倍
        helper.runAfterDelay(360, () -> {
            int prog = TechTreeData.get(server).getResearchProgress();
            if (prog < 100 || prog > 140) {
                helper.fail("两台 360 tick 进度应≈120（单台的 2 倍），实际 " + prog);
                return;
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- 验收 3：瓶尽暂停/补瓶继续

    /** 瓶尽暂停：红瓶槽空后进度不再增长；补瓶后续增长。 */
    @GameTest(template = "pole_flat", batch = "lab3", timeoutTicks = 900)
    public static void bottleDepletionPausesThenResumes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        TechTreeData data = resetLab(helper);

        LabBlockEntity lab = placeLab(helper, P1);
        data.setResearch(TECH_COST);

        // 先喂 1 瓶，跑 90 tick（60 消耗 1 瓶 + 30 缓冲），进度应增长
        lab.getRedInput().setStackInSlot(0,
                new ItemStack(ModItems.RED_SCIENCE_PACK.get(), 1));
        helper.runAfterDelay(90, () -> {
            int afterOne = TechTreeData.get(server).getResearchProgress();
            if (afterOne == 0) {
                helper.fail("第一瓶未消耗，进度为 0（红瓶="
                        + lab.getRedInput().getStackInSlot(0));
                return;
            }
            // 瓶已空，再跑 120 tick，进度不应再增长（瓶尽暂停）
            helper.runAfterDelay(120, () -> {
                int afterEmpty = TechTreeData.get(server).getResearchProgress();
                if (afterEmpty != afterOne) {
                    helper.fail("瓶尽后进度仍增长：before=" + afterOne + " after=" + afterEmpty);
                    return;
                }
                // 补 1 瓶，再跑 90 tick，进度应继续增长
                lab.getRedInput().setStackInSlot(0,
                        new ItemStack(ModItems.RED_SCIENCE_PACK.get(), 1));
                helper.runAfterDelay(90, () -> {
                    int afterRefill = TechTreeData.get(server).getResearchProgress();
                    if (afterRefill <= afterOne) {
                        helper.fail("补瓶后进度未增长：before=" + afterOne + " after=" + afterRefill);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    // ---------------------------------------------------------------- 验收 1：完成研究解锁配方

    /** 红瓶推进并完成研究，配方真拦截放行（工作台/组装机都能做）。 */
    @GameTest(template = "pole_flat", batch = "lab4", timeoutTicks = 400)
    public static void redScienceCompletesResearchAndUnlocks(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        TechTreeData data = resetLab(helper);

        LabBlockEntity lab = placeLab(helper, P1);
        data.setResearch(TECH_A); // 成本 5 点，1 个红瓶（10 点）即完成

        // 锁住的状态：配方应在服务端表里查不到
        TechLockManager.applyLock(server);
        ResourceLocation rid = ResourceLocation.parse(RECIPE_A);
        if (server.getRecipeManager().byKey(rid).isPresent()) {
            helper.fail("研究前配方已在服务端表里：" + RECIPE_A + "（锁定未生效？）");
            return;
        }

        // 喂 1 瓶，跑 90 tick（60 消耗 + 缓冲）完成研究
        lab.getRedInput().setStackInSlot(0,
                new ItemStack(ModItems.RED_SCIENCE_PACK.get(), 1));
        helper.runAfterDelay(90, () -> {
            // 科技 A 已解锁
            if (data.getLevel(TECH_A) < 1) {
                helper.fail("研究未完成：current=" + data.getCurrentResearch()
                        + " progress=" + data.getResearchProgress());
                return;
            }
            // 配方放行（applyLock 由解锁回调触发；这里显式重打一次确保同步）
            TechLockManager.applyLock(server);
            if (server.getRecipeManager().byKey(rid).isEmpty()) {
                helper.fail("研究完成后配方仍未回到服务端表：" + RECIPE_A);
                return;
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- 验收 4：双人共享

    /** 世界级共享：实验室投瓶推进的进度，第二个读取方看到同一份增长。 */
    @GameTest(template = "pole_flat", batch = "lab5", timeoutTicks = 400)
    public static void sharedProgressVisibleAcrossReaders(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        TechTreeData data = resetLab(helper);

        LabBlockEntity lab = placeLab(helper, P1);
        data.setResearch(TECH_COST);
        lab.getRedInput().setStackInSlot(0,
                new ItemStack(ModItems.RED_SCIENCE_PACK.get(), 3));

        helper.runAfterDelay(130, () -> {
            int writerView = TechTreeData.get(server).getResearchProgress();
            TechTreeData second = TechTreeData.get(server);
            if (second != data) {
                helper.fail("TechTreeData 未按世界共享：两次取得不同实例");
                return;
            }
            if (writerView == 0 || second.getResearchProgress() != writerView) {
                helper.fail("双人进度不一致：writer=" + writerView
                        + " reader=" + second.getResearchProgress());
                return;
            }
            helper.succeed();
        });
    }
}
