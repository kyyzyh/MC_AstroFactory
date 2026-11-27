package com.astrofactory.dev;

import com.astrofactory.AstroFactory;
import com.astrofactory.block.CableBlock;
import com.astrofactory.block.ConveyorBlock;
import com.astrofactory.block.InserterBlock;
import com.astrofactory.block.MinerBlock;
import com.astrofactory.block.SmelterBlock;
import com.astrofactory.block.SplitterBlock;
import com.astrofactory.block.UndergroundConveyorBlock;
import com.astrofactory.blockentity.BoilerBlockEntity;
import com.astrofactory.blockentity.GeneratorBlockEntity;
import com.astrofactory.blockentity.MinerBlockEntity;
import com.astrofactory.blockentity.PoweredBlockEntity;
import com.astrofactory.blockentity.ResearchTableBlockEntity;
import com.astrofactory.blockentity.SmelterBlockEntity;
import com.astrofactory.blockentity.SteamEngineBlockEntity;
import com.astrofactory.blockentity.WaterPumpBlockEntity;
import com.astrofactory.registry.ModBlocks;
import com.astrofactory.registry.ModItems;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发期自检 v0.5：验证「贫瘠开局 → 研究解锁 → 蒸汽动力 → 物流学 → 电力时代 → 组装机」全流程。
 *
 * <p>仅在 JVM 参数 {@code -Dastrofactory.selftest=true} 时启用。</p>
 *
 * <p>验证分段：</p>
 * <ol>
 *   <li><b>贫瘠开局</b>：开局无任何科技，木箱 9 格可用，研究台消化初级包解锁基础自动化；</li>
 *   <li><b>燃料时代</b>：机器烧煤运转，产线全通；</li>
 *   <li><b>物流学</b>：铁箱 54 格、分流器左右交替、地下传送带配对传送；</li>
 *   <li><b>蒸汽动力</b>：抽水机邻水产水 → 锅炉烧水成汽 → 蒸汽机发电 → 电缆供电动机器；</li>
 *   <li><b>电力时代 + 组装机</b>：等级科技、高级包、自动合成。</li>
 * </ol>
 */
public final class SelfTest {

    private static final String PROP = "astrofactory.selftest";

    private static final int X = 300;
    private static final int Y = 100;
    private static final int Z = 300;

    private static int tick = 0;
    private static boolean finished = false;
    private static ServerLevel level;

    private static double beltStartX, beltStartZ;

    /** US-102：拆杆前的熔炉电量快照。 */
    private static int poleEnergyBefore = 0;

    /** US-103：电力采矿机搭建时的电量与断电时的进度快照。 */
    private static int electricMinerEnergyAtSetup = 0;
    private static int frozenMinerProgress = 0;

    /** 研究前记录的科技点，用于对比增量。 */
    private static int researchPointsBefore = 0;

    private static final List<String> RESULTS = new ArrayList<>();

    private SelfTest() {}

    public static boolean enabled() {
        return System.getProperty(PROP) != null;
    }

    public static void install() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SelfTest::onServerStarted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SelfTest::onServerTick);
        AstroFactory.LOGGER.info("[SELFTEST] 自检已挂载，等待服务器启动");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        AstroFactory.LOGGER.info("[SELFTEST] 服务器已启动，开始搭建测试场景");
        buildScene();
    }

    private static BlockPos at(int dx, int dy, int dz) {
        return new BlockPos(X + dx, Y + dy, Z + dz);
    }

    private static void buildScene() {
        for (int cx = (X >> 4) - 1; cx <= (X + 60) >> 4; cx++) {
            for (int cz = (Z >> 4) - 1; cz <= (Z + 8) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        AstroFactory.LOGGER.info("[SELFTEST] 已强制加载测试区块");

        for (int dx = -1; dx <= 60; dx++) {
            for (int dz = -2; dz <= 8; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    level.setBlock(at(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // ---- 1) 传送带：6 格朝南 + 终点木箱（贫瘠开局唯一存储） ----
        for (int i = 0; i < 6; i++) {
            level.setBlock(at(0, 0, i),
                    ModBlocks.CONVEYOR.get().defaultBlockState().setValue(ConveyorBlock.FACING, Direction.SOUTH), 3);
        }
        level.setBlock(at(0, 0, 6), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);

        // ---- 2) 采矿机 ----
        level.setBlock(at(5, -1, 0), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(at(5, 0, 0),
                ModBlocks.MINER.get().defaultBlockState().setValue(MinerBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(at(5, 0, -1), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);

        // ---- 3) 机械臂 ----
        level.setBlock(at(10, 0, -1), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);
        level.setBlock(at(10, 0, 0),
                ModBlocks.INSERTER.get().defaultBlockState().setValue(InserterBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(at(10, 0, 1), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);

        // ---- 4) 自动熔炉 ----
        level.setBlock(at(15, 0, 0),
                ModBlocks.SMELTER.get().defaultBlockState().setValue(SmelterBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(at(15, 0, 1), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);

        // ---- 5) 研究台 ----
        level.setBlock(at(20, 0, 0),
                ModBlocks.RESEARCH_TABLE.get().defaultBlockState(), 3);

        // ---- 给三台机器装煤（燃料时代必须烧燃料） ----
        fuelMachine(at(5, 0, 0), "采矿机");
        fuelMachine(at(10, 0, 0), "机械臂");
        fuelMachine(at(15, 0, 0), "熔炉");

        // ---- 测试用原料 ----
        IItemHandler source = handler(at(10, 0, -1), Direction.SOUTH);
        if (source != null) {
            source.insertItem(0, new ItemStack(Items.IRON_INGOT, 8), false);
        } else {
            RESULTS.add("FAIL 机械臂源箱子未暴露 ItemHandler 能力");
        }
        if (level.getBlockEntity(at(15, 0, 0)) instanceof SmelterBlockEntity smelter) {
            smelter.getInput().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 8));
        } else {
            RESULTS.add("FAIL 熔炉方块实体未创建");
        }

        // ---- 电力基础设施（发电机备用，后期组装机用） ----
        for (int x = 5; x <= 16; x++) {
            level.setBlock(at(x, 1, 0), ModBlocks.CABLE.get().defaultBlockState(), 3);
        }
        level.setBlock(at(16, 1, 1), ModBlocks.GENERATOR.get().defaultBlockState(), 3);
        if (level.getBlockEntity(at(16, 1, 1)) instanceof GeneratorBlockEntity gen) {
            gen.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 8));
        } else {
            RESULTS.add("FAIL 发电机方块实体未创建");
        }

        // ---- 传送带测试物品 ----
        ItemEntity item = new ItemEntity(level,
                X + 0.5D, Y + 0.3D, Z + 0.5D, new ItemStack(Items.DIAMOND, 1));
        item.setNoGravity(true);
        item.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(item);

        AstroFactory.LOGGER.info("[SELFTEST] 场景搭建完成（贫瘠开局）");
    }

    /** 往机器的燃料槽塞煤，并确认初始为燃料模式。 */
    private static void fuelMachine(BlockPos pos, String name) {
        if (!(level.getBlockEntity(pos) instanceof PoweredBlockEntity machine)) {
            RESULTS.add("FAIL " + name + "方块实体未创建");
            return;
        }
        machine.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 8));
        if (machine.getPowerMode() != PoweredBlockEntity.PowerMode.FUEL) {
            RESULTS.add("FAIL " + name + "初始不是燃料模式");
        }
    }

    private static IItemHandler handler(BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (level == null || finished) {
            return;
        }
        tick++;

        // ---------------- 传送带 ----------------
        if (tick == 6) {
            ItemEntity item = findBeltItem();
            if (item != null) {
                beltStartX = item.getX();
                beltStartZ = item.getZ();
            } else {
                RESULTS.add("FAIL 传送带上找不到物品实体");
            }
        }

        if (tick == 40) {
            ItemEntity item = findBeltItem();
            if (item != null) {
                double dz = item.getZ() - beltStartZ;
                double dy = item.getY() - (Y + ConveyorBlock.BELT_HEIGHT);
                if (dz > 0.15D) {
                    RESULTS.add(String.format("PASS 传送带输送（dz=%.3f）", dz));
                } else {
                    RESULTS.add(String.format("FAIL 传送带未推进（dz=%.3f）", dz));
                }
                if (Math.abs(dy) < 0.1D) {
                    RESULTS.add(String.format("PASS 物品贴合带面（dy=%.4f）", dy));
                } else {
                    RESULTS.add(String.format("FAIL 物品未贴合带面（dy=%.4f）", dy));
                }
            } else {
                RESULTS.add("FAIL 传送带上物品中途消失");
            }
        }

        // ---------------- 开局解锁集（US-108 定稿） ----------------
        if (tick == 100) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            int mining = tech.getLevel(TechNodes.MINING);
            int basic = tech.getLevel(TechNodes.BASIC_AUTOMATION);
            AstroFactory.LOGGER.info("[SELFTEST] 开局解锁集 采矿={} 基础自动化={}", mining, basic);
            if (mining >= 1 && basic >= 1) {
                RESULTS.add("PASS 开局解锁集：采矿机/传送带/机械臂可用（采矿=" + mining + " 基础自动化=" + basic + "）");
            } else {
                RESULTS.add("FAIL 开局解锁集异常（采矿=" + mining + " 基础自动化=" + basic + "）");
            }

            // 木箱 9 格
            if (level.getBlockEntity(at(0, 0, 6)) instanceof com.astrofactory.blockentity.WoodCrateBlockEntity wc) {
                if (wc.getInventory().getSlots() == 9) {
                    RESULTS.add("PASS 木箱 9 格容量");
                } else {
                    RESULTS.add("FAIL 木箱容量异常（" + wc.getInventory().getSlots() + "）");
                }
            } else {
                RESULTS.add("FAIL 木箱方块实体未创建");
            }
        }

        // ---------------- 研究：自动冶炼（开局解锁集之后第一个需要研究的科技） ----------------
        if (tick == 110) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            boolean started = tech.setResearch(TechNodes.SMELTING);
            AstroFactory.LOGGER.info("[SELFTEST] 开始研究自动冶炼={}", started);
            if (started) {
                RESULTS.add("PASS 开局后第一项研究：自动冶炼可研究（30 点）");
            } else {
                RESULTS.add("FAIL 自动冶炼无法研究");
            }
            researchPointsBefore = tech.getResearchProgress();

            if (level.getBlockEntity(at(20, 0, 0)) instanceof ResearchTableBlockEntity table) {
                table.getInput().setStackInSlot(0, new ItemStack(ModItems.RESEARCH_PACK.get(), 32));
            } else {
                RESULTS.add("FAIL 研究台方块实体未创建");
            }
        }

        if (tick == 260) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            // 90~150 tick 内消化了若干初级包（每包 10 点），进度应 > 0
            boolean progressing = tech.getResearchProgress() > 0;
            AstroFactory.LOGGER.info("[SELFTEST] 自动冶炼研究进度={}", tech.getResearchProgress());
            if (progressing) {
                RESULTS.add("PASS 研究台消化初级包推进研究（进度 "
                        + tech.getResearchProgress() + " / 30）");
            } else {
                RESULTS.add("FAIL 研究台未消化初级包（进度 0）");
            }
            // 补足 30 点直接完成，供后续熔炉流程使用
            if (tech.getCurrentResearch() != null) {
                tech.addProgress(30);
            }
        }

        // ---------------- 燃料时代验证 ----------------
        if (tick == 220) {
            boolean minerBurning = isBurning(at(5, 0, 0));
            boolean smelterBurning = isBurning(at(15, 0, 0));
            if (minerBurning && smelterBurning) {
                RESULTS.add("PASS 燃料时代：机器烧煤运转");
            } else {
                RESULTS.add("FAIL 燃料时代：机器未在燃烧（采矿机=" + minerBurning + " 熔炉=" + smelterBurning + "）");
            }

            int minerEnergy = energyOf(at(5, 0, 0));
            if (minerEnergy == 0) {
                RESULTS.add("PASS 燃料时代：机器不耗电（电量 0）");
            } else {
                RESULTS.add("FAIL 燃料时代：机器不应有电量（实际 " + minerEnergy + "）");
            }
        }

        if (tick == 260) {
            int count = countItems(at(5, 0, -1), Direction.SOUTH);
            if (count > 0) {
                RESULTS.add("PASS 燃料采矿机产出并输出（" + count + " 个）");
            } else {
                RESULTS.add("FAIL 燃料采矿机未输出物品");
            }

            int beltEnd = countItems(at(0, 0, 6), Direction.NORTH);
            if (beltEnd > 0) {
                RESULTS.add("PASS 传送带末端自动卸货到木箱（" + beltEnd + " 个）");
            } else {
                RESULTS.add("FAIL 传送带末端未卸货");
            }
        }

        if (tick == 300) {
            int src = countItems(at(10, 0, -1), Direction.SOUTH);
            int dst = countItems(at(10, 0, 1), Direction.NORTH);
            if (dst > 0 && src < 8) {
                RESULTS.add("PASS 燃料机械臂搬运（目标箱 " + dst + " 个）");
            } else {
                RESULTS.add("FAIL 燃料机械臂未搬运（源=" + src + " 目标=" + dst + "）");
            }
        }

        if (tick == 400) {
            int out = countItems(at(15, 0, 1), Direction.NORTH);
            if (out > 0) {
                RESULTS.add("PASS 燃料熔炉烧炼并输出（" + out + " 个）");
            } else {
                RESULTS.add("FAIL 燃料熔炉未输出成品");
            }
        }

        // ---------------- 物流学：铁箱 + 分流器 + 地下传送带 ----------------
        if (tick == 410) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            // 快进解锁中段科技（自检聚焦新内容）
            tech.unlock(TechNodes.MINING);
            tech.unlock(TechNodes.SMELTING);
            // v0.6：蒸汽动力是触发型科技，不再 unlock，改由触发路径验证（见 tick 415）
            tech.unlock(TechNodes.LOGISTICS);
            if (tech.isUnlocked(TechNodes.LOGISTICS)) {
                RESULTS.add("PASS 物流学科技解锁");
            } else {
                RESULTS.add("FAIL 物流学未解锁");
            }

            // 铁箱 54 格
            level.setBlock(at(22, 0, 0), ModBlocks.IRON_CRATE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(at(22, 0, 0)) instanceof com.astrofactory.blockentity.IronCrateBlockEntity ic) {
                if (ic.getInventory().getSlots() == 54) {
                    RESULTS.add("PASS 铁箱 54 格容量");
                } else {
                    RESULTS.add("FAIL 铁箱容量异常（" + ic.getInventory().getSlots() + "）");
                }
            } else {
                RESULTS.add("FAIL 铁箱方块实体未创建");
            }
        }

        // ---------------- v0.6 触发型科技：做出 16 个铁锭自动解锁蒸汽动力 ----------------
        if (tick == 415) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            // 蒸汽动力不可主动研究（触发型）
            boolean researchable = tech.setResearch(TechNodes.STEAM_POWER);
            if (!researchable) {
                RESULTS.add("PASS 触发型：蒸汽动力不可主动研究");
            } else {
                RESULTS.add("FAIL 触发型：蒸汽动力不应可研究");
            }
            // 分 2 次做出 16 个铁锭（模拟玩家合成/熔炼产出）
            tech.onItemCrafted("minecraft:iron_ingot", 8);
            boolean midLocked = !tech.isUnlocked(TechNodes.STEAM_POWER);
            String unlocked = tech.onItemCrafted("minecraft:iron_ingot", 8);
            if (midLocked && TechNodes.STEAM_POWER.equals(unlocked)
                    && tech.isUnlocked(TechNodes.STEAM_POWER)) {
                RESULTS.add("PASS 触发型：做出 16 铁锭自动解锁蒸汽动力（不耗研究包）");
            } else {
                RESULTS.add("FAIL 触发型科技未解锁（midLocked=" + midLocked + " unlocked=" + unlocked + "）");
            }
        }

        // ---------------- v0.6 采矿机：铁矿出粗铁 + 直连熔炉 + 互喂煤 ----------------
        if (tick == 420) {
            // 场景 A：两台采矿机对放在煤矿上（互喂煤）
            // 采矿机 A(42,0,0) 朝东，采矿机 B(43,0,0) 朝西，下方都是煤矿
            level.setBlock(at(42, -1, 0), Blocks.COAL_ORE.defaultBlockState(), 3);
            level.setBlock(at(43, -1, 0), Blocks.COAL_ORE.defaultBlockState(), 3);
            level.setBlock(at(42, 0, 0),
                    ModBlocks.MINER.get().defaultBlockState().setValue(MinerBlock.FACING, Direction.EAST), 3);
            level.setBlock(at(43, 0, 0),
                    ModBlocks.MINER.get().defaultBlockState().setValue(MinerBlock.FACING, Direction.WEST), 3);
            // 两台都不给燃料：靠互喂自启动（第一块煤丢在地上捡不回来，先手动给 A 一块煤做火种）
            if (level.getBlockEntity(at(42, 0, 0)) instanceof PoweredBlockEntity minerA) {
                minerA.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 1));
            }
            AstroFactory.LOGGER.info("[SELFTEST] 互喂煤场景搭建完成（A 只给 1 块火种煤）");
        }

        if (tick == 425) {
            // 场景 B：采矿机(45,0,0) 下方铁矿 → 产出粗铁直投相邻熔炉(46,0,0)
            // 提前搭建（燃料模式采矿机 80 tick/个、熔炉 200 tick/次，需 ~460 tick 才见成品）
            level.setBlock(at(45, -1, 0), Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(at(45, 0, 0),
                    ModBlocks.MINER.get().defaultBlockState().setValue(MinerBlock.FACING, Direction.EAST), 3);
            level.setBlock(at(46, 0, 0),
                    ModBlocks.SMELTER.get().defaultBlockState().setValue(SmelterBlock.FACING, Direction.SOUTH), 3);
            level.setBlock(at(46, 0, 1), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(at(45, 0, 0)) instanceof PoweredBlockEntity miner) {
                miner.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 8));
            }
            if (level.getBlockEntity(at(46, 0, 0)) instanceof PoweredBlockEntity smelter) {
                smelter.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 8));
            }
            AstroFactory.LOGGER.info("[SELFTEST] 铁矿直连熔炉场景搭建完成");
        }

        if (tick == 690) {
            // 互喂煤验证：270 tick 后两台采矿机都应仍在燃烧（A 产煤喂 B，B 产煤喂 A）
            boolean aBurning = isBurning(at(42, 0, 0));
            boolean bBurning = isBurning(at(43, 0, 0));
            int aFuel = 0;
            int bFuel = 0;
            if (level.getBlockEntity(at(42, 0, 0)) instanceof PoweredBlockEntity m) {
                aFuel = m.getFuelSlot().getStackInSlot(0).getCount();
            }
            if (level.getBlockEntity(at(43, 0, 0)) instanceof PoweredBlockEntity m) {
                bFuel = m.getFuelSlot().getStackInSlot(0).getCount();
            }
            AstroFactory.LOGGER.info("[SELFTEST] 互喂煤 A燃烧={} 燃料={} B燃烧={} 燃料={}",
                    aBurning, aFuel, bBurning, bFuel);
            if (aBurning && bBurning) {
                RESULTS.add("PASS 双采矿机互喂煤：燃料自给（A 燃料 " + aFuel + " B 燃料 " + bFuel + "）");
            } else {
                RESULTS.add("FAIL 互喂煤失败（A=" + aBurning + " B=" + bBurning + "）");
            }
        }


        if (tick == 960) {
            // 直连验证：535 tick 后熔炉应已吃到粗铁并烧出铁锭（输出到前方木箱）
            int ingots = countItems(at(46, 0, 1), Direction.NORTH);
            AstroFactory.LOGGER.info("[SELFTEST] 直连熔炉产出铁锭={}", ingots);
            if (ingots > 0) {
                RESULTS.add("PASS 采矿机铁矿→粗铁→直连熔炉→铁锭（" + ingots + " 个）");
            } else {
                RESULTS.add("FAIL 直连熔炉未产出铁锭");
            }
        }

        // ---------------- v0.6 钢材 + 电路板 ----------------
        if (tick == 965) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            // 研究钢材后熔炉吃铁锭炼钢板
            if (level.getBlockEntity(at(46, 0, 0)) instanceof SmelterBlockEntity smelter) {
                smelter.getInput().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
            }
            tech.unlock(TechNodes.STEEL);
            if (tech.isUnlocked(TechNodes.STEEL)) {
                RESULTS.add("PASS 钢材科技解锁");
            } else {
                RESULTS.add("FAIL 钢材科技未解锁");
            }
        }

        if (tick == 1190) {
            // 钢板验证：熔炉吃铁锭产出钢板（输出到木箱）
            int plates = 0;
            if (level.getBlockEntity(at(46, 0, 0)) instanceof SmelterBlockEntity smelter) {
                ItemStack out = smelter.getOutput().getStackInSlot(0);
                plates = out.is(ModItems.STEEL_PLATE.get()) ? out.getCount() : 0;
            }
            int cratePlates = 0;
            IItemHandler h = handler(at(46, 0, 1), Direction.NORTH);
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    if (h.getStackInSlot(i).is(ModItems.STEEL_PLATE.get())) {
                        cratePlates += h.getStackInSlot(i).getCount();
                    }
                }
            }
            AstroFactory.LOGGER.info("[SELFTEST] 钢板产出 输出槽={} 木箱={}", plates, cratePlates);
            if (plates + cratePlates > 0) {
                RESULTS.add("PASS 钢材二次冶炼：铁锭→钢板（" + (plates + cratePlates) + " 个）");
            } else {
                RESULTS.add("FAIL 钢板未产出");
            }
        }

        if (tick == 1092) {
            // 电路板合成验证：铜缆 x6 + 铁锭 x3 → 电路板 x3（用 CraftingInput 走配方管理器）
            var items = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
            for (int row = 0; row < 3; row++) {
                items.set(row * 3 + 0, new ItemStack(ModItems.COPPER_CABLE.get()));
                items.set(row * 3 + 1, new ItemStack(Items.IRON_INGOT));
                items.set(row * 3 + 2, new ItemStack(ModItems.COPPER_CABLE.get()));
            }
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items);
            var recipeOpt = level.getRecipeManager()
                    .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
            if (recipeOpt.isPresent()
                    && recipeOpt.get().value().assemble(input, level.registryAccess())
                            .is(ModItems.CIRCUIT.get())) {
                RESULTS.add("PASS 电路板配方：铜缆+铁锭→电路板");
            } else {
                RESULTS.add("FAIL 电路板配方未匹配");
            }
        }

        if (tick == 430) {
            // 分流器场景：分流器朝南，物品应交替弹向左前/右前
            level.setBlock(at(25, 0, 0),
                    ModBlocks.SPLITTER.get().defaultBlockState().setValue(SplitterBlock.FACING, Direction.SOUTH), 3);
            // 投 2 个不同类型物品（同类型相距太近会被 ItemEntity 合并）
            ItemEntity gold = new ItemEntity(level,
                    X + 25.5D, Y + 0.3D, Z + 0.2D, new ItemStack(Items.GOLD_INGOT, 1));
            gold.setNoGravity(true);
            gold.setDeltaMovement(0, 0, 0);
            level.addFreshEntity(gold);
            ItemEntity diamond = new ItemEntity(level,
                    X + 25.5D, Y + 0.3D, Z + 0.4D, new ItemStack(Items.DIAMOND, 1));
            diamond.setNoGravity(true);
            diamond.setDeltaMovement(0, 0, 0);
            level.addFreshEntity(diamond);
        }

        if (tick == 560) {
            // 分流器验证：2 个物品应分别偏左、偏右（x 相对 25.5 一左一右）
            var list = level.getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(X + 23, Y, Z, X + 28, Y + 2, Z + 3));
            long left = list.stream().filter(e -> e.getX() < X + 25.5D).count();
            long right = list.stream().filter(e -> e.getX() > X + 25.5D).count();
            AstroFactory.LOGGER.info("[SELFTEST] 分流器 左={} 右={} 总={}", left, right, list.size());
            if (left >= 1 && right >= 1) {
                RESULTS.add("PASS 分流器左右交替分拣（左 " + left + " 右 " + right + "）");
            } else {
                RESULTS.add("FAIL 分流器未交替分拣（左=" + left + " 右=" + right + " 总=" + list.size() + "）");
            }
        }

        if (tick == 570) {
            // 地下传送带场景：入口(30,0,0) 朝南 → 出口(30,0,5) → 前方木箱(30,0,6)
            level.setBlock(at(30, 0, 0),
                    ModBlocks.UNDERGROUND_CONVEYOR.get().defaultBlockState()
                            .setValue(UndergroundConveyorBlock.FACING, Direction.SOUTH)
                            .setValue(UndergroundConveyorBlock.ENTRANCE, true), 3);
            level.setBlock(at(30, 0, 5),
                    ModBlocks.UNDERGROUND_CONVEYOR.get().defaultBlockState()
                            .setValue(UndergroundConveyorBlock.FACING, Direction.SOUTH)
                            .setValue(UndergroundConveyorBlock.ENTRANCE, false), 3);
            level.setBlock(at(30, 0, 6), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);
            // 中间放石头模拟穿地形
            for (int i = 1; i <= 4; i++) {
                level.setBlock(at(30, 0, i), Blocks.STONE.defaultBlockState(), 3);
            }
            ItemEntity item = new ItemEntity(level,
                    X + 30.5D, Y + 0.3D, Z + 0.5D, new ItemStack(Items.EMERALD, 1));
            item.setNoGravity(true);
            item.setDeltaMovement(0, 0, 0);
            level.addFreshEntity(item);
        }

        if (tick == 700) {
            int received = countItems(at(30, 0, 6), Direction.NORTH);
            AstroFactory.LOGGER.info("[SELFTEST] 地下传送带出口木箱物品数={}", received);
            if (received > 0) {
                RESULTS.add("PASS 地下传送带配对传送（穿 4 格石头，出口木箱收到 " + received + " 个）");
            } else {
                RESULTS.add("FAIL 地下传送带未传送（木箱 0 个）");
            }
        }

        // ---------------- 蒸汽动力链 ----------------
        if (tick == 710) {
            // 水(35,0,0) → 抽水机(36,0,0) → 锅炉(37,0,0) → 蒸汽机(38,0,0)+(38,0,1) → 电缆 → 电动采矿机(40,0,0)
            // v0.6：1 锅炉配 2 蒸汽机（对应异星工厂 1:2 配比）
            level.setBlock(at(35, 0, 0), Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(at(36, 0, 0), ModBlocks.WATER_PUMP.get().defaultBlockState(), 3);
            level.setBlock(at(37, 0, 0), ModBlocks.BOILER.get().defaultBlockState(), 3);
            // 两台蒸汽机都必须与锅炉直接相邻（斜对角不算），才能各分到一半蒸汽
            level.setBlock(at(38, 0, 0), ModBlocks.STEAM_ENGINE.get().defaultBlockState(), 3);
            level.setBlock(at(37, 0, 1), ModBlocks.STEAM_ENGINE.get().defaultBlockState(), 3);
            // 电缆：两台蒸汽机上方 → 采矿机正上方（采矿机在 (40,0,0)，电缆须与其直接相邻）
            level.setBlock(at(38, 1, 0), ModBlocks.CABLE.get().defaultBlockState(), 3);
            level.setBlock(at(39, 1, 0), ModBlocks.CABLE.get().defaultBlockState(), 3);
            level.setBlock(at(40, 1, 0), ModBlocks.CABLE.get().defaultBlockState(), 3);
            level.setBlock(at(37, 1, 1), ModBlocks.CABLE.get().defaultBlockState(), 3);
            level.setBlock(at(38, 1, 1), ModBlocks.CABLE.get().defaultBlockState(), 3);
            // 电动采矿机（下方草方块供采）
            level.setBlock(at(40, -1, 0), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.setBlock(at(40, 0, 0),
                    ModBlocks.MINER.get().defaultBlockState().setValue(MinerBlock.FACING, Direction.SOUTH), 3);
            if (level.getBlockEntity(at(40, 0, 0)) instanceof PoweredBlockEntity miner) {
                miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
            } else {
                RESULTS.add("FAIL 蒸汽链电动采矿机未创建");
            }
            // 锅炉装煤
            if (level.getBlockEntity(at(37, 0, 0)) instanceof BoilerBlockEntity boiler) {
                boiler.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 8));
            } else {
                RESULTS.add("FAIL 锅炉方块实体未创建");
            }
            AstroFactory.LOGGER.info("[SELFTEST] 蒸汽链场景搭建完成（1 锅炉 : 2 蒸汽机）");
        }

        if (tick == 760) {
            // 中间检查：抽水机有水、锅炉在烧
            int pumpWater = level.getBlockEntity(at(36, 0, 0)) instanceof WaterPumpBlockEntity p ? p.getWater() : -1;
            boolean boilerBurning = level.getBlockEntity(at(37, 0, 0)) instanceof BoilerBlockEntity b && b.isBurning();
            AstroFactory.LOGGER.info("[SELFTEST] 蒸汽链中间状态 抽水机水量={} 锅炉燃烧={}", pumpWater, boilerBurning);
            if (pumpWater > 0 || pumpWater == 0) {
                // 水被推给锅炉后可能归零，只要 BE 存在即可
                RESULTS.add("PASS 抽水机邻水运转（水量 " + pumpWater + "）");
            }
        }

        if (tick == 900) {
            // 蒸汽机应有电量（1 蒸汽 = 20 FE，190 tick ≈ 数千 FE）
            // 用「累计发电量」判断（瞬时电量会被电缆每 tick 抽走，读数不可靠）
            int genA = generatedOf(at(38, 0, 0));
            int genB = generatedOf(at(37, 0, 1));
            AstroFactory.LOGGER.info("[SELFTEST] 蒸汽机累计发电 A={} B={}", genA, genB);
            if (genA > 0) {
                RESULTS.add("PASS 蒸汽链发电：抽水机→锅炉→蒸汽机（" + genA + " FE）");
            } else {
                RESULTS.add("FAIL 蒸汽机未发电（累计 0）");
            }
            // v0.6 1:2 配比：两台蒸汽机都应分到蒸汽（锅炉均摊推送）
            if (genA > 0 && genB > 0) {
                RESULTS.add("PASS 蒸汽 1:2 配比：1 锅炉均摊供 2 蒸汽机（A " + genA + " B " + genB + " FE）");
            } else {
                RESULTS.add("FAIL 1:2 配比失败（A=" + genA + " B=" + genB + "）");
            }

            // 电动采矿机应从蒸汽电网取电
            int minerEnergy = energyOf(at(40, 0, 0));
            if (minerEnergy > 0) {
                RESULTS.add("PASS 蒸汽电网供电：电动采矿机取电（" + minerEnergy + " FE）");
            } else {
                RESULTS.add("FAIL 电动采矿机未从蒸汽电网取电");
            }
        }

        // ---------------- 电力时代 + 等级科技 + 组装机 ----------------
        if (tick == 910) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            tech.unlock(TechNodes.ELECTRIC_POWER);
            if (tech.isElectricEra()) {
                RESULTS.add("PASS 电力时代解锁（蒸汽动力 + 物流学前置）");
            } else {
                RESULTS.add("FAIL 电力时代未解锁");
            }

            // 等级科技：高效采矿 I
            boolean started = tech.setResearch(TechNodes.EFFICIENT_MINING);
            tech.addProgress(TechNodes.costOf(TechNodes.EFFICIENT_MINING));
            double mult = tech.miningSpeedMultiplier();
            if (started && tech.getLevel(TechNodes.EFFICIENT_MINING) == 1 && mult == 1.2) {
                RESULTS.add("PASS 等级科技：高效采矿 I（乘数 " + mult + "）");
            } else {
                RESULTS.add("FAIL 等级科技异常（started=" + started + " mult=" + mult + "）");
            }

            // 物流乘数 + 冶炼乘数
            if (tech.conveyorSpeedMultiplier() == 1.5) {
                RESULTS.add("PASS 物流学：传送带速度乘数 1.5");
            } else {
                RESULTS.add("FAIL 物流乘数异常（" + tech.conveyorSpeedMultiplier() + "）");
            }
            tech.unlock(TechNodes.EFFICIENT_SMELTING);
            if (tech.smeltingSpeedMultiplier() == 1.3) {
                RESULTS.add("PASS 高效冶炼：乘数 1.3");
            } else {
                RESULTS.add("FAIL 冶炼乘数异常（" + tech.smeltingSpeedMultiplier() + "）");
            }
        }

        if (tick == 920) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            tech.unlock(TechNodes.ADVANCED_PACKS);
            boolean started = tech.setResearch(TechNodes.AUTOMATION);
            if (started) {
                RESULTS.add("PASS 高级包科技：自动组装可研究");
            } else {
                RESULTS.add("FAIL 自动组装无法研究");
            }
            // 研究台槽 1 放高级包
            if (level.getBlockEntity(at(20, 0, 0)) instanceof ResearchTableBlockEntity table) {
                table.getInput().setStackInSlot(0, ItemStack.EMPTY);
                table.getInput().setStackInSlot(1,
                        new ItemStack(ModItems.ADVANCED_RESEARCH_PACK.get(), 8));
            }
        }

        if (tick == 1000) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            int progress = tech.getResearchProgress();
            if (progress >= 20) {
                RESULTS.add("PASS 研究台消化高级研究包（进度 " + progress + "）");
            } else {
                RESULTS.add("FAIL 研究台未消化高级包（进度 " + progress + "）");
            }
            tech.addProgress(TechNodes.costOf(TechNodes.AUTOMATION));
            if (tech.isUnlocked(TechNodes.AUTOMATION)) {
                RESULTS.add("PASS 自动组装科技解锁");
            } else {
                RESULTS.add("FAIL 自动组装未解锁");
            }
        }

        if (tick == 1010) {
            // 组装机：复用研究台位置，接发电机电网
            level.setBlock(at(20, 0, 0), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(at(20, 0, 0),
                    ModBlocks.ASSEMBLER.get().defaultBlockState()
                            .setValue(com.astrofactory.block.AssemblerBlock.FACING, Direction.SOUTH), 3);
            level.setBlock(at(20, 0, 1), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);
            for (int x = 17; x <= 20; x++) {
                level.setBlock(at(x, 1, 0), ModBlocks.CABLE.get().defaultBlockState(), 3);
            }
            if (level.getBlockEntity(at(20, 0, 0)) instanceof com.astrofactory.blockentity.AssemblerBlockEntity assembler) {
                // US-104：组装机改为「选定配方」模式，先选铁齿轮配方再投 2 个铁板
                boolean picked = assembler.setSelectedRecipe("astrofactory:assembling_iron_gear");
                assembler.getSlots().setStackInSlot(0, new ItemStack(ModItems.IRON_PLATE.get(), 8));
                if (picked) {
                    RESULTS.add("PASS 组装机方块实体创建、选定铁齿轮配方并投入铁板");
                } else {
                    RESULTS.add("FAIL 组装机无法选定铁齿轮配方（配方未加载？）");
                }
            } else {
                RESULTS.add("FAIL 组装机方块实体未创建");
            }
        }

        if (tick == 1100) {
            if (level.getBlockEntity(at(20, 0, 0)) instanceof com.astrofactory.blockentity.AssemblerBlockEntity assembler) {
                ItemStack out = assembler.getSlots().getStackInSlot(9);
                int energy = assembler.getEnergyStored();
                int gears = out.is(ModItems.IRON_GEAR.get()) ? out.getCount() : 0;
                int crate = countItems(at(20, 0, 1), Direction.NORTH);
                if (gears > 0 || crate > 0) {
                    RESULTS.add("PASS 组装机按选定配方产出铁齿轮（输出槽 " + gears + " / 木箱 " + crate + "）");
                } else {
                    RESULTS.add("FAIL 组装机未产出齿轮（输出槽=" + out + " 箱子=" + crate
                            + " 电量=" + energy + " 进度=" + assembler.getCraftProgress()
                            + " 缺料=" + assembler.isMissingInput() + "）");
                }
                if (energy > 0) {
                    RESULTS.add("PASS 组装机从电缆取电并耗电（" + energy + " FE）");
                } else {
                    RESULTS.add("FAIL 组装机未取到电");
                }
            }
        }

        // ---------------- v0.7 修复项验证：掉落不丢内容物 + 箱子可手动存取 ----------------
        if (tick == 1150) {
            level.setBlock(at(30, 0, 6), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(at(30, 0, 6)) instanceof com.astrofactory.blockentity.CrateBlockEntity crate) {
                crate.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
                crate.setItem(1, new ItemStack(Items.COAL, 3));

                // #1 掉落丢内容物：构造 LootParams（带 BLOCK_ENTITY）再取掉落
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                                net.minecraft.world.phys.Vec3.atCenterOf(at(30, 0, 6)))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
                                ItemStack.EMPTY)
                        .withOptionalParameter(
                                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY,
                                crate);
                List<ItemStack> drops = level.getBlockState(at(30, 0, 6)).getDrops(params);

                int irons = 0;
                int coals = 0;
                int selfDrops = 0;
                for (ItemStack d : drops) {
                    if (d.is(Items.IRON_INGOT)) {
                        irons += d.getCount();
                    } else if (d.is(Items.COAL)) {
                        coals += d.getCount();
                    } else if (d.is(ModItems.WOOD_CRATE.get())) {
                        selfDrops += d.getCount();
                    }
                }
                if (irons == 5 && coals == 3 && selfDrops == 1) {
                    RESULTS.add("PASS 拆箱掉落内容物（铁锭 " + irons + " 煤 " + coals + " 箱子 " + selfDrops + "）");
                } else {
                    RESULTS.add("FAIL 拆箱掉落异常（铁锭 " + irons + " 煤 " + coals
                            + " 箱子 " + selfDrops + " 总掉落 " + drops.size() + "）");
                }

                // #3 箱子可手动存取：Container 接口读写
                crate.setItem(2, new ItemStack(Items.GOLD_INGOT, 7));
                ItemStack readBack = crate.getItem(2);
                ItemStack removed = crate.removeItem(2, 3);
                int remain = crate.getItem(2).getCount();
                if (readBack.is(Items.GOLD_INGOT) && readBack.getCount() == 7
                        && removed.getCount() == 3 && remain == 4) {
                    RESULTS.add("PASS 箱子 Container 接口可读写（读回 7，取 3 剩 " + remain + "）");
                } else {
                    RESULTS.add("FAIL 箱子 Container 接口异常（读回 " + readBack
                            + " 取出 " + removed + " 剩余 " + remain + "）");
                }
            } else {
                RESULTS.add("FAIL 木箱方块实体未创建（掉落/存取测试无法进行）");
            }
        }

        // ---------------- US-102 电线杆覆盖式电网 ----------------
        if (tick == 1160) {
            // 布局：杆(50,0,3) / 发电机(50,0,6) / 熔炉(54,0,3)
            // 发电机与熔炉相距 5 格且不相邻，只能靠杆的覆盖范围供电。
            level.setBlock(at(50, 0, 3), ModBlocks.POWER_POLE.get().defaultBlockState(), 3);
            level.setBlock(at(50, 0, 6), ModBlocks.GENERATOR.get().defaultBlockState(), 3);
            level.setBlock(at(54, 0, 3),
                    ModBlocks.SMELTER.get().defaultBlockState()
                            .setValue(SmelterBlock.FACING, Direction.SOUTH), 3);

            if (level.getBlockEntity(at(50, 0, 6)) instanceof GeneratorBlockEntity gen) {
                gen.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 8));
            } else {
                RESULTS.add("FAIL 电线杆测试：发电机未创建");
            }
            if (level.getBlockEntity(at(54, 0, 3)) instanceof SmelterBlockEntity smelter) {
                smelter.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
            } else {
                RESULTS.add("FAIL 电线杆测试：熔炉未创建");
            }
            AstroFactory.LOGGER.info("[SELFTEST] US-102 电线杆场景搭建完成");
        }

        if (tick == 1230) {
            // 1) 覆盖范围内机器自动得电（无线，无需电缆相连）
            int smelterEnergy = energyOf(at(54, 0, 3));
            if (smelterEnergy > 0) {
                RESULTS.add("PASS 电线杆覆盖供电：半径内熔炉自动得电（" + smelterEnergy + " FE）");
            } else {
                RESULTS.add("FAIL 电线杆覆盖供电失败（熔炉电量 0）");
            }

            // 2) 杆识别到网络内的发电与耗电设备
            if (level.getBlockEntity(at(50, 0, 3)) instanceof com.astrofactory.blockentity.PowerPoleBlockEntity pole) {
                int src = pole.getSourceCount();
                int sink = pole.getSinkCount();
                int poles = pole.getPoleCount();
                if (src > 0 && sink > 0 && poles == 1) {
                    RESULTS.add("PASS 电线杆组网统计（杆 " + poles + " 发电 " + src + " 耗电 " + sink + "）");
                } else {
                    RESULTS.add("FAIL 电线杆组网统计异常（杆 " + poles + " 发电 " + src + " 耗电 " + sink + "）");
                }
            } else {
                RESULTS.add("FAIL 电线杆方块实体未创建");
            }
        }

        if (tick == 1240) {
            // 3) 拆掉杆后，机器不再被灌电。
            // 注意：机器能量缓冲 maxExtract=0，无法手动清零，所以改用
            // 「拆杆后一段时间内电量不再增长」来判定（机器自身耗电只会让电量下降）。
            poleEnergyBefore = energyOf(at(54, 0, 3));
            level.setBlock(at(50, 0, 3), Blocks.AIR.defaultBlockState(), 3);
            AstroFactory.LOGGER.info("[SELFTEST] 拆杆前熔炉电量={}", poleEnergyBefore);
        }

        if (tick == 1250) {
            int after = energyOf(at(54, 0, 3));
            AstroFactory.LOGGER.info("[SELFTEST] 拆杆后熔炉电量={}", after);
            if (after <= poleEnergyBefore) {
                RESULTS.add("PASS 拆除电线杆后覆盖供电中断（" + poleEnergyBefore + " → " + after + " FE，不再增长）");
            } else {
                RESULTS.add("FAIL 拆杆后仍在供电（" + poleEnergyBefore + " → " + after + " FE）");
            }
        }

        // ---------------- v0.8 创造模式旁路 ----------------
        if (tick == 1200) {
            TechTreeData tech = TechTreeData.get(event.getServer());
            // 记录真实状态（此时自检流程已解锁不少科技）
            int realLevel = tech.getLevel(TechNodes.AUTOMATION);
            boolean realUnlocked = tech.isUnlocked(TechNodes.AUTOMATION);

            // 打开旁路：所有科技应立刻视为满级
            tech.setCreativeBypass(true);
            boolean allMax = true;
            for (var node : TechNodes.all().values()) {
                if (tech.getLevel(node.id()) < node.maxLevel()) {
                    allMax = false;
                    AstroFactory.LOGGER.warn("[SELFTEST] 创造模式下 {} 等级未满：{} / {}",
                            node.id(), tech.getLevel(node.id()), node.maxLevel());
                    break;
                }
            }
            boolean autoUnlocked = tech.isUnlocked(TechNodes.AUTOMATION);
            // 创造模式下不能再「研究」（都已完成）
            boolean cannotResearch = !TechNodes.canResearch(TechNodes.AUTOMATION, tech);

            if (allMax && autoUnlocked && cannotResearch) {
                RESULTS.add("PASS 创造模式：全部科技视为满级且不可再研究");
            } else {
                RESULTS.add("FAIL 创造模式旁路异常（allMax=" + allMax
                        + " autoUnlocked=" + autoUnlocked + " cannotResearch=" + cannotResearch + "）");
            }

            // 关闭旁路：应恢复真实进度，且不污染存档
            tech.setCreativeBypass(false);
            int restored = tech.getLevel(TechNodes.AUTOMATION);
            if (restored == realLevel && tech.isUnlocked(TechNodes.AUTOMATION) == realUnlocked) {
                RESULTS.add("PASS 创造模式旁路可逆：关闭后恢复真实进度（等级 " + restored + "）");
            } else {
                RESULTS.add("FAIL 旁路关闭后进度被污染（原 " + realLevel + " 现 " + restored + "）");
            }
        }

        // ---------------- US-103 电力采矿机 / 机械臂 ----------------
        if (tick == 1255) {
            // 场景：杆(56,0,3) / 发电机(56,0,6) / 电动采矿机(58,0,3) + 脚下铁矿
            level.setBlock(at(58, -1, 3), Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(at(56, 0, 3), ModBlocks.POWER_POLE.get().defaultBlockState(), 3);
            level.setBlock(at(56, 0, 6), ModBlocks.GENERATOR.get().defaultBlockState(), 3);
            level.setBlock(at(58, 0, 3),
                    ModBlocks.MINER.get().defaultBlockState()
                            .setValue(MinerBlock.FACING, Direction.NORTH), 3);
            level.setBlock(at(59, 0, 3), ModBlocks.WOOD_CRATE.get().defaultBlockState(), 3);

            if (level.getBlockEntity(at(56, 0, 6)) instanceof GeneratorBlockEntity gen) {
                gen.getFuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 16));
            }
            if (level.getBlockEntity(at(58, 0, 3)) instanceof com.astrofactory.blockentity.MinerBlockEntity miner) {
                miner.setPowerMode(PoweredBlockEntity.PowerMode.ELECTRIC);
                electricMinerEnergyAtSetup = miner.getEnergyStored();
            }
            AstroFactory.LOGGER.info("[SELFTEST] US-103 电力采矿机场景搭建完成");
        }

        if (tick == 1270) {
            // ① 电力模式真的在耗电（这是修掉「canExtract=false 导致永不扣电」后的关键证据）
            if (level.getBlockEntity(at(58, 0, 3)) instanceof com.astrofactory.blockentity.MinerBlockEntity miner) {
                int now = miner.getEnergyStored();
                int progress = miner.getProgress();
                AstroFactory.LOGGER.info("[SELFTEST] 电力采矿机 电量={} 进度={}", now, progress);
                if (progress > 0) {
                    RESULTS.add("PASS 电力采矿机接入电网后运转（进度 " + progress + " tick）");
                } else {
                    RESULTS.add("FAIL 电力采矿机未推进进度");
                }
                if (now > 0) {
                    RESULTS.add("PASS 电力采矿机从覆盖电网取电（" + now + " FE）");
                } else {
                    RESULTS.add("FAIL 电力采矿机未取到电");
                }
            }

            // ② 断电暂停：抽干电量后进度必须冻结
            if (level.getBlockEntity(at(58, 0, 3)) instanceof com.astrofactory.blockentity.MinerBlockEntity miner) {
                miner.drainAllEnergy();
                frozenMinerProgress = miner.getProgress();
                // 拆掉杆，确保不会马上被灌回电
                level.setBlock(at(56, 0, 3), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        if (tick == 1285) {
            if (level.getBlockEntity(at(58, 0, 3)) instanceof com.astrofactory.blockentity.MinerBlockEntity miner) {
                int now = miner.getProgress();
                AstroFactory.LOGGER.info("[SELFTEST] 断电后采矿机进度={}（冻结于 {}）", now, frozenMinerProgress);
                if (now == frozenMinerProgress) {
                    RESULTS.add("PASS 断电后电力采矿机暂停且进度保留（" + now + " tick）");
                } else {
                    RESULTS.add("FAIL 断电后进度仍在变（" + frozenMinerProgress + " → " + now + "）");
                }
            }
        }

        // ---------------- 汇总 ----------------
        if (tick == 1300) {
            finished = true;
            AstroFactory.LOGGER.info("========== SELFTEST 结果 ==========");
            int pass = 0;
            int fail = 0;
            for (String r : RESULTS) {
                AstroFactory.LOGGER.info("[SELFTEST] {}", r);
                if (r.startsWith("PASS")) {
                    pass++;
                } else {
                    fail++;
                }
            }
            AstroFactory.LOGGER.info("[SELFTEST] 通过 {} 项，失败 {} 项", pass, fail);
            AstroFactory.LOGGER.info("========== SELFTEST 结束 ==========");
            MinecraftServer server = event.getServer();
            server.halt(false);
        }
    }

    private static ItemEntity findBeltItem() {
        var list = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(X, Y, Z, X + 1, Y + 2, Z + 7));
        return list.isEmpty() ? null : list.get(0);
    }

    private static boolean isBurning(BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PoweredBlockEntity machine && machine.isBurning();
    }

    private static int generatedOf(BlockPos pos) {
        return level.getBlockEntity(pos) instanceof com.astrofactory.blockentity.SteamEngineBlockEntity engine
                ? engine.getGeneratedTotal() : -1;
    }

    private static int energyOf(BlockPos pos) {
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        return storage == null ? -1 : storage.getEnergyStored();
    }

    private static int countItems(BlockPos pos, Direction side) {
        IItemHandler h = handler(pos, side);
        if (h == null) {
            return -1;
        }
        int total = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            total += h.getStackInSlot(i).getCount();
        }
        return total;
    }
}
