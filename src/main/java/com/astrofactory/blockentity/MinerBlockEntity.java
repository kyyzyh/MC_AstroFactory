package com.astrofactory.blockentity;

import com.astrofactory.registry.ModBlockEntities;
import com.astrofactory.util.ItemTransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * 采矿机：向下「挖矿」——依据机器下方方块决定产出，定期把矿产物塞进相邻容器/传送带。
 *
 * <p>v0.6 借鉴异星工厂前期流程的两处改进：</p>
 * <ul>
 *   <li><b>矿石识别</b>：下方是铁矿/铜矿/金矿/煤矿时产出对应原矿
 *       （对应异星工厂热能采矿机采出矿石，再由石炉炼成板）；</li>
 *   <li><b>直连与互喂</b>：产物是燃料（如煤）且相邻机器缺燃料时，优先补其燃料槽
 *       ——两台面对面的采矿机放在煤矿上即可互相补煤（燃料自给），
 *       产物是矿石且相邻是熔炉时直接投入熔炉（省传送带与机械臂）。</li>
 * </ul>
 */
public class MinerBlockEntity extends PoweredBlockEntity {

    /** 每多少 tick 产出一个物品（20 tick = 1 秒）。 */
    private static final int CYCLE_TICKS = 40;
    /** 每次产出消耗的能量（FE）。 */
    public static final int ENERGY_PER_ITEM = 200;
    /** 内部储能上限（FE）。 */
    public static final int ENERGY_CAPACITY = 20_000;
    /** 最大充能速度（FE/t）。 */
    public static final int MAX_RECEIVE = 200;

    /** 下方方块关键词 → 产出物品。v0.6：矿石产出原矿（对应异星工厂矿石→板链条）。 */
    private static final Map<String, ItemStack> ORE_PRODUCTS = Map.ofEntries(
            Map.entry("iron_ore", new ItemStack(Items.RAW_IRON)),
            Map.entry("copper_ore", new ItemStack(Items.RAW_COPPER)),
            Map.entry("gold_ore", new ItemStack(Items.RAW_GOLD)),
            Map.entry("coal_ore", new ItemStack(Items.COAL)),
            Map.entry("diamond_ore", new ItemStack(Items.DIAMOND)),
            Map.entry("redstone_ore", new ItemStack(Items.REDSTONE)),
            Map.entry("lapis_ore", new ItemStack(Items.LAPIS_LAZULI)),
            Map.entry("emerald_ore", new ItemStack(Items.EMERALD))
    );

    private int progress = 0;

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINER.get(), pos, state, ENERGY_CAPACITY, MAX_RECEIVE);
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 燃料模式：先推进燃烧
        if (getPowerMode() == PowerMode.FUEL) {
            tickFuel();
        }

        // 无燃料 / 无电则停工
        if (!canWork(ENERGY_PER_ITEM)) {
            return;
        }

        this.progress++;
        if (this.progress < techTicks(effectiveTicks(CYCLE_TICKS))) {
            return;
        }
        this.progress = 0;

        ItemStack product = rollProduct(serverLevel);
        if (product.isEmpty()) {
            return;
        }

        // 真正产出时才扣能量（燃料模式不扣，已按 tick 燃烧）
        consumeWorkCost(ENERGY_PER_ITEM);

        // v0.6 直连/互喂：产物是燃料且相邻机器缺燃料 → 优先补燃料槽
        if (tryFeedNeighborFuel(serverLevel, product)) {
            return;
        }

        // 朝四个水平方向依次尝试输出
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos target = this.worldPosition.relative(dir);
            ItemStack remaining = ItemTransferUtil.insertInto(serverLevel, target, dir.getOpposite(), product);
            if (remaining.isEmpty()) {
                return;
            }
        }
        // 全部塞不进去就丢弃（避免无限堆积在机器内部）
    }

    /**
     * v0.6 互喂：产物是燃料（煤/木板等）且相邻的燃料机器（采矿机/熔炉/机械臂/锅炉）
     * 燃料槽不满时，直接补 1 个进去。两台采矿机对放在煤矿上即可互相补煤。
     *
     * @return 是否喂掉了本次产物
     */
    private boolean tryFeedNeighborFuel(ServerLevel level, ItemStack product) {
        if (!isFuel(product)) {
            return false;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos target = this.worldPosition.relative(dir);
            // 1) PoweredBlockEntity 系机器（采矿机/熔炉/机械臂）
            if (level.getBlockEntity(target) instanceof PoweredBlockEntity machine
                    && machine.getPowerMode() == PowerMode.FUEL
                    && machine.getFuelSlot().getStackInSlot(0).getCount() < 8) {
                if (machine.tryInsertFuel(product)) {
                    return true;
                }
            }
            // 2) 锅炉（独立燃料表）
            if (level.getBlockEntity(target) instanceof BoilerBlockEntity boiler
                    && boiler.getFuelSlot().getStackInSlot(0).getCount() < 8) {
                if (boiler.tryInsertFuel(product)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 高效采矿科技：每级 +20% 采矿速度。 */
    @Override
    protected double techSpeedMultiplier() {
        if (this.level == null || this.level.getServer() == null) {
            return 1.0;
        }
        return com.astrofactory.tech.TechTreeData.get(this.level.getServer()).miningSpeedMultiplier();
    }

    /** 根据下方方块决定产出。v0.6：矿石方块产出对应原矿。 */
    private ItemStack rollProduct(ServerLevel level) {
        BlockPos below = this.worldPosition.below();
        BlockState belowState = level.getBlockState(below);
        String id = belowState.getBlock().builtInRegistryHolder().key().location().toString();
        String shortName = id.substring(id.indexOf(':') + 1);

        // v0.6：矿石 → 原矿（含深板岩变体，如 deepslate_iron_ore 含 iron_ore 子串）
        for (var entry : ORE_PRODUCTS.entrySet()) {
            if (shortName.contains(entry.getKey())) {
                return entry.getValue().copy();
            }
        }

        String name = belowState.getBlock().getDescriptionId();
        if (name.contains("deepslate")) {
            return new ItemStack(Items.COBBLED_DEEPSLATE);
        }
        if (name.contains("stone") || name.contains("andesite") || name.contains("diorite")
                || name.contains("granite") || name.contains("tuff")) {
            return new ItemStack(Items.COBBLESTONE);
        }
        if (name.contains("dirt") || name.contains("grass")) {
            return new ItemStack(Items.DIRT);
        }
        if (name.contains("sand")) {
            return new ItemStack(Items.SAND);
        }
        if (name.contains("netherrack")) {
            return new ItemStack(Items.NETHERRACK);
        }
        if (belowState.isAir()) {
            return ItemStack.EMPTY;
        }
        // 兜底：任何实心方块都产出圆石
        return new ItemStack(Items.COBBLESTONE);
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveData(tag, registries);
        tag.putInt("Progress", this.progress);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadData(tag, registries);
        this.progress = tag.getInt("Progress");
    }
}
