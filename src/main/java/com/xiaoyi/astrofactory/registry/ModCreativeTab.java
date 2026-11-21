package com.xiaoyi.astrofactory.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.Astrofactory;

/**
 * 创造模式标签页。
 *
 * <p>展示顺序即玩家认知顺序：物流 → 生产机器 → 动力 → 中间件 → 手册 → 武器。</p>
 */
public final class ModCreativeTab {
    private ModCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Astrofactory.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.astrofactory"))
                    .icon(() -> new ItemStack(ModItems.TRANSPORT_BELT_ITEM.get()))
                    .displayItems((params, output) -> {
                        // 物流
                        output.accept(ModItems.TRANSPORT_BELT_ITEM.get());
                        output.accept(ModItems.FAST_TRANSPORT_BELT_ITEM.get());
                        output.accept(ModItems.FUNNEL_ITEM.get());
                        output.accept(ModItems.BURNER_INSERTER_ITEM.get());
                        // 生产机器
                        output.accept(ModItems.BURNER_MINER_ITEM.get());
                        output.accept(ModItems.STONE_FURNACE_ITEM.get());
                        output.accept(ModItems.ASSEMBLER_ITEM.get());
                        output.accept(ModItems.LAB_ITEM.get());
                        output.accept(ModItems.ORE_VEIN_ITEM.get());
                        // 动力
                        output.accept(ModItems.BOILER_ITEM.get());
                        output.accept(ModItems.STEAM_ENGINE_ITEM.get());
                        output.accept(ModItems.OFFSHORE_PUMP_ITEM.get());
                        output.accept(ModItems.STEAM_PIPE_ITEM.get());
                        output.accept(ModItems.POWER_POLE_ITEM.get());
                        // 中间件
                        output.accept(ModItems.IRON_GEAR.get());
                        output.accept(ModItems.COPPER_CABLE.get());
                        output.accept(ModItems.ELECTRONIC_CIRCUIT.get());
                        output.accept(ModItems.AUTOMATION_PACK.get());
                        // 手册与武器
                        output.accept(ModItems.GUIDE_BOOK.get());
                        output.accept(ModItems.IRON_WRENCH.get());
                        output.accept(ModItems.STEEL_WRENCH.get());
                        output.accept(ModItems.POWER_GAUNTLET.get());
                        output.accept(ModItems.RIVET_GUN.get());
                    })
                    .build());
}
