package com.astrofactory.blockentity;

import com.astrofactory.block.OreFieldBlock;
import com.astrofactory.config.AstroFactoryConfig;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采矿机：向下「挖矿」——区域采集一片矿脉，定期把矿产物塞进相邻容器/传送带。
 *
 * <p>v0.8.9 改为区域采集（像戴森球矿机）：以机器脚下为中心覆盖一片方形区域
 * （一型 3x3、二型 4x4），每次从区域内随机挑一个有储量的矿脉格采出一个物品，
 * 并扣该格储量 BE。扣空的非支撑格会塌陷为空气（矿洞）。</p>
 *
 * <p>v0.6 借鉴异星工厂前期流程的两处改进：</p>
 * <ul>
 *   <li>矿石识别：下方是铁矿/铜矿/金矿/煤矿时产出对应原矿
 *       （对应异星工厂热能采矿机采出矿石，再由石炉炼成板）；</li>
 *   <li>直连与互喂：产物是燃料且相邻机器缺燃料时，优先补其燃料槽。</li>
 * </ul>
 *
 * <p>二型采矿机（miner_mk2）继承本类，仅覆盖面积/数值/实体类型。</p>
 */
public class MinerBlockEntity extends PoweredBlockEntity implements IMachineInfo {

    // ---- 区域采集参数（可被子类覆盖：二型 4x4 / 更大）----

    /** 区域采集边长（格）。一型 3，二型 4。以机器脚下为中心。 */
    protected int areaSize() {
        return AstroFactoryConfig.MINER_AREA.get();
    }

    /** 电力模式每 tick 耗电（FE/t）。 */
    protected int fePerTick() {
        return AstroFactoryConfig.MINER_FE_PER_TICK.get();
    }

    /** 电力模式产出一个物品所需 tick 数。 */
    protected int cycleTicksBase() {
        return AstroFactoryConfig.MINER_CYCLE_TICKS.get();
    }

    /** 内部储能上限（FE）。 */
    protected int energyCapacity() {
        return AstroFactoryConfig.MINER_CAPACITY.get();
    }

    /** 最大充能速度（FE/t）。 */
    protected int maxReceive() {
        return AstroFactoryConfig.MINER_MAX_RECEIVE.get();
    }

    // ---- 公开配置访问（供 GameTest/面板读取）----

    /** 一型每 tick 耗电（配置）。 */
    public static int configFePerTick() {
        return AstroFactoryConfig.MINER_FE_PER_TICK.get();
    }

    /** 一型内部储能上限（配置）。 */
    public static int configEnergyCapacity() {
        return AstroFactoryConfig.MINER_CAPACITY.get();
    }

    /** 一型最大充能速度（配置）。 */
    public static int configMaxReceive() {
        return AstroFactoryConfig.MINER_MAX_RECEIVE.get();
    }

    // ---- 矿石识别表（v0.6）----

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
        super(ModBlockEntities.MINER.get(), pos, state,
                AstroFactoryConfig.MINER_CAPACITY.get(),
                AstroFactoryConfig.MINER_MAX_RECEIVE.get());
    }

    /** 二型构造：传入二型的方块实体类型与数值（由子类在 super 中指定）。 */
    protected MinerBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                               BlockPos pos, BlockState state, int capacity, int maxReceive) {
        super(type, pos, state, capacity, maxReceive);
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

        // 先算产物：区域内没矿就不推进进度（避免空转白耗电）
        ItemStack product = rollProduct(serverLevel);
        if (product.isEmpty()) {
            setLit(false); // 区域无矿可采
            return;
        }

        // 通电/有燃料且区域有矿 → 正在下钻
        setLit(true);
        this.progress++;
        if (this.progress < techTicks(effectiveTicks(cycleTicksBase()))) {
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
            ItemStack remaining = ItemTransferUtil.insertIntoInputSide(
                    serverLevel, target, dir.getOpposite(), product);
            if (remaining.isEmpty()) {
                return;
            }
        }
    }

    /**
     * v0.6 互喂：产物是燃料（煤/木板等）且相邻的燃料机器（采矿机/熔炉/机械臂/锅炉）
     * 燃料槽不满时，直接补 1 个进去。两台采矿机对放在煤矿上即可互相补煤。
     */
    private boolean tryFeedNeighborFuel(ServerLevel level, ItemStack product) {
        if (!isFuel(product)) {
            return false;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos target = this.worldPosition.relative(dir);
            if (level.getBlockEntity(target) instanceof PoweredBlockEntity machine
                    && machine.getPowerMode() == PowerMode.FUEL
                    && machine.getFuelSlot().getStackInSlot(0).getCount() < 8) {
                if (machine.tryInsertFuel(product)) {
                    return true;
                }
            }
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

    /**
     * v0.8.9 区域采集：从覆盖区域内随机选一个有储量的矿脉格，产出该矿脉对应的矿，
     * 并扣该格储量。
     */
    private ItemStack rollProduct(ServerLevel level) {
        int size = areaSize();
        int from = -(size / 2);
        int to = size - (size / 2) - 1;
        List<BlockPos> withReserve = new ArrayList<>();
        for (int dx = from; dx <= to; dx++) {
            for (int dz = from; dz <= to; dz++) {
                BlockPos cell = this.worldPosition.offset(dx, -1, dz);
                BlockState cellState = level.getBlockState(cell);
                if (cellState.getBlock() instanceof OreFieldBlock && hasReserve(level, cell)) {
                    withReserve.add(cell);
                }
            }
        }
        if (!withReserve.isEmpty()) {
            BlockPos pick = withReserve.get(level.getRandom().nextInt(withReserve.size()));
            return consumeReserve(level, pick);
        }
        return rollProductBelow(level);
    }

    /** 该矿脉格是否仍有储量（无 BE 的旧方块视为恒可采，避免旧档案卡死）。 */
    private boolean hasReserve(ServerLevel level, BlockPos cell) {
        if (level.getBlockEntity(cell) instanceof OreFieldBlockEntity field) {
            return field.getReserve() > 0;
        }
        return true;
    }

    /** 从该矿脉格扣 1 储量并返回其产品；扣空则塌陷为空气（矿洞）。支撑格不塌陷。 */
    private ItemStack consumeReserve(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        ItemStack product = state.getBlock() instanceof OreFieldBlock field
                ? field.product() : ItemStack.EMPTY;
        if (level.getBlockEntity(cell) instanceof OreFieldBlockEntity field) {
            boolean exhausted = field.tryConsume(1);
            if (exhausted && !cell.equals(this.worldPosition.below())) {
                level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return product;
    }

    /** v0.6 单格兜底：依据机器下方方块决定产出（不耗储量）。 */
    private ItemStack rollProductBelow(ServerLevel level) {
        BlockPos below = this.worldPosition.below();
        BlockState belowState = level.getBlockState(below);
        String id = belowState.getBlock().builtInRegistryHolder().key().location().toString();
        String shortName = id.substring(id.indexOf(':') + 1);

        if (belowState.getBlock() instanceof OreFieldBlock field) {
            return field.product();
        }

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
        return new ItemStack(Items.COBBLESTONE);
    }

    /** 当前采矿进度（tick）。断电时应保持不变（US-103 验收断言用）。 */
    public int getProgress() {
        return this.progress;
    }

    /** 更新方块 LIT 状态（驱动下钻动画的开关）。 */
    protected void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(com.astrofactory.block.MinerBlock.LIT)
                && state.getValue(com.astrofactory.block.MinerBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(com.astrofactory.block.MinerBlock.LIT, lit), 3);
        }
    }

    /** 一个产出周期需要多少 tick（含科技加成）。 */
    public int getCycleTicks() {
        return techTicks(effectiveTicks(cycleTicksBase()));
    }

    // ------------------------------------------------------------ IMachineInfo 信息面板

    @Override
    public String infoTitle() {
        return getBlockState().getBlock().getName().getString();
    }

    @Override
    public String infoStatusKey() {
        if (this.level == null) {
            return "idle";
        }
        boolean hasOre = hasWorkableCell((ServerLevel) this.level);
        if (getPowerMode() == PowerMode.FUEL) {
            if (!isBurning()) {
                return "no_fuel";
            }
        } else if (!hasEnergy(fePerTick())) {
            return "no_power";
        }
        if (!hasOre) {
            return "no_ore";
        }
        return "working";
    }

    /** 区域内是否还有可采的矿脉（有储量）。纯原版矿石场景看脚下方块。 */
    private boolean hasWorkableCell(ServerLevel level) {
        int size = areaSize();
        int from = -(size / 2);
        int to = size - (size / 2) - 1;
        for (int dx = from; dx <= to; dx++) {
            for (int dz = from; dz <= to; dz++) {
                BlockPos cell = this.worldPosition.offset(dx, -1, dz);
                if (level.getBlockState(cell).getBlock() instanceof OreFieldBlock
                        && hasReserve(level, cell)) {
                    return true;
                }
            }
        }
        return com.astrofactory.block.MinerBlock.isMineableGround(
                this.level.getBlockState(this.worldPosition.below()));
    }

    @Override
    public int infoEnergy() {
        return getEnergyStored();
    }

    @Override
    public int infoEnergyCapacity() {
        return energyCapacity();
    }

    @Override
    public int infoProgress() {
        return this.progress;
    }

    @Override
    public int infoMaxProgress() {
        return getCycleTicks();
    }

    @Override
    public String infoExtra() {
        if (this.level instanceof ServerLevel sl) {
            ItemStack product = rollProduct(sl);
            if (!product.isEmpty()) {
                return product.getHoverName().getString();
            }
        }
        return null;
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
