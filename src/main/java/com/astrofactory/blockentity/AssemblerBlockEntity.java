package com.astrofactory.blockentity;

import com.astrofactory.block.AssemblerBlock;
import com.astrofactory.config.AstroFactoryConfig;
import com.astrofactory.recipe.AssemblerRecipe;
import com.astrofactory.recipe.AssemblerRecipeInput;
import com.astrofactory.recipe.AssemblerRecipes;
import com.astrofactory.registry.ModBlockEntities;
import com.astrofactory.util.ItemTransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装机方块实体（US-104 重做）。
 *
 * <p>与旧实现（3x3 网格自动匹配工作台配方）相比，本卡改成《异星工厂》式的
 * <b>选定配方</b>模型：</p>
 * <ul>
 *   <li>GUI 里从「已解锁配方列表」选一个目标产物，机器只做这一个配方；</li>
 *   <li>输入槽 0-8 是扁平投料区，不看摆位，机械臂可从任意面送料；</li>
 *   <li>按配方需求定量扣料（{@link SizedIngredient} 带 count）；</li>
 *   <li>缺料停等、<b>不耗电</b>；输出满暂停；断电暂停且进度保留；</li>
 *   <li>换配方时把新配方用不上的已投入原料退回（推出朝向容器，推不动就掉落）。</li>
 * </ul>
 *
 * <p>配方锁定接口已预留：{@link AssemblerRecipe#requiredTechOrNull()} 非空的配方
 * 需要对应科技解锁，US-105 接入科技树后自动生效。</p>
 */
public class AssemblerBlockEntity extends PoweredBlockEntity
        implements net.minecraft.world.MenuProvider {

    /** 输入槽数量（扁平投料区）。 */
    public static final int INPUT_SLOTS = 9;
    /** 输出槽索引。 */
    public static final int OUTPUT_SLOT = INPUT_SLOTS;
    /** 槽位总数。 */
    public static final int TOTAL_SLOTS = INPUT_SLOTS + 1;

    /** 槽 0-8 = 投料区，槽 9 = 输出。 */
    private final ItemStackHandler slots = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // 输出槽只出不进
            return slot != OUTPUT_SLOT;
        }
    };

    private int progress = 0;
    /** 当前选定的配方 id，空串 = 未选择。 */
    private String selectedRecipe = "";

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLER.get(), pos, state,
                AstroFactoryConfig.ASSEMBLER_CAPACITY.get(),
                AstroFactoryConfig.ASSEMBLER_MAX_RECEIVE.get());
        // 组装机是纯电力机器
        setPowerMode(PowerMode.ELECTRIC);
    }

    public ItemStackHandler getSlots() {
        return this.slots;
    }

    // ---------------------------------------------------------------- 配置读取

    /** 每 tick 耗电（FE/t）。 */
    public static int energyPerTick() {
        return AstroFactoryConfig.ASSEMBLER_ENERGY_PER_TICK.get();
    }

    /** 组装速度乘数。 */
    public static double speedMultiplier() {
        return AstroFactoryConfig.ASSEMBLER_SPEED_MULTIPLIER.get();
    }

    /** 该配方在当前配置下的实际周期（tick，至少 1）。 */
    public static int ticksFor(AssemblerRecipe recipe) {
        int base = Math.max(1, recipe.craftTicks());
        return Math.max(1, (int) Math.round(base / speedMultiplier()));
    }

    // ---------------------------------------------------------------- 状态查询（GUI）

    /** 当前选定的配方 id（空串 = 未选）。 */
    public String getSelectedRecipeId() {
        return this.selectedRecipe;
    }

    /** 当前选定的配方，未选/失效返回 null。 */
    public AssemblerRecipe getSelectedRecipe() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return AssemblerRecipes.byId(serverLevel, this.selectedRecipe)
                .map(RecipeHolder::value).orElse(null);
    }

    public int getCraftProgress() {
        return this.progress;
    }

    /** 本次组装需要的总 tick（GUI 进度条分母）。 */
    public int getCraftMaxProgress() {
        AssemblerRecipe recipe = getSelectedRecipe();
        return recipe == null ? 1 : ticksFor(recipe);
    }

    public boolean isWorking() {
        return getBlockState().getValue(AssemblerBlock.LIT);
    }

    /** 是否缺料（选了配方但原料不够）。 */
    public boolean isMissingInput() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        AssemblerRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            return false;
        }
        return !recipe.matches(makeInput(), serverLevel);
    }

    /** 输出槽是否已满到放不下下一份产物。 */
    public boolean isOutputFull() {
        AssemblerRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            return false;
        }
        return !canStoreResult(recipe.result());
    }

    // ---------------------------------------------------------------- 选定配方

    /**
     * 切换配方。
     *
     * <p>换配方时把<b>新配方用不上</b>的已投入原料退回（按配置开关）。
     * 这样做是因为玩家可能在旧配方里投了 8 个铜板，切到只用铁板的齿轮配方后，
     * 铜板留在槽里既占地方又让人以为被吞了。</p>
     *
     * @param recipeId 新配方 id，空串 = 取消选择
     * @return 是否切换成功（配方不存在或未解锁时拒绝）
     */
    public boolean setSelectedRecipe(String recipeId) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (recipeId == null) {
            recipeId = "";
        }
        if (!recipeId.isEmpty()) {
            var holder = AssemblerRecipes.byId(serverLevel, recipeId);
            if (holder.isEmpty()) {
                return false;
            }
            // 未解锁的配方不允许选（US-105 的锁定接口在此生效）
            if (!AssemblerRecipes.isUnlocked(serverLevel, holder.get().value())) {
                return false;
            }
        }
        if (recipeId.equals(this.selectedRecipe)) {
            return true;
        }
        this.selectedRecipe = recipeId;
        this.progress = 0;

        if (!recipeId.isEmpty()
                && AstroFactoryConfig.ASSEMBLER_RETURN_EXTRA_ON_RECIPE_CHANGE.get()) {
            returnUnusedInput(serverLevel);
        }

        setChanged();
        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    /**
     * 把当前配方用不上的已投入原料退回。
     *
     * <p>「用不上」= 不在新配方的任一 Ingredient 里。先尝试推给朝向的容器，
     * 推不动的掉在机器上方（不凭空消失）。</p>
     */
    private void returnUnusedInput(ServerLevel level) {
        AssemblerRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            return;
        }
        List<ItemStack> extra = new ArrayList<>();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = this.slots.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!isNeededBy(recipe, stack)) {
                extra.add(stack.copy());
                this.slots.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (extra.isEmpty()) {
            return;
        }
        Direction facing = getBlockState().getValue(AssemblerBlock.FACING);
        for (ItemStack stack : extra) {
            ItemStack remaining = ItemTransferUtil.insertInto(level,
                    this.worldPosition.relative(facing), facing.getOpposite(), stack.copy());
            if (!remaining.isEmpty()) {
                dropOnTop(level, remaining);
            }
        }
        setChanged();
    }

    /** 该物品是否是配方需要的原料（任一 Ingredient 命中即可）。 */
    private static boolean isNeededBy(AssemblerRecipe recipe, ItemStack stack) {
        for (SizedIngredient ingredient : recipe.ingredients()) {
            if (ingredient.ingredient().test(stack)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astrofactory.assembler");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new com.astrofactory.menu.AssemblerMenu(containerId, playerInv, this.slots, this,
                ContainerLevelAccess.create(this.level, this.worldPosition));
    }

    // ---------------------------------------------------------------- 主循环

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 未选配方：停机，不耗电
        AssemblerRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            this.progress = 0;
            setLit(false);
            return;
        }

        // 输出槽里有东西：先尝试外推（推不动不影响继续生产，只要还放得下）。
        // 历史 bug（US-104 修）：这里原来写成「推不出去就 return」，
        // 结果朝向没有容器时第一件产物产出后机器就永久停摆，只做得出一件。
        if (!this.slots.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            pushOutput();
        }

        // 缺料：停等，进度保留，不耗电（符合「缺料等待不耗电」）
        AssemblerRecipeInput input = makeInput();
        if (!recipe.matches(input, serverLevel)) {
            setLit(false);
            return;
        }

        // 有料有空间 → 开始/继续工作，每 tick 扣电
        int fePerTick = energyPerTick();
        if (fePerTick > 0 && !tickElectric(fePerTick)) {
            // 断电：暂停，进度保留
            setLit(false);
            return;
        }

        setLit(true);
        this.progress++;
        int need = ticksFor(recipe);
        if (this.progress < need) {
            return;
        }

        // 完成一次：先确认产物放得下，再扣料（避免材料被吞）
        ItemStack result = recipe.assemble(input, serverLevel.registryAccess());
        if (result.isEmpty()) {
            this.progress = 0;
            setLit(false);
            return;
        }
        if (!canStoreResult(result)) {
            // 放不下：退回满进度等待（不扣料）
            this.progress = need - 1;
            setLit(false);
            return;
        }

        this.progress = 0;
        consumeIngredients(recipe);

        ItemStack out = this.slots.getStackInSlot(OUTPUT_SLOT);
        if (out.isEmpty()) {
            this.slots.setStackInSlot(OUTPUT_SLOT, result);
        } else if (ItemStack.isSameItemSameComponents(out, result)) {
            out.grow(result.getCount());
            this.slots.setStackInSlot(OUTPUT_SLOT, out);
        } else {
            // 理论上进不来（canStoreResult 已确认），兜底掉落
            dropOnTop(serverLevel, result);
        }
        setChanged();
    }

    /** 产物能否存进输出槽。 */
    private boolean canStoreResult(ItemStack result) {
        ItemStack out = this.slots.getStackInSlot(OUTPUT_SLOT);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /** 按配方定量扣料。 */
    private void consumeIngredients(AssemblerRecipe recipe) {
        for (SizedIngredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            Ingredient ing = ingredient.ingredient();
            for (int i = 0; i < INPUT_SLOTS && remaining > 0; i++) {
                ItemStack stack = this.slots.getStackInSlot(i);
                if (stack.isEmpty() || !ing.test(stack)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                this.slots.extractItem(i, take, false);
                remaining -= take;
            }
        }
    }

    private void dropOnTop(ServerLevel level, ItemStack stack) {
        net.minecraft.world.entity.item.ItemEntity drop =
                new net.minecraft.world.entity.item.ItemEntity(level,
                        this.worldPosition.getX() + 0.5D,
                        this.worldPosition.getY() + 1.0D,
                        this.worldPosition.getZ() + 0.5D,
                        stack);
        level.addFreshEntity(drop);
    }

    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (state.getValue(AssemblerBlock.LIT) != lit && this.level != null) {
            this.level.setBlock(this.worldPosition, state.setValue(AssemblerBlock.LIT, lit), 3);
        }
    }

    /** 把输入槽包成配方输入。 */
    public AssemblerRecipeInput makeInput() {
        List<ItemStack> stacks = new ArrayList<>(INPUT_SLOTS);
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks.add(this.slots.getStackInSlot(i));
        }
        return new AssemblerRecipeInput(stacks);
    }

    /** 输出槽内容推给朝向的容器。 */
    private boolean pushOutput() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Direction facing = getBlockState().getValue(AssemblerBlock.FACING);
        ItemStack out = this.slots.getStackInSlot(OUTPUT_SLOT);
        if (out.isEmpty()) {
            return true;
        }
        ItemStack remaining = ItemTransferUtil.insertInto(serverLevel,
                this.worldPosition.relative(facing), facing.getOpposite(), out);
        if (remaining.isEmpty()) {
            this.slots.setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY);
            setChanged();
            return true;
        }
        this.slots.setStackInSlot(OUTPUT_SLOT, remaining);
        return false;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    public void addContentDrops(List<ItemStack> drops) {
        super.addContentDrops(drops);
        for (int i = 0; i < this.slots.getSlots(); i++) {
            ItemStack stack = this.slots.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveData(tag, registries);
        tag.put("Slots", this.slots.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
        tag.putString("SelectedRecipe", this.selectedRecipe);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadData(tag, registries);
        if (tag.contains("Slots")) {
            this.slots.deserializeNBT(registries, tag.getCompound("Slots"));
        }
        this.progress = tag.getInt("Progress");
        this.selectedRecipe = tag.getString("SelectedRecipe");
    }
}
