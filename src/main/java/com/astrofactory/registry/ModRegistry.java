package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import com.astrofactory.network.ModNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 统一注册入口，主类只需调用一次。
 */
public final class ModRegistry {

    private ModRegistry() {}

    public static void register(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);
        // US-104：组装机自定义配方类型
        ModRecipes.register(modBus);
        // v0.8.9：冰山矿体 Feature
        ModFeatures.FEATURES.register(modBus);

        modBus.addListener(ModRegistry::registerCapabilities);
        modBus.addListener(ModRegistry::registerPayloads);
    }

    /** 注册网络 payload（科技进度同步 + 选定研究）。 */
    private static void registerPayloads(
            net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        ModNetwork.register(event.registrar("1"));
    }

    /** 把方块实体的物品栏/储能暴露为能力，机械臂、漏斗、电缆才能读写。 */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CRATE.get(),
                (be, side) -> be.getInventory());

        // v0.5：木箱 / 铁箱
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.WOOD_CRATE.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.IRON_CRATE.get(),
                (be, side) -> be.getInventory());

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SMELTER.get(),
                (be, side) -> {
                    if (side == null) {
                        return be.getInput();
                    }
                    // 朝向面为输出面，其余面为输入面
                    var facing = be.getBlockState().getValue(com.astrofactory.block.SmelterBlock.FACING);
                    return side == facing ? be.getOutput() : be.getInput();
                });

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.RESEARCH_TABLE.get(),
                (be, side) -> be.getInput());

        // ---- 能量能力 ----
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.GENERATOR.get(),
                (be, side) -> be.getEnergy());

        // v0.5：蒸汽机（与发电机同规格，供电缆抽取）
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STEAM_ENGINE.get(),
                (be, side) -> be.getEnergy());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MINER.get(),
                (be, side) -> be.getEnergyBuffer());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SMELTER.get(),
                (be, side) -> be.getEnergyBuffer());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.INSERTER.get(),
                (be, side) -> be.getEnergyBuffer());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ASSEMBLER.get(),
                (be, side) -> be.getEnergyBuffer());

        // US-106：实验室（能量 + 红瓶输入）
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.LAB.get(),
                (be, side) -> be.getEnergyBuffer());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.LAB.get(),
                (be, side) -> be.getRedInput());

        // 组装机物品能力：网格输入 + 输出（朝向面为输出）
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ASSEMBLER.get(),
                (be, side) -> {
                    if (side == null) {
                        return be.getSlots();
                    }
                    var facing = be.getBlockState().getValue(com.astrofactory.block.AssemblerBlock.FACING);
                    return side == facing ? be.getSlots() : be.getSlots();
                });
    }
}
