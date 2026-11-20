package com.xiaoyi.astrofactory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.tutorial.TutorialProgress;

import java.util.ArrayList;
import java.util.List;

/**
 * 教程世界线框高亮（规格 §5.7：<b>用世界线框，不用粒子</b>）。
 *
 * <h2>用的 API</h2>
 * <p>26.3 有官方的 gizmo 系统：{@code Minecraft.collectPerTickGizmos()} 返回一个
 * {@code AutoCloseable} 的临时收集器，在作用域内调 {@link Gizmos#cuboid} 画出的
 * 线框会被引擎在帧末统一渲染。这是原版 {@code GameTestBlockHighlightRenderer}
 * 的同一套机制，不是自造的渲染管线。</p>
 *
 * <p>必须在 tick 里收集（{@code ClientTickEvent}），不能在渲染线程收集 ——
 * 收集器是 ThreadLocal 的，跨线程会抛 IllegalStateException。</p>
 *
 * <h2>高亮哪一格</h2>
 * <p>高亮「玩家周围该类型方块中最近的一个」。这样不用猜玩家想放哪，
 * 而是指出现有的机器在哪 —— 对「去给采矿机加燃料」这种步骤更有意义。
 * 附近没有该方块时退回「玩家脚下前方一格」，表示「这里可以放」。</p>
 */
@EventBusSubscriber(modid = Astrofactory.MODID, value = Dist.CLIENT)
public final class TutorialHighlight {
    private TutorialHighlight() {}

    /** 搜索半径（格）。太大要遍历很多方块，太小远处的机器看不到。 */
    private static final int SEARCH_RADIUS = 24;
    /** 线框颜色：青色，与任务卡边框一致。 */
    private static final int COLOR = 0xFF4FC3F7;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        Block target = TutorialProgress.highlightBlock(mc.player);
        if (target == null) {
            return;
        }

        List<BlockPos> found = findNearby(level, mc.player.blockPosition(), target);
        try (var scope = mc.collectPerTickGizmos()) {
            GizmoStyle style = GizmoStyle.stroke(COLOR);
            if (found.isEmpty()) {
                // 附近没有 → 高亮玩家脚下前方一格，提示「这里可以放」
                BlockPos hint = mc.player.blockPosition()
                        .relative(mc.player.getDirection());
                Gizmos.cuboid(new AABB(hint).inflate(0.002), style);
            } else {
                // 最多高亮 3 个，避免一堆线框糊住视野
                for (int i = 0; i < Math.min(3, found.size()); i++) {
                    Gizmos.cuboid(new AABB(found.get(i)).inflate(0.002), style);
                }
            }
        }
    }

    /**
     * 在半径内找目标方块，按距离排序。
     *
     * <p>遍历的是方块坐标而不是区块，因为半径只有 24 格（最多约 12 万次
     * {@code getBlockState}）。这是客户端 tick 里可接受的开销，且只在有
     * 高亮目标时才跑。真要优化可以缓存，但 0.1.0 没必要。</p>
     */
    private static List<BlockPos> findNearby(ClientLevel level, BlockPos center, Block target) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = SEARCH_RADIUS;
        // 先找近的：按切比雪夫距离逐层外扩，凑够 3 个就停
        for (int d = 0; d <= r && out.size() < 3; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dy = -d; dy <= d; dy++) {
                    for (int dz = -d; dz <= d; dz++) {
                        // 只看这一层的壳
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != d) {
                            continue;
                        }
                        p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (level.isOutsideBuildHeight(p)) {
                            continue;
                        }
                        if (level.getBlockState(p).getBlock() == target) {
                            out.add(p.immutable());
                            if (out.size() >= 3) {
                                return out;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }
}
