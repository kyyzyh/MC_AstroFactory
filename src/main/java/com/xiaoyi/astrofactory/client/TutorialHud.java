package com.xiaoyi.astrofactory.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.tutorial.Tutorial;

/**
 * 新手教程 HUD：右上角可折叠任务卡（规格 §5.7）。
 *
 * <h2>形式约束（用户已确认，不许改）</h2>
 * <ul>
 *   <li><b>可折叠</b>：点标题栏收起/展开，收起后只留一行</li>
 *   <li><b>不强制弹窗、不锁操作、不加遮罩</b>：纯叠加层，玩家能正常玩</li>
 *   <li>只显示<b>当前这一步</b>，不是 18 行清单铺满屏幕</li>
 * </ul>
 *
 * <h2>渲染方式</h2>
 * <p>26.3 的 HUD 走 {@code RenderGuiEvent.Post}，拿到的
 * {@link GuiGraphicsExtractor} 是 extract 式的（{@code fill}/{@code text}），
 * 不是旧的 {@code GuiGraphics}。</p>
 *
 * <p>折叠状态是<b>纯客户端</b>的：它只是「我想不想看」，不需要存档、
 * 不需要跨玩家同步，所以不进 attachment、不发包。按键 {@code K} 切换
 * （见 {@link ClientEvents#registerKeys}）。</p>
 */
@EventBusSubscriber(modid = Astrofactory.MODID, value = Dist.CLIENT)
public final class TutorialHud {
    private TutorialHud() {}

    // ---- 布局常量（右上角，留 4px 边距避开原版效果图标）----
    private static final int MARGIN = 4;
    private static final int PAD = 4;
    private static final int LINE = 10;
    private static final int WIDTH = 150;

    /** 折叠状态（纯客户端）。 */
    private static boolean collapsed = false;

    /** 切换折叠。 */
    public static void toggleCollapsed() {
        collapsed = !collapsed;
    }

    /** 折叠后/展开后的整体高度。 */
    static int panelHeight() {
        return collapsed ? LINE + PAD * 2 : LINE * 3 + PAD * 2;
    }

    /** 任务卡左上角 x（右上角对齐）。 */
    static int panelX(GuiGraphicsExtractor g) {
        return g.guiWidth() - WIDTH - MARGIN;
    }

    static int panelY() {
        return MARGIN;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 打开任何界面时不画（避免压在物品栏上）。
        // 26.3 里 Screen 挂在 Gui 上，不是 Minecraft 字段（mc.screen 已不存在）。
        if (mc.gui.screen() != null) {
            return;
        }
        Tutorial.Data data = Tutorial.of(mc.player);
        GuiGraphicsExtractor g = event.getGuiGraphics();
        int x = panelX(g);
        int y = panelY();
        int h = panelHeight();

        // 底板（半透明黑）+ 边框
        g.fill(x, y, x + WIDTH, y + h, 0xC0101010);
        g.fill(x, y, x + WIDTH, y + 1, 0xFF4FC3F7);
        g.fill(x, y + h - 1, x + WIDTH, y + h, 0xFF4FC3F7);
        g.fill(x, y, x + 1, y + h, 0xFF4FC3F7);
        g.fill(x + WIDTH - 1, y, x + WIDTH, y + h, 0xFF4FC3F7);

        int tx = x + PAD;
        int ty = y + PAD;

        if (data.done) {
            g.text(mc.font, Component.translatable("tutorial.astrofactory.all_done"),
                    tx, ty, 0xFF7CFC7C, false);
            return;
        }

        // 第一行：标题 + 折叠标记 + 进度
        Component title = collapsed
                ? Component.translatable("tutorial.astrofactory.title_collapsed")
                : Component.translatable("tutorial.astrofactory.title");
        g.text(mc.font, title, tx, ty, 0xFFFFD54F, false);
        String progress = (data.step + 1) + "/" + Tutorial.count();
        g.text(mc.font, progress, x + WIDTH - PAD - mc.font.width(progress), ty, 0xFF9E9E9E, false);

        if (collapsed) {
            return;
        }

        // 第二行：当前步骤标题
        Tutorial.Step cur = data.current();
        g.text(mc.font, Component.translatable(cur.titleKey()), tx, ty + LINE, 0xFFFFFFFF, false);

        // 第三行：说明（超宽就截断，不换行 —— 换行会撑高面板挡视线）
        Component desc = Component.translatable(cur.descKey());
        String s = desc.getString();
        int maxW = WIDTH - PAD * 2;
        if (mc.font.width(s) > maxW) {
            s = mc.font.plainSubstrByWidth(s, maxW - mc.font.width("...")) + "...";
        }
        g.text(mc.font, Component.literal(s), tx, ty + LINE * 2, 0xFFB0BEC5, false);
    }
}
