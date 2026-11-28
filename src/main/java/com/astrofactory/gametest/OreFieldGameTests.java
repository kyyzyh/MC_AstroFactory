package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.block.MinerBlock;
import com.astrofactory.blockentity.MinerBlockEntity;
import com.astrofactory.blockentity.PoweredBlockEntity;
import com.astrofactory.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * v0.8.9 GameTest：矿区生成 + 采矿机放置限制 + 信息面板接口。
 *
 * <p>覆盖验收项：</p>
 * <ol>
 *   <li>采矿机只能放在矿场 / 原版矿石上（泥土不行，isMineableGround 判定）；</li>
 *   <li>采矿机放在矿区上能采出原矿（IMachineInfo.infoStatusKey=working、infoExtra 非空）；</li>
 *   <li>通用信息面板接口返回正确的能量/进度/状态（IMachineInfo）。</li>
 * </ol>
 *
 * <p>模板 {@code pole_flat} 是 21x6x21 石头平台。注意：setBlock 直接放矿机不会触发
 * {@code canSurvive} 拦截（那是放置交互路径），故这里直接断言静态支撑判定
 * {@link MinerBlock#isMineableGround}，并单独验证放置在有效支撑上的开采逻辑。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class OreFieldGameTests {

    /** 放置支撑判定：矿场与各类原版矿石必须认可，泥土/石头必须拒绝。 */
    @GameTest(template = "pole_flat", timeoutTicks = 100)
    public static void mineableGroundDetection(GameTestHelper helper) {
        helper.assertTrue(MinerBlock.isMineableGround(ModBlocks.ORE_FIELD.get().defaultBlockState()),
                "矿区方块应被判定为可采支撑");
        helper.assertTrue(MinerBlock.isMineableGround(Blocks.IRON_ORE.defaultBlockState()),
                "铁矿应被判定为可采支撑");
        helper.assertTrue(MinerBlock.isMineableGround(Blocks.COAL_ORE.defaultBlockState()),
                "煤矿应被判定为可采支撑");
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

    /** 采矿机放在矿区上（电力+供电）：应能推进进度，信息面板显示 working 且有产出。 */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void minerOnOreFieldProduces(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(12, 1, 10);
        // 下方放矿区，保证有可采支撑（模拟世界生成出来的矿脉）
        helper.setBlock(minerPos.below(), ModBlocks.ORE_FIELD.get());
        helper.setBlock(minerPos, ModBlocks.MINER.get()
                .defaultBlockState().setValue(com.astrofactory.block.MinerBlock.FACING, Direction.NORTH));

        MinerBlockEntity miner = helper.getBlockEntity(minerPos);
        miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        fillEnergy(helper, miner);

        // 采样时刻选在周期中段（37 tick，周期 20），避免刚好撞上进度归零
        helper.runAfterDelay(37, () -> {
            helper.assertTrue(miner.getProgress() > 0,
                    "矿区上采矿机进度未推进（" + miner.getProgress() + "）");

            // 信息面板接口：工作状态
            String status = miner.infoStatusKey();
            helper.assertTrue("working".equals(status),
                    "矿区上采矿机状态应为 working，实际 " + status);

            // 信息面板能源/进度
            helper.assertTrue(miner.infoEnergy() > 0,
                    "信息面板应显示能量 > 0，实际 " + miner.infoEnergy());
            helper.assertTrue(miner.infoMaxProgress() > 0,
                    "信息面板应显示进度上限 > 0，实际 " + miner.infoMaxProgress());
            helper.assertTrue(miner.infoExtra() != null,
                    "矿区上采矿机信息面板应有产出名称");
            helper.succeed();
        });
    }

    /** 信息面板状态位：放在泥土上（不合规支撑）时，无矿 → 状态 no_ore。 */
    @GameTest(template = "pole_flat", timeoutTicks = 300)
    public static void minerStatusNoOreOnDirt(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(12, 1, 10);
        // 故意放下方为泥土（虽不合规，但直接 setBlock 不会触发 canSurvive；用于验证状态判定）
        helper.setBlock(minerPos.below(), Blocks.DIRT);
        helper.setBlock(minerPos, ModBlocks.MINER.get()
                .defaultBlockState().setValue(com.astrofactory.block.MinerBlock.FACING, Direction.NORTH));

        MinerBlockEntity miner = helper.getBlockEntity(minerPos);
        miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
        fillEnergy(helper, miner);

        helper.runAfterDelay(60, () -> {
            // 泥土不是矿石 → 状态为 no_ore（无有效矿脉可采）
            helper.assertTrue("no_ore".equals(miner.infoStatusKey()),
                    "下方泥土时状态应为 no_ore，实际 " + miner.infoStatusKey());
            helper.succeed();
        });
    }

    /** 多次注入直到能量缓冲接近满（受 maxReceive 单次上限逐次累加）。 */
    private static void fillEnergy(GameTestHelper helper, MinerBlockEntity miner) {
        int goal = MinerBlockEntity.energyCapacity();
        for (int i = 0; i < 200 && miner.getEnergyBuffer().getEnergyStored() < goal; i++) {
            miner.getEnergyBuffer().receiveEnergy(
                    MinerBlockEntity.energyCapacity(), false);
        }
    }

    private OreFieldGameTests() {}
}
