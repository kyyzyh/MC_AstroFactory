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
import com.xiaoyi.astrofactory.menu.StoneFurnaceMenu;
import com.xiaoyi.astrofactory.power.FuelPowerSource;
import com.xiaoyi.astrofactory.power.PowerSource;
import com.xiaoyi.astrofactory.recipe.MachineRecipe;
import com.xiaoyi.astrofactory.recipe.MachineType;
import com.xiaoyi.astrofactory.recipe.RecipeRegistry;

/**
 * 熔炼炉方块实体：把原矿熔成锭。
 *
 * <p>槽位：0 = 输入（原矿），1 = 输出（锭），2 = 燃料。
 * 产线位置：采矿机 → 传送带 → 【熔炼炉】 → 制作台 → 组装机</p>
 *
 * <p>容器样板方法与存档由 {@link AbstractMachineEntity} 统一处理。</p>
 */
public class StoneFurnaceBlockEntity extends AbstractMachineEntity {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FUEL_SLOT = 2;
    public static final int TOTAL_SLOTS = 3;

    /** 默认熔炼耗时；实际耗时取配方里的 time 字段。 */
    public static final int SMELT_INTERVAL = MachineType.SMELTING.defaultTicks();

    private int progress = 0;
    private final FuelPowerSource power = new FuelPowerSource(this, FUEL_SLOT);

    public StoneFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.STONE_FURNACE_BE.get(), pos, state, TOTAL_SLOTS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StoneFurnaceBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    /** 区块加载自修复由 {@link com.xiaoyi.astrofactory.machine.AbstractMachineEntity} 统一处理。 */
    @Override
    public void onLoad() {
        super.onLoad();
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        ItemStack input = getItem(INPUT_SLOT);
        MachineRecipe recipe = RecipeRegistry.findSingle(MachineType.SMELTING, input);
        boolean canWork = recipe != null && recipe.fitsInto(getItem(OUTPUT_SLOT));
        int interval = recipe == null ? SMELT_INTERVAL : recipe.ticks();

        // 燃料检查：没有燃料则停机（规格红线：燃烧期吃煤）
        boolean hasFuel = power.consume(true);

        if (canWork && hasFuel) {
            power.consume(false); // 实际消费 1 tick
            progress++;
            // 熔炼时冒烟（原版营火烟，零资产）
            MachineFeedback.tickParticles(level, pos,
                    net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE);
            if (progress >= interval) {
                progress = 0;
                doSmelt(recipe, pos);
                MachineFeedback.playCraft(level, pos);
            }
        } else {
            progress = 0;
        }

        boolean active = canWork && progress > 0;
        setActive(active);
    }

    /** 执行一次熔炼：按配方消耗输入、产出结果。 */
    private void doSmelt(MachineRecipe recipe, BlockPos pos) {
        ItemStack input = getItem(INPUT_SLOT);
        input.shrink(recipe.inputs().get(0).count());
        if (input.isEmpty()) {
            setItem(INPUT_SLOT, ItemStack.EMPTY);
        }
        ItemStack result = recipe.resultStack();
        ItemStack out = getItem(OUTPUT_SLOT);
        if (out.isEmpty()) {
            setItem(OUTPUT_SLOT, result);
        } else {
            out.grow(result.getCount());
        }
        setChanged();
        // story 系统：熔炉产出 +1 EXP（Q7 科技计数同源点）
        if (!level.isClientSide()) {
            var players = level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(8.0));
            if (!players.isEmpty()) {
                var data = com.xiaoyi.astrofactory.level.PlayerLevel.of(players.get(0));
                data.totalCrafted++;
                data.addExp(1);
            }
        }
    }

    /** 进度百分比，供界面显示。 */
    public int getProgressPercent() {
        MachineRecipe recipe = RecipeRegistry.findSingle(MachineType.SMELTING, getItem(INPUT_SLOT));
        int interval = recipe == null ? SMELT_INTERVAL : recipe.ticks();
        return Math.min(100, progress * 100 / Math.max(1, interval));
    }

    /** 输出槽禁止外部插入；输入槽只接受可熔炼的物品；燃料槽只接受燃料。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == OUTPUT_SLOT) {
            return false;
        }
        if (slot == FUEL_SLOT) {
            return PowerSource.fuelTicks(stack) > 0;
        }
        return slot == INPUT_SLOT && RecipeRegistry.isSingleInput(MachineType.SMELTING, stack);
    }

    // ------------------------------------------------------------ MenuProvider

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.astrofactory.stone_furnace");
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory inventory, Player player) {
        return new StoneFurnaceMenu(containerId, inventory, this);
    }

    // ------------------------------------------------------------ 存档

    @Override
    protected void saveMachineData(ValueOutput output) {
        output.putInt("Progress", progress);
        output.putInt("BurnBudget", power.getBurnBudgetForSave());
    }

    @Override
    protected void loadMachineData(ValueInput input) {
        progress = input.getIntOr("Progress", 0);
        power.setBurnBudgetForLoad(input.getIntOr("BurnBudget", 0));
    }

    /** 燃料状态文本（供 GUI 显示）。 */
    public String getFuelStatus() {
        return power.statusText();
    }

    /** 是否还有燃料（供 GUI 判断显示"无电力"）。 */
    public boolean hasFuel() {
        return power.isPowered();
    }
}
