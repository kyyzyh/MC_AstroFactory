package com.astrofactory.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 轴对齐立方体的顶点助手，用于 {@link MachineAnimationRenderer} 绘制活动部件。
 */
final class Cubes {

    private Cubes() {}

    /**
     * 绘制一个轴对齐立方体（块坐标：0~16 单位，方法参数按归一化 0~1 换算）。
     *
     * @param vc    顶点消费者
     * @param mat   变换矩阵（当前 PoseStack 末端）
     * @param x0,y0,z0 局部最小角（归一化 0~1）
     * @param x1,y1,z1 局部最大角（归一化 0~1）
     * @param r,g,b  RGB 颜色（0-255）
     * @param alpha  透明度（0-255）
     */
    static void box(VertexConsumer vc, Matrix4f mat,
                    float x0, float y0, float z0,
                    float x1, float y1, float z1,
                    int r, int g, int b, int alpha) {
        quad(vc, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, r, g, b, alpha);
        quad(vc, mat, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, r, g, b, alpha);
        quad(vc, mat, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, r, g, b, alpha);
        quad(vc, mat, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, r, g, b, alpha);
        quad(vc, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r, g, b, alpha);
        quad(vc, mat, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, r, g, b, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz,
                             int r, int g, int b, int a) {
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setNormal(nx, ny, nz);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }
}
