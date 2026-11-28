package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import com.astrofactory.block.CableBlock;
import com.astrofactory.block.PowerPoleBlock;
import com.astrofactory.block.ConveyorBlock;
import com.astrofactory.block.GeneratorBlock;
import com.astrofactory.block.CrateBlock;
import com.astrofactory.block.InserterBlock;
import com.astrofactory.block.MinerBlock;
import com.astrofactory.block.ResearchTableBlock;
import com.astrofactory.block.SmelterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册表。
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AstroFactory.MODID);

    /** 传送带：0.4 硬度，金属音效。 */
    public static final DeferredBlock<ConveyorBlock> CONVEYOR = BLOCKS.registerBlock(
            "conveyor",
            ConveyorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** 机械臂。 */
    public static final DeferredBlock<InserterBlock> INSERTER = BLOCKS.registerBlock(
            "inserter",
            InserterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** 采矿机。 */
    public static final DeferredBlock<MinerBlock> MINER = BLOCKS.registerBlock(
            "miner",
            MinerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** 自动熔炉。 */
    public static final DeferredBlock<SmelterBlock> SMELTER = BLOCKS.registerBlock(
            "smelter",
            SmelterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(SmelterBlock.LIT) ? 13 : 0)
                    .noOcclusion());

    /** 储物箱。 */
    public static final DeferredBlock<CrateBlock> CRATE = BLOCKS.registerBlock(
            "crate",
            CrateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD));

    /** 燃料发电机。 */
    public static final DeferredBlock<GeneratorBlock> GENERATOR = BLOCKS.registerBlock(
            "generator",
            GeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(GeneratorBlock.LIT) ? 12 : 0)
                    .noOcclusion());

    /** 电线杆（US-102）：覆盖式供电锚点。 */
    public static final DeferredBlock<PowerPoleBlock> POWER_POLE = BLOCKS.registerBlock(
            "power_pole",
            PowerPoleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    /** 电缆。 */
    public static final DeferredBlock<CableBlock> CABLE = BLOCKS.registerBlock(
            "cable",
            CableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(0.6F)
                    .sound(SoundType.WOOL)
                    .noOcclusion());

    /** 研究台：把研究包转成科技点。 */
    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.registerBlock(
            "research_table",
            ResearchTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    /** 组装机：电力自动合成（需「自动组装」科技）。 */
    public static final DeferredBlock<com.astrofactory.block.AssemblerBlock> ASSEMBLER = BLOCKS.registerBlock(
            "assembler",
            com.astrofactory.block.AssemblerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.astrofactory.block.AssemblerBlock.LIT) ? 10 : 0)
                    .noOcclusion());

    // ------------------------------------------------------------ v0.5 新增

    /** 木箱：9 格，贫瘠开局唯一存储（不加科技锁）。 */
    public static final DeferredBlock<com.astrofactory.block.WoodCrateBlock> WOOD_CRATE = BLOCKS.registerBlock(
            "wood_crate",
            com.astrofactory.block.WoodCrateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F)
                    .sound(SoundType.WOOD));

    /** 铁箱：54 格（需「物流学」科技）。 */
    public static final DeferredBlock<com.astrofactory.block.IronCrateBlock> IRON_CRATE = BLOCKS.registerBlock(
            "iron_crate",
            com.astrofactory.block.IronCrateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL));

    /** 分流器：左右交替分拣（需「物流学」科技）。 */
    public static final DeferredBlock<com.astrofactory.block.SplitterBlock> SPLITTER = BLOCKS.registerBlock(
            "splitter",
            com.astrofactory.block.SplitterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** 地下传送带：入口/出口配对传送（需「物流学」科技）。 */
    public static final DeferredBlock<com.astrofactory.block.UndergroundConveyorBlock> UNDERGROUND_CONVEYOR =
            BLOCKS.registerBlock(
                    "underground_conveyor",
                    com.astrofactory.block.UndergroundConveyorBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F)
                            .sound(SoundType.METAL)
                            .noOcclusion());

    /** 抽水机：邻水产水（需「蒸汽动力」科技）。 */
    public static final DeferredBlock<com.astrofactory.block.WaterPumpBlock> WATER_PUMP = BLOCKS.registerBlock(
            "water_pump",
            com.astrofactory.block.WaterPumpBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** 锅炉：燃料+水→蒸汽（需「蒸汽动力」科技）。 */
    public static final DeferredBlock<com.astrofactory.block.BoilerBlock> BOILER = BLOCKS.registerBlock(
            "boiler",
            com.astrofactory.block.BoilerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.astrofactory.block.BoilerBlock.LIT) ? 13 : 0)
                    .noOcclusion());

    /** 蒸汽机：蒸汽→FE（需「蒸汽动力」科技）。 */
    public static final DeferredBlock<com.astrofactory.block.SteamEngineBlock> STEAM_ENGINE = BLOCKS.registerBlock(
            "steam_engine",
            com.astrofactory.block.SteamEngineBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** 实验室：耗电吃红瓶推进研究（US-106）。 */
    public static final DeferredBlock<com.astrofactory.block.LabBlock> LAB = BLOCKS.registerBlock(
            "lab",
            com.astrofactory.block.LabBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.astrofactory.block.LabBlock.LIT) ? 9 : 0)
                    .noOcclusion());

    /** 矿脉（世界生成产物，v0.8.9 起分矿种；异星工厂风格）。 */
    public static final DeferredBlock<com.astrofactory.block.OreFieldBlock> ORE_FIELD_STONE = BLOCKS.registerBlock(
            "ore_field_stone",
            com.astrofactory.block.StoneField::new,
            com.astrofactory.block.OreFieldBlock.oreFieldProperties());
    public static final DeferredBlock<com.astrofactory.block.OreFieldBlock> ORE_FIELD_COPPER = BLOCKS.registerBlock(
            "ore_field_copper",
            com.astrofactory.block.CopperField::new,
            com.astrofactory.block.OreFieldBlock.oreFieldProperties());
    public static final DeferredBlock<com.astrofactory.block.OreFieldBlock> ORE_FIELD_IRON = BLOCKS.registerBlock(
            "ore_field_iron",
            com.astrofactory.block.IronField::new,
            com.astrofactory.block.OreFieldBlock.oreFieldProperties());
    public static final DeferredBlock<com.astrofactory.block.OreFieldBlock> ORE_FIELD_COAL = BLOCKS.registerBlock(
            "ore_field_coal",
            com.astrofactory.block.CoalField::new,
            com.astrofactory.block.OreFieldBlock.oreFieldProperties());

    private ModBlocks() {}
}
