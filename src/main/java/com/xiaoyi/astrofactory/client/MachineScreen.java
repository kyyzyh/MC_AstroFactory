package com.xiaoyi.astrofactory.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 机器 GUI 基类：统一「状态灯 + 进度条 + 能量条」三件套。
 *
 * <h2>为什么要统一布局（不是审美问题）</h2>
 * <p>Mekanism 有 200+ 个机器 GUI，靠的不是抽象，而是<b>纪律</b>：
 * 能量条永远在右侧、进度箭头永远在中间、状态灯永远在右上角。
 * 这带来三个实际收益：</p>
 * <ol>
 *   <li><b>识别优于回忆</b>：玩家学会第 1 台机器后，后面每台的学习成本归零；</li>
 *   <li><b>扫一眼定位故障</b>：工厂类玩法的核心循环是「看 → 诊断 → 修」。
 *       位置固定才能「扫」，位置随机只能「找」；</li>
 *   <li><b>横切能力只有一个接入点</b>：加一个警告类型只改这里，不用改 4 个 Screen。</li>
 * </ol>
 *
 * <h2>为什么用 fill 画而不是贴图</h2>
 * <p>本项目没有任何自有 GUI 贴图（复用原版 {@code furnace.png} / {@code dispenser.png}）。
 * 加一张贴图意味着要维护 {@code .png} + 九个切片坐标。而状态灯和进度条
 * 用 {@link GuiGraphicsExtractor#fill} 画几个矩形就够，<b>零资产</b>。
 * Mekanism 的状态灯贴图本身也只有 24×12（亮/暗两帧），本质就是两个色块。</p>
 */
public abstract class MachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    // ---------------------------------------------------------------- 状态枚举

    /** 机器状态：决定状态灯颜色与文字。 */
    public enum Status {
        /** 正常运行中。 */
        RUNNING(0xFF4CAF50, "gui.astrofactory.status.running"),
        /** 有料但没电。 */
        NO_POWER(0xFFFF5555, "gui.astrofactory.status.no_power"),
        /** 没有输入原料。 */
        NO_INPUT(0xFFFFC107, "gui.astrofactory.status.no_input"),
        /** 输出槽满了，产物出不去。 */
        OUTPUT_FULL(0xFFFF9800, "gui.astrofactory.status.output_full"),
        /** 缺燃料（燃烧期机器）。 */
        NO_FUEL(0xFFFF5555, "gui.astrofactory.status.no_fuel"),
        /** 停机（结构不完整 / 无配方 / 未知）。 */
        IDLE(0xFF9E9E9E, "gui.astrofactory.status.idle");

        public final int color;
        public final String langKey;

        Status(int color, String langKey) {
            this.color = color;
            this.langKey = langKey;
        }
    }

    /** 状态灯边长（像素）。 */
    private static final int LIGHT_SIZE = 5;

    protected MachineScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    // ---------------------------------------------------------------- 绘制原语

    /**
     * 在右上角画状态灯 + 状态文字。
     *
     * <p>位置固定在 {@code (imageWidth - 8, 6)}，全模组一致。</p>
     */
    protected void drawStatus(GuiGraphicsExtractor extractor, Status status) {
        int x = this.leftPos + this.imageWidth - LIGHT_SIZE - 3;
        int y = this.topPos + 5;

        // 深色底衬，保证在浅色背景上也看得见
        extractor.fill(x - 1, y - 1, x + LIGHT_SIZE + 1, y + LIGHT_SIZE + 1, 0xFF2B2B2B);
        extractor.fill(x, y, x + LIGHT_SIZE, y + LIGHT_SIZE, status.color);
    }

    /**
     * 在状态灯左侧画状态文字（右对齐），鼠标悬停时可读全文。
     *
     * <p>文字右对齐到状态灯左边，这样不同长度的文案不会互相推挤。</p>
     */
    protected void drawStatusText(GuiGraphicsExtractor extractor, Status status) {
        Component text = Component.translatable(status.langKey);
        int textWidth = this.font.width(text);
        int x = this.leftPos + this.imageWidth - LIGHT_SIZE - 8 - textWidth;
        int y = this.topPos + 5;
        if (x < this.leftPos + 4) {
            return; // 放不下就不画，避免压到标题
        }
        extractor.text(this.font, text, x, y, status.color);
    }

    /**
     * 画一条水平进度条。
     *
     * @param percent 0-100
     */
    protected void drawProgressBar(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        extractor.fill(x, y, x + w, y + h, 0xFF3A3A3A);
        int filled = (w - 2) * clamped / 100;
        if (filled > 0) {
            extractor.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, 0xFF4FC3F7);
        }
    }

    /**
     * 画一条竖直能量条（右边缘固定位置）。
     *
     * <p>从下往上填充 —— 这是电池的通用隐喻，玩家不用学。</p>
     */
    protected void drawEnergyBar(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int stored, int max) {
        extractor.fill(x, y, x + w, y + h, 0xFF3A3A3A);
        if (max <= 0) {
            return;
        }
        int filled = (h - 2) * Math.max(0, Math.min(stored, max)) / max;
        if (filled > 0) {
            // 电量低时变红：这是「要断电了」的预警，比数字更早被注意到
            int color = stored * 100 / max < 20 ? 0xFFFF5555 : 0xFF4CAF50;
            extractor.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, color);
        }
    }
}
