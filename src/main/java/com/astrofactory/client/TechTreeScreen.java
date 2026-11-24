package com.astrofactory.client;

import com.astrofactory.menu.TechTreeMenu;
import com.astrofactory.network.ModNetwork;
import com.astrofactory.tech.TechNodes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 科技树可视化界面（借鉴《异星工厂》科技树 GUI）。
 *
 * <p>布局：按 DAG 层级从左到右排列节点，节点间画连线；
 * 颜色语义——绿色 = 已研究，蓝色 = 可研究，黄色 = 在研，灰色 = 前置未满足。
 * 点击可研究/在研节点即向服务端发送「选定研究」请求。</p>
 */
public class TechTreeScreen extends AbstractContainerScreen<TechTreeMenu> {

    /** 节点渲染尺寸。 */
    private static final int NODE_W = 64;
    private static final int NODE_H = 40;
    private static final int GAP_X = 28;
    private static final int GAP_Y = 12;

    /** 手工布局：每个节点 id → (列, 行)。列从 0 起。v0.6：11 节点 8 列。 */
    private static final Map<String, int[]> LAYOUT = new HashMap<>();
    static {
        LAYOUT.put(TechNodes.BASIC_AUTOMATION, new int[]{0, 1});
        LAYOUT.put(TechNodes.MINING, new int[]{1, 0});
        LAYOUT.put(TechNodes.LOGISTICS, new int[]{1, 2});
        LAYOUT.put(TechNodes.SMELTING, new int[]{2, 0});
        LAYOUT.put(TechNodes.EFFICIENT_MINING, new int[]{2, 1});
        LAYOUT.put(TechNodes.STEAM_POWER, new int[]{3, 0});
        LAYOUT.put(TechNodes.STEEL, new int[]{3, 2});
        LAYOUT.put(TechNodes.EFFICIENT_SMELTING, new int[]{4, 0});
        LAYOUT.put(TechNodes.ELECTRIC_POWER, new int[]{5, 1});
        LAYOUT.put(TechNodes.ADVANCED_PACKS, new int[]{6, 1});
        LAYOUT.put(TechNodes.AUTOMATION, new int[]{7, 1});
    }

    /** 每列的节点数（用于垂直居中）。 */
    private static final Map<Integer, Integer> COLUMN_COUNTS = new HashMap<>();

    static {
        for (int[] pos : LAYOUT.values()) {
            COLUMN_COUNTS.merge(pos[0], 1, Integer::sum);
        }
    }

    private record NodeBox(TechNodes.Node node, int x, int y) {}

    private final List<NodeBox> boxes = new ArrayList<>();
    private NodeBox hovered = null;

    public TechTreeScreen(TechTreeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 360;
        this.imageHeight = 200;
        this.inventoryLabelY = -9999; // 隐藏默认标签
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        // 计算每个节点的屏幕坐标
        this.boxes.clear();
        int treeTop = this.topPos + 14;
        int treeLeft = this.leftPos + 8;
        for (var entry : TechNodes.all().entrySet()) {
            int[] pos = LAYOUT.get(entry.getKey());
            if (pos == null) {
                continue;
            }
            int col = pos[0];
            int count = COLUMN_COUNTS.getOrDefault(col, 1);
            int columnHeight = count * NODE_H + (count - 1) * GAP_Y;
            int x = treeLeft + col * (NODE_W + GAP_X);
            int y = treeTop + 44 - columnHeight / 2 + pos[1] * (NODE_H + GAP_Y);
            this.boxes.add(new NodeBox(entry.getValue(), x, y));
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // 科技进度由 payload 异步刷新，这里无需处理
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 背景
        graphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xF0101018);
        graphics.renderOutline(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF3C3F45);

        // 标题
        graphics.drawString(this.font, this.title,
                this.leftPos + 8, this.topPos + 5, 0xFFFFFF, false);

        // 在研状态行
        String current = ModNetwork.CLIENT_CURRENT;
        if (current != null && TechNodes.exists(current)) {
            TechNodes.Node node = TechNodes.get(current);
            int cost = TechNodes.costOf(current);
            int progress = ModNetwork.CLIENT_PROGRESS;
            String text = Component.translatable("gui.astrofactory.researching",
                    Component.translatable(node.nameKey()), progress, cost).getString();
            graphics.drawString(this.font, text,
                    this.leftPos + this.imageWidth - 8 - this.font.width(text),
                    this.topPos + 5, 0xFF55FFFF, false);
        }

        // 连线（先画线再画节点，线被节点盖住）
        this.hovered = null;
        Map<String, NodeBox> byId = new HashMap<>();
        for (NodeBox box : this.boxes) {
            byId.put(box.node().id(), box);
        }
        for (NodeBox box : this.boxes) {
            for (String parent : box.node().parents()) {
                NodeBox from = byId.get(parent);
                if (from == null) {
                    continue;
                }
                int x1 = from.x() + NODE_W;
                int y1 = from.y() + NODE_H / 2;
                int x2 = box.x();
                int y2 = box.y() + NODE_H / 2;
                int color = clientLevel(parent) >= 1 ? 0xFF3AAA64 : 0xFF555555;
                graphics.fill(x1, y1 - 1, x2, y1 + 1, color);
                graphics.fill(x2 - 3, y2 - 1, x2, y2 + 1, color);
                if (y1 != y2) {
                    // 折线：水平段 + 垂直段 + 水平段
                    int midX = x2 - GAP_X / 2;
                    graphics.fill(x1, y1 - 1, midX, y1 + 1, color);
                    graphics.fill(midX - 1, Math.min(y1, y2), midX + 1, Math.max(y1, y2) + 1, color);
                    graphics.fill(midX, y2 - 1, x2, y2 + 1, color);
                }
            }
        }

        // 节点
        for (NodeBox box : this.boxes) {
            renderNode(graphics, box, mouseX, mouseY);
        }
    }

    private void renderNode(GuiGraphics graphics, NodeBox box, int mouseX, int mouseY) {
        TechNodes.Node node = box.node();
        int level = clientLevel(node.id());
        boolean unlocked = level >= node.maxLevel();
        boolean inProgress = node.id().equals(ModNetwork.CLIENT_CURRENT);
        boolean researchable = canResearchClient(node);

        int border;
        int fill;
        if (unlocked) {
            border = 0xFF3AAA64;
            fill = 0xFF1E3D2C;
        } else if (inProgress) {
            border = 0xFFFFC24D;
            fill = 0xFF3D331E;
        } else if (researchable) {
            border = 0xFF4DA2FF;
            fill = 0xFF1E2C3D;
        } else {
            border = 0xFF4A4A4A;
            fill = 0xFF202024;
        }

        graphics.fill(box.x(), box.y(), box.x() + NODE_W, box.y() + NODE_H, fill);
        graphics.renderOutline(box.x(), box.y(), box.x() + NODE_W, box.y() + NODE_H, border);

        // 名称（最多两行，每行 9 字符宽度自适应缩略）
        String name = Component.translatable(node.nameKey()).getString();
        List<String> lines = wrap(name, 10);
        int textY = box.y() + 5;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawCenteredString(this.font, lines.get(i),
                    box.x() + NODE_W / 2, textY, 0xFFFFFF);
            textY += 9;
        }

        // 底部状态行
        String status;
        if (unlocked) {
            status = node.maxLevel() > 1 ? level + "/" + node.maxLevel() : "✓";
        } else if (inProgress) {
            status = ModNetwork.CLIENT_PROGRESS + "/" + TechNodes.costOf(node.id());
        } else if (node.triggerItem() != null) {
            // v0.6 触发型科技：显示触发进度（客户端无实时计数，显示阈值）
            status = "⚡" + node.triggerCount();
        } else {
            status = TechNodes.costOf(node.id()) + "p";
        }
        int statusColor = unlocked ? 0xFF3AAA64 : inProgress ? 0xFFFFC24D
                : researchable ? 0xFF4DA2FF : 0xFF888888;
        graphics.drawCenteredString(this.font, status,
                box.x() + NODE_W / 2, box.y() + NODE_H - 11, statusColor);

        // 包等级标记
        if (node.pack() == TechNodes.PackTier.ADVANCED) {
            graphics.drawString(this.font, "▲",
                    box.x() + NODE_W - 8, box.y() + 2, 0xFFFF7BAC, false);
        }

        if (mouseX >= box.x() && mouseX < box.x() + NODE_W
                && mouseY >= box.y() && mouseY < box.y() + NODE_H) {
            this.hovered = box;
            graphics.renderOutline(box.x(), box.y(), box.x() + NODE_W, box.y() + NODE_H, 0xFFFFFFFF);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        super.renderTooltip(graphics, x, y);
        if (this.hovered != null) {
            TechNodes.Node node = this.hovered.node();
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(node.nameKey())
                    .withStyle(node.pack() == TechNodes.PackTier.ADVANCED
                            ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE));
            if (node.maxLevel() > 1) {
                tooltip.add(Component.translatable("gui.astrofactory.level",
                        clientLevel(node.id()), node.maxLevel()).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable("gui.astrofactory.cost",
                    TechNodes.costOf(node.id())).withStyle(ChatFormatting.GRAY));
            // v0.6 触发型科技提示
            if (node.triggerItem() != null) {
                tooltip.add(Component.translatable("gui.astrofactory.triggered",
                        Component.translatable("item." + node.triggerItem().replace(":", ".")
                                .replace("astrofactory.item.", "astrofactory.")),
                        node.triggerCount()).withStyle(ChatFormatting.YELLOW));
            }
            tooltip.add(Component.translatable("gui.astrofactory.pack."
                    + (node.pack() == TechNodes.PackTier.ADVANCED ? "advanced" : "basic"))
                    .withStyle(ChatFormatting.GRAY));
            // 前置
            if (node.parents().length > 0) {
                List<Component> parents = new ArrayList<>();
                for (String p : node.parents()) {
                    Component name = Component.translatable(TechNodes.get(p).nameKey());
                    parents.add(clientLevel(p) >= 1
                            ? name.copy().withStyle(ChatFormatting.GREEN)
                            : name.copy().withStyle(ChatFormatting.RED));
                }
                tooltip.add(Component.translatable("gui.astrofactory.requires")
                        .append(Component.literal(" "))
                        .append(ComponentUtils.join(parents)));
            }
            // 描述
            tooltip.add(Component.translatable("desc.astrofactory." + node.id())
                    .withStyle(ChatFormatting.DARK_GRAY));
            // 操作提示
            if (canResearchClient(node) && !node.id().equals(ModNetwork.CLIENT_CURRENT)) {
                tooltip.add(Component.translatable("gui.astrofactory.click_to_research")
                        .withStyle(ChatFormatting.AQUA));
            }
            graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), x, y);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.hovered != null && button == 0) {
            TechNodes.Node node = this.hovered.node();
            if (canResearchClient(node)) {
                com.astrofactory.AstroFactory.LOGGER.info("[TechTree] 请求研究 {}", node.id());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.astrofactory.network.ModNetwork.SelectResearchPayload(node.id()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---------------------------------------------------------------- 客户端数据

    private static int clientLevel(String id) {
        return ModNetwork.CLIENT_LEVELS.getOrDefault(id, 0);
    }

    private static boolean canResearchClient(TechNodes.Node node) {
        if (clientLevel(node.id()) >= node.maxLevel()) {
            return false;
        }
        // v0.6 触发型科技不可主动研究，只能等动作触发
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

    /** 简单按字符数折行。 */
    private static List<String> wrap(String text, int charsPerLine) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length() && lines.size() < 2) {
            int end = Math.min(text.length(), start + charsPerLine);
            lines.add(text.substring(start, end));
            start = end;
        }
        return lines;
    }

    /** 极简 Component 连接工具。 */
    private static final class ComponentUtils {
        static Component join(List<Component> parts) {
            Component result = parts.get(0);
            for (int i = 1; i < parts.size(); i++) {
                result = result.copy().append(", ").append(parts.get(i));
            }
            return result;
        }
    }
}
