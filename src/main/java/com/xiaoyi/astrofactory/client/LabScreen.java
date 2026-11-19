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
 * 科研台界面：显示当前研究目标、进度条、解锁计数，并提供「投入」按钮。
 *
 * 26.3 渲染管线是 extract 式（GuiGraphicsExtractor），背景沿用原版发射器贴图。
 */
public class LabScreen extends AbstractContainerScreen<LabMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    private static final int BAR_X = 66;
    private static final int BAR_Y = 40;
    private static final int BAR_W = 80;
    private static final int BAR_H = 8;

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
        // 「投入」按钮：位于进度条右侧
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.astrofactory.research.contribute"),
                        b -> this.sendContribute())
                .bounds(this.leftPos + BAR_X, this.topPos + 54, 80, 18)
                .build());
    }

    private void sendContribute() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(
                    this.menu.containerId, LabMenu.BUTTON_CONTRIBUTE);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                (float) this.imageWidth, (float) this.imageHeight, 256.0f, 256.0f);
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        Technology tech = this.menu.currentTech();
        int x = this.leftPos + BAR_X;
        int y = this.topPos + BAR_Y;

        if (tech == null) {
            extractor.text(this.font,
                    Component.translatable("gui.astrofactory.research.none"),
                    x, y, 0xFF555555, false);
            return;
        }

        int points = this.menu.currentPoints();
        int cost = Math.max(1, this.menu.currentCost());
        int percent = Math.min(100, points * 100 / cost);

        // 科技名
        extractor.text(this.font, Component.translatable(tech.displayKey()),
                x, y - 11, 0xFF404040, false);
        // 进度条底
        extractor.fill(x, y, x + BAR_W, y + BAR_H, 0xFF3A3A3A);
        // 进度条填充
        extractor.fill(x + 1, y + 1, x + 1 + (BAR_W - 2) * percent / 100, y + BAR_H - 1,
                0xFF4FC3F7);
        // 数值
        extractor.text(this.font, Component.literal(points + " / " + cost),
                x, y + BAR_H + 2, 0xFF404040, false);
        // 解锁计数
        extractor.text(this.font,
                Component.translatable("gui.astrofactory.research.unlocked_count",
                        this.menu.unlockedCount(), this.menu.totalTechs()),
                this.leftPos + 8, this.topPos + 30, 0xFF404040, false);
    }
}
