package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 储物箱：通用存储容器，作为产线的终点或缓冲。
 *
 * <p>v0.5 起槽位数可参数化：木箱 9 格（开局可用）、储物箱 27 格、铁箱 54 格
 * ——对应异星工厂的 wooden chest → iron chest → steel chest 容量阶梯。</p>
 */
public class CrateBlockEntity extends FactoryBlockEntity {

    public static final int SLOTS = 27;

    private final ItemStackHandler inventory;

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.CRATE.get(), pos, state, SLOTS);
    }

    protected CrateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.inventory = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
    }

    /** 供外部（如机械臂）尝试放入，返回剩余数量。 */
    public int insert(ItemStack stack) {
        ItemStack remainder = stack;
        for (int i = 0; i < inventory.getSlots() && !remainder.isEmpty(); i++) {
            remainder = inventory.insertItem(i, remainder, false);
        }
        return remainder.getCount();
    }
}
