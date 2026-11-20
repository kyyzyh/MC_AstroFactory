package com.xiaoyi.astrofactory.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.menu.AssemblerMenu;
import com.xiaoyi.astrofactory.menu.BoilerMenu;
import com.xiaoyi.astrofactory.menu.LabMenu;
import com.xiaoyi.astrofactory.menu.StoneFurnaceMenu;

/**
 * 菜单（GUI 容器类型）注册。
 */
public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Astrofactory.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AssemblerMenu>> ASSEMBLER_MENU =
            MENUS.register("assembler", () -> AssemblerMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<LabMenu>> LAB_MENU =
            MENUS.register("lab", () -> LabMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<StoneFurnaceMenu>> STONE_FURNACE_MENU =
            MENUS.register("stone_furnace", () -> StoneFurnaceMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<BoilerMenu>> BOILER_MENU =
            MENUS.register("boiler", () -> BoilerMenu.TYPE);
}
