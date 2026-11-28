package com.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 矿脉方块（世界生成产物，v0.8.9 起分矿种）。
 *
 * <p>参照《异星工厂》，矿脉按矿种独立为一种方块：石矿 / 铜矿 / 铁矿 / 煤矿。
 * 四种矿脉共用本父类，只通过 {@link #fieldType()} 区分产出。
 * 自动采矿机只能放在矿脉（或原版矿石）上，见 {@link MinerBlock#isMineableGround}，
 * 并根据下方矿脉类型产出对应原矿。</p>
 *
 * <p>每种矿脉是一个独立子类（独立注册名、独立模型与纹理），
 * 未来要加金矿脉 / 钻石矿脉等，只需新增一个子类 + 资源 + worldgen。</p>
 */
public abstract class OreFieldBlock extends Block {

    public static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    protected OreFieldBlock(Properties properties) {
        super(properties);
    }

    /** 便捷建造方法：矿脉默认石头硬度、石头音效，可被各类镐采掘。 */
    public static Properties oreFieldProperties() {
        return Block.Properties.of()
                .strength(3.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    /** 本次产出的物品（可被下方矿脉类型覆盖）。 */
    public ItemStack product() {
        return fieldType().product();
    }

    /** 矿脉种类。 */
    public abstract FieldType fieldType();

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 矿脉种类（异星工厂风格：石 / 铜 / 铁 / 煤）。 */
    public enum FieldType {
        STONE(new ItemStack(Items.STONE)),
        COPPER(new ItemStack(Items.RAW_COPPER)),
        IRON(new ItemStack(Items.RAW_IRON)),
        COAL(new ItemStack(Items.COAL));

        private final ItemStack product;

        FieldType(ItemStack product) {
            this.product = product;
        }

        public ItemStack product() {
            return this.product.copy();
        }
    }
}
