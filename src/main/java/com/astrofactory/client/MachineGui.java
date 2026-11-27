package com.astrofactory.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 机器信息面板的共享绘制工具（US-101 起用）。
 *
 * <p>配色与科技树 / 组装机界面保持一致：深色底 + 细描边 + 琥珀/蓝/绿强调色。
 * 抽出静态方法是为了让锅炉、蒸汽机、后续的实验室等面板复用同一套视觉，
 * 而不是每个 Screen 各写一份颜色常量。</p>
 */
final class MachineGui {

    // ---------------------------------------------------------------- 配色

    static final int C_BG = 0xF00B0D12;
    static final int C_PANEL = 0xFF14171E;
    static final int C_TRACK = 0xFF1B1F26;
    static final int C_BORDER = 0xFF2B3038;
    static final int C_TEXT = 0xFFE7EAEF;
    static final int C_TEXT_DIM = 0xFF98A0AB;
    static final int C_TEXT_FAINT = 0xFF6A717C;
    static final int C_BLUE = 0xFF4DA2FF;
    static final int C_AMBER = 0xFFFFC24D;
    static final int C_GREEN = 0xFF43C57F;
    static final int C_RED = 0xFFE05A5A;

    private MachineGui() {}

    /** 面板底板 + 描边。 */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, C_BG);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, C_PANEL);
        g.renderOutline(x, y, x + w, y + h, C_BORDER);
    }

    /** 一条横向进度条（0..1）。 */
    static void hbar(GuiGraphics g, int x, int y, int w, int h, float ratio, int color) {
        g.fill(x, y, x + w, y + h, C_TRACK);
        int fill = (int) (w * Math.max(0f, Math.min(1f, ratio)));
        if (fill > 0) {
            g.fill(x, y, x + fill, y + h, color);
        }
        g.renderOutline(x, y, x + w, y + h, C_BORDER);
    }

    /** 一条竖向进度条（0..1），从底部往上长。 */
    static void vbar(GuiGraphics g, int x, int y, int w, int h, float ratio, int color) {
        g.fill(x, y, x + w, y + h, C_TRACK);
        int fill = (int) (h * Math.max(0f, Math.min(1f, ratio)));
        if (fill > 0) {
            g.fill(x, y + h - fill, x + w, y + h, color);
        }
        g.renderOutline(x, y, x + w, y + h, C_BORDER);
    }

    /** 「标签: 值」一行。 */
    static void line(GuiGraphics g, Font font, int x, int y, String label, String value, int valueColor) {
        g.drawString(font, label, x, y, C_TEXT_DIM, false);
        g.drawString(font, value, x + font.width(label) + 4, y, valueColor, false);
    }

    /** 顶栏：标题 + 状态灯。 */
    static void header(GuiGraphics g, Font font, int x, int y, int w, String title,
                       String status, int statusColor) {
        g.fill(x + 2, y + 13, x + w - 2, y + 14, C_BORDER);
        g.drawString(font, title, x + 6, y + 4, C_TEXT, false);
        int sw = font.width(status);
        g.drawString(font, status, x + w - 6 - sw, y + 4, statusColor, false);
        // 状态灯小圆点（用 3x3 方块近似）
        g.fill(x + w - 10 - sw, y + 6, x + w - 7 - sw, y + 9, statusColor);
    }
}
