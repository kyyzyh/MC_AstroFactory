package com.astrofactory.client;

import com.astrofactory.blockentity.PowerPoleBlockEntity;
import com.astrofactory.config.AstroFactoryConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

/**
 * 电线杆连线可视化（US-102）。
 *
 * <p>清单要求「连线可视化（细线渲染）」。这里不引入网络同步：客户端本来
 * 就有杆的方块实体与位置，直接扫描 {@code linkRange} 内的其它杆画线即可，
 * 与服务端的组网规则（直线距离 ≤ linkRange）保持一致。</p>
 *
 * <p>去重：只朝「坐标字典序更大」的杆画线，这样 A→B 与 B→A 只画一次。</p>
 */
public class PowerPoleRenderer implements BlockEntityRenderer<PowerPoleBlockEntity> {

    /** 线从杆顶接点出发（与模型顶部横担高度一致）。 */
    private static final float WIRE_Y = 14.0F / 16.0F;

    @Override
    public void render(PowerPoleBlockEntity pole, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = pole.getLevel();
        if (level == null) {
            return;
        }
        int linkRange = AstroFactoryConfig.POWER_POLE_LINK_RANGE.get();
        long linkSq = (long) linkRange * linkRange;
        BlockPos origin = pole.getBlockPos();

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        Matrix4f mat = pose.last().pose();

        // 局部坐标：当前杆中心顶部
        float ox = 0.5F;
        float oy = WIRE_Y;
        float oz = 0.5F;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -linkRange; dx <= linkRange; dx++) {
            for (int dy = -linkRange; dy <= linkRange; dy++) {
                for (int dz = -linkRange; dz <= linkRange; dz++) {
                    if ((long) dx * dx + (long) dy * dy + (long) dz * dz > linkSq) {
                        continue;
                    }
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    // 去重：只朝字典序更大的杆画
                    if (compare(m, origin) <= 0) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(m);
                    if (!(be instanceof PowerPoleBlockEntity)) {
                        continue;
                    }
                    // 用方块相对坐标画线（PoseStack 已定位到当前 BE 原点）
                    float tx = (float) dx + 0.5F;
                    float ty = (float) dy + WIRE_Y;
                    float tz = (float) dz + 0.5F;
                    line(lines, mat, ox, oy, oz, tx, ty, tz);
                }
            }
        }
        pose.popPose();
    }

    /** 画一条线（两端顶点，法线朝上避免光照黑线）。 */
    private static void line(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2) {
        // 铜色
        int r = 210;
        int g = 140;
        int b = 70;
        int a = 200;
        float nx = 0.0F;
        float ny = 1.0F;
        float nz = 0.0F;

        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
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
