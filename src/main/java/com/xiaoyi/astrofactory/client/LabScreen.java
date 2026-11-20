package com.xiaoyi.astrofactory.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.xiaoyi.astrofactory.menu.LabMenu;
import com.xiaoyi.astrofactory.research.Technology;

/**
 * 科研台界面：显示当前研究目标、进度条、解锁计数，并提供「上一个 / 投入 / 下一个」按钮。
 *
 * <p>26.3 渲染管线是 extract 式（{@link GuiGraphicsExtractor}），背景沿用原版发射器贴图。</p>
 *
 * <p>布局（相对 leftPos / topPos，界面 176×166）：</p>
 * <pre>
 *   y= 6  标题
 *   y=14  已解锁 x / y
 *   y=24  科技名（右侧显示 第 n / m 个可研究项）
 *   y=34  进度条 68..158
 *   y=44  进度数值
 *   y=54  [&lt;] [ 投入 ] [&gt;]
 *   y=84  玩家背包
 * </pre>
 */
public class LabScreen extends AbstractContainerScreen<LabMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    private static final int BAR_X = 68;
    private static final int BAR_Y = 34;
    private static final int BAR_W = 90;
    private static final int BAR_H = 8;

    private static final int BTN_Y = 54;
    private static final int BTN_H = 16;

    public LabScreen(LabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();

        // 上一个目标
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.astrofactory.research.prev"),
                        b -> this.sendButton(LabMenu.BUTTON_PREV))
                .bounds(this.leftPos + 68, this.topPos + BTN_Y, 20, BTN_H)
                .build());

        // 投入
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.astrofactory.research.contribute"),
                        b -> this.sendButton(LabMenu.BUTTON_CONTRIBUTE))
                .bounds(this.leftPos + 90, this.topPos + BTN_Y, 46, BTN_H)
                .build());

        // 下一个目标
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.astrofactory.research.next"),
                        b -> this.sendButton(LabMenu.BUTTON_NEXT))
                .bounds(this.leftPos + 138, this.topPos + BTN_Y, 20, BTN_H)
                .build());
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                (float) this.imageWidth, (float) this.imageHeight, 256.0f, 256.0f);
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        int x = this.leftPos + BAR_X;
        int y = this.topPos + BAR_Y;

        // 解锁计数
        extractor.text(this.font,
                Component.translatable("gui.astrofactory.research.unlocked_count",
                        this.menu.unlockedCount(), this.menu.totalTechs()),
                this.leftPos + 8, this.topPos + 14, 0xFF404040, false);

        Technology tech = this.menu.currentTech();
        if (tech == null) {
            extractor.text(this.font,
                    Component.translatable("gui.astrofactory.research.none"),
                    x, this.topPos + 24, 0xFF555555, false);
            return;
        }

        int points = this.menu.currentPoints();
        int cost = Math.max(1, this.menu.currentCost());
        int percent = Math.min(100, points * 100 / cost);

        // 科技名
        extractor.text(this.font, Component.translatable(tech.displayKey()),
                x, this.topPos + 24, 0xFF404040, false);

        // 第 n / m 个可研究项（多于 1 个时才显示，提示可以切换）
        int count = this.menu.researchableCount();
        if (count > 1) {
            extractor.text(this.font,
                    Component.translatable("gui.astrofactory.research.target_index",
                            this.menu.selectedOrdinal() + 1, count),
                    this.leftPos + 148, this.topPos + 24, 0xFF808080, false);
        }

        // 进度条底
        extractor.fill(x, y, x + BAR_W, y + BAR_H, 0xFF3A3A3A);
        // 进度条填充
        extractor.fill(x + 1, y + 1, x + 1 + (BAR_W - 2) * percent / 100, y + BAR_H - 1,
                0xFF4FC3F7);
        // 数值
        extractor.text(this.font, Component.literal(points + " / " + cost),
                x, this.topPos + 44, 0xFF404040, false);
    }
}
