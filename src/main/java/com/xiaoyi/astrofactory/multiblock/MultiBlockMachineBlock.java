package com.xiaoyi.astrofactory.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * 多方块机器基类（规格 §5.1）。
 *
 * <p><b>实现模型</b>：整台机器由同一种方块的多个 {@code PART} 状态组成，
 * 不额外注册 dummy 方块：</p>
 * <ul>
 *   <li>{@code PART = 0} 是 master，带 BlockEntity，负责全部逻辑；</li>
 *   <li>{@code PART = 1..sizeX*sizeZ-1} 是 dummy，{@link #newBlockEntity} 返回 null，不存数据；</li>
 *   <li>每格记录同一个 {@code FACING}，dummy 由 {@link MultiBlockHelper#masterOf} 反推 master；</li>
 *   <li>blockstate 按 {@code part} 给每格自己的模型片段（Q4 决定），拼出"一体机"观感。</li>
 * </ul>
 *
 * <p><b>尺寸</b>：默认正方形（{@link #size()}）；矩形机器覆写 {@link #sizeX()}/{@link #sizeZ()}
 * （锅炉 2×3、蒸汽机 3×5）。{@code sizeX} 是沿 FACING 右手方向的格数，
 * {@code sizeZ} 是沿 FACING 正面的格数。</p>
 *
 * <p><b>行为</b>：放置前整体预检（任一格不可替换就整体拒绝）；破坏任意一格整体拆除、
 * 只掉 1 个物品；右键任意一格都开 master 的 GUI；master 加载时自修复缺失的 dummy。</p>
 */
public abstract class MultiBlockMachineBlock extends BaseEntityBlock {
    /** 朝向，决定 N×N 的展开方向。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * 该格在机器内的索引：0 = master，1..sizeX*sizeZ-1 = dummy。
     *
     * <p>每种机器自己声明属性范围（2×2 用 0..3，3×3 用 0..8，2×3 用 0..5），
     * 这样 blockstate 只需覆盖真实存在的状态，不会出现缺失模型的警告。</p>
     */
    public abstract IntegerProperty partProperty();

    /**
     * 该机器的「运行中」外观属性；没有发光状态的机器（如科研台）返回 {@code null}。
     *
     * <p>供 {@link com.xiaoyi.astrofactory.machine.AbstractMachineEntity#setActive}
     * 统一做整机同步，避免每个 BE 各写一遍循环。</p>
     */
    public @Nullable BooleanProperty activeProperty() {
        return null;
    }

    protected MultiBlockMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(partProperty(), 0));
    }

    /** 机器边长（正方形机器）：2 = 2×2，3 = 3×3。 */
    public abstract int size();

    /** 沿 FACING 右手方向的格数；默认与 {@link #size()} 相同。 */
    public int sizeX() {
        return size();
    }

    /** 沿 FACING 正面的格数；默认与 {@link #size()} 相同。 */
    public int sizeZ() {
        return size();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, partProperty());
    }

    // ------------------------------------------------------------ 放置

    /**
     * 整体预检：任一格不可替换就返回 null，让 BlockItem 整体拒绝，
     * 不会出现"放了一半"的半截机器。
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos masterPos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        int sx = sizeX();
        int sz = sizeZ();

        for (BlockPos cell : MultiBlockHelper.allCells(masterPos, facing, sx, sz)) {
            if (cell.getY() < level.getMinY() || cell.getY() > level.getMaxY()) {
                return null;
            }
            if (!level.isLoaded(cell)) {
                return null;
            }
            if (!level.getBlockState(cell).canBeReplaced()) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(partProperty(), 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        int sx = sizeX();
        int sz = sizeZ();
        for (int dz = 0; dz < sz; dz++) {
            for (int dx = 0; dx < sx; dx++) {
                int part = MultiBlockHelper.partOf(dx, dz, sx);
                if (part == 0) {
                    continue;
                }
                BlockPos cell = MultiBlockHelper.cellPos(pos, facing, dx, dz);
                level.setBlock(cell, state.setValue(partProperty(), part), Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------ 方块实体

    /** dummy 格不建 BlockEntity。 */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(partProperty()) == 0 ? createBlockEntity(pos, state) : null;
    }

    protected abstract BlockEntity createBlockEntity(BlockPos pos, BlockState state);

    // ------------------------------------------------------------ 破坏

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
        if (level.isClientSide()) {
            return result;
        }
        BlockPos masterPos = masterPosOf(pos, state);
        if (!player.isCreative()) {
            Block.popResource(level, masterPos, new ItemStack(this.asItem()));
        }
        clearWhole(level, masterPos, state.getValue(FACING), sizeX(), sizeZ());
        return result;
    }

    /** 掉落与清理已在 playerWillDestroy 处理，这里只补统计，避免重复掉落。 */
    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }

    /** 爆炸等途径移除一格时清掉整机，避免留幽灵。 */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                               boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        clearWhole(level, masterPosOf(pos, state), state.getValue(FACING), sizeX(), sizeZ());
    }

    protected BlockPos masterPosOf(BlockPos pos, BlockState state) {
        int part = state.getValue(partProperty());
        return part == 0 ? pos
                : MultiBlockHelper.masterOf(pos, state.getValue(FACING), sizeX(), sizeZ(), part);
    }

    /** 清空整台机器（不发掉落、不连锁）。 */
    protected void clearWhole(Level level, BlockPos masterPos, Direction facing, int sizeX, int sizeZ) {
        for (BlockPos cell : MultiBlockHelper.allCells(masterPos, facing, sizeX, sizeZ)) {
            if (!level.isLoaded(cell)) {
                continue;
            }
            if (level.getBlockState(cell).getBlock() == this) {
                // UPDATE_CLIENTS：只同步客户端，不触发邻居更新，避免递归
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 正方形便捷重载。 */
    protected void clearWhole(Level level, BlockPos masterPos, Direction facing, int size) {
        clearWhole(level, masterPos, facing, size, size);
    }

    // ------------------------------------------------------------ 交互

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos masterPos = masterPosOf(pos, state);
        BlockEntity be = level.getBlockEntity(masterPos);
        if (be instanceof MenuProvider provider) {
            player.openMenu(provider);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------ 形状与渲染

    /**
     * 每格都是完整立方体，N×N 合起来就是整机实心。
     *
     * <p>规格原文写"master 碰撞箱完整 N×N、dummy 空碰撞"，但原版实体碰撞只查询位置
     * 落在扫掠 AABB 内的方块，master 的碰撞箱无法覆盖几格之外的 dummy 格；且 Q4 决定
     * dummy 各渲染自己那份模型（可见）。因此 dummy 必须自带碰撞，否则玩家会穿进机器内部。
     * 此处按"整体实心"实现，效果与规格意图一致。</p>
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ------------------------------------------------------------ 区块重载自修复

    /**
     * master 加载时调用：补齐缺失的 dummy。
     *
     * <p>若某格被别的方块占了，说明机器已被破坏了一半，直接清掉整机，不留幽灵。</p>
     */
    public void repair(Level level, BlockPos masterPos, BlockState masterState) {
        if (level.isClientSide()) {
            return;
        }
        Direction facing = masterState.getValue(FACING);
        int sx = sizeX();
        int sz = sizeZ();
        boolean broken = false;

        for (int dz = 0; dz < sz && !broken; dz++) {
            for (int dx = 0; dx < sx && !broken; dx++) {
                int part = MultiBlockHelper.partOf(dx, dz, sx);
                if (part == 0) {
                    continue;
                }
                BlockPos cell = MultiBlockHelper.cellPos(masterPos, facing, dx, dz);
                if (!level.isLoaded(cell)) {
                    continue;
                }
                BlockState existing = level.getBlockState(cell);
                if (existing.getBlock() == this && existing.getValue(partProperty()) == part) {
                    continue;
                }
                if (!existing.canBeReplaced()) {
                    broken = true;
                    break;
                }
                level.setBlock(cell, masterState.setValue(partProperty(), part), Block.UPDATE_ALL);
            }
        }

        if (broken) {
            clearWhole(level, masterPos, facing, sx, sz);
        }
    }
}
