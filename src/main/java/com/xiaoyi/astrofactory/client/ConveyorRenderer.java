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
import net.minecraft.world.phys.Vec3;

import com.xiaoyi.astrofactory.block.ConveyorBlock;
import com.xiaoyi.astrofactory.block.ConveyorBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 传送带物品渲染（26.3 渲染管线：extract -> submit）。
 *
 * 位置按 progress 连续插值，因此物品在带面上是平滑滑动，
 * 而不是逐格跳动——这是"真传送带"的视觉关键。
 */
public class ConveyorRenderer
        implements BlockEntityRenderer<ConveyorBlockEntity, ConveyorRenderer.State> {

    private final ItemModelResolver itemModelResolver;

    public ConveyorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    /** 自定义渲染状态：把方块实体数据快照到渲染线程。 */
    public static class State extends BlockEntityRenderState {
        public final List<ConveyorBlockEntity.ItemSlot> slots = new ArrayList<>();
        public Direction facing = Direction.NORTH;
        public float partialTick;
        public float speed = ConveyorBlockEntity.SPEED;
        public Level level;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ConveyorBlockEntity be, State state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.slots.clear();
        for (ConveyorBlockEntity.ItemSlot slot : be.getItems()) {
            if (!slot.stack.isEmpty()) {
                state.slots.add(slot.copy());
            }
        }
        BlockState blockState = be.getBlockState();
        state.facing = blockState.hasProperty(ConveyorBlock.FACING)
                ? blockState.getValue(ConveyorBlock.FACING)
                : Direction.NORTH;
        state.partialTick = partialTick;
        state.speed = be.getSpeed();
        state.level = be.getLevel();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraRenderState) {
        if (state.slots.isEmpty() || state.level == null) {
            return;
        }
        ItemOwner owner = new SimpleItemOwner(state.level);
        ItemStackRenderState itemRenderState = new ItemStackRenderState();

        for (ConveyorBlockEntity.ItemSlot slot : state.slots) {
            float progress = Math.min(1.0f,
                    slot.progress + state.speed * state.partialTick);

            // 沿朝向推进：progress 0 -> 格首，1 -> 格尾
            double advance = 0.5 - progress;
            double laneOffset = (slot.lane - (ConveyorBlockEntity.LANES - 1) / 2.0) * 0.22;
            double x = 0.5;
            double z = 0.5;
            switch (state.facing) {
                case NORTH -> { z = 0.5 + advance; x = 0.5 + laneOffset; }
                case SOUTH -> { z = 0.5 - advance; x = 0.5 - laneOffset; }
                case EAST  -> { x = 0.5 - advance; z = 0.5 + laneOffset; }
                case WEST  -> { x = 0.5 + advance; z = 0.5 - laneOffset; }
                default -> { }
            }

            itemRenderState.clear();
            itemModelResolver.updateForTopItem(itemRenderState, slot.stack,
                    ItemDisplayContext.GROUND, state.level, owner, 0);
            if (itemRenderState.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(x, 0.20, z);
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
