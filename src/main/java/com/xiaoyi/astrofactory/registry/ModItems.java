package com.xiaoyi.astrofactory.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.item.GuideBookItem;
import com.xiaoyi.astrofactory.item.WeaponItem;
import com.xiaoyi.astrofactory.weapon.Weapons;

/**
 * 物品注册：方块物品 + 中间物品 + 工具武器。
 *
 * <p>从原 {@code ModRegistry} 按职责拆出，只负责「物品」这一类。
 * 对外仍通过 {@code ModRegistry.XXX} 访问。</p>
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Astrofactory.MODID);

    // ---------------------------------------------------------------- 方块物品

    public static final DeferredItem<BlockItem> TRANSPORT_BELT_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> FAST_TRANSPORT_BELT_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.FAST_TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> BURNER_INSERTER_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.BURNER_INSERTER);
    public static final DeferredItem<BlockItem> BURNER_MINER_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.BURNER_MINER);
    public static final DeferredItem<BlockItem> ASSEMBLER_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLER);
    public static final DeferredItem<BlockItem> STONE_FURNACE_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.STONE_FURNACE);
    public static final DeferredItem<BlockItem> LAB_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.LAB);

    /** 矿脉方块物品（创造栏用，实际由世界生成放置）。 */
    public static final DeferredItem<BlockItem> ORE_VEIN_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.ORE_VEIN);

    public static final DeferredItem<BlockItem> BOILER_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.BOILER);
    public static final DeferredItem<BlockItem> STEAM_ENGINE_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_ENGINE);
    public static final DeferredItem<BlockItem> OFFSHORE_PUMP_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.OFFSHORE_PUMP);
    public static final DeferredItem<BlockItem> STEAM_PIPE_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_PIPE);
    public static final DeferredItem<BlockItem> POWER_POLE_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.POWER_POLE);

    // ---------------------------------------------------------------- 中间物品

    /** 铁齿轮：基础机械件，铁锭 ×2 → 1 个（熔炼炉 crafting 通道，见 machine_recipes）。 */
    public static final DeferredItem<Item> IRON_GEAR = ITEMS.registerSimpleItem("iron_gear");

    /** 铜线：导线类零件，铜锭 ×3 → 1 个（熔炼炉 crafting 通道，见 machine_recipes）。 */
    public static final DeferredItem<Item> COPPER_CABLE = ITEMS.registerSimpleItem("copper_cable");

    /** 电路板：铁齿轮 ×2 + 铜线 ×2，组装机 3×3 网格（gcg/.c./...）。 */
    public static final DeferredItem<Item> ELECTRONIC_CIRCUIT =
            ITEMS.registerSimpleItem("electronic_circuit");

    /** 红瓶（自动化科技包）：铜锭 ×2 + 铁齿轮 ×1 → 2 个，可手搓（工作台）。 */
    public static final DeferredItem<Item> AUTOMATION_PACK =
            ITEMS.registerSimpleItem("automation_pack");

    /** 新手手册：右键展开 10 步教程（规格 §8 出生点包）。 */
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK =
            ITEMS.registerItem("guide_book",
                    GuideBookItem::new,
                    () -> new Item.Properties().stacksTo(1));

    // ---------------------------------------------------------------- 武器（story 系统）
    // 0.1.0：4 把工业工具武器，等级门槛见 Weapons.java。

    public static final DeferredItem<WeaponItem> IRON_WRENCH =
            ITEMS.registerItem("iron_wrench",
                    props -> new WeaponItem(props, "iron_wrench", Weapons.meleeAttributes(3.0, -3.2f)),
                    () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<WeaponItem> STEEL_WRENCH =
            ITEMS.registerItem("steel_wrench",
                    props -> new WeaponItem(props, "steel_wrench", Weapons.meleeAttributes(5.0, -3.1f)),
                    () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<WeaponItem> POWER_GAUNTLET =
            ITEMS.registerItem("power_gauntlet",
                    props -> new WeaponItem(props, "power_gauntlet", Weapons.meleeAttributes(7.0, -3.0f)),
                    () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<WeaponItem> RIVET_GUN =
            ITEMS.registerItem("rivet_gun",
                    props -> new WeaponItem(props, "rivet_gun", Weapons.meleeAttributes(2.0, -2.8f)),
                    () -> new Item.Properties().stacksTo(1));
}
