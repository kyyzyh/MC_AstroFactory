package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import com.astrofactory.registry.ModItems;
import com.astrofactory.tech.TechNodes;
import com.astrofactory.tech.TechTreeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 研究台：投入「研究包」转化为科技点，推进当前在研科技。
 *
 * <p>借鉴《异星工厂》的实验室：科技不是凭空解锁，而要用产线制造出的
 * 研究包去「喂」。这样科技进度就与工厂产能挂钩，形成正反馈。</p>
 *
 * <p>v0.4：两个输入槽——槽 0 吃初级研究包，槽 1 吃高级研究包。
 * 在研科技需要哪一级的包就消化哪一槽，另一槽不动。
 * 高级包产出的科技点是初级包的 2 倍（20 点/包）。</p>
 */
public class ResearchTableBlockEntity extends FactoryBlockEntity {

    /** 每个初级研究包提供的科技点。 */
    public static final int POINTS_PER_BASIC = 10;
    /** 每个高级研究包提供的科技点。 */
    public static final int POINTS_PER_ADVANCED = 20;
    /** 每多少 tick 消化一个研究包。 */
    public static final int CYCLE_TICKS = 60;

    /** 槽 0 = 初级包，槽 1 = 高级包。 */
    private final ItemStackHandler input = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int progress = 0;

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInput() {
        return input;
    }

    /** 在研科技需要的包等级对应的槽位（0 = 初级，1 = 高级，-1 = 无在研）。 */
    private int requiredSlot(TechTreeData tech) {
        String current = tech.getCurrentResearch();
        if (current == null) {
            return -1;
        }
        TechNodes.Node node = TechNodes.get(current);
        if (node == null) {
            return -1;
        }
        return node.pack() == TechNodes.PackTier.ADVANCED ? 1 : 0;
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        TechTreeData tech = TechTreeData.get(serverLevel.getServer());

        int slot = requiredSlot(tech);
        if (slot < 0) {
            this.progress = 0;
            return;
        }
        if (input.getStackInSlot(slot).isEmpty()) {
            this.progress = 0;
            return;
        }

        this.progress++;
        if (this.progress < CYCLE_TICKS) {
            return;
        }
        this.progress = 0;

        input.extractItem(slot, 1, false);
        int points = slot == 1 ? POINTS_PER_ADVANCED : POINTS_PER_BASIC;
        var done = tech.addProgress(points);
        setChanged();

        // v0.7 修复：科技进度 / 解锁必须同步给客户端，否则 GUI 一直显示旧状态
        com.astrofactory.network.ModNetwork.syncToAll(tech);

        if (done != null && serverLevel.getServer().getPlayerList() != null) {
            TechNodes.Node node = TechNodes.get(done.getKey());
            String nameKey = node.nameKey();
            Component name = node.maxLevel() > 1
                    ? Component.translatable(nameKey).append(" " + toRoman(done.getValue()))
                    : Component.translatable(nameKey);
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.astrofactory.tech_unlocked", name), false);
        }
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }

    @Override
    public void addContentDrops(java.util.List<ItemStack> drops) {
        for (int i = 0; i < this.input.getSlots(); i++) {
            ItemStack stack = this.input.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Input", input.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Input")) {
            input.deserializeNBT(registries, tag.getCompound("Input"));
        }
        this.progress = tag.getInt("Progress");
    }
}
