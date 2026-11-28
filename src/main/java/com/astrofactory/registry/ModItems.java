package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import com.astrofactory.item.TechGatedBlockItem;
import com.astrofactory.item.WrenchItem;
import com.astrofactory.tech.TechNodes;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册表：这里只注册方块对应的 BlockItem。
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstroFactory.MODID);

    // -------- v0.5 贫瘠开局：开局仅研究台/研究包/扳手/木箱可用，其余加科技锁 --------

    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONVEYOR =
            ITEMS.register("conveyor", () -> new TechGatedBlockItem(
                    ModBlocks.CONVEYOR.get(), new Item.Properties(), TechNodes.BASIC_AUTOMATION));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> INSERTER =
            ITEMS.register("inserter", () -> new TechGatedBlockItem(
                    ModBlocks.INSERTER.get(), new Item.Properties(), TechNodes.BASIC_AUTOMATION));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> MINER =
            ITEMS.register("miner", () -> new TechGatedBlockItem(
                    ModBlocks.MINER.get(), new Item.Properties(), TechNodes.MINING));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> SMELTER =
            ITEMS.register("smelter", () -> new TechGatedBlockItem(
                    ModBlocks.SMELTER.get(), new Item.Properties(), TechNodes.SMELTING));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> CRATE =
            ITEMS.register("crate", () -> new TechGatedBlockItem(
                    ModBlocks.CRATE.get(), new Item.Properties(), TechNodes.BASIC_AUTOMATION));

    /** 木箱：9 格，贫瘠开局唯一存储（不加锁）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WOOD_CRATE =
            ITEMS.registerSimpleBlockItem("wood_crate", ModBlocks.WOOD_CRATE);

    /** 铁箱：54 格（需「物流学」科技）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> IRON_CRATE =
            ITEMS.register("iron_crate", () -> new TechGatedBlockItem(
                    ModBlocks.IRON_CRATE.get(), new Item.Properties(), TechNodes.LOGISTICS));

    /** 分流器（需「物流学」科技）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SPLITTER =
            ITEMS.register("splitter", () -> new TechGatedBlockItem(
                    ModBlocks.SPLITTER.get(), new Item.Properties(), TechNodes.LOGISTICS));

    /** 地下传送带（需「物流学」科技）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> UNDERGROUND_CONVEYOR =
            ITEMS.register("underground_conveyor", () -> new TechGatedBlockItem(
                    ModBlocks.UNDERGROUND_CONVEYOR.get(), new Item.Properties(), TechNodes.LOGISTICS));

    /** 抽水机（需「蒸汽动力」科技）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WATER_PUMP =
            ITEMS.register("water_pump", () -> new TechGatedBlockItem(
                    ModBlocks.WATER_PUMP.get(), new Item.Properties(), TechNodes.STEAM_POWER));

    /** 锅炉（需「蒸汽动力」科技）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BOILER =
            ITEMS.register("boiler", () -> new TechGatedBlockItem(
                    ModBlocks.BOILER.get(), new Item.Properties(), TechNodes.STEAM_POWER));

    /** 蒸汽机（需「蒸汽动力」科技）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STEAM_ENGINE =
            ITEMS.register("steam_engine", () -> new TechGatedBlockItem(
                    ModBlocks.STEAM_ENGINE.get(), new Item.Properties(), TechNodes.STEAM_POWER));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> GENERATOR =
            ITEMS.register("generator", () -> new TechGatedBlockItem(
                    ModBlocks.GENERATOR.get(), new Item.Properties(), TechNodes.ELECTRIC_POWER));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> CABLE =
            ITEMS.register("cable", () -> new TechGatedBlockItem(
                    ModBlocks.CABLE.get(), new Item.Properties(), TechNodes.ELECTRIC_POWER));

    /** 电线杆（US-102）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> POWER_POLE =
            ITEMS.register("power_pole", () -> new TechGatedBlockItem(
                    ModBlocks.POWER_POLE.get(), new Item.Properties(), TechNodes.ELECTRIC_POWER));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> RESEARCH_TABLE =
            ITEMS.registerSimpleBlockItem("research_table", ModBlocks.RESEARCH_TABLE);

    /** 扳手：把机器从燃料模式升级为电力模式。 */
    public static final DeferredItem<WrenchItem> WRENCH =
            ITEMS.register("wrench", () -> new WrenchItem(
                    new Item.Properties().stacksTo(1).durability(64)));

    /** 研究包：投入研究台换取科技点。 */
    public static final DeferredItem<Item> RESEARCH_PACK =
            ITEMS.register("research_pack", () -> new Item(new Item.Properties()));

    /** 高级研究包：电力时代后的科技需要（需「高级研究」科技解锁配方）。 */
    public static final DeferredItem<Item> ADVANCED_RESEARCH_PACK =
            ITEMS.register("advanced_research_pack", () -> new Item(new Item.Properties()));

    /** 科技终端：随身打开科技树 GUI。 */
    public static final DeferredItem<com.astrofactory.item.TechTerminalItem> TECH_TERMINAL =
            ITEMS.register("tech_terminal", () -> new com.astrofactory.item.TechTerminalItem(
                    new Item.Properties().stacksTo(1)));

    // -------- v0.6 产物链：钢板 / 铜缆 / 电路板（对应异星工厂 steel / copper cable / electronic circuit） --------

    /** 钢板：铁锭在熔炉中二次冶炼（需「钢材」科技）。 */
    public static final DeferredItem<Item> STEEL_PLATE =
            ITEMS.register("steel_plate", () -> new Item(new Item.Properties()));

    /** 铜缆：铜锭合成（电路板原料）。 */
    public static final DeferredItem<Item> COPPER_CABLE =
            ITEMS.register("copper_cable", () -> new Item(new Item.Properties()));

    /** 电路板：铜缆 + 铁锭合成（高级研究包 / 科技终端 / 组装机原料）。 */
    public static final DeferredItem<Item> CIRCUIT =
            ITEMS.register("circuit", () -> new Item(new Item.Properties()));

    /** 组装机 BlockItem（科技锁：自动组装）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ASSEMBLER =
            ITEMS.register("assembler", () -> new TechGatedBlockItem(
                    ModBlocks.ASSEMBLER.get(), new Item.Properties(), TechNodes.AUTOMATION));

    /** 四种矿脉 BlockItem（无科技锁——世界生成资源，创世者可自由取出铺矿脉）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ORE_FIELD_STONE =
            ITEMS.registerSimpleBlockItem("ore_field_stone", ModBlocks.ORE_FIELD_STONE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ORE_FIELD_COPPER =
            ITEMS.registerSimpleBlockItem("ore_field_copper", ModBlocks.ORE_FIELD_COPPER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ORE_FIELD_IRON =
            ITEMS.registerSimpleBlockItem("ore_field_iron", ModBlocks.ORE_FIELD_IRON);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ORE_FIELD_COAL =
            ITEMS.registerSimpleBlockItem("ore_field_coal", ModBlocks.ORE_FIELD_COAL);

    /** 实验室 BlockItem（US-106，科技锁：电力时代——实验室是电力研究机器）。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB =
            ITEMS.register("lab", () -> new TechGatedBlockItem(
                    ModBlocks.LAB.get(), new Item.Properties(), TechNodes.ELECTRIC_POWER));

    // -------- US-104 产物链：铁板 / 铜板 / 铁齿轮 / 铜线材 / 红瓶 --------
    // 组装机三个初始配方（铁齿轮 / 铜线材 / 红瓶）的输入输出物。

    /** 铁板（iron plate）：组装机基础原料。 */
    public static final DeferredItem<Item> IRON_PLATE =
            ITEMS.register("iron_plate", () -> new Item(new Item.Properties()));

    /** 铜板（copper plate）：组装机基础原料。 */
    public static final DeferredItem<Item> COPPER_PLATE =
            ITEMS.register("copper_plate", () -> new Item(new Item.Properties()));

    /** 铁齿轮（iron gear wheel）：2 铁板组装而成。 */
    public static final DeferredItem<Item> IRON_GEAR =
            ITEMS.register("iron_gear", () -> new Item(new Item.Properties()));

    /** 铜线材（copper wire）：1 铜板 → 2 线。 */
    public static final DeferredItem<Item> COPPER_WIRE =
            ITEMS.register("copper_wire", () -> new Item(new Item.Properties()));

    /** 红瓶（红色科技瓶）：齿轮 + 铜板组装，供实验室消费（US-106）。 */
    public static final DeferredItem<Item> RED_SCIENCE_PACK =
            ITEMS.register("red_science_pack", () -> new Item(new Item.Properties()));

    private ModItems() {}
}
