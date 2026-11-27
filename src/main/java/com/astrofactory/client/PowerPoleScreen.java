package com.astrofactory.client;

import com.astrofactory.menu.PowerPoleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 电线杆信息面板（US-102）。
 *
 * <p>展示这张电网的规模与实时吞吐：杆数、发电设备数、耗电设备数、
 * 上 tick 传输量 / 上限。状态灯：有传输=绿、有设备但无电=红、无设备=灰。</p>
 */
public class PowerPoleScreen extends AbstractContainerScreen<PowerPoleMenu> {

    private static final int W = 176;
    private static final int H = 118;

    public PowerPoleScreen(PowerPoleMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = H + 100;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MachineGui.panel(g, x, y, W, H);

        int poles = menu.getPoleCount();
        int sources = menu.getSourceCount();
        int sinks = menu.getSinkCount();
        int transfer = menu.getTransfer();
        int maxTransfer = menu.getMaxTransfer();

        String status;
        int statusColor;
        if (transfer > 0) {
            status = Component.translatable("gui.astrofactory.pole.flowing").getString();
            statusColor = MachineGui.C_GREEN;
        } else if (sources > 0 && sinks > 0) {
            status = Component.translatable("gui.astrofactory.pole.starved").getString();
            statusColor = MachineGui.C_RED;
        } else {
            status = Component.translatable("gui.astrofactory.pole.idle").getString();
            statusColor = MachineGui.C_TEXT_FAINT;
        }

        MachineGui.header(g, this.font, x, y, W,
                Component.translatable("block.astrofactory.power_pole").getString(),
                status, statusColor);

        // 传输量横条
        int barX = x + 10;
        int barY = y + 26;
        int barW = W - 20;
        float ratio = maxTransfer > 0 ? transfer / (float) maxTransfer : 0f;
        MachineGui.hbar(g, barX, barY, barW, 8, ratio, MachineGui.C_GREEN);

        int ty = y + 40;
        MachineGui.line(g, this.font, barX, ty,
                Component.translatable("gui.astrofactory.pole.transfer").getString(),
                transfer + " / " + maxTransfer + " FE/t", MachineGui.C_GREEN);

        MachineGui.line(g, this.font, barX, ty + 14,
                Component.translatable("gui.astrofactory.pole.poles").getString(),
                String.valueOf(poles), MachineGui.C_BLUE);

        MachineGui.line(g, this.font, barX, ty + 28,
                Component.translatable("gui.astrofactory.pole.sources").getString(),
                String.valueOf(sources), MachineGui.C_AMBER);

        MachineGui.line(g, this.font, barX, ty + 42,
                Component.translatable("gui.astrofactory.pole.sinks").getString(),
                String.valueOf(sinks), MachineGui.C_TEXT);

        MachineGui.line(g, this.font, barX, ty + 56,
                Component.translatable("gui.astrofactory.pole.range").getString(),
                menu.getRadius() + " / " + menu.getLinkRange(), MachineGui.C_TEXT_DIM);

        g.drawString(this.font,
                Component.translatable("gui.astrofactory.pole.hint").getString(),
                x + 8, y + H - 14, MachineGui.C_TEXT_FAINT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 全部内容在 renderBg 里画
    }
}
