package com.astrofactory.client;

import com.astrofactory.menu.InfoPanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 通用机器信息面板（v0.8.9）。
 *
 * <p>参考《异星工厂》机器信息面板：深色底板 + 顶部标题与状态灯，
 * 下方为能量条、进度条、输入区（左侧）与输出区（右侧）。
 * 无背包区（纯只读查看，不可取放物品）。</p>
 */
public class InfoPanelScreen extends AbstractContainerScreen<InfoPanelMenu> {

    private static final int W = 176;
    private static final int H = 132;

    private static final int C_BG = MachineGui.C_BG;
    private static final int C_PANEL = MachineGui.C_PANEL;
    private static final int C_BORDER = MachineGui.C_BORDER;
    private static final int C_TEXT = MachineGui.C_TEXT;
    private static final int C_TEXT_DIM = MachineGui.C_TEXT_DIM;
    private static final int C_TEXT_FAINT = MachineGui.C_TEXT_FAINT;
    private static final int C_BLUE = MachineGui.C_BLUE;
    private static final int C_AMBER = MachineGui.C_AMBER;
    private static final int C_GREEN = MachineGui.C_GREEN;
    private static final int C_RED = MachineGui.C_RED;
    private static final int C_TRACK = MachineGui.C_TRACK;

    public InfoPanelScreen(InfoPanelMenu menu, Inventory playerInv, Component title) {
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

        String title = menu.infoTitle();
        String status = Component.translatable(
                        "gui.astrofactory.info.status." + statusKey())
                .getString();
        int statusColor = statusColor(statusKey());
        MachineGui.header(g, this.font, x, y, W, title, status, statusColor);

        int row = 22;
        // 能量条
        if (menu.hasEnergyBar()) {
            g.drawString(this.font,
                    Component.translatable("gui.astrofactory.info.energy",
                            menu.getEnergy(), menu.getEnergyCapacity()).getString(),
                    x + 8, y + row, C_TEXT_DIM, false);
            MachineGui.hbar(g, x + 8, y + row + 10, W - 16, 6,
                    menu.getEnergyCapacity() > 0
                            ? menu.getEnergy() / (float) menu.getEnergyCapacity() : 0f,
                    energyColor());
            row += 22;
        }
        // 进度条
        if (menu.hasProgressBar()) {
            g.drawString(this.font,
                    Component.translatable("gui.astrofactory.info.progress",
                            menu.getProgress(), menu.getMaxProgress()).getString(),
                    x + 8, y + row, C_TEXT_DIM, false);
            MachineGui.hbar(g, x + 8, y + row + 10, W - 16, 6,
                    menu.getProgress() / (float) menu.getMaxProgress(), C_AMBER);
            row += 22;
        }

        // 输入 / 输出槽区
        if (menu.hasInput()) {
            drawSlotGroup(g, x + 8, y + row, "gui.astrofactory.info.input", "left", menu.inputHandler());
        }
        if (menu.hasOutput()) {
            drawSlotGroup(g, x + W - 8 - 52, y + row, "gui.astrofactory.info.output", "right", menu.outputHandler());
        }
    }

    /** 输入/输出槽区：标题 + 若干 18px 槽并排。 */
    private void drawSlotGroup(GuiGraphics g, int x, int y, String labelKey, String align, ItemStackHandler handler) {
        g.drawString(this.font, Component.translatable(labelKey).getString(),
                x, y, C_TEXT_FAINT, false);
        int slotsY = y + 10;
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            int slotX = x + i * 20;
            g.fill(slotX, slotsY, slotX + 18, slotsY + 18, 0xFF07090C);
            g.renderOutline(slotX, slotsY, slotX + 18, slotsY + 18, C_BORDER);
            if (handler.getStackInSlot(i) != null && !handler.getStackInSlot(i).isEmpty()) {
                g.renderItem(handler.getStackInSlot(i), slotX + 1, slotsY + 1);
            }
        }
        // 空提示
        // （槽为空时画一个占位叉线，提示该位置可用）
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 全部内容已在 renderBg 里绘制（无背包槽 / 无玩家栏）
    }

    // ---------------------------------------------------------------- 状态与配色

    /** 客户端状态键：优先取服务端通过 DataSlot 同步进来的？——不，状态键是字符串，
     *  无法走 DataSlot。这里直接读 BE（InfoPanelMenu.inputHandler 同样的方式）。 */
    private String statusKey() {
        // 用菜单持有的 IMachineInfo——但客户端被构造为 null。因此从方块位置重新取。
        // InfoPanelMenu 未暴露状态键 getter，这里通过对服务端同一 BE 的读取无法做到，
        // 因此在菜单新增 getter，见 InfoPanelMenu.statusKey()。
        return this.menu.statusKey();
    }

    private int statusColor(String key) {
        switch (key) {
            case "working":
                return C_GREEN;
            case "idle":
                return C_TEXT_FAINT;
            case "no_fuel":
                return C_AMBER;
            case "no_power":
                return C_RED;
            case "no_ore":
                return C_AMBER;
            case "no_water":
                return C_BLUE;
            default:
                return C_TEXT_DIM;
        }
    }

    private int energyColor() {
        float ratio = menu.getEnergyCapacity() > 0
                ? menu.getEnergy() / (float) menu.getEnergyCapacity() : 0f;
        if (ratio < 0.15f) {
            return C_RED;
        }
        return ratio > 0.85f ? C_GREEN : C_BLUE;
    }
}
