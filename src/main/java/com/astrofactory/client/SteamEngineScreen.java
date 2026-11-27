package com.astrofactory.client;

import com.astrofactory.menu.SteamEngineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 蒸汽机信息面板（US-101）。
 *
 * <p>清单要求「GUI 显示发电量」。这里展示储能条、累计发电、累计耗汽，
 * 并把「是否相邻锅炉」的间接信息体现在状态灯上（有发电在涨 = 正在工作）。</p>
 */
public class SteamEngineScreen extends AbstractContainerScreen<SteamEngineMenu> {

    private static final int W = 176;
    private static final int H = 118;

    public SteamEngineScreen(SteamEngineMenu menu, Inventory playerInv, Component title) {
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

        int stored = menu.getEnergyStored();
        int cap = menu.getEnergyCapacity();
        boolean full = stored >= cap;
        boolean hasPower = stored > 0;

        String status = Component.translatable(
                full ? "gui.astrofactory.steam.full"
                        : hasPower ? "gui.astrofactory.steam.working"
                        : "gui.astrofactory.steam.idle").getString();
        int statusColor = full ? MachineGui.C_GREEN
                : hasPower ? MachineGui.C_AMBER : MachineGui.C_TEXT_FAINT;

        MachineGui.header(g, this.font, x, y, W,
                Component.translatable("block.astrofactory.steam_engine").getString(),
                status, statusColor);

        // 竖向储能条 + 右侧数值
        int barX = x + 10;
        int barY = y + 24;
        int barW = 10;
        int barH = 62;
        MachineGui.vbar(g, barX, barY, barW, barH, stored / (float) cap, MachineGui.C_GREEN);

        int tx = barX + barW + 10;
        MachineGui.line(g, this.font, tx, y + 24,
                Component.translatable("gui.astrofactory.steam.energy").getString(),
                stored + " / " + cap + " FE", MachineGui.C_GREEN);

        MachineGui.line(g, this.font, tx, y + 40,
                Component.translatable("gui.astrofactory.steam.rate").getString(),
                menu.getGenerationPerTick() + " FE/t", MachineGui.C_BLUE);

        MachineGui.line(g, this.font, tx, y + 56,
                Component.translatable("gui.astrofactory.steam.generated").getString(),
                menu.getGeneratedTotal() + " FE", MachineGui.C_AMBER);

        MachineGui.line(g, this.font, tx, y + 72,
                Component.translatable("gui.astrofactory.steam.consumed").getString(),
                String.valueOf(menu.getSteamConsumed()), MachineGui.C_TEXT_DIM);

        g.drawString(this.font,
                Component.translatable("gui.astrofactory.steam.hint").getString(),
                x + 8, y + H - 14, MachineGui.C_TEXT_FAINT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 全部内容在 renderBg 里画
    }
}
