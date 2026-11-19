package com.xiaoyi.astrofactory;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.block.AssemblerBlock;
import com.xiaoyi.astrofactory.block.AssemblerBlockEntity;
import com.xiaoyi.astrofactory.block.BurnerInserterBlock;
import com.xiaoyi.astrofactory.block.BurnerInserterBlockEntity;
import com.xiaoyi.astrofactory.block.BurnerMinerBlock;
import com.xiaoyi.astrofactory.block.BurnerMinerBlockEntity;
import com.xiaoyi.astrofactory.block.FastTransportBeltBlock;
import com.xiaoyi.astrofactory.block.FastTransportBeltBlockEntity;
import com.xiaoyi.astrofactory.block.LabBlock;
import com.xiaoyi.astrofactory.block.LabBlockEntity;
import com.xiaoyi.astrofactory.block.StoneFurnaceBlock;
import com.xiaoyi.astrofactory.block.StoneFurnaceBlockEntity;
import com.xiaoyi.astrofactory.block.TransportBeltBlock;
import com.xiaoyi.astrofactory.block.TransportBeltBlockEntity;
import com.xiaoyi.astrofactory.menu.AssemblerMenu;
import com.xiaoyi.astrofactory.menu.LabMenu;
import com.xiaoyi.astrofactory.menu.StoneFurnaceMenu;

import java.util.Set;

/**
 * AstroFactory 全部注册入口。
 *
 * <p>命名统一对齐开发提示词 v3 的规格 ID（Q10 决定）：
 * transport_belt / fast_transport_belt / burner_inserter / burner_miner /
 * stone_furnace / assembler / lab / iron_gear / copper_cable / electronic_circuit。</p>
 *
 * <p>26.3 注意：ResourceLocation 已更名为 net.minecraft.resources.Identifier；
 * BlockEntityType 已无 Builder，直接用公开构造器 (supplier, Set&lt;Block&gt;)。</p>
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

    /** 传送带：真连续移动，无 GUI，带朝向。开局免费可造。 */
    public static final DeferredBlock<TransportBeltBlock> TRANSPORT_BELT = BLOCKS.registerBlock(
            "transport_belt",
            TransportBeltBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion());

    /** 高速传送带：logistics 科技解锁。 */
    public static final DeferredBlock<FastTransportBeltBlock> FAST_TRANSPORT_BELT =
            BLOCKS.registerBlock(
                    "fast_transport_belt",
                    FastTransportBeltBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.5f, 6.0f)
                            .noOcclusion());

    /** 燃烧机械臂：抓取 / 投放，自己烧煤，会偷燃料。 */
    public static final DeferredBlock<BurnerInserterBlock> BURNER_INSERTER = BLOCKS.registerBlock(
            "burner_inserter",
            BurnerInserterBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 6.0f)
                    .noOcclusion());

    /** 燃烧采矿机：2×2 多方块，覆盖下方 2×2，烧煤产出。 */
    public static final DeferredBlock<BurnerMinerBlock> BURNER_MINER = BLOCKS.registerBlock(
            "burner_miner",
            BurnerMinerBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f, 8.0f)
                    .noOcclusion());

    /** 石熔炉：2×2 多方块，烧煤熔炼。 */
    public static final DeferredBlock<StoneFurnaceBlock> STONE_FURNACE = BLOCKS.registerBlock(
            "stone_furnace",
            StoneFurnaceBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f, 8.0f)
                    .noOcclusion());

    /** 组装机：3×3 多方块，电力驱动，按配方自动合成。 */
    public static final DeferredBlock<AssemblerBlock> ASSEMBLER = BLOCKS.registerBlock(
            "assembler",
            AssemblerBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 8.0f)
                    .noOcclusion());

    /** 科研台（lab）：3×3 多方块，电力驱动，推进科技树。 */
    public static final DeferredBlock<LabBlock> LAB = BLOCKS.registerBlock(
            "lab",
            LabBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f, 4.0f)
                    .noOcclusion());

    // ---------------------------------------------------------------- 方块物品

    public static final DeferredItem<net.minecraft.world.item.BlockItem> TRANSPORT_BELT_ITEM =
            ITEMS.registerSimpleBlockItem(TRANSPORT_BELT);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FAST_TRANSPORT_BELT_ITEM =
            ITEMS.registerSimpleBlockItem(FAST_TRANSPORT_BELT);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNER_INSERTER_ITEM =
            ITEMS.registerSimpleBlockItem(BURNER_INSERTER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNER_MINER_ITEM =
            ITEMS.registerSimpleBlockItem(BURNER_MINER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ASSEMBLER_ITEM =
            ITEMS.registerSimpleBlockItem(ASSEMBLER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STONE_FURNACE_ITEM =
            ITEMS.registerSimpleBlockItem(STONE_FURNACE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_ITEM =
            ITEMS.registerSimpleBlockItem(LAB);

    // ---------------------------------------------------------------- 中间物品

    /** 铁齿轮：基础机械件，由铁板 ×2 冲压而成。 */
    public static final DeferredItem<net.minecraft.world.item.Item> IRON_GEAR =
            ITEMS.registerSimpleItem("iron_gear");

    /** 铜线：铜板 ×1 → 2 个。 */
    public static final DeferredItem<net.minecraft.world.item.Item> COPPER_CABLE =
            ITEMS.registerSimpleItem("copper_cable");

    /** 电路板：铁板 ×1 + 铜线 ×3。 */
    public static final DeferredItem<net.minecraft.world.item.Item> ELECTRONIC_CIRCUIT =
            ITEMS.registerSimpleItem("electronic_circuit");

    /** 红瓶（自动化科技包）：铜板 ×1 + 铁齿轮 ×1，可手搓。 */
    public static final DeferredItem<net.minecraft.world.item.Item> AUTOMATION_PACK =
            ITEMS.registerSimpleItem("automation_pack");

    // ---------------------------------------------------------------- 方块实体

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransportBeltBlockEntity>> TRANSPORT_BELT_BE =
            BLOCK_ENTITY_TYPES.register("transport_belt",
                    () -> new BlockEntityType<>(TransportBeltBlockEntity::new, Set.of(TRANSPORT_BELT.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FastTransportBeltBlockEntity>> FAST_TRANSPORT_BELT_BE =
            BLOCK_ENTITY_TYPES.register("fast_transport_belt",
                    () -> new BlockEntityType<>(
                            FastTransportBeltBlockEntity::new,
                            Set.of(FAST_TRANSPORT_BELT.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerInserterBlockEntity>> BURNER_INSERTER_BE =
            BLOCK_ENTITY_TYPES.register("burner_inserter",
                    () -> new BlockEntityType<>(BurnerInserterBlockEntity::new, Set.of(BURNER_INSERTER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerMinerBlockEntity>> BURNER_MINER_BE =
            BLOCK_ENTITY_TYPES.register("burner_miner",
                    () -> new BlockEntityType<>(BurnerMinerBlockEntity::new, Set.of(BURNER_MINER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_BE =
            BLOCK_ENTITY_TYPES.register("assembler",
                    () -> new BlockEntityType<>(AssemblerBlockEntity::new, Set.of(ASSEMBLER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneFurnaceBlockEntity>> STONE_FURNACE_BE =
            BLOCK_ENTITY_TYPES.register("stone_furnace",
                    () -> new BlockEntityType<>(
                            StoneFurnaceBlockEntity::new,
                            Set.of(STONE_FURNACE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabBlockEntity>> LAB_BE =
            BLOCK_ENTITY_TYPES.register("lab",
                    () -> new BlockEntityType<>(
                            LabBlockEntity::new,
                            Set.of(LAB.get())));

    // ---------------------------------------------------------------- 菜单

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<AssemblerMenu>> ASSEMBLER_MENU =
            MENUS.register("assembler", () -> AssemblerMenu.TYPE);

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<LabMenu>> LAB_MENU =
            MENUS.register("lab", () -> LabMenu.TYPE);

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<StoneFurnaceMenu>> STONE_FURNACE_MENU =
            MENUS.register("stone_furnace", () -> StoneFurnaceMenu.TYPE);

    // ---------------------------------------------------------------- 创造模式标签页

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.astrofactory"))
                    .icon(() -> new ItemStack(TRANSPORT_BELT_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(TRANSPORT_BELT_ITEM.get());
                        output.accept(FAST_TRANSPORT_BELT_ITEM.get());
                        output.accept(BURNER_INSERTER_ITEM.get());
                        output.accept(BURNER_MINER_ITEM.get());
                        output.accept(STONE_FURNACE_ITEM.get());
                        output.accept(ASSEMBLER_ITEM.get());
                        output.accept(LAB_ITEM.get());
                        output.accept(IRON_GEAR.get());
                        output.accept(COPPER_CABLE.get());
                        output.accept(ELECTRONIC_CIRCUIT.get());
                        output.accept(AUTOMATION_PACK.get());
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
