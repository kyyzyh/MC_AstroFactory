package com.xiaoyi.astrofactory.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.xiaoyi.astrofactory.menu.AssemblerMenu;

/**
 * 组装机界面：3x3 输入 + 输出 + 玩家背包。
 *
 * <p>26.3 渲染管线为 extract 式（{@link GuiGraphicsExtractor}），不是旧的
 * {@code render(GuiGraphics)}。背景沿用原版发射器贴图，避免额外美术资源。</p>
 *
 * <p><b>规格红线</b>（§5.7 第 13-15 步）：玩家放下组装机、发现它不转，
 * 才被告知需要发电。所以「不转」必须有明确反馈 —— 右上角状态灯变红
 * 并写明「无电力」，而不是让玩家以为是 bug。这就是 {@link MachineScreen}
 * 存在的理由。</p>
 */
public class AssemblerScreen extends MachineScreen<AssemblerMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                (float) this.imageWidth, (float) this.imageHeight, 256.0f, 256.0f);
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        Status status = this.menu.isPowered() ? Status.RUNNING : Status.NO_POWER;
        drawStatus(extractor, status);
        drawStatusText(extractor, status);

        // 断电时额外给一行醒目提示（规格 §5.4：GUI 显示「无电力」）
        if (!this.menu.isPowered()) {
            Component text = Component.translatable("gui.astrofactory.no_power");
            int x = this.leftPos + 8;
            int y = this.topPos + 72;
            extractor.fill(x - 1, y - 1, x + this.font.width(text) + 1, y + 9, 0xC0000000);
            extractor.text(this.font, text, x, y, 0xFFFF5555);
        }
    }
}
