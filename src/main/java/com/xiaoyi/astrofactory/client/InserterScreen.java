package com.xiaoyi.astrofactory.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.xiaoyi.astrofactory.block.BurnerInserterBlockEntity.ArmStatus;
import com.xiaoyi.astrofactory.menu.InserterMenu;

/**
 * 机械臂界面。
 *
 * <h2>布局</h2>
 * <pre>
 *  ┌──────────────────────────────────────┐
 *  │ 燃烧机械臂                    [●] 运行中 │
 *  │  [煤]                                    │
 *  │        ┌─┬─┬─┐   过滤：[白名单] [×1]    │
 *  │        ├─┼─┼─┤   Keep N：不限  [调整]   │
 *  │        └─┴─┴─┘                          │
 *  │  [ 玩家背包 ]                            │
 *  └──────────────────────────────────────┘
 * </pre>
 *
 * <h2>为什么把「卡住原因」放在 GUI 里而不是只在 tooltip</h2>
 * <p>玩家打开 GUI 的动作本身就是「我要修它」，所以答案应该出现在这里。
 * 把「NO_SPACE」翻译成「输出侧放不下」比显示一个红灯有用得多 ——
 * 前者直接告诉玩家去检查什么。</p>
 */
public class InserterScreen extends MachineScreen<InserterMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    private Button whitelistButton;
    private Button limitButton;
    private Button keepNButton;

    public InserterScreen(InserterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + 106;
        int y = this.topPos + 16;

        whitelistButton = this.addRenderableWidget(Button.builder(
                        filterLabel(),
                        b -> sendButton(InserterMenu.BUTTON_WHITELIST))
                .bounds(x, y, 62, 16).build());

        limitButton = this.addRenderableWidget(Button.builder(
                        limitLabel(),
                        b -> sendButton(InserterMenu.BUTTON_LIMIT))
                .bounds(x, y + 18, 62, 16).build());

        keepNButton = this.addRenderableWidget(Button.builder(
                        keepNLabel(),
                        b -> sendButton(InserterMenu.BUTTON_KEEP_N))
                .bounds(x, y + 36, 62, 16).build());
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private Component filterLabel() {
        return Component.translatable(this.menu.isWhitelist()
                ? "gui.astrofactory.inserter.filter.whitelist"
                : "gui.astrofactory.inserter.filter.blacklist");
    }

    private Component limitLabel() {
        return Component.translatable("gui.astrofactory.inserter.limit", this.menu.getMoveLimit());
    }

    private Component keepNLabel() {
        int n = this.menu.getKeepN();
        return n <= 0
                ? Component.translatable("gui.astrofactory.inserter.keep.off")
                : Component.translatable("gui.astrofactory.inserter.keep", n);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                (float) this.imageWidth, (float) this.imageHeight, 256.0f, 256.0f);
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        // 按钮文案随数据变化，每帧刷新
        if (whitelistButton != null) {
            whitelistButton.setMessage(filterLabel());
            limitButton.setMessage(limitLabel());
            keepNButton.setMessage(keepNLabel());
        }

        ArmStatus status = this.menu.getStatus();
        drawStatus(extractor, toStatus(status));
        drawStatusText(extractor, toStatus(status));

        // 「为什么不动」的完整说明（比状态灯更具体）
        Component reason = Component.translatable(reasonKey(status));
        int rx = this.leftPos + 8;
        int ry = this.topPos + 68;
        extractor.text(this.font, reason, rx, ry, toStatus(status).color);

        // 燃料槽标签，避免玩家把煤误放进过滤槽
        extractor.text(this.font, Component.translatable("gui.astrofactory.inserter.fuel"),
                this.leftPos + 6, this.topPos + 8, 0xFF404040);
    }

    /** 把机械臂的细粒度状态映射到统一的 6 态（决定灯色）。 */
    private static Status toStatus(ArmStatus s) {
        return switch (s) {
            case RUNNING -> Status.RUNNING;
            case NO_FUEL -> Status.NO_FUEL;
            case NO_INPUT, FILTERED -> Status.NO_INPUT;
            case NO_SPACE, KEEP_SATISFIED -> Status.OUTPUT_FULL;
            case IDLE -> Status.IDLE;
        };
    }

    /** 每个细粒度状态一句人话。 */
    private static String reasonKey(ArmStatus s) {
        return switch (s) {
            case RUNNING -> "gui.astrofactory.inserter.reason.running";
            case NO_FUEL -> "gui.astrofactory.inserter.reason.no_fuel";
            case NO_INPUT -> "gui.astrofactory.inserter.reason.no_input";
            case FILTERED -> "gui.astrofactory.inserter.reason.filtered";
            case NO_SPACE -> "gui.astrofactory.inserter.reason.no_space";
            case KEEP_SATISFIED -> "gui.astrofactory.inserter.reason.keep";
            case IDLE -> "gui.astrofactory.inserter.reason.idle";
        };
    }
}
