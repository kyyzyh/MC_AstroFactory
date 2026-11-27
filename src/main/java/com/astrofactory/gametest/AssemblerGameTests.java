package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.block.AssemblerBlock;
import com.astrofactory.blockentity.AssemblerBlockEntity;
import com.astrofactory.registry.ModBlocks;
import com.astrofactory.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * US-104 GameTest：组装机（选定配方模式）。
 *
 * <p>覆盖清单验收项：</p>
 * <ol>
 *   <li>三种配方（铁齿轮 / 铜线材 / 红瓶）投入产出断言；</li>
 *   <li>缺一种原料时停等，补料自动恢复（且停等不耗电）；</li>
 *   <li>换配方时原料退回断言。</li>
 * </ol>
 *
 * <p>模板 {@code pole_flat} 是 21x6x21 石头平台，机器在测试里 setBlock 放置，
 * 直接灌电（不依赖发电机时序）。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class AssemblerGameTests {

    private static final String GEAR = "astrofactory:assembling_iron_gear";
    private static final String WIRE = "astrofactory:assembling_copper_wire";
    private static final String RED = "astrofactory:assembling_red_science_pack";

    private static AssemblerBlockEntity placeAssembler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ASSEMBLER.get().defaultBlockState()
                .setValue(AssemblerBlock.FACING, Direction.SOUTH));
        AssemblerBlockEntity be = helper.getBlockEntity(pos);
        // 注意：EnergyStorage 每次充电受 maxReceive 限制，一次性传容量只能充进 maxReceive，
        // 必须循环充满，否则机器跑十几个 tick 就断电（曾导致 5 个用例全红）。
        int guard = 0;
        while (be.getEnergyStored() < be.getEnergyCapacity() && guard++ < 10_000) {
            int moved = be.getEnergyBuffer().receiveEnergy(be.getEnergyCapacity(), false);
            if (moved <= 0) {
                break;
            }
        }
        return be;
    }

    /** 铁齿轮：2 铁板 → 1 齿轮。 */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void craftsIronGear(GameTestHelper helper) {
        BlockPos pos = new BlockPos(10, 1, 10);
        AssemblerBlockEntity be = placeAssembler(helper, pos);
        if (!be.setSelectedRecipe(GEAR)) {
            helper.fail("无法选定铁齿轮配方（配方未加载？）");
            return;
        }
        be.getSlots().setStackInSlot(0, new ItemStack(ModItems.IRON_PLATE.get(), 4));

        // 实测：方块放置后机器有约 40 tick 的启动延迟（首个 tick 才建立 ticker），
        // 40 tick/次 × 2 次 = 80，加启动延迟给到 240 tick 才稳。
        helper.runAfterDelay(240, () -> {
            ItemStack out = be.getSlots().getStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT);
            if (!out.is(ModItems.IRON_GEAR.get())) {
                helper.fail("铁齿轮配方未产出齿轮，输出槽=" + out
                        + " 进度=" + be.getCraftProgress() + "/" + be.getCraftMaxProgress()
                        + " 电量=" + be.getEnergyStored()
                        + " 铁板=" + be.getSlots().getStackInSlot(0).getCount());
                return;
            }
            if (out.getCount() < 2) {
                helper.fail("4 铁板应至少产出 2 齿轮，实际 " + out.getCount()
                        + " 进度=" + be.getCraftProgress()
                        + " 铁板=" + be.getSlots().getStackInSlot(0).getCount());
                return;
            }
            ItemStack left = be.getSlots().getStackInSlot(0);
            if (left.getCount() > 0) {
                helper.fail("铁板应被消耗完，剩余 " + left.getCount());
                return;
            }
            helper.succeed();
        });
    }

    /** 铜线材：1 铜板 → 2 线。 */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void craftsCopperWire(GameTestHelper helper) {
        BlockPos pos = new BlockPos(10, 1, 10);
        AssemblerBlockEntity be = placeAssembler(helper, pos);
        if (!be.setSelectedRecipe(WIRE)) {
            helper.fail("无法选定铜线材配方");
            return;
        }
        be.getSlots().setStackInSlot(0, new ItemStack(ModItems.COPPER_PLATE.get(), 1));

        helper.runAfterDelay(120, () -> {
            AssemblerBlockEntity cur = helper.getBlockEntity(pos);
            ItemStack out = cur.getSlots().getStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT);
            if (!out.is(ModItems.COPPER_WIRE.get())) {
                helper.fail("铜线材配方未产出线材，输出槽=" + out
                        + " 进度=" + cur.getCraftProgress() + "/" + cur.getCraftMaxProgress()
                        + " 电量=" + cur.getEnergyStored()
                        + " 缺料=" + cur.isMissingInput()
                        + " 配方=" + cur.getSelectedRecipeId());
                return;
            }
            if (out.getCount() != 2) {
                helper.fail("1 铜板应产出 2 线，实际 " + out.getCount());
                return;
            }
            helper.succeed();
        });
    }

    /** 红瓶：1 齿轮 + 1 铜板 → 1 红瓶，5 秒/个。 */
    @GameTest(template = "pole_flat", timeoutTicks = 600)
    public static void craftsRedSciencePack(GameTestHelper helper) {
        BlockPos pos = new BlockPos(10, 1, 10);
        AssemblerBlockEntity be = placeAssembler(helper, pos);
        if (!be.setSelectedRecipe(RED)) {
            helper.fail("无法选定红瓶配方");
            return;
        }
        be.getSlots().setStackInSlot(0, new ItemStack(ModItems.IRON_GEAR.get(), 2));
        be.getSlots().setStackInSlot(1, new ItemStack(ModItems.COPPER_PLATE.get(), 2));

        // 红瓶标称 100 tick（5 秒）+ 启动延迟约 40 tick，给 260 tick 余量
        helper.runAfterDelay(260, () -> {
            ItemStack out = be.getSlots().getStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT);
            if (!out.is(ModItems.RED_SCIENCE_PACK.get())) {
                helper.fail("红瓶配方未产出红瓶，输出槽=" + out
                        + " 进度=" + be.getCraftProgress() + " 缺料=" + be.isMissingInput());
                return;
            }
            if (out.getCount() < 1) {
                helper.fail("红瓶数量为 0");
                return;
            }
            helper.succeed();
        });
    }

    /** 缺一种原料：停等且不耗电；补料后自动恢复。 */
    @GameTest(template = "pole_flat", timeoutTicks = 600)
    public static void pausesWhenMissingIngredient(GameTestHelper helper) {
        BlockPos pos = new BlockPos(10, 1, 10);
        AssemblerBlockEntity be = placeAssembler(helper, pos);
        if (!be.setSelectedRecipe(RED)) {
            helper.fail("无法选定红瓶配方");
            return;
        }
        // 只给齿轮，缺铜板
        be.getSlots().setStackInSlot(0, new ItemStack(ModItems.IRON_GEAR.get(), 4));

        helper.runAfterDelay(40, () -> {
            int energyBefore = be.getEnergyStored();
            int progress = be.getCraftProgress();
            if (progress != 0) {
                helper.fail("缺料时进度应保持 0，实际 " + progress);
                return;
            }
            if (!be.isMissingInput()) {
                helper.fail("缺铜板时应判定为缺料");
                return;
            }
            helper.runAfterDelay(40, () -> {
                int energyAfter = be.getEnergyStored();
                if (energyAfter != energyBefore) {
                    helper.fail("缺料等待不应耗电：" + energyBefore + " → " + energyAfter);
                    return;
                }
                // 补上铜板 → 应自动恢复并产出
                be.getSlots().setStackInSlot(1, new ItemStack(ModItems.COPPER_PLATE.get(), 4));
                helper.runAfterDelay(260, () -> {
                    ItemStack out = be.getSlots().getStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT);
                    if (!out.is(ModItems.RED_SCIENCE_PACK.get())) {
                        helper.fail("补料后未恢复生产，输出槽=" + out);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    /** 换配方：新配方用不上的原料被退回（不在输入槽里了）。 */
    @GameTest(template = "pole_flat", timeoutTicks = 400)
    public static void switchingRecipeReturnsUnusedInput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(10, 1, 10);
        AssemblerBlockEntity be = placeAssembler(helper, pos);

        // 先投铜板（准备做线材）
        be.getSlots().setStackInSlot(0, new ItemStack(ModItems.COPPER_PLATE.get(), 3));
        if (!be.setSelectedRecipe(WIRE)) {
            helper.fail("无法选定铜线材配方");
            return;
        }

        // 立刻切到齿轮配方（只用铁板）→ 3 个铜板应被退回，输入槽里不应再有铜板
        if (!be.setSelectedRecipe(GEAR)) {
            helper.fail("无法切换到铁齿轮配方");
            return;
        }
        int copperLeft = 0;
        for (int i = 0; i < AssemblerBlockEntity.INPUT_SLOTS; i++) {
            ItemStack stack = be.getSlots().getStackInSlot(i);
            if (stack.is(ModItems.COPPER_PLATE.get())) {
                copperLeft += stack.getCount();
            }
        }
        if (copperLeft != 0) {
            helper.fail("换配方后输入槽仍留有 " + copperLeft + " 个铜板（应已退回）");
            return;
        }
        // 铁板不受影响：投入铁板后应能正常产出齿轮
        be.getSlots().setStackInSlot(0, new ItemStack(ModItems.IRON_PLATE.get(), 2));
        helper.runAfterDelay(200, () -> {
            ItemStack out = be.getSlots().getStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT);
            if (!out.is(ModItems.IRON_GEAR.get())) {
                helper.fail("换配方后未按新配方产出，输出槽=" + out);
                return;
            }
            helper.succeed();
        });
    }
}
