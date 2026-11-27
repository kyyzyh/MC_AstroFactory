package com.astrofactory.client;

import com.astrofactory.menu.BoilerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 锅炉信息面板（US-101）。
 *
 * <p>清单要求「GUI 显示蒸汽量」，这里一并显示水量、燃烧进度和缓冲上限。
 * 锅炉没有物品槽，燃料靠手持右键投料，所以面板只做状态展示。</p>
 */
public class BoilerScreen extends AbstractContainerScreen<BoilerMenu> {

    private static final int W = 176;
    private static final int H = 110;

    public BoilerScreen(BoilerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = H + 100; // 无背包，隐藏
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MachineGui.panel(g, x, y, W, H);

        boolean burning = menu.getBurnTime() > 0;
        boolean hasSteam = menu.getSteam() > 0;
        String status = Component.translatable(burning
                ? "gui.astrofactory.boiler.status.burning"
                : "gui.astrofactory.boiler.status.idle").getString();
        MachineGui.header(g, this.font, x, y, W,
                Component.translatable("block.astrofactory.boiler").getString(),
                status, burning ? MachineGui.C_AMBER : MachineGui.C_TEXT_FAINT);

        int cap = Math.max(1, menu.getFluidCapacity());

        // 燃烧进度条
        int burnTotal = menu.getBurnTotal();
        float burnRatio = burnTotal > 0 ? menu.getBurnTime() / (float) burnTotal : 0f;
        g.drawString(this.font,
                Component.translatable("gui.astrofactory.boiler.burn").getString(),
                x + 8, y + 22, MachineGui.C_TEXT_DIM, false);
        MachineGui.hbar(g, x + 8, y + 32, W - 16, 6, burnRatio,
                burning ? MachineGui.C_AMBER : MachineGui.C_TRACK);

        // 水量
        g.drawString(this.font,
                Component.translatable("gui.astrofactory.boiler.water").getString(),
                x + 8, y + 44, MachineGui.C_TEXT_DIM, false);
        g.drawString(this.font, menu.getWater() + " / " + cap,
                x + 60, y + 44, MachineGui.C_BLUE, false);
        MachineGui.hbar(g, x + 8, y + 54, W - 16, 6,
                menu.getWater() / (float) cap, MachineGui.C_BLUE);

        // 蒸汽量
        g.drawString(this.font,
                Component.translatable("gui.astrofactory.boiler.steam").getString(),
                x + 8, y + 66, MachineGui.C_TEXT_DIM, false);
        g.drawString(this.font, menu.getSteam() + " / " + cap,
                x + 60, y + 66, hasSteam ? MachineGui.C_GREEN : MachineGui.C_TEXT_FAINT, false);
        MachineGui.hbar(g, x + 8, y + 76, W - 16, 6,
                menu.getSteam() / (float) cap, MachineGui.C_GREEN);

        // 提示行
        g.drawString(this.font,
                Component.translatable("gui.astrofactory.boiler.hint").getString(),
                x + 8, y + H - 14, MachineGui.C_TEXT_FAINT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 全部内容在 renderBg 里画（无物品槽，不需要默认槽位标签）
    }
}
