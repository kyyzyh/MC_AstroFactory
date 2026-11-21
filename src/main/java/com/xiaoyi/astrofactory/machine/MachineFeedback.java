package com.xiaoyi.astrofactory.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * 机器反馈：音效 + 粒子。
 *
 * <h2>为什么全部复用原版音效/粒子</h2>
 * <p>自制 {@code .ogg} 需要音频资产 + {@code sounds.json} + 授权问题，
 * 而原版 {@link SoundEvents} 和 {@link ParticleTypes} 已经覆盖了工业场景
 * 需要的全部声音与视觉。IE 的粉碎机就是给「正在加工的物品」喷
 * {@code ItemParticleOption}，玩家一眼知道机器在吃铁矿 —— 零资产。</p>
 *
 * <h2>为什么粒子用概率</h2>
 * <p>每 tick 都喷会让画面糊成一片，也让服务端每 tick 发包。
 * IE 的电弧炉用 {@code 1/6} 概率，看起来是「偶发火花」而不是「消防栓」。</p>
 *
 * <h2>为什么用 addAlwaysVisibleParticle</h2>
 * <p>工厂玩家经常站很远看全景，默认粒子在 32 格外就不渲染了。
 * {@code addAlwaysVisibleParticle} 让远距离也能看到机器在冒烟 —— 这正是
 * 「扫一眼知道产线在跑」的关键。</p>
 */
public final class MachineFeedback {
    private MachineFeedback() {}

    /** 粒子出现概率（1/N 每 tick）。 */
    private static final int PARTICLE_CHANCE = 6;

    /**
     * 机器启动时的一声闷响。
     *
     * <p>用 {@code BLOCK_ANVIL_LAND} 降调：铁块落地的声音天然带「重工业」感，
     * 降调后像大型机械合闸。</p>
     */
    public static void playStart(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35f, 0.7f);
    }

    /** 机器停机时的轻响（比启动轻，避免烦躁）。 */
    public static void playStop(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.18f, 0.5f);
    }

    /** 一次产出成功的反馈音。 */
    public static void playCraft(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, 1.4f);
    }

    /**
     * 加工中偶发粒子。
     *
     * @param particle 粒子类型（熔炉用 {@code CAMPFIRE_COSY_SMOKE}，
     *                 组装机用 {@code CRIT}，钢熔炉用 {@code LAVA}）
     */
    public static void tickParticles(Level level, BlockPos pos, ParticleOptions particle) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        RandomSource rand = level.getRandom();
        if (rand.nextInt(PARTICLE_CHANCE) != 0) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.9;
        double z = pos.getZ() + 0.5;
        // 水平轻微散开，垂直向上飘
        server.sendParticles(particle, x, y, z, 1, 0.22, 0.05, 0.22, 0.01);
    }

    /**
     * 「这台机器吃的是什么」—— 在输入端喷出物品碎屑。
     *
     * <p>IE 粉碎机的绝招：粒子携带物品图标，玩家看到铁矿碎屑飞出来，
     * 立刻明白这台机器在处理什么。比任何 GUI 文字都直观。</p>
     */
    public static void inputPuff(Level level, BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) {
            return;
        }
        var option = new net.minecraft.core.particles.ItemParticleOption(
                ParticleTypes.ITEM,
                net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1)));
        server.sendParticles(option,
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                6, 0.15, 0.15, 0.15, 0.02);
    }
}
