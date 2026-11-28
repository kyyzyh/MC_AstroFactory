package com.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

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
public abstract class OreFieldBlock extends Block implements EntityBlock {

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

    // ------------------------------------------------------------ v0.8.9 矿体：储量 BE

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.astrofactory.blockentity.OreFieldBlockEntity(pos, state);
    }

    /**
     * 手挖矿脉掉落对应矿（石矿脉→石矿 raw_stone，铜→铜原矿…），而非矿脉方块自身。
     * 参照异星工厂：矿藏本体不可回收，采走的是矿石。
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(fieldType().product());
    }

    /**
     * 服务端 tick：矿脉格储量扣空后自动塌陷为空气（矿洞）。
     * 采矿机从覆盖区内格位扣储量，扣空的非支撑格会被这里清除。
     */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        if (type == com.astrofactory.registry.ModBlockEntities.ORE_FIELD.get()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof com.astrofactory.blockentity.OreFieldBlockEntity field) {
                    field.serverTick();
                }
            };
        }
        return null;
    }

    /** 矿脉种类（异星工厂风格：石 / 铜 / 铁 / 煤）。 */
    public enum FieldType {
        // v0.8.9：石矿脉产出 raw_stone（不可放置），熔炉烧成 minecraft:stone
        STONE(new ItemStack(com.astrofactory.registry.ModItems.RAW_STONE.get())),
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
