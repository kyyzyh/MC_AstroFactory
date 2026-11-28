package com.astrofactory.menu;

import com.astrofactory.blockentity.IMachineInfo;
import com.astrofactory.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 通用机器信息面板菜单（v0.8.9）。
 *
 * <p>为「自动采矿机 / 自动熔炉 / 机械臂 / 抽水机 / 燃料发电机」等
 * 没有独立面板的机器提供<b>只读信息面板</b>：展示运行状态、能量、进度、
 * 输入与输出槽。菜单不持有实际槽位（只读，不可交互取放），数值与槽内容
 * 通过 DataSlot（整数）同步给客户端。</p>
 *
 * <p>槽内容无法用 DataSlot 传，因此客户端侧直接读取服务端 BE 的 handler
 * （{@link IMachineInfo#infoInput()}/{@link IMachineInfo#infoOutput()}），
 * 与 BoilerMenu 客户端直接持有 BE 引用同一思路——客户端方块实体虽不 tick，
 * 但槽内容在打开面板时是快照，足够展示。</p>
 */
public class InfoPanelMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    /** 服务端 BE；客户端为 null（客户端从方块位置重新取 BE）。 */
    private final IMachineInfo machine;

    /** 状态位打包（单 DataSlot）：0=有能量条 1=有进度条 2=有输入 3=有输出。 */
    private static final int HAS_ENERGY = 1;
    private static final int HAS_PROGRESS = 1 << 1;
    private static final int HAS_INPUT = 1 << 2;
    private static final int HAS_OUTPUT = 1 << 3;

    private int syncedEnergy = 0;
    private int syncedEnergyCap = 0;
    private int syncedProgress = 0;
    private int syncedMaxProgress = 0;
    private int syncedStatusFlags = 0;

    /** 客户端构造：extraData 只传方块位置。 */
    public InfoPanelMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, beOf(playerInv, buf.readBlockPos()));
    }

    private static IMachineInfo beOf(Inventory playerInv, BlockPos pos) {
        return playerInv.player.level().getBlockEntity(pos) instanceof IMachineInfo info ? info : null;
    }

    /** 服务端构造。 */
    public InfoPanelMenu(int containerId, Inventory playerInv, IMachineInfo machine) {
        super(ModMenus.INFO_PANEL.get(), containerId);
        this.machine = machine;
        this.access = machine == null || !(machine instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return machine != null ? machine.infoEnergy() : syncedEnergy;
            }
            @Override
            public void set(int value) {
                syncedEnergy = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return machine != null ? machine.infoEnergyCapacity() : syncedEnergyCap;
            }
            @Override
            public void set(int value) {
                syncedEnergyCap = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return machine != null ? machine.infoProgress() : syncedProgress;
            }
            @Override
            public void set(int value) {
                syncedProgress = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return machine != null ? machine.infoMaxProgress() : syncedMaxProgress;
            }
            @Override
            public void set(int value) {
                syncedMaxProgress = value;
            }
        });
        // 状态打包：有能量条/进度条/输入/输出
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (machine == null) {
                    return syncedStatusFlags;
                }
                int f = 0;
                if (machine.infoEnergy() >= 0 && machine.infoEnergyCapacity() > 0) {
                    f |= HAS_ENERGY;
                }
                if (machine.infoProgress() >= 0 && machine.infoMaxProgress() > 0) {
                    f |= HAS_PROGRESS;
                }
                if (machine.hasInfoInput()) {
                    f |= HAS_INPUT;
                }
                if (machine.hasInfoOutput()) {
                    f |= HAS_OUTPUT;
                }
                return f;
            }
            @Override
            public void set(int value) {
                syncedStatusFlags = value;
            }
        });
    }

    // ---------------------------------------------------------------- 客户端读取

    public String infoTitle() {
        return machine != null ? machine.infoTitle() : "";
    }

    /** 客户端也直接读 BE 以取得输入槽内容快照。 */
    public ItemStackHandler inputHandler() {
        return machine != null ? machine.infoInput() : new ItemStackHandler(0);
    }

    public ItemStackHandler outputHandler() {
        return machine != null ? machine.infoOutput() : new ItemStackHandler(0);
    }

    public int getEnergy() {
        return this.syncedEnergy;
    }

    public int getEnergyCapacity() {
        return this.syncedEnergyCap;
    }

    public int getProgress() {
        return this.syncedProgress;
    }

    public int getMaxProgress() {
        return Math.max(1, this.syncedMaxProgress);
    }

    public boolean hasEnergyBar() {
        return (this.syncedStatusFlags & HAS_ENERGY) != 0;
    }

    public boolean hasProgressBar() {
        return (this.syncedStatusFlags & HAS_PROGRESS) != 0;
    }

    public boolean hasInput() {
        return (this.syncedStatusFlags & HAS_INPUT) != 0;
    }

    public boolean hasOutput() {
        return (this.syncedStatusFlags & HAS_OUTPUT) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // 只读面板
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.machine == null) {
            return true; // 客户端
        }
        if (!(this.machine instanceof net.minecraft.world.level.block.entity.BlockEntity be)) {
            return true;
        }
        return AbstractContainerMenu.stillValid(this.access, player,
                be.getBlockState().getBlock());
    }

    /** 状态键（渲染层用于查翻译/配色）。客户端与服务器都直接读 BE。 */
    public String statusKey() {
        return machine != null ? machine.infoStatusKey() : "idle";
    }
}
