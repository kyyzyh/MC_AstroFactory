package com.astrofactory.client;

import com.astrofactory.menu.TechTreeMenu;
import com.astrofactory.network.ModNetwork;
import com.astrofactory.tech.TechNodes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科技树界面（v0.7.1 重做）。
 *
 * <p>480x280 固定面板，分四块：</p>
 * <ul>
 *   <li><b>顶栏</b>：标题、时代徽章、完成度、在研进度条；</li>
 *   <li><b>画布</b>（左上大区域）：按 DAG 最长路径自动分列，滚轮以鼠标为锚点缩放，
 *       空白处拖拽平移；节点卡片带时代色条、状态色、等级/成本/触发进度角标，
 *       父子之间画折线箭头；</li>
 *   <li><b>详情侧栏</b>（右上）：选中节点的名称、时代、状态、描述、成本、前置、
 *       触发进度条，底部一个操作按钮；</li>
 *   <li><b>底部</b>：玩家背包与快捷栏（研究台打开时额外有 2 个研究包输入槽）。</li>
 * </ul>
 *
 * <p>节点状态色：绿 = 已完成，琥珀 = 研究中，蓝 = 可研究，灰 = 前置未满足。</p>
 *
 * <p><b>注意</b>：1.21.1 的 {@code Slot.x/y} 是 final，界面无法二次摆放槽位，
 * 因此槽位坐标全部写死在 {@link TechTreeMenu} 里，本界面的布局常量必须与之对齐。</p>
 */
public class TechTreeScreen extends AbstractContainerScreen<TechTreeMenu> {

    // ---------------------------------------------------------------- 尺寸常量

    private static final int PAD = 6;
    private static final int TOP_BAR_H = 22;
    /** 背包区起始（面板内坐标），需与 TechTreeMenu 的槽位坐标对齐。 */
    private static final int INVENTORY_TOP = 192;
    private static final int SIDEBAR_W = 146;

    private static final int NODE_W = 60;
    private static final int NODE_H = 32;
    private static final int GAP_X = 22;
    private static final int GAP_Y = 10;

    // ---------------------------------------------------------------- 配色

    private static final int C_BG = 0xF00B0D12;
    private static final int C_PANEL = 0xFF14171E;
    private static final int C_CANVAS = 0xFF07090C;
    private static final int C_GRID = 0xFF12161C;
    private static final int C_BORDER = 0xFF2B3038;
    private static final int C_TEXT = 0xFFE7EAEF;
    private static final int C_TEXT_DIM = 0xFF98A0AB;
    private static final int C_TEXT_FAINT = 0xFF6A717C;
    private static final int C_GREEN = 0xFF43C57F;
    private static final int C_BLUE = 0xFF4DA2FF;
    private static final int C_AMBER = 0xFFFFC24D;
    private static final int C_GRAY = 0xFF6A7078;
    private static final int C_PURPLE = 0xFFC88BFF;
    private static final int C_RED = 0xFFE05A5A;

    // ---------------------------------------------------------------- 自动布局

    /** 节点 id → 画布内坐标（原点在树的左上角）。 */
    private static final Map<String, int[]> LAYOUT = new LinkedHashMap<>();
    private static int treeW = NODE_W;
    private static int treeH = NODE_H;

    static {
        buildLayout();
    }

    /** 按 DAG 最长路径分列；列内按注册顺序排列，并整体垂直居中。 */
    private static void buildLayout() {
        Map<String, TechNodes.Node> nodes = TechNodes.all();
        Map<String, Integer> depth = new HashMap<>();
        for (String id : nodes.keySet()) {
            depthOf(id, depth, nodes, new ArrayList<>());
        }
        Map<Integer, List<String>> columns = new LinkedHashMap<>();
        for (String id : nodes.keySet()) {
            columns.computeIfAbsent(depth.getOrDefault(id, 0), k -> new ArrayList<>()).add(id);
        }
        int maxColumnHeight = NODE_H;
        for (List<String> column : columns.values()) {
            maxColumnHeight = Math.max(maxColumnHeight,
                    column.size() * NODE_H + (column.size() - 1) * GAP_Y);
        }
        LAYOUT.clear();
        for (var entry : columns.entrySet()) {
            int col = entry.getKey();
            List<String> column = entry.getValue();
            int columnHeight = column.size() * NODE_H + (column.size() - 1) * GAP_Y;
            int startY = (maxColumnHeight - columnHeight) / 2;
            for (int row = 0; row < column.size(); row++) {
                LAYOUT.put(column.get(row), new int[]{
                        col * (NODE_W + GAP_X),
                        startY + row * (NODE_H + GAP_Y)});
            }
        }
        int cols = columns.size();
        treeW = cols * NODE_W + Math.max(0, cols - 1) * GAP_X;
        treeH = maxColumnHeight;
    }

    private static int depthOf(String id, Map<String, Integer> cache,
                               Map<String, TechNodes.Node> nodes, List<String> visiting) {
        Integer cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        TechNodes.Node node = nodes.get(id);
        if (node == null || visiting.contains(id)) {
            return 0;
        }
        visiting.add(id);
        int depth = 0;
        for (String parent : node.parents()) {
            depth = Math.max(depth, depthOf(parent, cache, nodes, visiting) + 1);
        }
        visiting.remove(id);
        cache.put(id, depth);
        return depth;
    }

    private record NodeBox(TechNodes.Node node, int x, int y) {}

    private final List<NodeBox> boxes = new ArrayList<>();

    // ---------------------------------------------------------------- 视图状态

    private int canvasX, canvasY, canvasW, canvasH;
    private int sidebarX, sidebarY, sidebarH;

    private float scale = 1.0f;
    private float panX, panY;
    private boolean viewInitialized = false;

    private boolean dragging = false;
    private double dragLastX, dragLastY;

    private String selected = null;
    private NodeBox hovered = null;

    public TechTreeScreen(TechTreeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = TechTreeMenu.PANEL_W;
        this.imageHeight = TechTreeMenu.PANEL_H;
        this.inventoryLabelY = -9999;
    }

    // ---------------------------------------------------------------- 初始化

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.canvasX = this.leftPos + PAD;
        this.canvasY = this.topPos + TOP_BAR_H + PAD;
        this.canvasW = TechTreeMenu.PANEL_W - PAD * 3 - SIDEBAR_W;
        this.canvasH = INVENTORY_TOP - TOP_BAR_H - PAD * 2;

        this.sidebarX = this.leftPos + TechTreeMenu.PANEL_W - PAD - SIDEBAR_W;
        this.sidebarY = this.canvasY;
        this.sidebarH = this.canvasH;

        this.boxes.clear();
        for (var entry : TechNodes.all().entrySet()) {
            int[] pos = LAYOUT.get(entry.getKey());
            if (pos == null) {
                continue;
            }
            this.boxes.add(new NodeBox(entry.getValue(), pos[0], pos[1]));
        }

        if (!this.viewInitialized) {
            this.fitToView();
            this.viewInitialized = true;
        }
        if (this.selected == null) {
            this.selected = defaultSelection();
        }
    }

    /** 默认选中：在研 → 第一个可研究 → 第一个节点。 */
    private String defaultSelection() {
        String current = ModNetwork.CLIENT_CURRENT;
        if (current != null && TechNodes.exists(current)) {
            return current;
        }
        for (TechNodes.Node node : TechNodes.all().values()) {
            if (canResearchClient(node)) {
                return node.id();
            }
        }
        for (TechNodes.Node node : TechNodes.all().values()) {
            return node.id();
        }
        return null;
    }

    private void fitToView() {
        float fitX = (float) (canvasW - 10) / treeW;
        float fitY = (float) (canvasH - 10) / treeH;
        this.scale = Math.max(0.45f, Math.min(1.0f, Math.min(fitX, fitY)));
        this.panX = (canvasW - treeW * this.scale) / 2f;
        this.panY = (canvasH - treeH * this.scale) / 2f;
    }

    // ---------------------------------------------------------------- 渲染主流程

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 全部自绘，屏蔽默认标题与背包标签
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x0 = this.leftPos;
        int y0 = this.topPos;

        // 面板底
        graphics.fill(x0, y0, x0 + TechTreeMenu.PANEL_W, y0 + TechTreeMenu.PANEL_H, C_BG);
        outline(graphics, x0, y0, TechTreeMenu.PANEL_W, TechTreeMenu.PANEL_H, C_BORDER);

        renderTopBar(graphics);

        // 画布
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, C_CANVAS);
        renderCanvas(graphics, mouseX, mouseY);
        outline(graphics, canvasX, canvasY, canvasW, canvasH, C_BORDER);

        // 侧栏
        graphics.fill(sidebarX, sidebarY, sidebarX + SIDEBAR_W, sidebarY + sidebarH, C_PANEL);
        outline(graphics, sidebarX, sidebarY, SIDEBAR_W, sidebarH, C_BORDER);
        renderSidebar(graphics, mouseX, mouseY);

        // 背包区
        int invY = y0 + INVENTORY_TOP;
        int invH = TechTreeMenu.PANEL_H - INVENTORY_TOP - PAD;
        graphics.fill(x0 + PAD, invY, x0 + TechTreeMenu.PANEL_W - PAD, y0 + TechTreeMenu.PANEL_H - PAD, C_PANEL);
        outline(graphics, x0 + PAD, invY, TechTreeMenu.PANEL_W - PAD * 2, invH, C_BORDER);

        // 槽位底纹
        for (var slot : this.menu.slots) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF0C0E13);
        }

        // 研究包槽标签
        if (this.menu.hasTable()) {
            graphics.drawString(this.font, "研究包输入", x0 + 24, y0 + 244, C_TEXT_FAINT, false);
        }
    }

    /** 描边（x, y, 宽, 高）。 */
    private void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.renderOutline(x, y, w, h, color);
    }

    // ---------------------------------------------------------------- 顶栏

    private void renderTopBar(GuiGraphics graphics) {
        int x0 = this.leftPos;
        int y0 = this.topPos;

        graphics.fill(x0 + 1, y0 + 1, x0 + TechTreeMenu.PANEL_W - 1, y0 + TOP_BAR_H, C_PANEL);
        graphics.fill(x0 + 1, y0 + TOP_BAR_H, x0 + TechTreeMenu.PANEL_W - 1,
                y0 + TOP_BAR_H + 1, C_BORDER);

        graphics.drawString(this.font, this.title, x0 + PAD + 2, y0 + 7, C_TEXT, true);

        int x = x0 + PAD + 2 + this.font.width(this.title) + 12;

        // 时代徽章
        String eraKey = "gui.astrofactory.era.manual";
        int eraColor = 0xFFB08A5A;
        if (clientLevel(TechNodes.ELECTRIC_POWER) >= 1) {
            eraKey = "gui.astrofactory.era.electric";
            eraColor = C_BLUE;
        } else if (clientLevel(TechNodes.STEAM_POWER) >= 1) {
            eraKey = "gui.astrofactory.era.fuel";
            eraColor = 0xFFE08A3C;
        }
        String era = Component.translatable(eraKey).getString();
        int eraW = this.font.width(era) + 8;
        graphics.fill(x, y0 + 4, x + eraW, y0 + TOP_BAR_H - 3, 0xFF1B2029);
        outline(graphics, x, y0 + 4, eraW, TOP_BAR_H - 7, eraColor);
        graphics.drawString(this.font, era, x + 4, y0 + 8, eraColor, false);
        x += eraW + 10;

        // 完成度
        int done = 0;
        int total = TechNodes.all().size();
        for (TechNodes.Node node : TechNodes.all().values()) {
            if (clientLevel(node.id()) >= node.maxLevel()) {
                done++;
            }
        }
        graphics.drawString(this.font,
                Component.translatable("gui.astrofactory.completed", done, total).getString(),
                x, y0 + 8, C_TEXT_DIM, false);
        x += this.font.width(
                Component.translatable("gui.astrofactory.completed", done, total).getString()) + 8;

        // 创造模式徽章：明确告诉玩家「现在是全解锁试玩态」，而不是真的研究完了
        if (ModNetwork.CLIENT_CREATIVE_BYPASS) {
            String badge = Component.translatable("gui.astrofactory.creative_badge").getString();
            int bw = this.font.width(badge) + 8;
            graphics.fill(x, y0 + 4, x + bw, y0 + TOP_BAR_H - 3, 0xFF1E2A1F);
            outline(graphics, x, y0 + 4, bw, TOP_BAR_H - 7, C_GREEN);
            graphics.drawString(this.font, badge, x + 4, y0 + 8, C_GREEN, false);
        }

        // 在研进度（右对齐）
        String current = ModNetwork.CLIENT_CURRENT;
        if (current != null && TechNodes.exists(current)) {
            TechNodes.Node node = TechNodes.get(current);
            int cost = Math.max(1, TechNodes.costOf(current));
            int progress = Math.min(cost, ModNetwork.CLIENT_PROGRESS);
            String label = Component.translatable("gui.astrofactory.researching",
                    Component.translatable(node.nameKey()), progress, cost).getString();
            int textW = this.font.width(label);
            int right = x0 + TechTreeMenu.PANEL_W - PAD - 2;
            graphics.drawString(this.font, label, right - textW, y0 + 4, C_AMBER, false);
            int barW = Math.min(150, textW);
            int barX = right - barW;
            int barY = y0 + TOP_BAR_H - 7;
            graphics.fill(barX, barY, barX + barW, barY + 3, 0xFF262B34);
            graphics.fill(barX, barY, barX + (int) (barW * (progress / (float) cost)), barY + 3, C_AMBER);
        }
    }

    // ---------------------------------------------------------------- 画布

    private void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvasX + panX, canvasY + panY, 0f);
        pose.scale(scale, scale, 1f);

        // 网格底纹
        for (int gx = -600; gx <= treeW + 600; gx += 24) {
            graphics.fill(gx, -600, gx + 1, treeH + 600, C_GRID);
        }
        for (int gy = -600; gy <= treeH + 600; gy += 24) {
            graphics.fill(-600, gy, treeW + 600, gy + 1, C_GRID);
        }

        // 连线 + 箭头
        Map<String, NodeBox> byId = new HashMap<>();
        for (NodeBox box : this.boxes) {
            byId.put(box.node().id(), box);
        }
        for (NodeBox box : this.boxes) {
            for (String parentId : box.node().parents()) {
                NodeBox from = byId.get(parentId);
                if (from == null) {
                    continue;
                }
                boolean satisfied = clientLevel(parentId) >= 1;
                int color = satisfied ? 0xFF3E7F5C : 0xFF343941;
                int x1 = from.x() + NODE_W;
                int y1 = from.y() + NODE_H / 2;
                int x2 = box.x();
                int y2 = box.y() + NODE_H / 2;
                int midX = x1 + Math.max(4, (x2 - x1) / 2);
                graphics.fill(x1, y1 - 1, midX, y1 + 1, color);
                graphics.fill(midX - 1, Math.min(y1, y2), midX + 1, Math.max(y1, y2) + 1, color);
                graphics.fill(midX, y2 - 1, x2, y2 + 1, color);
                graphics.fill(x2 - 4, y2 - 3, x2 - 1, y2 + 3, color);
                graphics.fill(x2 - 6, y2 - 2, x2 - 4, y2 + 2, color);
            }
        }

        // 节点
        this.hovered = null;
        for (NodeBox box : this.boxes) {
            renderNode(graphics, box, mouseX, mouseY);
        }

        pose.popPose();
        graphics.disableScissor();

        // 画布左下角操作提示
        graphics.drawString(this.font,
                Component.translatable("gui.astrofactory.detail.hint").getString(),
                canvasX + 4, canvasY + canvasH - 11, C_TEXT_FAINT, false);
    }

    private void renderNode(GuiGraphics graphics, NodeBox box, int mouseX, int mouseY) {
        TechNodes.Node node = box.node();
        int level = clientLevel(node.id());
        boolean completed = level >= node.maxLevel();
        boolean researching = node.id().equals(ModNetwork.CLIENT_CURRENT);
        boolean available = canResearchClient(node);
        boolean isSelected = node.id().equals(this.selected);

        int border;
        int fill;
        if (completed) {
            border = C_GREEN;
            fill = 0xFF15251C;
        } else if (researching) {
            border = C_AMBER;
            fill = 0xFF2A2416;
        } else if (available) {
            border = C_BLUE;
            fill = 0xFF161F2B;
        } else {
            border = 0xFF3A3F48;
            fill = 0xFF171A20;
        }

        int x = box.x();
        int y = box.y();
        graphics.fill(x, y, x + NODE_W, y + NODE_H, fill);
        graphics.fill(x, y, x + 3, y + NODE_H, eraColor(node.era()));
        outline(graphics, x, y, NODE_W, NODE_H, isSelected ? 0xFFFFFFFF : border);
        if (isSelected) {
            outline(graphics, x - 1, y - 1, NODE_W + 2, NODE_H + 2, 0xFF7A8493);
        }

        if (inBox(mouseX, mouseY, box)) {
            this.hovered = box;
            outline(graphics, x - 2, y - 2, NODE_W + 4, NODE_H + 4, 0xFFB9C2CE);
        }

        // 名称（按像素宽度折行，最多两行）
        String name = Component.translatable(node.nameKey()).getString();
        List<String> lines = wrap(name, NODE_W - 12, 2);
        int textX = x + 6;
        int textY = y + (lines.size() > 1 ? 5 : 10);
        for (String line : lines) {
            graphics.drawString(this.font, line, textX, textY, C_TEXT, false);
            textY += 10;
        }

        // 右下角角标
        String badge;
        int badgeColor;
        if (completed) {
            badge = node.maxLevel() > 1 ? level + "/" + node.maxLevel() : "\u2714";
            badgeColor = C_GREEN;
        } else if (researching) {
            badge = ModNetwork.CLIENT_PROGRESS + "/" + TechNodes.costOf(node.id());
            badgeColor = C_AMBER;
        } else if (node.triggerItem() != null) {
            int have = triggerCount(node.triggerItem());
            badge = have + "/" + node.triggerCount();
            badgeColor = have > 0 ? C_AMBER : C_TEXT_FAINT;
        } else if (node.maxLevel() > 1) {
            badge = level + "/" + node.maxLevel();
            badgeColor = C_TEXT_DIM;
        } else {
            badge = TechNodes.costOf(node.id()) + "p";
            badgeColor = C_TEXT_DIM;
        }
        graphics.drawString(this.font, badge,
                x + NODE_W - 5 - this.font.width(badge), y + NODE_H - 11, badgeColor, false);

        // 左下角研究包标记
        int packColor = node.pack() == TechNodes.PackTier.ADVANCED ? C_PURPLE : 0xFFB0B6BF;
        graphics.fill(x + 5, y + NODE_H - 9, x + 9, y + NODE_H - 5, packColor);
    }

    // ---------------------------------------------------------------- 侧栏

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = sidebarX + 7;
        int y = sidebarY + 6;
        int w = SIDEBAR_W - 14;

        TechNodes.Node node = this.selected == null ? null : TechNodes.get(this.selected);
        if (node == null) {
            List<String> hint = wrap(Component.translatable("gui.astrofactory.detail.none")
                    .getString(), w, 3);
            int hy = sidebarY + sidebarH / 2 - hint.size() * 5;
            for (String line : hint) {
                graphics.drawString(this.font, line, x, hy, C_TEXT_FAINT, false);
                hy += 11;
            }
            return;
        }

        graphics.enableScissor(sidebarX + 1, sidebarY + 1,
                sidebarX + SIDEBAR_W - 1, sidebarY + sidebarH - 26);

        // 名称
        for (String line : wrap(Component.translatable(node.nameKey()).getString(), w, 2)) {
            graphics.drawString(this.font, line, x, y, C_TEXT, true);
            y += 11;
        }
        y += 2;

        // 时代 + 状态
        String era = Component.translatable(eraKey(node.era())).getString();
        String status = Component.translatable(statusKey(node)).getString();
        graphics.drawString(this.font, era, x, y, eraColor(node.era()), false);
        graphics.drawString(this.font, status, x + w - this.font.width(status), y,
                statusColor(node), false);
        y += 12;

        // 描述
        for (String line : wrap(Component.translatable("desc.astrofactory." + node.id()).getString(), w, 4)) {
            graphics.drawString(this.font, line, x, y, C_TEXT_DIM, false);
            y += 10;
        }
        y += 3;

        // 成本 / 等级 / 研究包
        if (node.triggerItem() == null) {
            y = row(graphics, x, y, w, "gui.astrofactory.detail.cost",
                    Component.translatable("gui.astrofactory.cost", node.costPerLevel()).getString());
        }
        if (node.maxLevel() > 1) {
            y = row(graphics, x, y, w, "gui.astrofactory.detail.level",
                    Component.translatable("gui.astrofactory.level",
                            clientLevel(node.id()), node.maxLevel()).getString());
        }
        y = row(graphics, x, y, w, "gui.astrofactory.detail.pack",
                Component.translatable("gui.astrofactory.pack."
                        + (node.pack() == TechNodes.PackTier.ADVANCED ? "advanced" : "basic")).getString());

        // 触发进度
        if (node.triggerItem() != null) {
            y += 2;
            graphics.drawString(this.font,
                    Component.translatable("gui.astrofactory.detail.trigger").getString(),
                    x, y, C_TEXT_FAINT, false);
            y += 10;
            int have = triggerCount(node.triggerItem());
            int need = Math.max(1, node.triggerCount());
            String itemName = Component.translatable(
                    "item." + node.triggerItem().replace(':', '.')).getString();
            graphics.drawString(this.font, itemName + "  " + Math.min(have, need) + "/" + need,
                    x, y, C_AMBER, false);
            y += 11;
            graphics.fill(x, y, x + w, y + 4, 0xFF262B34);
            graphics.fill(x, y, x + (int) (w * Math.min(1f, have / (float) need)), y + 4, C_AMBER);
            y += 8;
        }

        // 前置
        if (node.parents().length > 0) {
            y += 2;
            graphics.drawString(this.font,
                    Component.translatable("gui.astrofactory.detail.requires").getString(),
                    x, y, C_TEXT_FAINT, false);
            y += 10;
            for (String parentId : node.parents()) {
                TechNodes.Node parent = TechNodes.get(parentId);
                if (parent == null) {
                    continue;
                }
                boolean ok = clientLevel(parentId) >= 1;
                graphics.drawString(this.font,
                        (ok ? "\u2714 " : "\u2716 ")
                                + Component.translatable(parent.nameKey()).getString(),
                        x, y, ok ? C_GREEN : C_RED, false);
                y += 10;
            }
        }

        graphics.disableScissor();

        renderActionButton(graphics, mouseX, mouseY, node);
    }

    private void renderActionButton(GuiGraphics graphics, int mouseX, int mouseY, TechNodes.Node node) {
        int bx = sidebarX + 7;
        int by = sidebarY + sidebarH - 22;
        int bw = SIDEBAR_W - 14;
        int bh = 17;

        boolean clickable = canResearchClient(node);
        String label;
        int color;
        if (clientLevel(node.id()) >= node.maxLevel()) {
            label = "gui.astrofactory.action.done";
            color = C_GREEN;
        } else if (node.id().equals(ModNetwork.CLIENT_CURRENT)) {
            label = "gui.astrofactory.action.researching";
            color = C_AMBER;
        } else if (node.triggerItem() != null) {
            label = "gui.astrofactory.action.triggered";
            color = C_TEXT_FAINT;
        } else if (clickable) {
            label = "gui.astrofactory.action.research";
            color = C_BLUE;
        } else {
            label = "gui.astrofactory.action.locked";
            color = C_GRAY;
        }

        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        int fill = clickable ? (hover ? 0xFF1F3550 : 0xFF182636) : 0xFF1A1D23;
        graphics.fill(bx, by, bx + bw, by + bh, fill);
        outline(graphics, bx, by, bw, bh,
                clickable ? (hover ? 0xFF8CC6FF : color) : 0xFF31363E);
        String text = Component.translatable(label).getString();
        graphics.drawString(this.font, text, bx + (bw - this.font.width(text)) / 2,
                by + 5, clickable ? C_TEXT : color, false);
    }

    /** 一行「标签 ……… 值」。 */
    private int row(GuiGraphics graphics, int x, int y, int w, String labelKey, String value) {
        graphics.drawString(this.font, Component.translatable(labelKey).getString(),
                x, y, C_TEXT_FAINT, false);
        graphics.drawString(this.font, value, x + w - this.font.width(value), y, C_TEXT_DIM, false);
        return y + 10;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 侧栏操作按钮
            int bx = sidebarX + 7;
            int by = sidebarY + sidebarH - 22;
            if (mouseX >= bx && mouseX < bx + SIDEBAR_W - 14
                    && mouseY >= by && mouseY < by + 17) {
                TechNodes.Node node = this.selected == null ? null : TechNodes.get(this.selected);
                if (node != null && canResearchClient(node)) {
                    PacketDistributor.sendToServer(new ModNetwork.SelectResearchPayload(node.id()));
                }
                return true;
            }
            // 画布：点节点选中，点空白开始拖拽
            if (insideCanvas(mouseX, mouseY)) {
                NodeBox hit = nodeAt(mouseX, mouseY);
                if (hit != null) {
                    this.selected = hit.node().id();
                    return true;
                }
                this.dragging = true;
                this.dragLastX = mouseX;
                this.dragLastY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (this.dragging) {
            this.panX += (float) (mouseX - this.dragLastX);
            this.panY += (float) (mouseY - this.dragLastY);
            this.dragLastX = mouseX;
            this.dragLastY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideCanvas(mouseX, mouseY) && scrollY != 0) {
            float oldScale = this.scale;
            float factor = scrollY > 0 ? 1.12f : 1f / 1.12f;
            this.scale = Math.max(0.35f, Math.min(2.0f, oldScale * factor));
            // 以鼠标位置为锚点缩放
            float worldX = (float) ((mouseX - canvasX - panX) / oldScale);
            float worldY = (float) ((mouseY - canvasY - panY) / oldScale);
            this.panX = (float) (mouseX - canvasX) - worldX * this.scale;
            this.panY = (float) (mouseY - canvasY) - worldY * this.scale;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasX && mouseX < canvasX + canvasW
                && mouseY >= canvasY && mouseY < canvasY + canvasH;
    }

    private NodeBox nodeAt(double mouseX, double mouseY) {
        for (NodeBox box : this.boxes) {
            if (inBox(mouseX, mouseY, box)) {
                return box;
            }
        }
        return null;
    }

    private boolean inBox(double mouseX, double mouseY, NodeBox box) {
        float worldX = (float) ((mouseX - canvasX - panX) / scale);
        float worldY = (float) ((mouseY - canvasY - panY) / scale);
        return worldX >= box.x() && worldX < box.x() + NODE_W
                && worldY >= box.y() && worldY < box.y() + NODE_H;
    }

    // ---------------------------------------------------------------- Tooltip

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (this.hovered != null && insideCanvas(x, y)) {
            TechNodes.Node node = this.hovered.node();
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(node.nameKey())
                    .withStyle(node.pack() == TechNodes.PackTier.ADVANCED
                            ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE));
            tooltip.add(Component.translatable(eraKey(node.era())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("desc.astrofactory." + node.id())
                    .withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), x, y);
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    // ---------------------------------------------------------------- 数据 / 工具

    private static int clientLevel(String id) {
        // 创造模式旁路生效时，服务端 snapshot 已把每项等级填成满级；
        // 这里再兜一层，防止客户端在收到新 payload 之前短暂显示灰色。
        if (ModNetwork.CLIENT_CREATIVE_BYPASS) {
            TechNodes.Node node = TechNodes.get(id);
            return node == null ? 1 : node.maxLevel();
        }
        return ModNetwork.CLIENT_LEVELS.getOrDefault(id, 0);
    }

    private static int triggerCount(String itemId) {
        return ModNetwork.CLIENT_TRIGGER_COUNTS.getOrDefault(itemId, 0);
    }

    private static boolean canResearchClient(TechNodes.Node node) {
        if (clientLevel(node.id()) >= node.maxLevel()) {
            return false;
        }
        // 触发型科技不可主动研究，只能靠做出物品触发
        if (node.triggerItem() != null) {
            return false;
        }
        for (String parent : node.parents()) {
            if (clientLevel(parent) < 1) {
                return false;
            }
        }
        return true;
    }

    private static String eraKey(TechNodes.Era era) {
        return switch (era) {
            case MANUAL -> "gui.astrofactory.era.manual";
            case FUEL -> "gui.astrofactory.era.fuel";
            case ELECTRIC -> "gui.astrofactory.era.electric";
        };
    }

    private static int eraColor(TechNodes.Era era) {
        return switch (era) {
            case MANUAL -> 0xFFB08A5A;
            case FUEL -> 0xFFE08A3C;
            case ELECTRIC -> C_BLUE;
        };
    }

    private static String statusKey(TechNodes.Node node) {
        if (clientLevel(node.id()) >= node.maxLevel()) {
            return "gui.astrofactory.status.completed";
        }
        if (node.id().equals(ModNetwork.CLIENT_CURRENT)) {
            return "gui.astrofactory.status.researching";
        }
        if (node.triggerItem() != null) {
            return "gui.astrofactory.status.triggered";
        }
        return canResearchClient(node)
                ? "gui.astrofactory.status.available"
                : "gui.astrofactory.status.locked";
    }

    private static int statusColor(TechNodes.Node node) {
        if (clientLevel(node.id()) >= node.maxLevel()) {
            return C_GREEN;
        }
        if (node.id().equals(ModNetwork.CLIENT_CURRENT)) {
            return C_AMBER;
        }
        if (node.triggerItem() != null) {
            return C_TEXT_FAINT;
        }
        return canResearchClient(node) ? C_BLUE : C_GRAY;
    }

    /** 按像素宽度折行（中英文混排可用）。 */
    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() >= maxLines) {
                    return lines;
                }
                continue;
            }
            current.append(c);
            if (this.font.width(current.toString()) > maxWidth) {
                current.deleteCharAt(current.length() - 1);
                if (current.length() == 0) {
                    current.append(c);
                }
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() >= maxLines) {
                    return lines;
                }
                current.append(c);
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        return lines;
    }
}
