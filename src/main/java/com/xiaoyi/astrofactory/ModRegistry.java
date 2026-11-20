package com.xiaoyi.astrofactory;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import com.xiaoyi.astrofactory.block.AssemblerBlock;
import com.xiaoyi.astrofactory.block.AssemblerBlockEntity;
import com.xiaoyi.astrofactory.block.BoilerBlock;
import com.xiaoyi.astrofactory.block.BoilerBlockEntity;
import com.xiaoyi.astrofactory.block.BurnerInserterBlock;
import com.xiaoyi.astrofactory.block.BurnerInserterBlockEntity;
import com.xiaoyi.astrofactory.block.BurnerMinerBlock;
import com.xiaoyi.astrofactory.block.BurnerMinerBlockEntity;
import com.xiaoyi.astrofactory.block.FastTransportBeltBlock;
import com.xiaoyi.astrofactory.block.FastTransportBeltBlockEntity;
import com.xiaoyi.astrofactory.block.LabBlock;
import com.xiaoyi.astrofactory.block.LabBlockEntity;
import com.xiaoyi.astrofactory.block.OffshorePumpBlock;
import com.xiaoyi.astrofactory.block.OffshorePumpBlockEntity;
import com.xiaoyi.astrofactory.block.OreVeinBlock;
import com.xiaoyi.astrofactory.block.OreVeinBlockEntity;
import com.xiaoyi.astrofactory.block.PowerPoleBlock;
import com.xiaoyi.astrofactory.block.PowerPoleBlockEntity;
import com.xiaoyi.astrofactory.block.SteamEngineBlock;
import com.xiaoyi.astrofactory.block.SteamEngineBlockEntity;
import com.xiaoyi.astrofactory.block.SteamPipeBlock;
import com.xiaoyi.astrofactory.block.SteamPipeBlockEntity;
import com.xiaoyi.astrofactory.block.StoneFurnaceBlock;
import com.xiaoyi.astrofactory.block.StoneFurnaceBlockEntity;
import com.xiaoyi.astrofactory.block.TransportBeltBlock;
import com.xiaoyi.astrofactory.block.TransportBeltBlockEntity;
import com.xiaoyi.astrofactory.item.GuideBookItem;
import com.xiaoyi.astrofactory.item.WeaponItem;
import com.xiaoyi.astrofactory.menu.AssemblerMenu;
import com.xiaoyi.astrofactory.menu.BoilerMenu;
import com.xiaoyi.astrofactory.menu.LabMenu;
import com.xiaoyi.astrofactory.menu.StoneFurnaceMenu;
import com.xiaoyi.astrofactory.registry.ModBlockEntities;
import com.xiaoyi.astrofactory.registry.ModBlocks;
import com.xiaoyi.astrofactory.registry.ModCreativeTab;
import com.xiaoyi.astrofactory.registry.ModItems;
import com.xiaoyi.astrofactory.registry.ModMenus;

/**
 * AstroFactory 全部注册的统一入口。
 *
 * <p>实际注册逻辑按职责拆到了 {@code registry} 包：</p>
 * <ul>
 *   <li>{@link ModBlocks} —— 方块</li>
 *   <li>{@link ModItems} —— 方块物品、中间物品、武器</li>
 *   <li>{@link ModBlockEntities} —— 方块实体类型</li>
 *   <li>{@link ModMenus} —— GUI 菜单类型</li>
 *   <li>{@link ModCreativeTab} —— 创造模式标签页</li>
 * </ul>
 *
 * <p>这里保留同名字段作为转发别名，所以既有代码（{@code ModRegistry.XXX}）无需改动。
 * 新代码建议直接用 {@code ModBlocks}/{@code ModItems} 等，职责更清楚。</p>
 *
 * <p>命名统一对齐开发提示词 v3 的规格 ID（Q10 决定）：
 * transport_belt / fast_transport_belt / burner_inserter / burner_miner /
 * stone_furnace / assembler / lab / iron_gear / copper_cable / electronic_circuit。</p>
 */
public final class ModRegistry {
    private ModRegistry() {}

    // ---------------------------------------------------------------- 方块

    public static final DeferredBlock<TransportBeltBlock> TRANSPORT_BELT = ModBlocks.TRANSPORT_BELT;
    public static final DeferredBlock<FastTransportBeltBlock> FAST_TRANSPORT_BELT =
            ModBlocks.FAST_TRANSPORT_BELT;
    public static final DeferredBlock<BurnerInserterBlock> BURNER_INSERTER = ModBlocks.BURNER_INSERTER;
    public static final DeferredBlock<BurnerMinerBlock> BURNER_MINER = ModBlocks.BURNER_MINER;
    public static final DeferredBlock<StoneFurnaceBlock> STONE_FURNACE = ModBlocks.STONE_FURNACE;
    public static final DeferredBlock<AssemblerBlock> ASSEMBLER = ModBlocks.ASSEMBLER;
    public static final DeferredBlock<LabBlock> LAB = ModBlocks.LAB;
    public static final DeferredBlock<OreVeinBlock> ORE_VEIN = ModBlocks.ORE_VEIN;
    public static final DeferredBlock<BoilerBlock> BOILER = ModBlocks.BOILER;
    public static final DeferredBlock<SteamEngineBlock> STEAM_ENGINE = ModBlocks.STEAM_ENGINE;
    public static final DeferredBlock<OffshorePumpBlock> OFFSHORE_PUMP = ModBlocks.OFFSHORE_PUMP;
    public static final DeferredBlock<SteamPipeBlock> STEAM_PIPE = ModBlocks.STEAM_PIPE;
    public static final DeferredBlock<PowerPoleBlock> POWER_POLE = ModBlocks.POWER_POLE;

    // ---------------------------------------------------------------- 物品

    public static final DeferredItem<net.minecraft.world.item.BlockItem> TRANSPORT_BELT_ITEM =
            ModItems.TRANSPORT_BELT_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FAST_TRANSPORT_BELT_ITEM =
            ModItems.FAST_TRANSPORT_BELT_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNER_INSERTER_ITEM =
            ModItems.BURNER_INSERTER_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNER_MINER_ITEM =
            ModItems.BURNER_MINER_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ASSEMBLER_ITEM =
            ModItems.ASSEMBLER_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STONE_FURNACE_ITEM =
            ModItems.STONE_FURNACE_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_ITEM = ModItems.LAB_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ORE_VEIN_ITEM =
            ModItems.ORE_VEIN_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BOILER_ITEM = ModItems.BOILER_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STEAM_ENGINE_ITEM =
            ModItems.STEAM_ENGINE_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> OFFSHORE_PUMP_ITEM =
            ModItems.OFFSHORE_PUMP_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STEAM_PIPE_ITEM =
            ModItems.STEAM_PIPE_ITEM;
    public static final DeferredItem<net.minecraft.world.item.BlockItem> POWER_POLE_ITEM =
            ModItems.POWER_POLE_ITEM;

    public static final DeferredItem<Item> IRON_GEAR = ModItems.IRON_GEAR;
    public static final DeferredItem<Item> COPPER_CABLE = ModItems.COPPER_CABLE;
    public static final DeferredItem<Item> ELECTRONIC_CIRCUIT = ModItems.ELECTRONIC_CIRCUIT;
    public static final DeferredItem<Item> AUTOMATION_PACK = ModItems.AUTOMATION_PACK;
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = ModItems.GUIDE_BOOK;
    public static final DeferredItem<WeaponItem> IRON_WRENCH = ModItems.IRON_WRENCH;
    public static final DeferredItem<WeaponItem> STEEL_WRENCH = ModItems.STEEL_WRENCH;
    public static final DeferredItem<WeaponItem> POWER_GAUNTLET = ModItems.POWER_GAUNTLET;
    public static final DeferredItem<WeaponItem> RIVET_GUN = ModItems.RIVET_GUN;

    // ---------------------------------------------------------------- 方块实体

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransportBeltBlockEntity>> TRANSPORT_BELT_BE =
            ModBlockEntities.TRANSPORT_BELT_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FastTransportBeltBlockEntity>> FAST_TRANSPORT_BELT_BE =
            ModBlockEntities.FAST_TRANSPORT_BELT_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerInserterBlockEntity>> BURNER_INSERTER_BE =
            ModBlockEntities.BURNER_INSERTER_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerMinerBlockEntity>> BURNER_MINER_BE =
            ModBlockEntities.BURNER_MINER_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_BE =
            ModBlockEntities.ASSEMBLER_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneFurnaceBlockEntity>> STONE_FURNACE_BE =
            ModBlockEntities.STONE_FURNACE_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabBlockEntity>> LAB_BE =
            ModBlockEntities.LAB_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreVeinBlockEntity>> ORE_VEIN_BE =
            ModBlockEntities.ORE_VEIN_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> BOILER_BE =
            ModBlockEntities.BOILER_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENGINE_BE =
            ModBlockEntities.STEAM_ENGINE_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OffshorePumpBlockEntity>> OFFSHORE_PUMP_BE =
            ModBlockEntities.OFFSHORE_PUMP_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamPipeBlockEntity>> STEAM_PIPE_BE =
            ModBlockEntities.STEAM_PIPE_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerPoleBlockEntity>> POWER_POLE_BE =
            ModBlockEntities.POWER_POLE_BE;

    // ---------------------------------------------------------------- 菜单

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<AssemblerMenu>> ASSEMBLER_MENU =
            ModMenus.ASSEMBLER_MENU;
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<LabMenu>> LAB_MENU = ModMenus.LAB_MENU;
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<StoneFurnaceMenu>> STONE_FURNACE_MENU =
            ModMenus.STONE_FURNACE_MENU;
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<BoilerMenu>> BOILER_MENU = ModMenus.BOILER_MENU;

    // ---------------------------------------------------------------- 创造模式标签页

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = ModCreativeTab.TAB;

    /** 按模块顺序注册到 mod 事件总线。 */
    public static void register(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTab.CREATIVE_TABS.register(modBus);
        com.xiaoyi.astrofactory.research.ResearchProgress.register(modBus);
        com.xiaoyi.astrofactory.level.PlayerLevel.register(modBus);
    }
}
