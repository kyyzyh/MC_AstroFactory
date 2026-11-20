# -*- coding: utf-8 -*-
"""重写 TransportBeltBlock.java（最终定稿：设计 A，所有入口统一 0.1875）。

自洽性推导（唯一解）：
  设带子 B 在格 C、朝向 f，出口格 = C+f+(0,dy,0)，出口世界高度 = C.y+exitY。
  接收带 R 的入口世界高度 = (C.y+dy) + R.entryY。
  要求相等 => exitY = dy + R.entryY。
  取 R.entryY 恒为 0.1875，则：
      dy=0  -> exitY=0.1875   (flat)
      dy=+1 -> exitY=1.1875   (up)
      dy=-1 -> exitY=-0.8125  (down)
  这是唯一自洽解：flat/up/down 之间可以任意串联，且每次交接高度精确对齐。
"""
import io

CONTENT = r'''package com.xiaoyi.astrofactory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.xiaoyi.astrofactory.ModRegistry;

/**
 * 传送带：真实连续移动的物品输送。
 *
 * <h2>坡度模型（规格 §5.2「斜坡」）</h2>
 *
 * <p>每格带子有 {@link Slope} 三态。一条链是否自洽，唯一判据是：
 * <b>任何带子的出口高度，必须正好等于下一格带子的入口高度</b>。</p>
 *
 * <p>所有带子的入口都取本格带面 {@code 0.1875}，代入判据
 * （{@code exitY = dy + 0.1875}）得到<b>唯一能串联</b>的解：</p>
 *
 * <pre>
 *   坡度   入口 y   出口 y    出口格        说明
 *   flat   0.1875   0.1875    前方 同层      平接平
 *   up     0.1875   1.1875    前方 上一格    爬到上一格带面
 *   down   0.1875  -0.8125    前方 下一格    降到下一格带面
 * </pre>
 *
 * <p>验证 up：出口世界高度 {@code C.y+1.1875}；出口格 {@code C+f+above} 的
 * 入口是 {@code (C.y+1)+0.1875}，相等。✓</p>
 *
 * <p>验证 down：出口世界高度 {@code C.y-0.8125}；出口格 {@code C+f+below} 的
 * 入口是 {@code (C.y-1)+0.1875}，相等。✓</p>
 *
 * <p>所以连续 up 能一格一格往上爬、连续 down 能一格一格往下走，任何坡度
 * 组合都能精确对接。物品在本格内的 y 从 {@code entryY} 线性插值到
 * {@code exitY}（规格明确禁止曲线插值）。</p>
 *
 * <p><b>为什么不是「down 入口在 1.1875」那种方案</b>：那样 down 格的下游必须
 * 在斜下方，而它的「前方同层」是空的 —— 串联时下一格接收不到，物品会卡住。
 * 只有让所有入口统一在 0.1875、出口按坡度 ±1 格，才能逐级串联。</p>
 *
 * <p><b>已知视觉限制</b>：down 格的物品 y 会降到 {@code -0.8125}，即穿过本格
 * 底面进入下一格。模型只画本格内可见的入口段（0.1875 → 0 的短斜坡）。
 * 因此 down 格下方应保持空气（坡道悬空），否则物品看起来会钻进地面。</p>
 *
 * <p><b>不照抄铁轨</b>：铁轨用 {@code RailShape} 十态编码「弯 + 坡」的组合；
 * 这里朝向与坡度正交（{@code FACING} × {@code SLOPE} = 12 态），转弯由相邻
 * 两格 FACING 的差异自然形成，不需要额外状态。</p>
 */
public class TransportBeltBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 坡度：平坦 / 向上 / 向下。 */
    public static final EnumProperty<Slope> SLOPE = EnumProperty.create("slope", Slope.class);

    /** 平坦带面高度（像素 3 -> 0.1875 格）。 */
    public static final double SURFACE_Y = 3.0 / 16.0;

    /** 平坦：16x3x16 贴地薄板。 */
    private static final VoxelShape SHAPE_FLAT =
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

    /**
     * 斜坡碰撞箱：两级台阶近似坡面（每级 0.5 格）。
     *
     * <p>0.5 格小于玩家自动上台阶高度（0.6 格），所以能直接走上去不用跳。
     * 碰撞箱必须按朝向分别给出 —— {@link VoxelShape} 不会随 blockstate 的
     * y 旋转自动变换。</p>
     */
    private static final VoxelShape[][] RAMP_SHAPES = new VoxelShape[4][2];
    private static final int F_NORTH = 0;
    private static final int F_SOUTH = 1;
    private static final int F_WEST = 2;
    private static final int F_EAST = 3;

    static {
        // NORTH：入口在南（z=16），出口在北（z=0）
        RAMP_SHAPES[F_NORTH][0] = Shapes.or(   // UP：北半高、南半低
                Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0),
                Block.box(0.0, 0.0, 8.0, 16.0, 8.0, 16.0));
        RAMP_SHAPES[F_NORTH][1] = Shapes.or(   // DOWN：南半高（入口）、北半低
                Block.box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0),
                Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 8.0));
        // SOUTH：z 镜像
        RAMP_SHAPES[F_SOUTH][0] = Shapes.or(
                Block.box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0),
                Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 8.0));
        RAMP_SHAPES[F_SOUTH][1] = Shapes.or(
                Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0),
                Block.box(0.0, 0.0, 8.0, 16.0, 8.0, 16.0));
        // EAST：入口在西（x=0），出口在东（x=16）
        RAMP_SHAPES[F_EAST][0] = Shapes.or(
                Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0),
                Block.box(0.0, 0.0, 0.0, 8.0, 8.0, 16.0));
        RAMP_SHAPES[F_EAST][1] = Shapes.or(
                Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0),
                Block.box(8.0, 0.0, 0.0, 16.0, 8.0, 16.0));
        // WEST：入口在东（x=16），出口在西（x=0）
        RAMP_SHAPES[F_WEST][0] = Shapes.or(
                Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0),
                Block.box(8.0, 0.0, 0.0, 16.0, 8.0, 16.0));
        RAMP_SHAPES[F_WEST][1] = Shapes.or(
                Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0),
                Block.box(0.0, 0.0, 0.0, 8.0, 8.0, 16.0));
    }

    /** 取某个状态对应的形状。 */
    private static VoxelShape shapeOf(BlockState state) {
        Slope slope = state.getValue(SLOPE);
        if (slope == Slope.FLAT) {
            return SHAPE_FLAT;
        }
        int fi = switch (state.getValue(FACING)) {
            case SOUTH -> F_SOUTH;
            case WEST -> F_WEST;
            case EAST -> F_EAST;
            default -> F_NORTH;
        };
        return RAMP_SHAPES[fi][slope == Slope.UP ? 0 : 1];
    }

    public TransportBeltBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SLOPE, Slope.FLAT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SLOPE);
    }

    // ------------------------------------------------------------ 放置

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SLOPE, detectSlope(context.getLevel(), context.getClickedPos(), facing));
    }

    /**
     * 邻居方块变化时重新判定坡度。
     *
     * <p><b>这里只能覆盖 6 邻接的情况</b>。坡度判定要看「前方上一格」这类
     * <b>斜对角</b>位置，而 Minecraft 的方块更新只发给 6 个正交邻居 ——
     * 斜对角上的方块被放上 / 拆掉时，本格<b>收不到</b> {@code neighborChanged}。
     * 因此坡度同步主要靠 {@link #onPlace} / {@link #onRemove} 里的
     * {@link #refreshSlopeAround}（放置是低频操作，扫描 5x5x5 代价可忽略）。</p>
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Slope want = detectSlope(level, pos, facing);
        if (state.getValue(SLOPE) != want) {
            level.setBlock(pos, state.setValue(SLOPE, want), Block.UPDATE_ALL);
        }
    }

    /** 放下后刷新周围坡度（含斜对角）。 */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            refreshSlopeAround(level, pos);
        }
    }

    /**
     * 拆掉后刷新周围坡度（含斜对角），避免残留悬空的坡顶。
     *
     * <p>26.3 中 {@code onRemove} 已改名为
     * {@link #affectNeighborsAfterRemoval}，且只在服务端调用（入参是
     * {@code ServerLevel}），所以这里不需要再判 {@code isClientSide}。</p>
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        refreshSlopeAround(level, pos);
    }

    /**
     * 重算以 {@code center} 为中心 5x5x5 范围内所有传送带的坡度。
     *
     * <p>半径 2 足以覆盖「前方上一格」这类斜对角（水平 1 + 垂直 1），
     * 以及连续两级台阶的情况。</p>
     */
    private static void refreshSlopeAround(Level level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof TransportBeltBlock)) {
                        continue;
                    }
                    Direction f = s.getValue(FACING);
                    Slope want = detectSlope(level, p, f);
                    if (s.getValue(SLOPE) != want) {
                        level.setBlock(p, s.setValue(SLOPE, want), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /**
     * 按邻居判定某位置该用什么坡度。
     *
     * <p>判定顺序：</p>
     * <ol>
     *   <li><b>前方同层</b>有同向带子 → {@code flat}（平接平）</li>
     *   <li><b>前方上一层</b>有同向带子 → {@code up}（要爬上去）</li>
     *   <li><b>前方下一层</b>有同向带子 → {@code down}（要降下去）</li>
     *   <li>都没有 → {@code flat}（末端，物品在此堆积）</li>
     * </ol>
     *
     * <p>先判「前方同层」是关键：平地续接时若不先判，会被「前方上一层」
     * 误判成上坡（例如上坡道正下方压着一条平地）。</p>
     */
    public static Slope detectSlope(Level level, BlockPos pos, Direction facing) {
        BlockPos front = pos.relative(facing);

        if (isBeltFacing(level.getBlockState(front), facing)) {
            return Slope.FLAT;
        }
        if (isBeltFacing(level.getBlockState(front.above()), facing)) {
            return Slope.UP;
        }
        if (isBeltFacing(level.getBlockState(front.below()), facing)) {
            return Slope.DOWN;
        }
        return Slope.FLAT;
    }

    /** 该方块状态是不是「同朝向的传送带」。 */
    public static boolean isBeltFacing(BlockState state, Direction facing) {
        if (!(state.getBlock() instanceof TransportBeltBlock)) {
            return false;
        }
        return state.hasProperty(FACING) && state.getValue(FACING) == facing;
    }

    // ------------------------------------------------------------ 几何

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeOf(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ------------------------------------------------------------ 方块实体

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransportBeltBlockEntity(pos, state);
    }

    /** 本方块对应的方块实体类型；子类覆写以接入自己的 BE。 */
    protected BlockEntityType<? extends TransportBeltBlockEntity> getBlockEntityType() {
        return ModRegistry.TRANSPORT_BELT_BE.get();
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            // 客户端也要 tick：本地推进 progress，保证物品连续滑动
            return createTickerHelper(type, getBlockEntityType(),
                    TransportBeltBlockEntity::clientTick);
        }
        return createTickerHelper(type, getBlockEntityType(),
                TransportBeltBlockEntity::serverTick);
    }

    // ------------------------------------------------------------ 坡度枚举

    /**
     * 坡度。
     *
     * <p>{@link #entryY()} / {@link #exitY()} 是物品在本格内起止的 y
     * （相对本格底面，单位格）。渲染按 {@link #yAt} 线性插值 ——
     * 规格明确禁止曲线插值。</p>
     */
    public enum Slope implements StringRepresentable {
        /** 平地：y 恒为 0.1875，出口在前方同层。 */
        FLAT("flat", SURFACE_Y, SURFACE_Y),
        /** 上坡：从本格带面爬到前方上一格带面。 */
        UP("up", SURFACE_Y, SURFACE_Y + 1.0),
        /** 下坡：从本格带面降到前方下一格带面。 */
        DOWN("down", SURFACE_Y, SURFACE_Y - 1.0);

        private final String name;
        private final double entryY;
        private final double exitY;

        Slope(String name, double entryY, double exitY) {
            this.name = name;
            this.entryY = entryY;
            this.exitY = exitY;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** 物品进入本格时的 y。 */
        public double entryY() {
            return entryY;
        }

        /** 物品离开本格时的 y。 */
        public double exitY() {
            return exitY;
        }

        /** 按 progress（0..1）线性插值出物品 y。 */
        public double yAt(double progress) {
            return entryY + (exitY - entryY) * progress;
        }

        /** 出口是否在前方上一格（只有 up 是）。 */
        public boolean exitsAbove() {
            return this == UP;
        }

        /** 出口是否在前方下一格（只有 down 是）。 */
        public boolean exitsBelow() {
            return this == DOWN;
        }
    }
}
'''

path = "src/main/java/com/xiaoyi/astrofactory/block/TransportBeltBlock.java"
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(CONTENT)
print("wrote", len(CONTENT), "chars")
