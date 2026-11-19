package com.xiaoyi.astrofactory.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.xiaoyi.astrofactory.menu.SmelterMenu;

/**
 * 熔炼炉界面：输入槽 + 进度箭头 + 输出槽。
 */
public class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/furnace.png");

    public SmelterScreen(SmelterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                (float) this.imageWidth, (float) this.imageHeight, 256.0f, 256.0f);
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        // 进度条：从 0 到 100% 逐步填充
        int percent = this.menu.getProgressPercent();
        if (percent > 0) {
            int width = 24 * Math.min(100, percent) / 100;
            extractor.fill(this.leftPos + 79, this.topPos + 34,
                    this.leftPos + 79 + width, this.topPos + 48,
                    0xFFFFFFFF);
        }
    }
}
