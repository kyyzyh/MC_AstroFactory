package com.xiaoyi.astrofactory.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.xiaoyi.astrofactory.menu.BoilerMenu;

/**
 * 锅炉界面：燃料槽 + 蒸汽量条。
 */
public class BoilerScreen extends AbstractContainerScreen<BoilerMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/furnace.png");

    public BoilerScreen(BoilerMenu menu, Inventory inventory, Component title) {
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

        // 蒸汽量条（竖向，最大 100）
        int steam = this.menu.getSteam();
        if (steam > 0) {
            int height = 40 * Math.min(100, steam) / 100;
            extractor.fill(this.leftPos + 110, this.topPos + 60 - height,
                    this.leftPos + 118, this.topPos + 60,
                    0xFFB0D8F0);
        }
    }
}
