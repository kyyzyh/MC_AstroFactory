package com.astrofactory.client;

import com.astrofactory.menu.AssemblerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * 组装机界面（v0.8 重做）。
 *
 * <p>视觉风格与科技树界面保持一致：深色底 + 细描边 + 琥珀/蓝色强调色。
 * 相比之前只有几个纯色方块的版本，这里补齐了：</p>
 * <ul>
 *   <li>顶栏：标题 + 工作状态灯；</li>
 *   <li>3x3 网格区与输出槽的分组框，中间一条带箭头的流程指示；</li>
 *   <li>右侧竖向能量条（带刻度与 FE 数值 tooltip）；</li>
 *   <li>底部横向合成进度条；</li>
 *   <li>无电 / 空闲 / 工作中三种状态文案。</li>
 * </ul>
 */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    // ---------------------------------------------------------------- 尺寸

    private static final int W = 176;
    private static final int H = 186;

    /** 网格区框（面板内坐标，与 AssemblerMenu 的槽位对齐：槽位 30/17 起，18 间距）。 */
    private static final int GRID_X = 26;
    private static final int GRID_Y = 15;
    private static final int GRID_W = 62;
    private static final int GRID_H = 62;

    /** 输出槽框。 */
    private static final int OUT_X = 120;
    private static final int OUT_Y = 37;

    /** 能量条（竖向）。 */
    private static final int ENERGY_X = 152;
    private static final int ENERGY_Y = 15;
    private static final int ENERGY_W = 8;
    private static final int ENERGY_H = 62;

    /** 合成进度条（横向）。 */
    private static final int PROG_X = 26;
    private static final int PROG_Y = 80;
    private static final int PROG_W = 126;
    private static final int PROG_H = 5;

    // ---------------------------------------------------------------- 配色（对齐科技树）

    private static final int C_BG = 0xF00B0D12;
    private static final int C_PANEL = 0xFF14171E;
    private static final int C_SLOT = 0xFF07090C;
    private static final int C_BORDER = 0xFF2B3038;
    private static final int C_BORDER_LIT = 0xFF3F4650;
    private static final int C_TEXT = 0xFFE7EAEF;
    private static final int C_TEXT_DIM = 0xFF98A0AB;
    private static final int C_TEXT_FAINT = 0xFF6A717C;
    private static final int C_BLUE = 0xFF4DA2FF;
    private static final int C_AMBER = 0xFFFFC24D;
    private static final int C_GREEN = 0xFF43C57F;
    private static final int C_RED = 0xFFE05A5A;
    private static final int C_TRACK = 0xFF1B1F26;

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        // 背包起点随面板加高而整体下移
        this.inventoryLabelY = H - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // ---- 底板
        g.fill(x, y, x + W, y + H, C_BG);
        g.fill(x + 2, y + 2, x + W - 2, y + H - 2, C_PANEL);
        g.renderOutline(x, y, x + W, y + H, C_BORDER);

        // ---- 顶栏分隔线
        g.fill(x + 2, y + 13, x + W - 2, y + 14, C_BORDER);

        // ---- 3x3 网格区
        drawSlotGroup(g, x + GRID_X, y + GRID_Y, GRID_W, GRID_H);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(g, x + 30 + col * 18 - 1, y + 17 + row * 18 - 1);
            }
        }

        // ---- 流程箭头（网格 → 输出）
        drawArrow(g, x + 92, y + 44);

        // ---- 输出槽
        drawSlotGroup(g, x + OUT_X - 4, y + OUT_Y - 4, 26, 26);
        drawSlot(g, x + OUT_X - 1, y + OUT_Y - 1);

        // ---- 竖向能量条
        drawEnergyBar(g, x + ENERGY_X, y + ENERGY_Y);

        // ---- 合成进度条
        drawProgressBar(g, x + PROG_X, y + PROG_Y);

        // ---- 背包区背景（坐标必须与 AssemblerMenu 的槽位一致：104 起，162 快捷栏）
        int invTop = y + 104;
        g.fill(x + 6, invTop - 3, x + W - 6, invTop + 56, 0x60000000);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + 8 + col * 18 - 1, invTop + row * 18 - 1);
            }
        }
        int hotbarTop = y + 162;
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + 8 + col * 18 - 1, hotbarTop - 1);
        }
    }

    /** 分组框：深色底 + 亮描边。 */
    private void drawSlotGroup(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x40000000);
        g.renderOutline(x, y, x + w, y + h, C_BORDER_LIT);
    }

    /** 单个槽位底：内凹感（深色 + 上方一道暗边）。 */
    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, C_SLOT);
        g.fill(x, y, x + 18, y + 1, 0xFF05070A);
        g.renderOutline(x, y, x + 18, y + 18, C_BORDER);
    }

    /** 向右的流程箭头。 */
    private void drawArrow(GuiGraphics g, int x, int y) {
        boolean working = this.menu.getCraftProgress() > 0;
        int color = working ? C_AMBER : C_BORDER_LIT;
        g.fill(x, y - 1, x + 16, y + 1, color);
        // 箭头三角
        for (int i = 0; i < 5; i++) {
            g.fill(x + 16 + i, y - 1 - i, x + 17 + i, y + 1 + i, color);
        }
    }

    /** 竖向能量条：轨道 + 填充 + 分段刻度。 */
    private void drawEnergyBar(GuiGraphics g, int x, int y) {
        int stored = this.menu.getEnergyStored();
        int capacity = Math.max(1, this.menu.getEnergyCapacity());

        g.fill(x, y, x + ENERGY_W, y + ENERGY_H, C_TRACK);
        g.renderOutline(x, y, x + ENERGY_W, y + ENERGY_H, C_BORDER);

        int innerH = ENERGY_H - 2;
        int fillH = (int) Math.round(innerH * Math.min(1.0D, (double) stored / capacity));
        if (fillH > 0) {
            int top = y + 1 + (innerH - fillH);
            // 电量低时转红，够用为蓝，接近满为绿
            double ratio = (double) stored / capacity;
            int color = ratio < 0.15D ? C_RED : (ratio > 0.85D ? C_GREEN : C_BLUE);
            g.fill(x + 1, top, x + ENERGY_W - 1, y + 1 + innerH, color);
        }
        // 四等分刻度
        for (int i = 1; i < 4; i++) {
            int ty = y + 1 + innerH * i / 4;
            g.fill(x + 1, ty, x + ENERGY_W - 1, ty + 1, 0x60000000);
        }
    }

    /** 横向合成进度条。 */
    private void drawProgressBar(GuiGraphics g, int x, int y) {
        int progress = this.menu.getCraftProgress();
        int max = this.menu.getCraftMaxProgress();
        int energy = this.menu.getEnergyStored();

        g.fill(x, y, x + PROG_W, y + PROG_H, C_TRACK);
        g.renderOutline(x, y, x + PROG_W, y + PROG_H, C_BORDER);

        if (progress > 0 && max > 0) {
            int w = Math.max(1, (int) Math.round((PROG_W - 2) * Math.min(1.0D, (double) progress / max)));
            // 有电时琥珀，没电时暗红（提示玩家供电不足）
            int color = energy > 0 ? C_AMBER : 0xFF7A3A3A;
            g.fill(x + 1, y + 1, x + 1 + w, y + PROG_H - 1, color);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderEnergyTooltip(g, mouseX, mouseY);
        this.renderTooltip(g, mouseX, mouseY);
    }

    /** 鼠标悬停能量条时显示精确数值。 */
    private void renderEnergyTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int bx = this.leftPos + ENERGY_X;
        int by = this.topPos + ENERGY_Y;
        if (mouseX >= bx - 2 && mouseX <= bx + ENERGY_W + 2
                && mouseY >= by && mouseY <= by + ENERGY_H) {
            g.renderTooltip(this.font,
                    List.of(
                            Component.translatable("gui.astrofactory.assembler.energy",
                                    this.menu.getEnergyStored(),
                                    this.menu.getEnergyCapacity())),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 标题
        g.drawString(this.font, this.title, 8, 4, C_TEXT, false);

        // 状态灯 + 文案（顶栏右侧）
        int stored = this.menu.getEnergyStored();
        int progress = this.menu.getCraftProgress();
        Component status;
        int dotColor;
        if (stored <= 0) {
            status = Component.translatable("gui.astrofactory.assembler.no_power");
            dotColor = C_RED;
        } else if (progress > 0) {
            status = Component.translatable("gui.astrofactory.assembler.working");
            dotColor = C_GREEN;
        } else {
            status = Component.translatable("gui.astrofactory.assembler.idle");
            dotColor = C_TEXT_FAINT;
        }
        int dotX = W - 10 - this.font.width(status);
        g.fill(dotX - 6, 6, dotX - 2, 10, dotColor);
        g.drawString(this.font, status, dotX, 4, C_TEXT_DIM, false);

        // 能量条数值（竖排空间太小，横排显示在条左侧）
        g.drawString(this.font, "FE", ENERGY_X - 2, ENERGY_Y + ENERGY_H + 3, C_TEXT_FAINT, false);

        // 进度百分比
        int max = this.menu.getCraftMaxProgress();
        if (progress > 0 && max > 0) {
            int pct = (int) Math.round(100.0D * progress / max);
            String s = pct + "%";
            g.drawString(this.font, s, PROG_X + PROG_W - this.font.width(s), PROG_Y - 9, C_TEXT_DIM, false);
        }

        // 背包标签（面板高 186，背包从 104 起）
        g.drawString(this.font,
                Component.translatable("container.inventory"),
                8, 93, C_TEXT_FAINT, false);
    }
}
