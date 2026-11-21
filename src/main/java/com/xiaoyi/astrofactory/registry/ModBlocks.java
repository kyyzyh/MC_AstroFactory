package com.xiaoyi.astrofactory.registry;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.block.AssemblerBlock;
import com.xiaoyi.astrofactory.block.BoilerBlock;
import com.xiaoyi.astrofactory.block.BurnerInserterBlock;
import com.xiaoyi.astrofactory.block.BurnerMinerBlock;
import com.xiaoyi.astrofactory.block.FastTransportBeltBlock;
import com.xiaoyi.astrofactory.block.FunnelBlock;
import com.xiaoyi.astrofactory.block.LabBlock;
import com.xiaoyi.astrofactory.block.OffshorePumpBlock;
import com.xiaoyi.astrofactory.block.OreVeinBlock;
import com.xiaoyi.astrofactory.block.PowerPoleBlock;
import com.xiaoyi.astrofactory.block.SteamEngineBlock;
import com.xiaoyi.astrofactory.block.SteamPipeBlock;
import com.xiaoyi.astrofactory.block.StoneFurnaceBlock;
import com.xiaoyi.astrofactory.block.TransportBeltBlock;

/**
 * 方块注册。
 *
 * <p>从原 {@code ModRegistry} 按职责拆出，只负责「方块」这一类。
 * 对外仍通过 {@code ModRegistry.XXX} 访问（那里保留了同名字段）。</p>
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Astrofactory.MODID);

    /**
     * 运行中机器的自发光等级（0-15）。
     *
     * <p><b>为什么这条最重要</b>：机器在跑就发光、停机就熄灯 —— 玩家站在
     * 产线外扫一眼就知道哪台停了，不用打开任何 GUI。Mekanism 的
     * {@code ACTIVE_LIGHT} 就是这个思路，成本只有一行 {@code lightLevel}。</p>
     *
     * <p>取 7 而不是 15：7 刚好在黑暗中能看清轮廓，又不会把厂房照得像白天，
     * 保留「机器在工作」的视觉对比。</p>
     */
    public static final int MACHINE_LIGHT = 7;

    // ---------------------------------------------------------------- 物流

    /** 传送带：真连续移动，无 GUI，带朝向。开局免费可造。 */
    public static final DeferredBlock<TransportBeltBlock> TRANSPORT_BELT = BLOCKS.registerBlock(
            "transport_belt",
            TransportBeltBlock::new,
            ModBlocks::metalProps);

    /** 高速传送带：logistics 科技解锁。 */
    public static final DeferredBlock<FastTransportBeltBlock> FAST_TRANSPORT_BELT =
            BLOCKS.registerBlock("fast_transport_belt", FastTransportBeltBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.5f, 6.0f)
                            .noOcclusion());

    /**
     * 漏斗：1 格被动转移，无 GUI、无电、无燃料。
     *
     * <p>补上「机器出口掉进下方箱子」这个最低成本连接 ——
     * 没有它，玩家为了 1 格落差被迫摆一个要喂煤的机械臂。</p>
     */
    public static final DeferredBlock<FunnelBlock> FUNNEL =
            BLOCKS.registerBlock("funnel", FunnelBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0f, 6.0f)
                            .noOcclusion());

    /** 燃烧机械臂：抓取 / 投放，自己烧煤，会偷燃料。 */
    public static final DeferredBlock<BurnerInserterBlock> BURNER_INSERTER =
            BLOCKS.registerBlock("burner_inserter", BurnerInserterBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.5f, 6.0f)
                            .noOcclusion());

    // ---------------------------------------------------------------- 生产机器

    /** 燃烧采矿机：2×2 多方块，覆盖下方 2×2，烧煤产出。 */
    public static final DeferredBlock<BurnerMinerBlock> BURNER_MINER =
            BLOCKS.registerBlock("burner_miner", BurnerMinerBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(3.5f, 8.0f)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(BurnerMinerBlock.ACTIVE) ? MACHINE_LIGHT : 0));

    /** 石熔炉：2×2 多方块，烧煤熔炼。 */
    public static final DeferredBlock<StoneFurnaceBlock> STONE_FURNACE =
            BLOCKS.registerBlock("stone_furnace", StoneFurnaceBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(3.5f, 8.0f)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(StoneFurnaceBlock.ACTIVE) ? MACHINE_LIGHT : 0));

    /** 组装机：3×3 多方块，电力驱动，按配方自动合成。 */
    public static final DeferredBlock<AssemblerBlock> ASSEMBLER =
            BLOCKS.registerBlock("assembler", AssemblerBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 8.0f)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(AssemblerBlock.ACTIVE) ? MACHINE_LIGHT : 0));

    /** 科研台（lab）：3×3 多方块，电力驱动，推进科技树。 */
    public static final DeferredBlock<LabBlock> LAB =
            BLOCKS.registerBlock("lab", LabBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0f, 4.0f)
                            .noOcclusion());

    /** 矿脉方块：真实带储量的可枯竭矿点（规格 §5.6）。单格 + BE。 */
    public static final DeferredBlock<OreVeinBlock> ORE_VEIN =
            BLOCKS.registerBlock("ore_vein", OreVeinBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(3.0f, 8.0f)
                            .noOcclusion());

    // ---------------------------------------------------------------- 动力系统

    /** 锅炉：2×3 多方块，烧煤产蒸汽（规格 §5.5）。 */
    public static final DeferredBlock<BoilerBlock> BOILER =
            BLOCKS.registerBlock("boiler", BoilerBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f, 8.0f)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(BoilerBlock.ACTIVE) ? MACHINE_LIGHT : 0));

    /** 蒸汽机：3×5 多方块，消耗蒸汽发电（规格 §5.5）。 */
    public static final DeferredBlock<SteamEngineBlock> STEAM_ENGINE =
            BLOCKS.registerBlock("steam_engine", SteamEngineBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f, 8.0f)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(SteamEngineBlock.ACTIVE) ? MACHINE_LIGHT : 0));

    /** 离岸泵：1×2 多方块，贴水时持续产蒸汽（规格 §5.5）。 */
    public static final DeferredBlock<OffshorePumpBlock> OFFSHORE_PUMP =
            BLOCKS.registerBlock("offshore_pump", OffshorePumpBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 8.0f)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(OffshorePumpBlock.ACTIVE) ? MACHINE_LIGHT : 0));

    /** 蒸汽管道：单格蒸汽节点（规格 §5.5）。 */
    public static final DeferredBlock<SteamPipeBlock> STEAM_PIPE =
            BLOCKS.registerBlock("steam_pipe", SteamPipeBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0f, 6.0f)
                            .noOcclusion());

    /** 电线杆：单格能量节点，5 格范围供电（规格 §5.5）。 */
    public static final DeferredBlock<PowerPoleBlock> POWER_POLE =
            BLOCKS.registerBlock("power_pole", PowerPoleBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0f, 4.0f)
                            .noOcclusion());

    /** 金属方块通用属性（传送带等薄片方块用）。 */
    private static BlockBehaviour.Properties metalProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0f, 6.0f)
                .noOcclusion();
    }
}
