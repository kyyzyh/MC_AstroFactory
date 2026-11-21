package com.xiaoyi.astrofactory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

import com.xiaoyi.astrofactory.tutorial.Tutorial;

import java.util.List;

/**
 * 新手手册界面：直接复用原版 {@link BookViewScreen}。
 *
 * <p><b>为什么继承原版而不是自己画</b>：原版书已经解决了背景贴图、翻页按钮、
 * 翻页音效、文字自动换行、页码显示、ESC 关闭 —— 全部是我们要的。
 * 自己画一遍只会得到一样的外观 + 一堆 bug。这里只做一件事：
 * 把 {@link GuideBookPages} 生成的页面塞进 {@code BookAccess}。</p>
 *
 * <p>进度从客户端玩家 attachment 读（{@link Tutorial#of}），
 * 因为 attachment 已通过 {@code SYNC_CODEC} 同步到客户端。</p>
 */
public class GuideBookScreen extends BookViewScreen {

    public GuideBookScreen() {
        super();
        setBookAccess(new BookViewScreen.BookAccess(pages()));
    }

    /** 从客户端玩家读进度，生成书页。 */
    private static List<Component> pages() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return GuideBookPages.build(0, false);
        }
        Tutorial.Data data = Tutorial.of(mc.player);
        return GuideBookPages.build(data.step, data.done);
    }

    /** 供 GuideBookItem 的客户端分支调用。 */
    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new GuideBookScreen());
    }
}
