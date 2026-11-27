package com.astrofactory.datagen;

import com.astrofactory.AstroFactory;
import com.astrofactory.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * 物品 tag DataGen（US-104）。
 *
 * <p>配方里凡是可以「同类互替」的原料都走 tag 而不是硬编码物品 id，这样后续
 * 加模组兼容（例如别的模组的铁板）只要往 tag 里加一条即可。本卡先提供
 * 板类（plates）与线材类（wires）两个通用 tag。</p>
 */
public class ModItemTagsProvider extends ItemTagsProvider {

    /** 板类通用 tag（铁板 / 铜板等）。 */
    public static final TagKey<Item> PLATES =
            ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    AstroFactory.MODID, "plates"));

    /** 线材通用 tag。 */
    public static final TagKey<Item> WIRES =
            ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    AstroFactory.MODID, "wires"));

    /** 齿轮通用 tag。 */
    public static final TagKey<Item> GEARS =
            ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    AstroFactory.MODID, "gears"));

    public ModItemTagsProvider(PackOutput output,
                               CompletableFuture<HolderLookup.Provider> lookup,
                               CompletableFuture<TagLookup<Block>> blockTags,
                               ExistingFileHelper helper) {
        super(output, lookup, blockTags, AstroFactory.MODID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(PLATES).add(ModItems.IRON_PLATE.get(), ModItems.COPPER_PLATE.get());
        tag(WIRES).add(ModItems.COPPER_WIRE.get());
        tag(GEARS).add(ModItems.IRON_GEAR.get());
    }

    @Override
    public String getName() {
        return "AstroFactory 物品 tag";
    }
}
