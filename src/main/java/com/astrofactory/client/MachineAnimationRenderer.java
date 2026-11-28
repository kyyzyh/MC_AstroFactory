package com.astrofactory.client;

import com.astrofactory.blockentity.AssemblerBlockEntity;
import com.astrofactory.blockentity.BoilerBlockEntity;
import com.astrofactory.blockentity.GeneratorBlockEntity;
import com.astrofactory.blockentity.InserterBlockEntity;
import com.astrofactory.blockentity.LabBlockEntity;
import com.astrofactory.blockentity.MinerBlockEntity;
import com.astrofactory.blockentity.SmelterBlockEntity;
import com.astrofactory.blockentity.SteamEngineBlockEntity;
import com.astrofactory.blockentity.WaterPumpBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

/**
 * 全机器统一的活动部件动画渲染器（动效）。
 *
 * <p>服务端在机器工作/停工时通过 {@code setBlock} 切换 {@link BlockStateProperties#LIT}，
 * 客户端经方块状态同步感知；本渲染器只在 {@code lit=true} 时按机器类型绘制<b>活动部件</b>
 * （下钻钻头、摆臂、飞轮、活塞、转子、火焰等），动画用客户端本地时间 + partialTick 驱动，
 * <b>零网络同步、零逐 tick 网格遍历</b>（只在渲染时读取自身方块实体）。</p>
 *
 * <p>几何约定：本地坐标取块单位 0~1，PoseStack 已定位到方块原点（角点，非中心），
 * 模型正面（front）朝向本地 -Z。渲染前按方块 FAcing 绕 Y 旋转，使正面朝外。</p>
 */
public class MachineAnimationRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    /** 金属灰蓝。 */
    private static final int[] METAL = {120, 130, 140};
    /** 深灰。 */
    private static final int[] DARK = {70, 76, 84};
    /** 亮橙（火焰/发光）。 */
    private static final int[] FLAME = {255, 140, 40};
    /** 钢蓝（高温体）。 */
    private static final int[] STEEL = {200, 215, 230};

    @Override
    public void render(T be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.hasProperty(BlockStateProperties.LIT)
                && Boolean.TRUE.equals(state.getValue(BlockStateProperties.LIT)))) {
            return; // 未工作：不绘制活动部件
        }

        float t = (be.getLevel() == null ? 0f
                : (be.getLevel().getGameTime() % 240L) + partialTick);

        pose.pushPose();
        applyFacing(pose, state);
        Matrix4f mat = pose.last().pose();
        // POSITION_COLOR_NORMAL 缓冲：与 Cubes.addVertex+setColor+setNormal 匹配
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());

        if (be instanceof MinerBlockEntity) {
            renderMiner(vc, mat, t);
        } else if (be instanceof InserterBlockEntity) {
            renderInserter(vc, mat, t);
        } else if (be instanceof SteamEngineBlockEntity) {
            renderSteamEngine(vc, mat, t);
        } else if (be instanceof WaterPumpBlockEntity) {
            renderWaterPump(vc, mat, t);
        } else if (be instanceof GeneratorBlockEntity) {
            renderGenerator(vc, mat, t);
        } else if (be instanceof AssemblerBlockEntity) {
            renderAssembler(vc, mat, t);
        } else if (be instanceof BoilerBlockEntity) {
            renderBoiler(vc, mat, t);
        } else if (be instanceof SmelterBlockEntity) {
            renderSmelter(vc, mat, t);
        } else if (be instanceof LabBlockEntity) {
            renderLab(vc, mat, t);
        }

        pose.popPose();
    }

    /** 按方块 FAcing 旋转，使本地正面（-Z）朝外。 */
    private static void applyFacing(PoseStack pose, BlockState state) {
        Direction d = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        int yaw = switch (d) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        if (yaw != 0) {
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
        }
    }

    // ------------------------------------------------------------ 采矿机：下钻钻头

    /**
     * 采矿机下钻动作：中央一组钻杆做竖直往复（下扎到底再回弹），
     * 顶部电机舱下方挂一个旋转钻头块。
     */
    private static void renderMiner(VertexConsumer vc, Matrix4f mat, float t) {
        // 下钻进度：0~1 往复（周期 ~2.4s）
        float ph = (float) (0.5 - 0.5 * Math.cos(t / 4.0 * Math.PI * 2));
        // 钻杆从 y=0.70（电机底部）下探到 y=0.10
        float top = 0.70f;
        float bot = 0.10f;
        float yTop = top - (top - bot) * ph;
        float yBot = yTop - 0.45f * (0.6f + 0.4f * ph); // 杆身随压下稍微拉长

        // 钻杆（中心细轴）
        Cubes.box(vc, mat, 0.44f, yBot, 0.44f, 0.56f, yTop, 0.56f,
                METAL[0], METAL[1], METAL[2], 255);
        // 钻头（底部粗头）
        Cubes.box(vc, mat, 0.40f, yBot, 0.40f, 0.60f, yBot + 0.12f, 0.60f,
                DARK[0], DARK[1], DARK[2], 255);
    }

    // ------------------------------------------------------------ 机械臂：摆臂

    /** 机械臂摆臂：绕竖直轴左右摆动（取料→投放）。 */
    private static void renderInserter(VertexConsumer vc, Matrix4f mat, float t) {
        float swing = (float) Math.sin(t / 5.0 * Math.PI * 2); // -1..1
        float ang = swing * 0.6f; // ~±34°
        // 摆臂根部在本地正面 -Z 方向水平伸出，绕顶部转轴摆动（此处用色块在 X-Z 平面扫）。
        // 简化示意：一根水平臂从中心指向 -Z，摆动时 Y 抬升变化营造抓起/放下。
        float tipZ = -(0.30f + 0.28f * (float) ((swing + 1) / 2)); // 前伸
        float tipY = 0.55f + 0.10f * (float) ((swing + 1) / 2);    // 抬升
        // 肩部
        Cubes.box(vc, mat, 0.46f, 0.50f, 0.46f, 0.54f, 0.64f, 0.54f,
                METAL[0], METAL[1], METAL[2], 255);
        // 前伸臂（沿 -Z）
        Cubes.box(vc, mat, 0.45f, 0.52f, tipZ, 0.55f, tipY, -0.06f,
                STEEL[0], STEEL[1], STEEL[2], 220);
        // 末端抓爪
        Cubes.box(vc, mat, 0.42f, 0.50f, tipZ - 0.10f, 0.58f, tipY + 0.08f, tipZ,
                DARK[0], DARK[1], DARK[2], 255);
        // 夹角示意（摆角）
        float a2 = ang * 0.5f;
        Cubes.box(vc, mat, 0.47f, 0.52f, 0.47f, 0.53f, 0.55f + a2, 0.53f,
                FLAME[0], FLAME[1], FLAME[2], 160);
    }

    // ------------------------------------------------------------ 蒸汽机：飞轮 + 活塞

    /** 蒸汽机：侧向飞轮旋转（用绕轴旋转的偏心块模拟）+ 往复活塞。 */
    private static void renderSteamEngine(VertexConsumer vc, Matrix4f mat, float t) {
        float a = (float) (t / 3.0 * Math.PI * 2); // 旋转角
        float cx = 0.30f, cz = 0.50f; // 飞轮圆心
        float r = 0.14f;
        // 飞轮偏心块（旋转）
        float fx = (float) (cx + r * Math.cos(a));
        float fz = (float) (cz + r * Math.sin(a));
        Cubes.box(vc, mat, fx - 0.05f, 0.52f, fz - 0.05f, fx + 0.05f, 0.66f, fz + 0.05f,
                METAL[0], METAL[1], METAL[2], 255);
        // 活塞往复（Y 上下）
        float py = 0.28f + 0.18f * (float) ((Math.sin(t / 2.0 * Math.PI * 2) + 1) / 2);
        Cubes.box(vc, mat, 0.46f, py, 0.46f, 0.54f, py + 0.16f, 0.54f,
                STEEL[0], STEEL[1], STEEL[2], 230);
    }

    // ------------------------------------------------------------ 抽水机：抽水活塞

    /** 抽水机：竖直活塞快速上下抽动 + 侧向水管脉动。 */
    private static void renderWaterPump(VertexConsumer vc, Matrix4f mat, float t) {
        float ph = (float) (0.5 - 0.5 * Math.cos(t / 1.2 * Math.PI * 2)); // 快
        float top = 0.30f + 0.22f * ph;
        Cubes.box(vc, mat, 0.44f, 0.10f, 0.44f, 0.56f, top, 0.56f,
                STEEL[0], STEEL[1], STEEL[2], 230);
        // 出水管朝 -Z 脉动
        float pulse = 0.04f * (float) Math.sin(t / 1.2 * Math.PI * 2);
        Cubes.box(vc, mat, 0.40f, 0.22f, -0.16f - pulse, 0.60f, 0.34f, -0.02f,
                FLAME[0], FLAME[1], FLAME[2], 180);
    }

    // ------------------------------------------------------------ 发电机：转子

    /** 发电机：内部转子旋转（偏心块绕轴转）。 */
    private static void renderGenerator(VertexConsumer vc, Matrix4f mat, float t) {
        float a = (float) (t / 4.0 * Math.PI * 2);
        float cx = 0.50f, cz = 0.50f; // 中心轴
        float r = 0.16f;
        float fx = (float) (cx + r * Math.cos(a));
        float fz = (float) (cz + r * Math.sin(a));
        Cubes.box(vc, mat, fx - 0.05f, 0.50f, fz - 0.05f, fx + 0.05f, 0.58f, fz + 0.05f,
                METAL[0], METAL[1], METAL[2], 255);
        // 中心轴
        Cubes.box(vc, mat, 0.47f, 0.44f, 0.47f, 0.53f, 0.62f, 0.53f,
                DARK[0], DARK[1], DARK[2], 200);
        // 顶部燃烧辉光
        float f = 0.5f + 0.5f * (float) Math.sin(t / 3.0 * Math.PI * 2);
        Cubes.box(vc, mat, 0.46f, 0.62f, 0.46f, 0.54f, 0.64f + 0.08f * f, 0.54f,
                FLAME[0], FLAME[1], FLAME[2], (int) (120 + 100 * f));
    }

    // ------------------------------------------------------------ 组装机：抓取臂

    /** 组装机：内部机械臂反复抓取（竖直往复 + 轻微旋转）。 */
    private static void renderAssembler(VertexConsumer vc, Matrix4f mat, float t) {
        float ph = (float) (0.5 - 0.5 * Math.cos(t / 3.0 * Math.PI * 2));
        float yC = 0.30f + 0.20f * ph; // 爪头高度
        // 立柱
        Cubes.box(vc, mat, 0.47f, 0.16f, 0.47f, 0.53f, yC, 0.53f,
                METAL[0], METAL[1], METAL[2], 255);
        // 双爪
        Cubes.box(vc, mat, 0.42f, yC, 0.42f, 0.58f, yC + 0.10f, 0.58f,
                STEEL[0], STEEL[1], STEEL[2], 230);
        // 夹缝
        Cubes.box(vc, mat, 0.48f, yC, 0.48f, 0.52f, yC + 0.09f, 0.52f,
                FLAME[0], FLAME[1], FLAME[2], 150);
    }

    // ------------------------------------------------------------ 锅炉：燃烧火焰

    /** 锅炉：炉膛火焰跳动。 */
    private static void renderBoiler(VertexConsumer vc, Matrix4f mat, float t) {
        float f = 0.5f + 0.5f * (float) Math.sin(t / 2.0 * Math.PI * 2);
        float h = 0.36f + 0.16f * f;
        Cubes.box(vc, mat, 0.44f, 0.18f, 0.44f, 0.56f, h, 0.56f,
                FLAME[0], FLAME[1], FLAME[2], (int) (140 + 110 * f));
        Cubes.box(vc, mat, 0.46f, 0.20f, 0.46f, 0.54f, h * 0.7f, 0.54f,
                255, 200, 90, (int) (160 + 90 * f));
    }

    // ------------------------------------------------------------ 熔炉：炼焰

    /** 熔炉：内芯高温焰。 */
    private static void renderSmelter(VertexConsumer vc, Matrix4f mat, float t) {
        float f = 0.5f + 0.5f * (float) Math.sin(t / 2.4 * Math.PI * 2);
        float h = 0.36f + 0.18f * f;
        Cubes.box(vc, mat, 0.44f, 0.18f, 0.44f, 0.56f, h, 0.56f,
                FLAME[0], FLAME[1], FLAME[2], (int) (150 + 100 * f));
        Cubes.box(vc, mat, 0.46f, 0.20f, 0.46f, 0.54f, h * 0.68f, 0.54f,
                255, 220, 120, (int) (170 + 80 * f));
    }

    // ------------------------------------------------------------ 实验室：研究光

    /** 实验室：研究中的发光脉动。 */
    private static void renderLab(VertexConsumer vc, Matrix4f mat, float t) {
        float f = 0.5f + 0.5f * (float) Math.sin(t / 1.8 * Math.PI * 2);
        float s = 0.42f + 0.08f * f;
        Cubes.box(vc, mat, 0.50f - s / 2, 0.34f, 0.50f - s / 2,
                0.50f + s / 2, 0.34f + s, 0.50f + s / 2,
                FLAME[0], FLAME[1], FLAME[2], (int) (130 + 120 * f));
        // 顶灯
        Cubes.box(vc, mat, 0.46f, 0.72f, 0.46f, 0.54f, 0.74f, 0.54f,
                220, 230, 255, (int) (120 + 120 * f));
    }
}
