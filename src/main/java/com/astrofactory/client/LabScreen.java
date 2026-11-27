package com.astrofactory.client;

import com.astrofactory.menu.LabMenu;
import com.astrofactory.network.ModNetwork;
import com.astrofactory.tech.TechDef;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验室研究界面（US-106）。
 *
 * <p>左侧是「可研究的科技」列表（含硬编码科技 {@link TechNodes} 与数据驱动科技
 * {@link TechRegistry}），每条显示名称 + 成本 + 状态；点可研究的行立即开始研究
 * （无在研）或加入队列（已在研），点队列中的行可移除；顶部显示当前在研与进度条。
 * 红瓶槽 / 玩家背包由 {@link LabMenu} 负责，本界面只管绘制与点击。</p>
 */
public class LabScreen extends AbstractContainerScreen<LabMenu> {

    private static final int C_BG = 0xF00B0D12;
    private static final int C_PANEL = 0xFF14171E;
    private static final int C_BORDER = 0xFF2B3038;
    private static final int C_TEXT = 0xFFE7EAEF;
    private static final int C_DIM = 0xFF98A0AB;
    private static final int C_GREEN = 0xFF43C57F;
    private static final int C_BLUE = 0xFF4DA2FF;
    private static final int C_AMBER = 0xFFFFC24D;
    private static final int C_GRAY = 0xFF6A7078;
    private static final int C_ROW_HOVER = 0x30FFFFFF;

    /** 列表区（不被红瓶槽/背包遮挡）。 */
    private static final int LIST_X = 8;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 136;
    private static final int ROW_H = 14;
    private static final int LIST_ROWS = 4;

    private int scroll = 0;

    public LabScreen(LabMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    /** 科技行：id + 名称 + 成本 + 状态。 */
    private record TechRow(String id, String name, int cost) {}

    private List<TechRow> allRows() {
        List<TechRow> rows = new ArrayList<>();
        // 硬编码科技
        for (var entry : TechNodes.all().entrySet()) {
            var node = entry.getValue();
            rows.add(new TechRow(node.id(), Component.translatable(node.nameKey()).getString(),
                    node.costPerLevel()));
        }
        // 数据驱动科技（US-105/106）
        for (TechDef def : TechRegistry.all()) {
            rows.add(new TechRow(def.id(), def.name(), def.packCount()));
        }
        return rows;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, C_BG);
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, C_PANEL);

        // 顶部标题 + 当前研究
        graphics.drawString(this.font, Component.translatable("screen.astrofactory.lab"),
                x + 8, y + 6, C_TEXT, false);
        String current = ModNetwork.CLIENT_CURRENT;
        if (current != null) {
            int cost = costOf(current);
            int prog = Math.min(cost, ModNetwork.CLIENT_PROGRESS);
            graphics.drawString(this.font, nameOf(current), x + 8, y + 22, C_AMBER, false);
            // 进度条
            int barX = x + 8, barY = y + 34, barW = 100, barH = 5;
            graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF3A3F48);
            int fill = (int) (barW * (cost <= 0 ? 0 : (double) prog / cost));
            graphics.fill(barX, barY, barX + fill, barY + barH, C_BLUE);
            graphics.drawString(this.font, prog + "/" + cost, barX + barW + 4, barY - 1, C_DIM, false);
        } else {
            graphics.drawString(this.font,
                    Component.translatable("gui.astrofactory.lab.no_current"),
                    x + 8, y + 22, C_DIM, false);
        }

        // 科技列表区（只绘制可见行）
        List<TechRow> rows = allRows();
        int listLeft = x + LIST_X, listTop = y + 40;
        for (int i = 0; i < LIST_ROWS; i++) {
            int rowIdx = scroll + i;
            int ry = listTop + i * ROW_H;
            if (rowIdx >= rows.size()) {
                break;
            }
            TechRow row = rows.get(rowIdx);
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + LIST_W
                    && mouseY >= ry && mouseY < ry + ROW_H;
            if (hovered) {
                graphics.fill(listLeft, ry, listLeft + LIST_W, ry + ROW_H, C_ROW_HOVER);
            }
            drawRow(graphics, row, listLeft, ry, hovered);
        }

        // 红瓶槽外框（152,30 与菜单一致）
        int slotX = x + 152, slotY = y + 30;
        graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, C_BORDER);
        graphics.drawString(this.font, Component.translatable("gui.astrofactory.lab.red_slot"),
                x + 132, y + 10, C_DIM, false);
    }

    private void drawRow(GuiGraphics graphics, TechRow row, int listLeft, int ry, boolean hovered) {
        String id = row.id();
        boolean done = ModNetwork.CLIENT_LEVELS.getOrDefault(id, 0) >= 1;
        boolean current = id.equals(ModNetwork.CLIENT_CURRENT);
        boolean queued = ModNetwork.CLIENT_QUEUE.contains(id);
        int color;
        if (done) {
            color = C_GREEN;
        } else if (current) {
            color = C_AMBER;
        } else if (queued) {
            color = C_BLUE;
        } else {
            color = availableOnClient(id) ? C_TEXT : C_GRAY;
        }
        graphics.drawString(this.font, row.name(), listLeft, ry + 2, color, false);
        // 状态 / 成本徽标
        String badge;
        if (done) {
            badge = "✓";
        } else if (current) {
            badge = "◆";
        } else if (queued) {
            badge = "排队";
        } else {
            badge = String.valueOf(row.cost());
        }
        graphics.drawString(this.font, badge, listLeft + LIST_W - this.font.width(badge),
                ry + 2, C_DIM, false);
    }

    /** 客户端近似可研究性（前置是否满足）。 */
    private boolean availableOnClient(String id) {
        // 前置全满足视为可研究
        for (String parent : parentsOf(id)) {
            if (ModNetwork.CLIENT_LEVELS.getOrDefault(parent, 0) < 1) {
                return false;
            }
        }
        return true;
    }

    private List<String> parentsOf(String id) {
        var def = TechRegistry.get(id);
        if (def != null) {
            return def.parents();
        }
        var node = TechNodes.get(id);
        return node == null ? List.of() : java.util.Arrays.asList(node.parents());
    }

    private int costOf(String id) {
        var def = TechRegistry.get(id);
        if (def != null) {
            return def.packCount();
        }
        var node = TechNodes.get(id);
        return node == null ? 0 : node.costPerLevel();
    }

    private String nameOf(String id) {
        var def = TechRegistry.get(id);
        if (def != null) {
            return def.name();
        }
        var node = TechNodes.get(id);
        return node != null ? Component.translatable(node.nameKey()).getString() : id;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int listLeft = x + LIST_X, listTop = y + 40;
        List<TechRow> rows = allRows();
        for (int i = 0; i < LIST_ROWS; i++) {
            int rowIdx = scroll + i;
            int ry = listTop + i * ROW_H;
            if (rowIdx >= rows.size()) {
                break;
            }
            if (mouseX >= listLeft && mouseX < listLeft + LIST_W
                    && mouseY >= ry && mouseY < ry + ROW_H) {
                TechRow row = rows.get(rowIdx);
                handleClick(row);
                return true;
            }
        }

        // 滚轮已由 mouseScrolled 处理，这里交给父类（槽位点击）
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 点击行：无在研 → 直接开研；在研中 → 入队；已排队 → 移除。 */
    private void handleClick(TechRow row) {
        String id = row.id();
        boolean current = id.equals(ModNetwork.CLIENT_CURRENT);
        boolean queued = ModNetwork.CLIENT_QUEUE.contains(id);
        if (current) {
            // 当前在研，不做切换（避免打断）
            return;
        }
        if (queued) {
            PacketDistributor.sendToServer(
                    new ModNetwork.DequeueResearchPayload(id));
            return;
        }
        // 无在研 → 直接开研；否则入队
        if (ModNetwork.CLIENT_CURRENT == null && !ModNetwork.CLIENT_CREATIVE_BYPASS) {
            PacketDistributor.sendToServer(new ModNetwork.SelectResearchPayload(id));
        } else {
            PacketDistributor.sendToServer(new ModNetwork.EnqueueResearchPayload(id));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // 滚轮作用于科技列表区
        if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                && mouseY >= y + 34 && mouseY < y + 34 + LIST_ROWS * ROW_H) {
            int delta = (int) -Math.signum(verticalAmount);
            int maxScroll = Math.max(0, allRows().size() - LIST_ROWS);
            this.scroll = Math.max(0, Math.min(maxScroll, this.scroll + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题由 renderBg 绘制，这里留空避免重复
    }
}
