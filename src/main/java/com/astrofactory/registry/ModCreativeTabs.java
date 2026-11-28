package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 创造模式标签页注册。
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AstroFactory.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FACTORY_TAB = TABS.register(
            "factory",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.astrofactory"))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> ModBlocks.CONVEYOR.get().asItem().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.CONVEYOR.get());
                        output.accept(ModBlocks.INSERTER.get());
                        output.accept(ModBlocks.MINER.get());
                        output.accept(ModBlocks.SMELTER.get());
                        output.accept(ModBlocks.CRATE.get());
                        output.accept(ModBlocks.GENERATOR.get());
                        output.accept(ModBlocks.CABLE.get());
                        output.accept(ModBlocks.POWER_POLE.get());
                        output.accept(ModBlocks.RESEARCH_TABLE.get());
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.RESEARCH_PACK.get());
                        output.accept(ModItems.ADVANCED_RESEARCH_PACK.get());
                        output.accept(ModItems.TECH_TERMINAL.get());
                        output.accept(ModItems.STEEL_PLATE.get());
                        output.accept(ModItems.COPPER_CABLE.get());
                        output.accept(ModItems.CIRCUIT.get());
                        // US-104：组装机产物链
                        output.accept(ModItems.IRON_PLATE.get());
                        output.accept(ModItems.COPPER_PLATE.get());
                        output.accept(ModItems.IRON_GEAR.get());
                        output.accept(ModItems.COPPER_WIRE.get());
                        output.accept(ModItems.RED_SCIENCE_PACK.get());
                        output.accept(ModBlocks.ASSEMBLER.get());
                        // US-106：实验室
                        output.accept(ModBlocks.LAB.get());
                        // v0.5 新增
                        output.accept(ModBlocks.WOOD_CRATE.get());
                        output.accept(ModBlocks.IRON_CRATE.get());
                        output.accept(ModBlocks.SPLITTER.get());
                        output.accept(ModBlocks.UNDERGROUND_CONVEYOR.get());
                        output.accept(ModBlocks.WATER_PUMP.get());
                        output.accept(ModBlocks.BOILER.get());
                        output.accept(ModBlocks.STEAM_ENGINE.get());
                        // 矿区（世界生成资源，创世者铺矿脉用）
                        output.accept(ModBlocks.ORE_FIELD.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
