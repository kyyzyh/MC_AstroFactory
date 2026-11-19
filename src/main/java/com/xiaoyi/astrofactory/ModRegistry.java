package com.xiaoyi.astrofactory;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.block.ConveyorBlock;
import com.xiaoyi.astrofactory.block.ConveyorBlockEntity;
import com.xiaoyi.astrofactory.block.RoboticArmBlock;
import com.xiaoyi.astrofactory.block.RoboticArmBlockEntity;
import com.xiaoyi.astrofactory.block.MinerBlock;
import com.xiaoyi.astrofactory.block.MinerBlockEntity;
import com.xiaoyi.astrofactory.block.AssemblerBlock;
import com.xiaoyi.astrofactory.block.ResearchTableBlock;
import com.xiaoyi.astrofactory.block.AssemblerBlockEntity;

import java.util.Set;

/**
 * AstroFactory 全部注册入口。
 * 26.3 注意：ResourceLocation 已更名为 net.minecraft.resources.Identifier；
 * BlockEntityType 已无 Builder，直接用公开构造器 (supplier, Set<Block>)。
 */
public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Astrofactory.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Astrofactory.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Astrofactory.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Astrofactory.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Astrofactory.MODID);

    // ---------------------------------------------------------------- 方块

    /** 传送带：真连续移动，无 GUI，带朝向。 */
    public static final DeferredBlock<ConveyorBlock> CONVEYOR = BLOCKS.registerBlock(
            "conveyor",
            ConveyorBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion());

    /** 高速传送带：带速 2 倍，由科技 logistics_1 解锁。 */
    public static final DeferredBlock<com.xiaoyi.astrofactory.block.FastConveyorBlock> FAST_CONVEYOR =
            BLOCKS.registerBlock(
                    "fast_conveyor",
                    com.xiaoyi.astrofactory.block.FastConveyorBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.5f, 6.0f)
                            .noOcclusion());

    /** 机械臂：抓取 / 投放。 */
    public static final DeferredBlock<RoboticArmBlock> ROBOTIC_ARM = BLOCKS.registerBlock(
            "robotic_arm",
            RoboticArmBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 6.0f)
                    .noOcclusion());

    /** 采矿机：无限矿脉，随时间产出。 */
    public static final DeferredBlock<MinerBlock> MINER = BLOCKS.registerBlock(
            "miner",
            MinerBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f, 8.0f)
                    .noOcclusion());

    /** 熔炼炉：原矿 -> 锭。 */
    public static final DeferredBlock<com.xiaoyi.astrofactory.block.SmelterBlock> SMELTER = BLOCKS.registerBlock(
            "smelter",
            com.xiaoyi.astrofactory.block.SmelterBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f, 8.0f)
                    .noOcclusion());

    /** 制作台：锭 -> 零件。 */
    public static final DeferredBlock<com.xiaoyi.astrofactory.block.CrafterBlock> CRAFTER = BLOCKS.registerBlock(
            "crafter",
            com.xiaoyi.astrofactory.block.CrafterBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f, 6.0f)
                    .noOcclusion());

    /** 组装机：按配方自动合成。 */
    public static final DeferredBlock<AssemblerBlock> ASSEMBLER = BLOCKS.registerBlock(
            "assembler",
            AssemblerBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 8.0f)
                    .noOcclusion());

    /** 科研台：推进科技树。 */
    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.registerBlock(
            "research_table",
            ResearchTableBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f, 4.0f)
                    .noOcclusion());

    // ---------------------------------------------------------------- 方块物品

    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONVEYOR_ITEM =
            ITEMS.registerSimpleBlockItem(CONVEYOR);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FAST_CONVEYOR_ITEM =
            ITEMS.registerSimpleBlockItem(FAST_CONVEYOR);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ROBOTIC_ARM_ITEM =
            ITEMS.registerSimpleBlockItem(ROBOTIC_ARM);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MINER_ITEM =
            ITEMS.registerSimpleBlockItem(MINER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ASSEMBLER_ITEM =
            ITEMS.registerSimpleBlockItem(ASSEMBLER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SMELTER_ITEM =
            ITEMS.registerSimpleBlockItem(SMELTER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CRAFTER_ITEM =
            ITEMS.registerSimpleBlockItem(CRAFTER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RESEARCH_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem(RESEARCH_TABLE);

    // ---------------------------------------------------------------- 中间物品

    /** 铁齿轮：基础机械件，由铁锭冲压而成。 */
    public static final DeferredItem<net.minecraft.world.item.Item> IRON_GEAR =
            ITEMS.registerSimpleItem("iron_gear");

    /** 铜线圈：由铜锭 + 铁齿轮绕制。 */
    public static final DeferredItem<net.minecraft.world.item.Item> COPPER_COIL =
            ITEMS.registerSimpleItem("copper_coil");

    /** 电路板：由金锭 + 红石 + 铜线圈制成。 */
    public static final DeferredItem<net.minecraft.world.item.Item> CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("circuit_board");

    // ---------------------------------------------------------------- 方块实体

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConveyorBlockEntity>> CONVEYOR_BE =
            BLOCK_ENTITY_TYPES.register("conveyor",
                    () -> new BlockEntityType<>(ConveyorBlockEntity::new, Set.of(CONVEYOR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.xiaoyi.astrofactory.block.FastConveyorBlockEntity>> FAST_CONVEYOR_BE =
            BLOCK_ENTITY_TYPES.register("fast_conveyor",
                    () -> new BlockEntityType<>(
                            com.xiaoyi.astrofactory.block.FastConveyorBlockEntity::new,
                            Set.of(FAST_CONVEYOR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RoboticArmBlockEntity>> ROBOTIC_ARM_BE =
            BLOCK_ENTITY_TYPES.register("robotic_arm",
                    () -> new BlockEntityType<>(RoboticArmBlockEntity::new, Set.of(ROBOTIC_ARM.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MinerBlockEntity>> MINER_BE =
            BLOCK_ENTITY_TYPES.register("miner",
                    () -> new BlockEntityType<>(MinerBlockEntity::new, Set.of(MINER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_BE =
            BLOCK_ENTITY_TYPES.register("assembler",
                    () -> new BlockEntityType<>(AssemblerBlockEntity::new, Set.of(ASSEMBLER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.xiaoyi.astrofactory.block.SmelterBlockEntity>> SMELTER_BE =
            BLOCK_ENTITY_TYPES.register("smelter",
                    () -> new BlockEntityType<>(
                            com.xiaoyi.astrofactory.block.SmelterBlockEntity::new,
                            Set.of(SMELTER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.xiaoyi.astrofactory.block.CrafterBlockEntity>> CRAFTER_BE =
            BLOCK_ENTITY_TYPES.register("crafter",
                    () -> new BlockEntityType<>(
                            com.xiaoyi.astrofactory.block.CrafterBlockEntity::new,
                            Set.of(CRAFTER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.xiaoyi.astrofactory.block.ResearchTableBlockEntity>> RESEARCH_TABLE_BE =
            BLOCK_ENTITY_TYPES.register("research_table",
                    () -> new BlockEntityType<>(
                            com.xiaoyi.astrofactory.block.ResearchTableBlockEntity::new,
                            Set.of(RESEARCH_TABLE.get())));

    // ---------------------------------------------------------------- 菜单

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.xiaoyi.astrofactory.menu.AssemblerMenu>> ASSEMBLER_MENU =
            MENUS.register("assembler", () -> com.xiaoyi.astrofactory.menu.AssemblerMenu.TYPE);

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.xiaoyi.astrofactory.menu.ResearchMenu>> RESEARCH_MENU =
            MENUS.register("research_table", () -> com.xiaoyi.astrofactory.menu.ResearchMenu.TYPE);

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.xiaoyi.astrofactory.menu.SmelterMenu>> SMELTER_MENU =
            MENUS.register("smelter", () -> com.xiaoyi.astrofactory.menu.SmelterMenu.TYPE);

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.xiaoyi.astrofactory.menu.CrafterMenu>> CRAFTER_MENU =
            MENUS.register("crafter", () -> com.xiaoyi.astrofactory.menu.CrafterMenu.TYPE);

    // ---------------------------------------------------------------- 创造模式标签页

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.astrofactory"))
                    .icon(() -> new ItemStack(CONVEYOR_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(CONVEYOR_ITEM.get());
                        output.accept(FAST_CONVEYOR_ITEM.get());
                        output.accept(ROBOTIC_ARM_ITEM.get());
                        output.accept(MINER_ITEM.get());
                        output.accept(SMELTER_ITEM.get());
                        output.accept(CRAFTER_ITEM.get());
                        output.accept(ASSEMBLER_ITEM.get());
                        output.accept(RESEARCH_TABLE_ITEM.get());
                        output.accept(IRON_GEAR.get());
                        output.accept(COPPER_COIL.get());
                        output.accept(CIRCUIT_BOARD.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
        com.xiaoyi.astrofactory.research.ResearchProgress.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
