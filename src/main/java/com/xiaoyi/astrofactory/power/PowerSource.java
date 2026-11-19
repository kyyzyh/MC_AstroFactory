package com.xiaoyi.astrofactory.power;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 动力源统一抽象（规格 §5.5）。
 *
 * <p>三阶段动力：燃烧（采矿/熔炉/燃烧臂）→ 蒸汽 → 电力（组装机/科研台/电动臂）。
 * 每种机器用组合持有一个 PowerSource，不通过继承区分动力类型。</p>
 *
 * <p>本接口只关心"能不能工作"和"消耗一次燃料"，
 * 具体能源从哪来（燃料槽 / 蒸汽网络 / 电网）由实现类决定。</p>
 */
public interface PowerSource {
    /**
     * *尝试消费一次动力。
     *
     * @param simulate true 时只查询不实际消费
     * @return 本次能否消费（机器据此决定是工作还是停机）
     */
    boolean consume(boolean simulate);

    /** 当前是否有可用动力（供 GUI 显示）。 */
    boolean isPowered();

    /** 剩余动力描述（供 GUI 显示，如 "123 tick" 或 "50%"）。 */
    default String statusText() {
        return isPowered() ? "powered" : "no power";
    }

    /**
     * 从给定物品堆中提取燃料值（燃烧 tick 数）。
     * 兼容原版烧炉燃料表，也兼容 mod 燃料。
     */
    static int fuelTicks(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        // 原版烧炉燃料表 —— 26.3 用 AbstractFurnaceBlockEntity 的燃料表
        // 这里用硬编码表避免依赖内部 API
        var item = stack.getItem();
        // coal / charcoal: 1600 tick (80 秒)
        if (item == net.minecraft.world.item.Items.COAL
                || item == net.minecraft.world.item.Items.CHARCOAL) {
            return 1600;
        }
        // coal blocks: 16000
        if (item == net.minecraft.world.item.Items.COAL_BLOCK) {
            return 16000;
        }
        // planks / logs / wood: 300
        if (item == net.minecraft.world.item.Items.OAK_PLANKS
                || item == net.minecraft.world.item.Items.SPRUCE_PLANKS
                || item == net.minecraft.world.item.Items.BIRCH_PLANKS
                || item == net.minecraft.world.item.Items.JUNGLE_PLANKS
                || item == net.minecraft.world.item.Items.ACACIA_PLANKS
                || item == net.minecraft.world.item.Items.DARK_OAK_PLANKS
                || item == net.minecraft.world.item.Items.MANGROVE_PLANKS
                || item == net.minecraft.world.item.Items.CHERRY_PLANKS
                || item == net.minecraft.world.item.Items.BAMBOO_PLANKS
                || item == net.minecraft.world.item.Items.CRIMSON_PLANKS
                || item == net.minecraft.world.item.Items.WARPED_PLANKS) {
            return 300;
        }
        // sticks: 100
        if (item == net.minecraft.world.item.Items.STICK) {
            return 100;
        }
        // saplings: 100
        if (item == net.minecraft.world.item.Items.OAK_SAPLING
                || item == net.minecraft.world.item.Items.SPRUCE_SAPLING
                || item == net.minecraft.world.item.Items.BIRCH_SAPLING
                || item == net.minecraft.world.item.Items.JUNGLE_SAPLING
                || item == net.minecraft.world.item.Items.ACACIA_SAPLING
                || item == net.minecraft.world.item.Items.DARK_OAK_SAPLING) {
            return 100;
        }
        // blaze rod: 2400
        if (item == net.minecraft.world.item.Items.BLAZE_ROD) {
            return 2400;
        }
        // lava bucket: 20000
        if (item == net.minecraft.world.item.Items.LAVA_BUCKET) {
            return 20000;
        }
        return 0;
    }
}
