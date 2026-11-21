package com.xiaoyi.astrofactory.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.xiaoyi.astrofactory.world.OreVeinData;

import java.util.List;

/**
 * 探矿仪：右键报告最近矿田的方向、距离和大致矿种。
 *
 * <h2>为什么需要这个物品</h2>
 * <p>0.1.0 把矿田扩到了全世界（{@code OreVeinData#tryGenerateForChunk}），
 * 但「全世界有矿」和「玩家找得到矿」是两件事。没有探矿仪，玩家只能盲挖，
 * 那和「矿只够用十分钟」没有体验差别。探矿仪把「扩张」变成可执行的动作。</p>
 *
 * <h2>为什么查 chunk 列表而不是扫方块</h2>
 * <p>扫方块要遍历方圆几百格的所有方块，一次右键就是几百万次 getBlockState，
 * 服务器会卡。{@link OreVeinData} 本来就记录了每个矿田的 chunk 坐标
 * （幂等机制需要），直接查列表是常数级开销。</p>
 *
 * <h2>为什么给方向而不是给坐标</h2>
 * <p>给坐标等于把探索变成照抄。给「东北 120 格」保留走路和发现的过程，
 * 又不会让人完全瞎找 —— 这是「先撞墙再给梯子」的梯子。</p>
 */
public class ProspectorItem extends Item {
    /** 最大报告距离（chunk）：12 chunk = 192 格。 */
    private static final int MAX_RANGE_CHUNKS = 12;
    /** 最多报告几个矿田。 */
    private static final int MAX_REPORT = 3;

    public ProspectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            // 客户端返回 SUCCESS 让挥手动画正常播放
            return InteractionResult.SUCCESS;
        }

        // 右键有 4 tick 冷却，防止连点刷屏
        if (sp.getCooldowns().isOnCooldown(this.getDefaultInstance())) {
            return InteractionResult.SUCCESS;
        }
        sp.getCooldowns().addCooldown(this.getDefaultInstance(), 20);

        ChunkPos from = ChunkPos.containing(sp.blockPosition());
        var data = serverLevel.getDataStorage().computeIfAbsent(OreVeinData.TYPE);
        ChunkPos spawnChunk = ChunkPos.containing(
                serverLevel.getLevelData().getRespawnData().pos());

        // 用「预测」而不是「已生成列表」：玩家站在没探索过的区域时，
        // 已生成列表是空的，只有预测才能指出矿在哪。
        List<ChunkPos> near = data.predictVeins(from, spawnChunk, MAX_REPORT,
                MAX_RANGE_CHUNKS, serverLevel.getSeed());

        if (near.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("msg.astrofactory.prospector.none",
                    MAX_RANGE_CHUNKS * 16).withStyle(ChatFormatting.GRAY));
            sp.sendSystemMessage(Component.translatable("msg.astrofactory.prospector.hint",
                    OreVeinData.scatterOneIn()).withStyle(ChatFormatting.DARK_GRAY));
            return InteractionResult.SUCCESS;
        }

        sp.sendSystemMessage(Component.translatable("msg.astrofactory.prospector.header",
                near.size()).withStyle(ChatFormatting.GOLD));
        for (ChunkPos chunk : near) {
            sp.sendSystemMessage(describe(sp.blockPosition(), chunk, data.isPlaced(chunk)));
        }
        return InteractionResult.SUCCESS;
    }

    /** 把「矿田 chunk」转成「方位 + 距离」一行。 */
    private static Component describe(BlockPos from, ChunkPos chunk, boolean visited) {
        // 矿田中心（OreVeinData 用 chunk 中心放矿）
        int tx = chunk.getMiddleBlockX();
        int tz = chunk.getMiddleBlockZ();
        int dx = tx - from.getX();
        int dz = tz - from.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));

        Component line = Component.translatable("msg.astrofactory.prospector.line",
                Component.translatable(directionKey(dx, dz)).withStyle(ChatFormatting.AQUA),
                dist);
        if (!visited) {
            // 还没走到过的矿田：提醒它是预测，走过去才会真正生成
            line = line.copy().append(Component.translatable("msg.astrofactory.prospector.unvisited")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    /**
     * 8 方位（按 Minecraft 的朝向习惯：北 = -Z，东 = +X）。
     *
     * <p>用 8 方位而不是 4 方位：4 方位下「东北 30 格」会被说成「东 30 格」，
     * 玩家走过去偏 30 格，矿田半径才 5 格，等于白跑。</p>
     */
    private static String directionKey(int dx, int dz) {
        // 先按角度分 8 份。atan2(dx, -dz) 让 -Z 成为 0 度（北）
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) {
            deg += 360;
        }
        int sector = (int) Math.round(deg / 45.0) % 8;
        return switch (sector) {
            case 0 -> "dir.astrofactory.north";
            case 1 -> "dir.astrofactory.northeast";
            case 2 -> "dir.astrofactory.east";
            case 3 -> "dir.astrofactory.southeast";
            case 4 -> "dir.astrofactory.south";
            case 5 -> "dir.astrofactory.southwest";
            case 6 -> "dir.astrofactory.west";
            default -> "dir.astrofactory.northwest";
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return use(player.level(), player, context.getHand());
    }
}
