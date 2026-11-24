package com.astrofactory.item;

import com.astrofactory.tech.TechTreeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * 受科技锁约束的方块物品：未解锁对应科技时无法放置。
 *
 * <p>这是科技树的「门」——让研究进度真正影响玩法，而不只是数字。
 * 例如没研究「自动采矿」就放不下采矿机。</p>
 */
public class TechGatedBlockItem extends BlockItem {

    private final String requiredTech;

    public TechGatedBlockItem(Block block, Item.Properties properties, String requiredTech) {
        super(block, properties);
        this.requiredTech = requiredTech;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            TechTreeData tech = TechTreeData.get(serverLevel.getServer());
            if (!tech.isUnlocked(this.requiredTech)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                            Component.translatable("message.astrofactory.tech_locked",
                                            Component.translatable(
                                                    "tech.astrofactory." + this.requiredTech))
                                    .withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
        }
        return super.place(context);
    }
}
