package com.astrofactory.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 电缆：把相连的发电机与耗能机器接成一张电网。
 *
 * <p>工作方式：电缆自身用计划刻每 tick 触发一次，从自身出发做 BFS，
 * 收集同一张电网里的发电机与机器，然后把发电机的能量按需分配给机器。</p>
 *
 * <p>为避免一条电网被重复搬运，只有 BFS 中发现的「第一根电缆」（即坐标最小的那根）
 * 负责执行分配。这个判定是确定性的，不需要额外状态同步。</p>
 */
public class CableBlock extends Block {

    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    /** 六个方向是否连接（用于渲染线缆粗细）。 */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    /** 电网搜索的最大电缆数，防止跨度过大卡服。 */
    private static final int MAX_CABLES = 512;
    /** 每根电缆每 tick 最多搬运的能量。 */
    private static final int TRANSFER_PER_CABLE = 256;

    /**
     * 电网拓扑缓存：电缆位置 → 所属电网（全部电缆 + 负责搬运的 owner）。
     *
     * <p>之前每根电缆每 tick 都跑一次 BFS（最多 512 格）。一条 100 根的电网
     * 就是每 tick 5 万次方块查询——这是真正的卡服来源。现在同一张电网只在
     * 结构变化后重算一次，其余电缆直接命中缓存。</p>
     */
    private static final java.util.Map<net.minecraft.world.level.Level,
            java.util.Map<BlockPos, GridInfo>> GRID_CACHE = new java.util.WeakHashMap<>();

    /** 一张电网的拓扑快照。 */
    private record GridInfo(java.util.Set<BlockPos> cables, BlockPos owner) {}

    /** 结构变化（放置/移除/邻居变动）时作废该维度的缓存。 */
    private static void invalidateCache(net.minecraft.world.level.Level level) {
        GRID_CACHE.remove(level);
    }

    private static final VoxelShape CORE = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);

    public CableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends CableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, Block.box(6, 6, 0, 10, 10, 6));
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, Block.box(6, 6, 10, 10, 10, 16));
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, Block.box(0, 6, 6, 6, 10, 10));
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, Block.box(10, 6, 6, 16, 10, 10));
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, Block.box(6, 10, 6, 10, 16, 10));
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, Block.box(6, 0, 6, 10, 6, 10));
        }
        return shape;
    }

    /** 该位置是否能被电缆连接（电缆本体或带能量能力的方块）。 */
    private static boolean connectable(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof CableBlock) {
            return true;
        }
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null;
    }

    /** 根据邻居刷新六个连接位。 */
    public static BlockState updateConnections(BlockState state, Level level, BlockPos pos) {
        return state
                .setValue(NORTH, connectable(level, pos.north()))
                .setValue(SOUTH, connectable(level, pos.south()))
                .setValue(EAST, connectable(level, pos.east()))
                .setValue(WEST, connectable(level, pos.west()))
                .setValue(UP, connectable(level, pos.above()))
                .setValue(DOWN, connectable(level, pos.below()));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        invalidateCache(level);
        // 刷新自身与邻居的连接位
        level.setBlock(pos, updateConnections(state, level, pos), 2);
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.getBlock() instanceof CableBlock) {
                level.setBlock(neighbor, updateConnections(neighborState, level, neighbor), 2);
            }
        }
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            invalidateCache(level);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        invalidateCache(level);
        BlockState updated = updateConnections(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        distribute(level, pos);
        // 自我续期，保持电网每 tick 都在搬运
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** 从本格出发遍历电网并搬运能量。 */
    private void distribute(ServerLevel level, BlockPos origin) {
        // 先查缓存：同一张电网只有 owner 负责搬运，其余电缆直接返回
        GridInfo info = resolveGrid(level, origin);
        if (info == null || !info.owner().equals(origin)) {
            return;
        }

        // 只在真正要搬运时，才去扫描电网边界上的发电机与机器
        List<IEnergyStorage> sources = new ArrayList<>();
        List<IEnergyStorage> sinks = new ArrayList<>();
        for (BlockPos cable : info.cables()) {
            for (Direction dir : Direction.values()) {
                BlockPos next = cable.relative(dir);
                if (level.getBlockState(next).getBlock() instanceof CableBlock) {
                    continue;
                }
                IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, next, dir.getOpposite());
                if (storage == null) {
                    continue;
                }
                if (storage.canExtract()) {
                    sources.add(storage);
                }
                if (storage.canReceive()) {
                    sinks.add(storage);
                }
            }
        }

        if (sources.isEmpty() || sinks.isEmpty()) {
            return;
        }

        int budget = TRANSFER_PER_CABLE * info.cables().size();

        // 只统计「还有空间」的机器，否则会白白占用电量配额
        List<IEnergyStorage> activeSinks = new ArrayList<>();
        for (IEnergyStorage sink : sinks) {
            if (sink.getMaxEnergyStored() > sink.getEnergyStored()) {
                activeSinks.add(sink);
            }
        }
        if (activeSinks.isEmpty()) {
            return;
        }

        // 公平分摊：把可用电量平均切成 N 份，避免第一台机器把电全部吸走
        // （采矿机容量 20000，若先到先得，熔炉和机械臂会永远饿死）
        int totalAvailable = 0;
        for (IEnergyStorage source : sources) {
            totalAvailable += source.getEnergyStored();
        }
        totalAvailable = Math.min(totalAvailable, budget);
        if (totalAvailable <= 0) {
            return;
        }

        int quota = Math.max(1, totalAvailable / activeSinks.size());

        for (IEnergyStorage sink : activeSinks) {
            int space = sink.getMaxEnergyStored() - sink.getEnergyStored();
            int want = Math.min(quota, space);
            if (want <= 0) {
                continue;
            }
            // 依次从各电源取电，直到满足这一份配额
            for (IEnergyStorage source : sources) {
                if (want <= 0) {
                    break;
                }
                int available = source.extractEnergy(want, true);
                if (available <= 0) {
                    continue;
                }
                int accepted = sink.receiveEnergy(available, true);
                if (accepted <= 0) {
                    continue;
                }
                int moved = source.extractEnergy(accepted, false);
                if (moved > 0) {
                    int actuallyReceived = sink.receiveEnergy(moved, false);
                    if (actuallyReceived < moved) {
                        // 理论不会发生（已模拟过），保险起见把差额退回
                        source.receiveEnergy(moved - actuallyReceived, false);
                    }
                    want -= actuallyReceived;
                }
            }
        }
    }

    /**
     * 取得 {@code origin} 所在电网的拓扑快照（带缓存）。
     *
     * <p>缓存按维度存放，电缆结构变化时整体作废。这样一张 N 根的电网
     * 只在结构变动后跑一次 BFS，而不是每 tick 每根都跑。</p>
     */
    private static GridInfo resolveGrid(ServerLevel level, BlockPos origin) {
        var byDim = GRID_CACHE.computeIfAbsent(level, k -> new java.util.HashMap<>());
        GridInfo cached = byDim.get(origin);
        if (cached != null) {
            return cached;
        }

        // BFS 收集整张电网
        Set<BlockPos> cables = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        cables.add(origin);
        while (!queue.isEmpty() && cables.size() <= MAX_CABLES) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (level.getBlockState(next).getBlock() instanceof CableBlock && cables.add(next)) {
                    queue.add(next);
                }
            }
        }

        // 坐标最小的那根负责搬运（确定性，无需额外状态同步）
        BlockPos owner = origin;
        for (BlockPos cable : cables) {
            if (compare(cable, owner) < 0) {
                owner = cable;
            }
        }

        GridInfo info = new GridInfo(cables, owner);
        // 整张电网的每根电缆都记同一个快照
        for (BlockPos cable : cables) {
            byDim.put(cable, info);
        }
        return info;
    }

    private static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }
}
