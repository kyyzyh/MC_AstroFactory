package com.astrofactory.gametest;

import com.astrofactory.AstroFactory;
import com.astrofactory.menu.TechTreeMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 菜单 extraData 解码回归测试（US-109 后补）。
 *
 * <p>缺陷复盘：科技终端用无 extraData 的 {@code openMenu(provider)} 打开科技树，
 * 客户端 {@code TechTreeMenu} 收到 {@code null} buf，旧代码直接
 * {@code buf.readableBytes()} → NPE → 客户端「网络协议错误」断连。</p>
 *
 * <p>这里锁定 {@link TechTreeMenu#decodeHasTable} 的 null 安全契约，
 * 防止再退化。</p>
 */
@GameTestHolder(AstroFactory.MODID)
@PrefixGameTestTemplate(false)
public class MenuPayloadGameTests {

    /** null buf（无 extraData 的 openMenu）必须安全返回 false，不抛异常。 */
    @GameTest(template = "pole_flat", batch = "menuPayload", timeoutTicks = 100)
    public static void nullBufferDecodesToFalse(GameTestHelper helper) {
        boolean result;
        try {
            result = TechTreeMenu.decodeHasTable(null);
        } catch (Throwable t) {
            helper.fail("null buf 解码抛异常（曾导致客户端网络协议错误断连）：" + t);
            return;
        }
        if (result) {
            helper.fail("null buf 应解码为 false（不打开研究台槽位），实际 true");
            return;
        }
        helper.succeed();
    }

    /** 空 buffer（readableBytes == 0）同样安全返回 false。 */
    @GameTest(template = "pole_flat", batch = "menuPayload", timeoutTicks = 100)
    public static void emptyBufferDecodesToFalse(GameTestHelper helper) {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        boolean result = TechTreeMenu.decodeHasTable(buf);
        if (result) {
            helper.fail("空 buffer 应解码为 false，实际 true");
            return;
        }
        helper.succeed();
    }

    private MenuPayloadGameTests() {}
}
