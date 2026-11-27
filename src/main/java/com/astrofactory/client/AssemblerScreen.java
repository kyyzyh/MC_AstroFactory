package com.astrofactory.client;

import com.astrofactory.menu.AssemblerMenu;
import com.astrofactory.recipe.AssemblerRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
import java.util.Optional;

/**
 * 组装机界面（US-104 重做：配方选择列表）。
 *
 * <p>布局（宽 200 x 高 210）：</p>
 * <pre>
 *  ┌───────────────────────────────────────────┐
 *  │ 组装机                       ● 状态        │
 *  ├──────────────────┬────────────────────────┤
 *  │ 3x3 投料区  →  输出│ 配方列表（可滚动）     │
 *  │                  │  ▸ 铁齿轮   2x铁板      │
 *  │  进度条           │  ▸ 铜线材   1x铜板      │
 *  │  能量条           │  ▸ 红瓶     1x齿轮+1x铜板│
 *  ├──────────────────┴────────────────────────┤
 *  │ 背包                                      │
 *  └───────────────────────────────────────────┘
 * </pre>
 *
 * <p>点击右侧列表项 → {@code clickMenuButton(玩家, 下标)} → 服务端校验解锁并切换配方。
 * 点击已选中的项 → 再次点击取消选择。</p>
 */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    private static final int W = 200;
    private static final int H = 210;

    // ---- 投料区 / 输出 / 进度 / 能量（与 AssemblerMenu 槽位对齐：30/30 起，18 间距）
    private static final int GRID_X = 26;
    private static final int GRID_Y = 28;
    private static final int GRID_W = 62;
    private static final int GRID_H = 62;
    private static final int OUT_X = 120;
    private static final int OUT_Y = 46;
    private static final int PROG_X = 26;
    private static final int PROG_Y = 96;
    private static final int PROG_W = 74;
    private static final int PROG_H = 5;
    private static final int ENERGY_X = 106;
    private static final int ENERGY_Y = 94;
    private static final int ENERGY_W = 14;
    private static final int ENERGY_H = 8;

    // ---- 配方列表
    private static final int LIST_X = 112;
    private static final int LIST_Y = 28;
    private static final int LIST_W = 82;
    private static final int LIST_H = 62;
    private static final int ROW_H = 18;

    // ---- 配色（对齐科技树 / 现有机器界面）
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
    private static final int C_ROW_HOVER = 0x30FFFFFF;
    private static final int C_ROW_SEL = 0x504DA2FF;

    /** 列表滚动偏移（行）。 */
    private int scrollRow = 0;

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = H - 94;
    }

    private List<RecipeHolder<AssemblerRecipe>> recipes() {
        return this.menu.unlockedRecipes();
    }

    // ---------------------------------------------------------------- 绘制背景

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        g.fill(x, y, x + W, y + H, C_BG);
        g.fill(x + 2, y + 2, x + W - 2, y + H - 2, C_PANEL);
        g.renderOutline(x, y, x + W, y + H, C_BORDER);
        g.fill(x + 2, y + 13, x + W - 2, y + 14, C_BORDER);

        // 投料区
        drawSlotGroup(g, x + GRID_X, y + GRID_Y, GRID_W, GRID_H);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(g, x + 30 + col * 18 - 1, y + 30 + row * 18 - 1);
            }
        }

        // 流程箭头
        drawArrow(g, x + 92, y + 56);

        // 输出槽
        drawSlotGroup(g, x + OUT_X - 4, y + OUT_Y - 4, 26, 26);
        drawSlot(g, x + OUT_X - 1, y + OUT_Y - 1);

        // 进度条 + 能量条
        drawProgressBar(g, x + PROG_X, y + PROG_Y);
        drawEnergyBar(g, x + ENERGY_X, y + ENERGY_Y);

        // 配方列表
        drawRecipeList(g, x + LIST_X, y + LIST_Y, mouseX, mouseY);

        // 背包
        int invTop = y + 116;
        g.fill(x + 6, invTop - 3, x + W - 6, invTop + 56, 0x60000000);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + 8 + col * 18 - 1, invTop + row * 18 - 1);
            }
        }
        int hotbarTop = y + 174;
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + 8 + col * 18 - 1, hotbarTop - 1);
        }
    }

    /** 配方列表：每行一个产物图标 + 名称，选中行高亮。 */
    private void drawRecipeList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        g.fill(x, y, x + LIST_W, y + LIST_H, 0x40000000);
        g.renderOutline(x, y, x + LIST_W, y + LIST_H, C_BORDER_LIT);

        List<RecipeHolder<AssemblerRecipe>> list = recipes();
        if (list.isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("gui.astrofactory.assembler.none_unlocked"),
                    x + 4, y + 4, C_TEXT_FAINT, false);
            return;
        }

        int selected = this.menu.getSelectedIndex();
        int maxRows = LIST_H / ROW_H;
        clampScroll(list.size(), maxRows);

        for (int i = 0; i < maxRows; i++) {
            int index = this.scrollRow + i;
            if (index >= list.size()) {
                break;
            }
            int rowY = y + i * ROW_H;
            boolean hovered = isInside(mouseX, mouseY, x, rowY, LIST_W, ROW_H);
            if (index == selected) {
                g.fill(x + 1, rowY, x + LIST_W - 1, rowY + ROW_H - 1, C_ROW_SEL);
            } else if (hovered) {
                g.fill(x + 1, rowY, x + LIST_W - 1, rowY + ROW_H - 1, C_ROW_HOVER);
            }

            ItemStack out = list.get(index).value().result();
            g.renderItem(out, x + 3, rowY + 1);
            // 名称（超宽截断）
            String name = out.getHoverName().getString();
            int maxW = LIST_W - 24;
            String shown = this.font.width(name) <= maxW ? name
                    : this.font.plainSubstrByWidth(name, maxW - 6) + "…";
            g.drawString(this.font, shown, x + 22, rowY + 5, C_TEXT, false);
        }

        // 滚动指示
        if (list.size() > maxRows) {
            int barH = Math.max(8, LIST_H * maxRows / list.size());
            int barY = y + (LIST_H - barH) * this.scrollRow / Math.max(1, list.size() - maxRows);
            g.fill(x + LIST_W - 3, barY, x + LIST_W - 1, barY + barH, C_BORDER_LIT);
        }
    }

    private void clampScroll(int total, int maxRows) {
        int maxScroll = Math.max(0, total - maxRows);
        if (this.scrollRow > maxScroll) {
            this.scrollRow = maxScroll;
        }
        if (this.scrollRow < 0) {
            this.scrollRow = 0;
        }
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawSlotGroup(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x40000000);
        g.renderOutline(x, y, x + w, y + h, C_BORDER_LIT);
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, C_SLOT);
        g.fill(x, y, x + 18, y + 1, 0xFF05070A);
        g.renderOutline(x, y, x + 18, y + 18, C_BORDER);
    }

    private void drawArrow(GuiGraphics g, int x, int y) {
        boolean working = this.menu.getCraftProgress() > 0;
        int color = working ? C_AMBER : C_BORDER_LIT;
        g.fill(x, y - 1, x + 16, y + 1, color);
        for (int i = 0; i < 5; i++) {
            g.fill(x + 16 + i, y - 1 - i, x + 17 + i, y + 1 + i, color);
        }
    }

    /** 横向进度条（投料区下方）。 */
    private void drawProgressBar(GuiGraphics g, int x, int y) {
        int progress = this.menu.getCraftProgress();
        int max = this.menu.getCraftMaxProgress();

        g.fill(x, y, x + PROG_W, y + PROG_H, C_TRACK);
        g.renderOutline(x, y, x + PROG_W, y + PROG_H, C_BORDER);

        if (progress > 0 && max > 0) {
            int w = Math.max(1, (int) Math.round((PROG_W - 2) * Math.min(1.0D, (double) progress / max)));
            int color = this.menu.isPowered() ? C_AMBER : 0xFF7A3A3A;
            g.fill(x + 1, y + 1, x + 1 + w, y + PROG_H - 1, color);
        }
    }

    /** 小号横向能量条。 */
    private void drawEnergyBar(GuiGraphics g, int x, int y) {
        int stored = this.menu.getEnergyStored();
        int capacity = Math.max(1, AssemblerBlockEntityCapacity());
        g.fill(x, y, x + ENERGY_W, y + ENERGY_H, C_TRACK);
        g.renderOutline(x, y, x + ENERGY_W, y + ENERGY_H, C_BORDER);
        int innerW = ENERGY_W - 2;
        int fillW = (int) Math.round(innerW * Math.min(1.0D, (double) stored / capacity));
        if (fillW > 0) {
            double ratio = (double) stored / capacity;
            int color = ratio < 0.15D ? C_RED : (ratio > 0.85D ? C_GREEN : C_BLUE);
            g.fill(x + 1, y + 1, x + 1 + fillW, y + ENERGY_H - 1, color);
        }
    }

    private static int AssemblerBlockEntityCapacity() {
        return com.astrofactory.config.AstroFactoryConfig.ASSEMBLER_CAPACITY.get();
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 配方列表点击
        int lx = this.leftPos + LIST_X;
        int ly = this.topPos + LIST_Y;
        if (isInside((int) mouseX, (int) mouseY, lx, ly, LIST_W, LIST_H)) {
            int maxRows = LIST_H / ROW_H;
            int row = ((int) mouseY - ly) / ROW_H;
            if (row >= 0 && row < maxRows) {
                int index = this.scrollRow + row;
                List<RecipeHolder<AssemblerRecipe>> list = recipes();
                if (index >= 0 && index < list.size()) {
                    // 点击已选中的行 → 取消选择
                    int send = index == this.menu.getSelectedIndex() ? 65535 : index;
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(
                                this.menu.containerId, send);
                    }
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int lx = this.leftPos + LIST_X;
        int ly = this.topPos + LIST_Y;
        if (isInside((int) mouseX, (int) mouseY, lx, ly, LIST_W, LIST_H)) {
            int maxRows = LIST_H / ROW_H;
            int maxScroll = Math.max(0, recipes().size() - maxRows);
            if (maxScroll > 0) {
                this.scrollRow += scrollY > 0 ? -1 : 1;
                clampScroll(recipes().size(), maxRows);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- 前景

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderRecipeTooltip(g, mouseX, mouseY);
        renderEnergyTooltip(g, mouseX, mouseY);
        this.renderTooltip(g, mouseX, mouseY);
    }

    /** 悬停配方行时显示原料需求。 */
    private void renderRecipeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int lx = this.leftPos + LIST_X;
        int ly = this.topPos + LIST_Y;
        if (!isInside(mouseX, mouseY, lx, ly, LIST_W, LIST_H)) {
            return;
        }
        int maxRows = LIST_H / ROW_H;
        int row = (mouseY - ly) / ROW_H;
        if (row < 0 || row >= maxRows) {
            return;
        }
        int index = this.scrollRow + row;
        List<RecipeHolder<AssemblerRecipe>> list = recipes();
        if (index < 0 || index >= list.size()) {
            return;
        }
        AssemblerRecipe recipe = list.get(index).value();

        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("gui.astrofactory.assembler.need"));
        for (SizedIngredient ingredient : recipe.ingredients()) {
            ItemStack[] items = ingredient.ingredient().getItems();
            String name = items.length > 0
                    ? items[0].getHoverName().getString()
                    : "?";
            lines.add(Component.literal("  " + ingredient.count() + "x " + name));
        }
        int secs = Math.max(1, Math.round(recipe.craftTicks() / 20.0F));
        lines.add(Component.literal("  " + secs + "s"));
        g.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private void renderEnergyTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int bx = this.leftPos + ENERGY_X;
        int by = this.topPos + ENERGY_Y;
        if (isInside(mouseX, mouseY, bx - 2, by - 2, ENERGY_W + 4, ENERGY_H + 4)) {
            g.renderTooltip(this.font,
                    List.of(Component.translatable("gui.astrofactory.assembler.energy",
                            this.menu.getEnergyStored(), AssemblerBlockEntityCapacity())),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 4, C_TEXT, false);

        // 状态灯
        Component status;
        int dotColor;
        if (!this.menu.hasSelectedRecipe()) {
            status = Component.translatable("gui.astrofactory.assembler.no_recipe");
            dotColor = C_TEXT_FAINT;
        } else if (!this.menu.isPowered()) {
            status = Component.translatable("gui.astrofactory.assembler.no_power");
            dotColor = C_RED;
        } else if (this.menu.isOutputFull()) {
            status = Component.translatable("gui.astrofactory.assembler.output_full");
            dotColor = C_AMBER;
        } else if (this.menu.isMissingInput()) {
            status = Component.translatable("gui.astrofactory.assembler.waiting");
            dotColor = C_AMBER;
        } else {
            status = Component.translatable("gui.astrofactory.assembler.working");
            dotColor = C_GREEN;
        }
        int dotX = W - 10 - this.font.width(status);
        g.fill(dotX - 6, 6, dotX - 2, 10, dotColor);
        g.drawString(this.font, status, dotX, 4, C_TEXT_DIM, false);

        // 当前配方名（列表上方）
        g.drawString(this.font,
                Component.translatable("gui.astrofactory.assembler.recipe"),
                LIST_X, LIST_Y - 9, C_TEXT_FAINT, false);

        // 进度百分比
        int progress = this.menu.getCraftProgress();
        int max = this.menu.getCraftMaxProgress();
        if (progress > 0 && max > 0) {
            String s = (int) Math.round(100.0D * progress / max) + "%";
            g.drawString(this.font, s, PROG_X + PROG_W - this.font.width(s), PROG_Y - 9,
                    C_TEXT_DIM, false);
        }

        g.drawString(this.font, Component.translatable("container.inventory"),
                8, 105, C_TEXT_FAINT, false);
    }
}
