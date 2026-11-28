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

    // ---- US-103：数值全部走配置（config/astrofactory-common.toml） ----

    /** 电力模式每 tick 耗电（FE/t）。蓝图基准 20。 */
    public static int fePerTick() {
        return com.astrofactory.config.AstroFactoryConfig.MINER_FE_PER_TICK.get();
    }

    /** 电力模式产出一个物品所需 tick 数。蓝图基准 20（1 矿/秒，是热能版的 2 倍速）。 */
    public static int cycleTicks() {
        return com.astrofactory.config.AstroFactoryConfig.MINER_CYCLE_TICKS.get();
    }

    /** 内部储能上限（FE）。蓝图基准 10,000。 */
    public static int energyCapacity() {
        return com.astrofactory.config.AstroFactoryConfig.MINER_CAPACITY.get();
    }

    /** 最大充能速度（FE/t）。 */
    public static int maxReceive() {
        return com.astrofactory.config.AstroFactoryConfig.MINER_MAX_RECEIVE.get();
    }

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
        super(ModBlockEntities.MINER.get(), pos, state, energyCapacity(), maxReceive());
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 燃料模式：先推进燃烧
        if (getPowerMode() == PowerMode.FUEL) {
            tickFuel();
            if (!isBurning()) {
                setLit(false); // 无燃料停工
                return;
            }
        } else {
            // 电力模式（US-103）：每 tick 扣电；没电就暂停，进度不清零
            if (!tickElectric(fePerTick())) {
                setLit(false); // 断电停工
                return;
            }
        }

        // 先算产物：下方没矿就不推进进度（避免空转白耗电）
        ItemStack product = rollProduct(serverLevel);
        if (product.isEmpty()) {
            setLit(false); // 下方无矿可采
            return;
        }

        // 通电/有燃料且下方有矿 → 正在下钻
        setLit(true);
        this.progress++;
        if (this.progress < techTicks(effectiveTicks(cycleTicks()))) {
            return;
        }
        this.progress = 0;

        // v0.6 直连/互喂：产物是燃料且相邻机器缺燃料 → 优先补燃料槽
        if (tryFeedNeighborFuel(serverLevel, product)) {
            return;
        }

        // 朝四个水平方向依次尝试输出
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos target = this.worldPosition.relative(dir);
            // insertIntoInputSide 会自动跳过熔炉/组装机的输出面，
            // 避免把原料塞进成品槽污染产物
            ItemStack remaining = ItemTransferUtil.insertIntoInputSide(
                    serverLevel, target, dir.getOpposite(), product);
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

    /** 当前采矿进度（tick）。断电时应保持不变（US-103 验收断言用）。 */
    public int getProgress() {
        return this.progress;
    }

    /** 更新方块 LIT 状态（驱动下钻动画的开关）。 */
    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(com.astrofactory.block.MinerBlock.LIT)
                && state.getValue(com.astrofactory.block.MinerBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(com.astrofactory.block.MinerBlock.LIT, lit), 3);
        }
    }

    /** 一个产出周期需要多少 tick（含科技加成）。 */
    public int getCycleTicks() {
        return techTicks(effectiveTicks(cycleTicks()));
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
