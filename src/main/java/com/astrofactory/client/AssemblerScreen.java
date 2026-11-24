package com.astrofactory.client;

import com.astrofactory.blockentity.AssemblerBlockEntity;
import com.astrofactory.menu.AssemblerMenu;
import com.astrofactory.registry.ModMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 组装机界面：3x3 网格 + 输出槽 + 能量条。
 */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "astrofactory", "textures/gui/assembler.png");

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 无贴图时用纯色背景 + 描边（与科技树 GUI 同风格）
        graphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xF0101018);
        graphics.renderOutline(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF3C3F45);

        // 网格区域描边
        graphics.renderOutline(this.leftPos + 28, this.topPos + 15,
                this.leftPos + 28 + 58, this.topPos + 15 + 58, 0xFF4A4E55);
        // 输出槽描边
        graphics.renderOutline(this.leftPos + 122, this.topPos + 33,
                this.leftPos + 122 + 18, this.topPos + 33 + 18, 0xFF4A4E55);
        // 箭头
        int arrowX = this.leftPos + 92;
        int arrowY = this.topPos + 38;
        graphics.fill(arrowX, arrowY, arrowX + 24, arrowY + 4, 0xFF6A9CFF);
        graphics.fill(arrowX + 24, arrowY - 2, arrowX + 27, arrowY + 6, 0xFF6A9CFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.leftPos + 8, this.topPos + 5, 0xFFFFFF, false);
    }
}
