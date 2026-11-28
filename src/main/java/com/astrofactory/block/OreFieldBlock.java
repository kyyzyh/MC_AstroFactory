package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 矿区方块（世界生成产物）。
 *
 * <p>对应《异星工厂》的矿石区块：世界生成时在地下/地表成片出现，
 * 只有把它作为下方方块时，自动采矿机才能放置并采出原矿（泥土不行）。</p>
 *
 * <p>这是一个纯装饰/资源方块，无方块实体、无状态——只为标记「这里是矿脉」，
 * 采矿机的放置校验与 {@code rollProduct} 都认它。</p>
 */
public class OreFieldBlock extends Block {

    public static final MapCodec<OreFieldBlock> CODEC = simpleCodec(OreFieldBlock::new);

    public static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public OreFieldBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends OreFieldBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 便捷建造方法：矿区默认石头硬度、石头音效，可被各类镐采掘。 */
    public static Properties oreFieldProperties() {
        return Block.Properties.of()
                .strength(3.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }
}
