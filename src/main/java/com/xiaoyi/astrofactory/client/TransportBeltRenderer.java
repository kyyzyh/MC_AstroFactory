package com.xiaoyi.astrofactory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.xiaoyi.astrofactory.block.TransportBeltBlock;
import com.xiaoyi.astrofactory.block.TransportBeltBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 传送带物品渲染（26.3 渲染管线：extract -> submit）。
 *
 * <p>位置按 progress 连续插值，因此物品在带面上是平滑滑动，
 * 而不是逐格跳动 —— 这是「真传送带」的视觉关键。</p>
 *
 * <p><b>坡度</b>：y 坐标按 {@code 0.1875 + progress × 坡度高度差} <b>线性插值</b>
 * （规格 §5.2 明确禁止曲线插值）。三种坡度的入口 y 都是 0.1875，
 * 所以相邻带子交接时物品高度天然连续。</p>
 */
public class TransportBeltRenderer
        implements BlockEntityRenderer<TransportBeltBlockEntity, TransportBeltRenderer.State> {

    private final ItemModelResolver itemModelResolver;

    public TransportBeltRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    /** 自定义渲染状态：把方块实体数据快照到渲染线程。 */
    public static class State extends BlockEntityRenderState {
        public final List<TransportBeltBlockEntity.ItemSlot> slots = new ArrayList<>();
        public Direction facing = Direction.NORTH;
        public TransportBeltBlock.Slope slope = TransportBeltBlock.Slope.FLAT;
        public float partialTick;
        public float speed = TransportBeltBlockEntity.SPEED;
        public Level level;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TransportBeltBlockEntity be, State state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.slots.clear();
        for (TransportBeltBlockEntity.ItemSlot slot : be.getItems()) {
            if (!slot.stack.isEmpty()) {
                state.slots.add(slot.copy());
            }
        }
        BlockState blockState = be.getBlockState();
        state.facing = blockState.hasProperty(TransportBeltBlock.FACING)
                ? blockState.getValue(TransportBeltBlock.FACING)
                : Direction.NORTH;
        state.slope = blockState.hasProperty(TransportBeltBlock.SLOPE)
                ? blockState.getValue(TransportBeltBlock.SLOPE)
                : TransportBeltBlock.Slope.FLAT;
        state.partialTick = partialTick;
        state.speed = be.getSpeed();
        state.level = be.getLevel();
    }

    @Override
    public int getViewDistance() {
        // 距离剔除：超过 64 格不渲染（规格 6.4 与工程约束「性能红线」）。
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(TransportBeltBlockEntity blockEntity) {
        // 视锥剔除由 BlockEntityRenderDispatcher 依据此包围盒判定。
        // up 的物品会爬到上一格、down 的物品会降到下一格，两者都要扩包围盒，
        // 否则坡顶 / 坡底的物品会被误剔除（视觉上「闪一下消失」）。
        AABB box = new AABB(blockEntity.getBlockPos()).inflate(0.25);
        BlockState state = blockEntity.getBlockState();
        if (state.hasProperty(TransportBeltBlock.SLOPE)) {
            TransportBeltBlock.Slope slope = state.getValue(TransportBeltBlock.SLOPE);
            if (slope.exitsAbove()) {
                return box.expandTowards(0.0, 1.0, 0.0);
            }
            if (slope.exitsBelow()) {
                return box.expandTowards(0.0, -1.0, 0.0);
            }
        }
        return box;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraRenderState) {
        if (state.slots.isEmpty() || state.level == null) {
            return;
        }
        ItemOwner owner = new SimpleItemOwner(state.level);
        ItemStackRenderState itemRenderState = new ItemStackRenderState();

        for (TransportBeltBlockEntity.ItemSlot slot : state.slots) {
            float progress = Math.min(1.0f,
                    slot.progress + state.speed * state.partialTick);

            // 沿朝向推进：progress 0 -> 格首，1 -> 格尾
            double advance = 0.5 - progress;
            double laneOffset = (slot.lane - (TransportBeltBlockEntity.LANES - 1) / 2.0) * 0.22;
            double x = 0.5;
            double z = 0.5;
            switch (state.facing) {
                case NORTH -> { z = 0.5 + advance; x = 0.5 + laneOffset; }
                case SOUTH -> { z = 0.5 - advance; x = 0.5 - laneOffset; }
                case EAST  -> { x = 0.5 - advance; z = 0.5 + laneOffset; }
                case WEST  -> { x = 0.5 + advance; z = 0.5 - laneOffset; }
                default -> { }
            }

            // 坡度：y 线性插值（规格 §5.2 明确禁止曲线插值）
            double y = state.slope.yAt(progress) + 0.0125;   // 略高于带面，避免 z-fighting

            itemRenderState.clear();
            itemModelResolver.updateForTopItem(itemRenderState, slot.stack,
                    ItemDisplayContext.GROUND, state.level, owner, 0);
            if (itemRenderState.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.scale(0.42f, 0.42f, 0.42f);
            itemRenderState.submit(poseStack, collector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /** 极简 ItemOwner 实现，供物品模型解析用。 */
    private record SimpleItemOwner(Level level) implements ItemOwner {
        @Override
        public Vec3 position() {
            return Vec3.ZERO;
        }

        @Override
        public float getVisualRotationYInDegrees() {
            return 0.0f;
        }
    }
}
