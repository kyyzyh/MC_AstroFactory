package com.xiaoyi.astrofactory.weapon;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.level.PlayerLevel;

import java.util.LinkedHashMap;

/**
 * 武器系统：工业工具当武器用。
 *
 * <p>0.1.0 范围内做 3 把近战武器 + 1 把远程，全部是「工业工具」形态，
 * 不引入魔法/剑 ——《异星工厂》没有剑，只有工具。</p>
 *
 * <p>武器等级门槛：高等级武器需要玩家达到对应等级才能拿，
 * 这是「角色成长」与「武器系统」的耦合点。
 * 门槛用 {@link #requiredLevel(String)} 查，实际禁用在
 * {@link com.xiaoyi.astrofactory.item.WeaponItem} 的 use 里做（防止低级玩家切高级武器）。</p>
 *
 * <p>数值（灰区，可在规格范围内单独说明后调整）：
 *   - 铁扳手：攻击 3，速度 -3.2，等级 1（出生即用）
 *   - 钢扳手：攻击 5，速度 -3.1，等级 5
 *   - 动力拳套：攻击 7，速度 -3.0，等级 10
 *   - 铆钉枪：远程，射速 1.0，等级 15
 * </p>
 */
public final class Weapons {
    private Weapons() {}

    /** 武器定义。 */
    public record Weapon(
            String id,
            String displayKey,
            String descriptionKey,
            double attackDamage,
            float attackSpeed,
            int requiredLevel,
            Type type
    ) {
        public enum Type { MELEE, RANGED }
    }

    private static final java.util.Map<String, Weapon> BY_ID = new LinkedHashMap<>();

    static {
        add(new Weapon("iron_wrench", "weapon.astrofactory.iron_wrench",
                "weapon.astrofactory.iron_wrench.desc", 3.0, -3.2f, 1, Weapon.Type.MELEE));
        add(new Weapon("steel_wrench", "weapon.astrofactory.steel_wrench",
                "weapon.astrofactory.steel_wrench.desc", 5.0, -3.1f, 5, Weapon.Type.MELEE));
        add(new Weapon("power_gauntlet", "weapon.astrofactory.power_gauntlet",
                "weapon.astrofactory.power_gauntlet.desc", 7.0, -3.0f, 10, Weapon.Type.MELEE));
        add(new Weapon("rivet_gun", "weapon.astrofactory.rivet_gun",
                "weapon.astrofactory.rivet_gun.desc", 2.0, -2.8f, 15, Weapon.Type.RANGED));
    }

    private static void add(Weapon w) {
        BY_ID.put(w.id(), w);
    }

    public static Weapon get(String id) {
        return BY_ID.get(id);
    }

    public static java.util.List<Weapon> all() {
        return new java.util.ArrayList<>(BY_ID.values());
    }

    /** 武器所需等级（灰区，可单独说明后调）。 */
    public static int requiredLevel(String id) {
        Weapon w = get(id);
        return w == null ? 1 : w.requiredLevel();
    }

    /** 玩家是否达到拿该武器的等级。 */
    public static boolean meetsRequirement(net.minecraft.world.entity.player.Player player, String id) {
        return PlayerLevel.of(player).level >= requiredLevel(id);
    }

    /** 构建近战武器的属性组件（攻击伤害 + 攻击速度）。 */
    public static ItemAttributeModifiers meleeAttributes(double damage, float speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
