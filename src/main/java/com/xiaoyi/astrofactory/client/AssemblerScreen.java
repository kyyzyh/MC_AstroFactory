package com.xiaoyi.astrofactory.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.xiaoyi.astrofactory.menu.AssemblerMenu;

/**
 * 组装机界面：3x3 输入 + 输出 + 玩家背包。
 * 26.3 渲染管线为 extract 式（GuiGraphicsExtractor），不是旧的 render(GuiGraphics)。
 * 背景沿用原版发射器贴图，避免额外美术资源。
 */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                (float) this.imageWidth, (float) this.imageHeight, 256.0f, 256.0f);
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        // 断电提示（规格 §5.4：组装机断电 GUI 显示「无电力」）
        if (!this.menu.isPowered()) {
            Component text = Component.translatable("gui.astrofactory.no_power");
            int x = this.leftPos + 8;
            int y = this.topPos + 72;
            extractor.fill(x - 1, y - 1, x + this.font.width(text) + 1, y + 9, 0xC0000000);
            extractor.text(this.font, text, x, y, 0xFFFF5555);
        }
    }
}
