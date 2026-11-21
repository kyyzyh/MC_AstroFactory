package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.xiaoyi.astrofactory.ModRegistry;
import com.xiaoyi.astrofactory.machine.AbstractMachineEntity;
import com.xiaoyi.astrofactory.machine.MachineFeedback;
import com.xiaoyi.astrofactory.menu.AssemblerMenu;
import com.xiaoyi.astrofactory.power.ElectricPowerSource;
import com.xiaoyi.astrofactory.recipe.MachineRecipe;
import com.xiaoyi.astrofactory.recipe.MachineType;
import com.xiaoyi.astrofactory.recipe.RecipeRegistry;

/**
 * 组装机方块实体。
 *
 * <p>槽位布局：0..8 = 3x3 输入，9 = 输出。
 * 每 tick 推进一次合成进度，输入匹配某条内置配方时产出（结果槽满则等待）。</p>
 *
 * <p>电力驱动（规格 §5.4）：断电停机，GUI 显示「无电力」。</p>
 */
public class AssemblerBlockEntity extends AbstractMachineEntity {
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOT = 9;
    public static final int TOTAL_SLOTS = 10;
    /** 默认合成耗时；实际耗时取配方里的 time 字段。 */
    public static final int CRAFT_INTERVAL = MachineType.ASSEMBLING.defaultTicks();

    private int craftProgress = 0;
    /** 电力驱动（规格 §5.4：组装机是电动的，断电停机）。 */
    private final ElectricPowerSource power = new ElectricPowerSource(this);

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ASSEMBLER_BE.get(), pos, state, TOTAL_SLOTS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblerBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        // 输入全空时无需匹配配方（也避免无谓的配方表初始化）
        MachineRecipe match = hasAnyInput() ? RecipeRegistry.findGrid(slotView()) : null;
        // 断电停机（规格 §5.4）：没有电力则无法合成
        boolean powered = power.consume(true);
        boolean active = match != null && powered;

        if (active) {
            power.consume(false);
            int interval = match.ticks();
            craftProgress++;
            // 加工中偶发火花（零资产，用原版 CRIT 粒子）
            MachineFeedback.tickParticles(level, pos, net.minecraft.core.particles.ParticleTypes.CRIT);
            if (craftProgress >= interval) {
                craftProgress = 0;
                if (craft(match)) {
                    setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    MachineFeedback.playCraft(level, pos);
                }
            }
        } else {
            craftProgress = 0;
        }

        setActive(active);
    }

    private boolean hasAnyInput() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 执行合成；成功返回 true。 */
    private boolean craft(MachineRecipe recipe) {
        ItemStack result = recipe.resultStack();
        // 输出槽能否容纳
        ItemStack out = getItem(OUTPUT_SLOT);
        if (!recipe.fitsInto(out)) {
            return false;
        }
        // 只消耗配方要求的格子，多余物品原样留在输入槽
        for (int i = 0; i < INPUT_SLOTS; i++) {
            MachineRecipe.Ingredient need = recipe.inputs().get(i);
            if (need == null) {
                continue;
            }
            ItemStack stack = getItem(i);
            if (stack.isEmpty()) {
                return false;
            }
            stack.shrink(need.count());
            if (stack.isEmpty()) {
                setItem(i, ItemStack.EMPTY);
            }
        }
        // 产出
        if (out.isEmpty()) {
            setItem(OUTPUT_SLOT, result);
        } else {
            out.grow(result.getCount());
        }
        return true;
    }

    /** 尝试从外部插入（机械臂/传送带）。 */
    public boolean insertItem(ItemStack stack) {
        // 先合并
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack slot = getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                setChanged();
                return true;
            }
        }
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (getItem(i).isEmpty()) {
                setItem(i, stack.copyWithCount(1));
                setChanged();
                return true;
            }
        }
        return false;
    }

    /** 当前合成进度百分比，供界面显示。 */
    public int getProgressPercent() {
        MachineRecipe recipe = hasAnyInput() ? RecipeRegistry.findGrid(slotView()) : null;
        int interval = recipe == null ? CRAFT_INTERVAL : recipe.ticks();
        return Math.min(100, craftProgress * 100 / Math.max(1, interval));
    }

    /** 是否通电（供界面显示"无电力"）。 */
    public boolean isPowered() {
        return power.isPowered();
    }

    /** 电力状态文本。 */
    public String getPowerStatus() {
        return power.statusText();
    }

    public ItemStack extractResult() {
        ItemStack out = getItem(OUTPUT_SLOT);
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        setItem(OUTPUT_SLOT, ItemStack.EMPTY);
        setChanged();
        return out;
    }

    /** 输出槽禁止外部插入（只能通过内部合成产出）。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != OUTPUT_SLOT;
    }

    // ------------------------------------------------------------ MenuProvider

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.astrofactory.assembler");
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory inventory, Player player) {
        return new AssemblerMenu(containerId, inventory, this);
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveMachineData(ValueOutput output) {
        output.putInt("CraftProgress", craftProgress);
    }

    @Override
    protected void loadMachineData(ValueInput input) {
        craftProgress = input.getIntOr("CraftProgress", 0);
    }
}
