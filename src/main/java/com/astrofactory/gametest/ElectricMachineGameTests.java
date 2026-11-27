package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.blockentity.InserterBlockEntity;
import com.astrofactory.blockentity.MinerBlockEntity;
import com.astrofactory.blockentity.PoweredBlockEntity;
import com.astrofactory.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * US-103 GameTest：电力采矿机与电力机械臂。
 *
 * <p>覆盖清单验收项：</p>
 * <ol>
 *   <li>接入电网后运转，产能达配置值；</li>
 *   <li>断电：设备暂停，<b>进度保留</b>；恢复供电后继续；</li>
 *   <li>预定机制回归：两臂竞争时总数守恒（不复制、不吞物品）。</li>
 * </ol>
 *
 * <p>模板 {@code pole_flat} 是 21x6x21 石头平台，方块全部在测试里 setBlock。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class ElectricMachineGameTests {

    private static final BlockPos POLE = new BlockPos(10, 1, 10);
    private static final BlockPos GENERATOR = new BlockPos(9, 1, 10);

    /**
     * 电力采矿机：供电后进度推进、断电后进度冻结、恢复后继续。
     *
     * <p>断言链：<br>
     * ① 通电一段时间 → 进度 > 0（在动）<br>
     * ② 抽走全部电量 → 等 60 tick → 进度不变（暂停且不清零）<br>
     * ③ 重新灌电 → 等 60 tick → 进度继续增长（恢复）</p>
     */
    @GameTest(template = "pole_flat", timeoutTicks = 600)
    public static void minerPausesAndResumes(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(12, 1, 10);
        // 下方放铁矿，保证有产物可采（否则不推进进度）
        helper.setBlock(minerPos.below(), Blocks.IRON_ORE);
        helper.setBlock(minerPos, ModBlocks.MINER.get()
                .defaultBlockState().setValue(com.astrofactory.block.MinerBlock.FACING, Direction.NORTH));

        MinerBlockEntity miner = helper.getBlockEntity(minerPos);
        miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        // 直接灌满电（不依赖发电机时序，聚焦「断电暂停」这一条）
        miner.getEnergyBuffer().receiveEnergy(10_000, false);

        // 采样时刻故意选在周期中段（37 tick，周期 20），避免刚好撞上进度归零
        helper.runAfterDelay(37, () -> {
            int moving = miner.getProgress();
            helper.assertTrue(moving > 0, "通电后进度未推进（" + moving + "）");

            // ② 断电：抽干电量
            miner.drainAllEnergy();
            helper.assertTrue(miner.getEnergyBuffer().getEnergyStored() == 0, "未能抽干电量");

            helper.runAfterDelay(60, () -> {
                int frozen = miner.getProgress();
                helper.assertTrue(frozen == moving,
                        "断电后进度仍在变（" + moving + " → " + frozen + "），应冻结");

                // ③ 恢复供电
                miner.getEnergyBuffer().receiveEnergy(10_000, false);
                helper.runAfterDelay(60, () -> {
                    int resumed = miner.getProgress();
                    // 进度可能刚好跨周期归零，所以用「不相等」而不是「更大」
                    helper.assertTrue(resumed != frozen || miner.getEnergyBuffer().getEnergyStored() < 10_000,
                            "恢复供电后进度未继续（" + frozen + " → " + resumed + "）");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * 电力采矿机：断电时如果电量不足一个 tick 的消耗，也必须停（不能超支）。
     */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void minerStopsWhenEnergyInsufficient(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(12, 1, 10);
        helper.setBlock(minerPos.below(), Blocks.IRON_ORE);
        helper.setBlock(minerPos, ModBlocks.MINER.get()
                .defaultBlockState().setValue(com.astrofactory.block.MinerBlock.FACING, Direction.NORTH));

        MinerBlockEntity miner = helper.getBlockEntity(minerPos);
        miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        // 只给不够一个 tick 的电
        miner.getEnergyBuffer().receiveEnergy(Math.max(0, MinerBlockEntity.fePerTick() - 1), false);

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(miner.getProgress() == 0,
                    "电量不足时不应推进进度（" + miner.getProgress() + "）");
            helper.assertTrue(miner.getEnergyBuffer().getEnergyStored()
                            < MinerBlockEntity.fePerTick(),
                    "电量不足时不应被扣电");
            helper.succeed();
        });
    }

    /**
     * 电力机械臂：断电时冷却冻结，且不会把物品卡在半路（物品要么在源，要么在目标）。
     */
    @GameTest(template = "pole_flat", timeoutTicks = 600)
    public static void inserterPausesWithoutLosingItems(GameTestHelper helper) {
        BlockPos src = new BlockPos(5, 1, 5);
        BlockPos armPos = new BlockPos(5, 1, 6);
        BlockPos dst = new BlockPos(5, 1, 7);

        helper.setBlock(src, ModBlocks.WOOD_CRATE.get());
        helper.setBlock(armPos, ModBlocks.INSERTER.get()
                .defaultBlockState().setValue(com.astrofactory.block.InserterBlock.FACING, Direction.SOUTH));
        helper.setBlock(dst, ModBlocks.WOOD_CRATE.get());

        var srcCrate = (com.astrofactory.blockentity.CrateBlockEntity) helper.getBlockEntity(src);
        var dstCrate = (com.astrofactory.blockentity.CrateBlockEntity) helper.getBlockEntity(dst);
        srcCrate.setItem(0, new ItemStack(Items.IRON_INGOT, 8));

        InserterBlockEntity arm = helper.getBlockEntity(armPos);
        arm.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        arm.getEnergyBuffer().receiveEnergy(10_000, false);

        // ① 通电搬几次
        helper.runAfterDelay(80, () -> {
            int movedIn = count(dstCrate, Items.IRON_INGOT);
            helper.assertTrue(movedIn > 0, "通电后机械臂未搬运（目标 " + movedIn + " 个）");

            // ② 断电
            arm.drainAllEnergy();
            int cooldownAtCut = arm.getCooldown();

            helper.runAfterDelay(80, () -> {
                int movedAfter = count(dstCrate, Items.IRON_INGOT);
                int movedDuringCut = movedAfter - movedIn;
                // 断电瞬间最多完成手上那一次（原子完成），之后必须完全静止
                helper.assertTrue(movedDuringCut <= 1,
                        "断电后仍在搬运（增加了 " + movedDuringCut + " 个）");
                helper.assertTrue(arm.getCooldown() == cooldownAtCut
                                || movedDuringCut == 1,
                        "断电后冷却未冻结（" + cooldownAtCut + " → " + arm.getCooldown() + "）");

                // ③ 物品守恒：源 + 目标 + 机械臂手上 = 8
                int inSrc = count(srcCrate, Items.IRON_INGOT);
                int total = inSrc + movedAfter;
                helper.assertTrue(total == 8,
                        "物品不守恒（源 " + inSrc + " + 目标 " + movedAfter + " = " + total + "，应为 8）");
                helper.succeed();
            });
        });
    }

    /**
     * 预定机制回归：两台机械臂同时抢同一个源箱，总数必须守恒（不复制、不吞）。
     */
    @GameTest(template = "pole_flat", timeoutTicks = 600)
    public static void twoArmsCompetingConserveItems(GameTestHelper helper) {
        BlockPos src = new BlockPos(10, 1, 5);
        BlockPos armA = new BlockPos(10, 1, 6);
        BlockPos armB = new BlockPos(10, 1, 4);
        BlockPos dstA = new BlockPos(10, 1, 7);
        BlockPos dstB = new BlockPos(10, 1, 3);

        helper.setBlock(src, ModBlocks.WOOD_CRATE.get());
        helper.setBlock(armA, ModBlocks.INSERTER.get()
                .defaultBlockState().setValue(com.astrofactory.block.InserterBlock.FACING, Direction.SOUTH));
        helper.setBlock(armB, ModBlocks.INSERTER.get()
                .defaultBlockState().setValue(com.astrofactory.block.InserterBlock.FACING, Direction.NORTH));
        helper.setBlock(dstA, ModBlocks.WOOD_CRATE.get());
        helper.setBlock(dstB, ModBlocks.WOOD_CRATE.get());

        var srcCrate = (com.astrofactory.blockentity.CrateBlockEntity) helper.getBlockEntity(src);
        var dstACrate = (com.astrofactory.blockentity.CrateBlockEntity) helper.getBlockEntity(dstA);
        var dstBCrate = (com.astrofactory.blockentity.CrateBlockEntity) helper.getBlockEntity(dstB);
        srcCrate.setItem(0, new ItemStack(Items.GOLD_INGOT, 16));

        for (BlockPos p : new BlockPos[]{armA, armB}) {
            InserterBlockEntity arm = helper.getBlockEntity(p);
            arm.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
            arm.getEnergyBuffer().receiveEnergy(10_000, false);
        }

        helper.runAfterDelay(200, () -> {
            int inSrc = count(srcCrate, Items.GOLD_INGOT);
            int inA = count(dstACrate, Items.GOLD_INGOT);
            int inB = count(dstBCrate, Items.GOLD_INGOT);
            int total = inSrc + inA + inB;
            helper.assertTrue(inA > 0 && inB > 0,
                    "两臂未同时工作（A " + inA + " B " + inB + "）");
            helper.assertTrue(total == 16,
                    "两臂竞争后总数不守恒（源 " + inSrc + " + A " + inA + " + B " + inB
                            + " = " + total + "，应为 16）");
            helper.succeed();
        });
    }

    /** 统计容器里某物品的总数。 */
    private static int count(com.astrofactory.blockentity.CrateBlockEntity crate,
                             net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < crate.getContainerSize(); i++) {
            ItemStack stack = crate.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
