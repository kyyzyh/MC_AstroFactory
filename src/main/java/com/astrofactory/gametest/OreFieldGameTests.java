package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.block.MinerBlock;
import com.astrofactory.blockentity.MinerBlockEntity;
import com.astrofactory.blockentity.OreFieldBlockEntity;
import com.astrofactory.blockentity.PoweredBlockEntity;
import com.astrofactory.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * v0.8.9 GameTest：四种矿脉（石/铜/铁/煤）+ 采矿机放置限制 + 按矿种产出。
 *
 * <p>覆盖验收项：</p>
 * <ol>
 *   <li>四种矿脉（石矿脉/铜矿脉/铁矿脉/煤矿脉）都被判定为可采支撑，泥土/石头/草不行；</li>
 *   <li>采矿机放在铁矿脉上 → 进度推进、状态 working、产出为铁原矿；</li>
 *   <li>四种矿脉各自产出对应物品（石矿脉→石头，铜→铜原矿，铁→铁原矿，煤→煤）；</li>
 *   <li>信息面板在泥土上显示 no_ore。</li>
 * </ol>
 *
 * <p>模板 {@code pole_flat} 是 21x6x21 石头平台。setBlock 直接放矿机不触发
 * {@code canSurvive}（那是放置交互路径），故直接断言 {@link MinerBlock#isMineableGround}
 * 与各矿脉的 {@code FieldType#product()}。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class OreFieldGameTests {

    /** 四种矿脉都被认可为可采支撑；原版矿石可采；泥土/石头/草不可采。 */
    @GameTest(template = "pole_flat", timeoutTicks = 100)
    public static void mineableGroundDetection(GameTestHelper helper) {
        helper.assertTrue(MinerBlock.isMineableGround(ModBlocks.ORE_FIELD_STONE.get().defaultBlockState()),
                "石矿脉应被判定为可采支撑");
        helper.assertTrue(MinerBlock.isMineableGround(ModBlocks.ORE_FIELD_COPPER.get().defaultBlockState()),
                "铜矿脉应被判定为可采支撑");
        helper.assertTrue(MinerBlock.isMineableGround(ModBlocks.ORE_FIELD_IRON.get().defaultBlockState()),
                "铁矿脉应被判定为可采支撑");
        helper.assertTrue(MinerBlock.isMineableGround(ModBlocks.ORE_FIELD_COAL.get().defaultBlockState()),
                "煤矿脉应被判定为可采支撑");
        helper.assertTrue(MinerBlock.isMineableGround(Blocks.IRON_ORE.defaultBlockState()),
                "原版铁矿应被判定为可采支撑");
        helper.assertTrue(MinerBlock.isMineableGround(Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState()),
                "深层金矿应被判定为可采支撑");
        helper.assertFalse(MinerBlock.isMineableGround(Blocks.DIRT.defaultBlockState()),
                "泥土不应被判定为可采支撑");
        helper.assertFalse(MinerBlock.isMineableGround(Blocks.STONE.defaultBlockState()),
                "石头不应被判定为可采支撑");
        helper.assertFalse(MinerBlock.isMineableGround(Blocks.GRASS_BLOCK.defaultBlockState()),
                "草方块不应被判定为可采支撑");
        helper.succeed();
    }

    /** 每种矿脉产出对应物品（异星工厂风格：矿脉类型决定采出物）。 */
    @GameTest(template = "pole_flat", timeoutTicks = 100)
    public static void eachFieldTypeProducesCorrectOre(GameTestHelper helper) {
        assertProduct(helper, ModBlocks.ORE_FIELD_STONE, com.astrofactory.registry.ModItems.RAW_STONE.get());
        assertProduct(helper, ModBlocks.ORE_FIELD_COPPER, Items.RAW_COPPER);
        assertProduct(helper, ModBlocks.ORE_FIELD_IRON, Items.RAW_IRON);
        assertProduct(helper, ModBlocks.ORE_FIELD_COAL, Items.COAL);
        helper.succeed();
    }

    private static void assertProduct(GameTestHelper helper,
                                      net.neoforged.neoforge.registries.DeferredBlock<com.astrofactory.block.OreFieldBlock> field,
                                      net.minecraft.world.item.Item expect) {
        com.astrofactory.block.OreFieldBlock of = field.get();
        ItemStack product = of.product();
        helper.assertTrue(product.is(expect),
                "矿脉产出应为 " + expect + "，实际 " + product);
    }

    /** 采矿机放在铁矿脉上（电力+供电）：进度推进、状态 working、产出铁原矿。 */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void minerOnIronFieldProduces(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(12, 1, 10);
        helper.setBlock(minerPos.below(), ModBlocks.ORE_FIELD_IRON.get());
        helper.setBlock(minerPos, ModBlocks.MINER.get()
                .defaultBlockState().setValue(com.astrofactory.block.MinerBlock.FACING, Direction.NORTH));

        MinerBlockEntity miner = helper.getBlockEntity(minerPos);
        miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        fillEnergy(helper, miner);

        // 采样时刻选在周期中段（37 tick，周期 20），避免刚好撞上进度归零
        helper.runAfterDelay(37, () -> {
            helper.assertTrue(miner.getProgress() > 0,
                    "铁矿脉上采矿机进度未推进（" + miner.getProgress() + "）");

            String status = miner.infoStatusKey();
            helper.assertTrue("working".equals(status),
                    "铁矿脉上采矿机状态应为 working，实际 " + status);

            helper.assertTrue(miner.infoEnergy() > 0,
                    "信息面板应显示能量 > 0，实际 " + miner.infoEnergy());
            helper.assertTrue(miner.infoMaxProgress() > 0,
                    "信息面板应显示进度上限 > 0，实际 " + miner.infoMaxProgress());
            helper.assertTrue(miner.infoExtra() != null,
                    "铁矿脉上采矿机信息面板应有产出名称（铁原矿）");
            // infoExtra 应含铁原矿名
            helper.assertTrue(miner.infoExtra().contains(
                            net.minecraft.network.chat.Component.translatable(
                                    Items.RAW_IRON.getDescriptionId()).getString()),
                    "infoExtra 应显示铁原矿，实际 " + miner.infoExtra());
            helper.succeed();
        });
    }

    /** 信息面板状态位：放在泥土上（不合规支撑）时，无矿 → 状态 no_ore。 */
    @GameTest(template = "pole_flat", timeoutTicks = 300)
    public static void minerStatusNoOreOnDirt(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(12, 1, 10);
        helper.setBlock(minerPos.below(), Blocks.DIRT);
        helper.setBlock(minerPos, ModBlocks.MINER.get()
                .defaultBlockState().setValue(com.astrofactory.block.MinerBlock.FACING, Direction.NORTH));

        MinerBlockEntity miner = helper.getBlockEntity(minerPos);
        miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        fillEnergy(helper, miner);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue("no_ore".equals(miner.infoStatusKey()),
                    "下方泥土时状态应为 no_ore，实际 " + miner.infoStatusKey());
            helper.succeed();
        });
    }

    /** 矿脉格储量扣空后，非支撑格塌陷为空气（矿洞）。 */
    @GameTest(template = "pole_flat", timeoutTicks = 300)
    public static void fieldCollapsesWhenReserveDepleted(GameTestHelper helper) {
        BlockPos cellPos = new BlockPos(12, 1, 12);
        helper.setBlock(cellPos, ModBlocks.ORE_FIELD_IRON.get());
        OreFieldBlockEntity field = helper.getBlockEntity(cellPos);
        field.setReserve(2); // 小储量便于测试

        // 手动扣空并触发 serverTick 塌陷
        field.tryConsume(2);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(helper.getBlockState(cellPos).isAir(),
                    "储量扣空的矿脉格应塌陷为空气，实际 "
                            + helper.getBlockState(cellPos).getBlock());
            helper.succeed();
        });
    }

    /** 区块中一个矿脉格被采空塌陷，不影响相邻格。 */
    @GameTest(template = "pole_flat", timeoutTicks = 300)
    public static void collapseOnlyAffectsExhaustedCell(GameTestHelper helper) {
        BlockPos cellA = new BlockPos(12, 1, 11);
        BlockPos cellB = new BlockPos(12, 1, 13);
        helper.setBlock(cellA, ModBlocks.ORE_FIELD_IRON.get());
        helper.setBlock(cellB, ModBlocks.ORE_FIELD_IRON.get());
        OreFieldBlockEntity a = helper.getBlockEntity(cellA);
        OreFieldBlockEntity b = helper.getBlockEntity(cellB);
        a.setReserve(1);
        b.setReserve(10);
        a.tryConsume(1);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(helper.getBlockState(cellA).isAir(),
                    "耗尽的矿脉格应塌陷");
            helper.assertFalse(helper.getBlockState(cellB).isAir(),
                    "未耗尽的矿脉格不应塌陷");
            helper.succeed();
        });
    }

    /** 多次注入直到能量缓冲接近满（受 maxReceive 单次上限逐次累加）。 */
    private static void fillEnergy(GameTestHelper helper, MinerBlockEntity miner) {
        int goal = MinerBlockEntity.configEnergyCapacity();
        for (int i = 0; i < 200 && miner.getEnergyBuffer().getEnergyStored() < goal; i++) {
            miner.getEnergyBuffer().receiveEnergy(
                    MinerBlockEntity.configEnergyCapacity(), false);
        }
    }

    private OreFieldGameTests() {}
}
