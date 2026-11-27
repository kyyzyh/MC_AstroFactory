package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import com.astrofactory.menu.TechTreeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型注册。
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AstroFactory.MODID);

    /** 科技树菜单：客户端构造无额外数据（科技进度走独立 payload 同步）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<TechTreeMenu>> TECH_TREE =
            MENUS.register("tech_tree", () -> IMenuTypeExtension.create(TechTreeMenu::new));

    /** 组装机菜单：客户端构造从 extraData 读方块位置。 */
    public static final DeferredHolder<MenuType<?>, MenuType<com.astrofactory.menu.AssemblerMenu>> ASSEMBLER =
            MENUS.register("assembler", () -> IMenuTypeExtension.create(
                    com.astrofactory.menu.AssemblerMenu::new));

    /** 锅炉信息面板（US-101）：客户端构造从 extraData 读方块位置。 */
    public static final DeferredHolder<MenuType<?>, MenuType<com.astrofactory.menu.BoilerMenu>> BOILER =
            MENUS.register("boiler", () -> IMenuTypeExtension.create(
                    com.astrofactory.menu.BoilerMenu::new));

    /** 蒸汽机信息面板（US-101）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<com.astrofactory.menu.SteamEngineMenu>> STEAM_ENGINE =
            MENUS.register("steam_engine", () -> IMenuTypeExtension.create(
                    com.astrofactory.menu.SteamEngineMenu::new));

    private ModMenus() {}
}
