package com.astrofactory.blockentity;

import com.astrofactory.block.LabBlock;
import com.astrofactory.config.AstroFactoryConfig;
import com.astrofactory.network.ModNetwork;
import com.astrofactory.registry.ModBlockEntities;
import com.astrofactory.registry.ModItems;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 实验室方块实体（US-106）。
 *
 * <p>对应《异星工厂》的实验室：耗电（20 FE/t）、吃红瓶，把每个红瓶的
 * 科技点投进<b>世界级共享</b>的 {@link TechTreeData}。多台实验室各自消化自己的瓶、
 * 累加到同一份进度，因此并联线性加速；其中一台完成了研究，所有玩家立即看到。</p>
 *
 * <p>暂停与恢复：</p>
 * <ul>
 *   <li><b>断电</b>：{@code tickElectric} 返回 false → 不消化瓶、周期不动，进度保留；</li>
 *   <li><b>瓶尽</b>：红瓶槽为空 → 停等，补瓶即继续；</li>
 *   <li><b>无在研项目</b>：不耗电、不吃瓶。</li>
 * </ul>
 */
public class LabBlockEntity extends PoweredBlockEntity implements MenuProvider {

    /** 红瓶输入槽数。 */
    public static final int RED_SLOTS = 1;
    /** 每消耗一个红瓶投入的科技点。 */
    public static final int POINTS_PER_RED = 10;

    /** 红瓶输入槽。 */
    private final ItemStackHandler redInput = new ItemStackHandler(RED_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int progress = 0;

    public LabBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB.get(), pos, state,
                AstroFactoryConfig.LAB_CAPACITY.get(),
                AstroFactoryConfig.LAB_MAX_RECEIVE.get());
        // 实验室是纯电力机器
        setPowerMode(PowerMode.ELECTRIC);
    }

    public ItemStackHandler getRedInput() {
        return this.redInput;
    }

    /** 每 tick 耗电（FE/t）。 */
    public static int energyPerTick() {
        return AstroFactoryConfig.LAB_ENERGY_PER_TICK.get();
    }

    /** 每消耗一个红瓶所需 tick 数。 */
    public static int cycleTicks() {
        return AstroFactoryConfig.LAB_CYCLE_TICKS.get();
    }

    public int getProgress() {
        return this.progress;
    }

    public int getCycleTicks() {
        return cycleTicks();
    }

    /** 是否有电在烧。 */
    public boolean isWorking() {
        return this.progress > 0;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        TechTreeData tech = TechTreeData.get(serverLevel.getServer());

        // 无在研项目：不耗电、不吃瓶
        if (tech.getCurrentResearch() == null) {
            setLit(false);
            this.progress = 0;
            return;
        }

        // 断电：暂停保留进度
        int fe = energyPerTick();
        if (!tickElectric(fe)) {
            setLit(false);
            return;
        }

        // 红瓶槽为空：瓶尽停等
        ItemStack red = this.redInput.getStackInSlot(0);
        if (red.isEmpty() || !red.is(ModItems.RED_SCIENCE_PACK.get())) {
            setLit(false);
            this.progress = 0;
            return;
        }

        // 有电有瓶：推进周期
        this.progress++;
        setLit(true);
        if (this.progress < cycleTicks()) {
            return;
        }
        this.progress = 0;
        this.redInput.extractItem(0, 1, false);
        setChanged();

        var done = tech.addProgress(POINTS_PER_RED);
        setChanged();

        // 研究完成：广播进度（解锁配方由 US-105 的 unlock 回调自动触发）
        ModNetwork.syncToAll(tech);

        if (done != null && serverLevel.getServer().getPlayerList() != null) {
            String key = done.getKey();
            Component display = researchDisplayName(key);
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.astrofactory.tech_unlocked", display), false);
        }
    }

    /** 科技显示名组件：数据驱动科技用 name（lang key），否则用硬编码科技的语言键。 */
    private static Component researchDisplayName(String techId) {
        var def = com.astrofactory.tech.TechRegistry.get(techId);
        if (def != null) {
            return Component.translatable(def.name());
        }
        TechNodes.Node node = TechNodes.get(techId);
        return node != null ? Component.translatable(node.nameKey()) : Component.literal(techId);
    }

    private void setLit(boolean lit) {
        if (this.level == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (state.getBlock() instanceof LabBlock && state.getValue(LabBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(LabBlock.LIT, lit), 3);
        }
    }

    // ---------------------------------------------------------------- 交互

    /** 空手右键打开研究界面。 */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new com.astrofactory.menu.LabMenu(containerId, playerInv, this.worldPosition,
                this.redInput, this.level);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.astrofactory.lab");
    }

    // ---------------------------------------------------------------- NBT / drop

    /** 拆机器时把红瓶掉出来。 */
    @Override
    public void addContentDrops(java.util.List<ItemStack> drops) {
        for (int i = 0; i < this.redInput.getSlots(); i++) {
            ItemStack stack = this.redInput.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveData(tag, registries);
        tag.put("RedInput", this.redInput.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadData(tag, registries);
        if (tag.contains("RedInput")) {
            this.redInput.deserializeNBT(registries, tag.getCompound("RedInput"));
        }
        this.progress = tag.getInt("Progress");
    }
}
