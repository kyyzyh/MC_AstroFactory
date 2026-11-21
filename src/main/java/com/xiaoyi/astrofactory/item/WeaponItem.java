package com.xiaoyi.astrofactory.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.xiaoyi.astrofactory.weapon.Weapons;

/**
 * 武器物品：近战/远程统一实现。
 *
 * <p>0.1.0：近战武器靠属性组件给攻击伤害，hurtEnemy 不额外处理
 * （与原版剑一致）；远程武器（铆钉枪）在 use 里做最小实现 ——
 * 射出一颗「铆钉弹」实体。0.1.0 范围内不做弹道物理，
 * 命中即判定，距离 8 格直线。</p>
 *
 * <p>等级门槛：玩家未达到 {@link Weapons#requiredLevel(String)} 时，
 * use 返回 PASS 并给出提示，防止低级玩家切高级武器。</p>
 */
public class WeaponItem extends Item {
    private final String weaponId;

    public WeaponItem(Properties properties, String weaponId, ItemAttributeModifiers attributes) {
        super(properties.attributes(attributes));
        this.weaponId = weaponId;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (!Weapons.meetsRequirement(sp, weaponId)) {
            sp.sendSystemMessage(Component.translatable(
                    "weapon.astrofactory.level_too_low",
                    Weapons.requiredLevel(weaponId)));
            return InteractionResult.FAIL;
        }
        Weapons.Weapon w = Weapons.get(weaponId);
        if (w != null && w.type() == Weapons.Weapon.Type.RANGED) {
            fireRivet(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // 扳手类武器对本模组机器右键 = 诊断（不是攻击）。
        // 理由：扳手的语义是「工具」，玩家拿它对着机器右键时想的是
        // 「这机器怎么了」，不是「打它」。这与 Pipez/EnderIO 的扳手一致。
        if (isWrench() && !player.isShiftKeyDown()
                && WrenchItem.tryDiagnose(context)) {
            return InteractionResult.CONSUME;
        }
        return use(player.level(), player, context.getHand());
    }

    /** 名字里带 wrench 的算扳手（iron_wrench / steel_wrench）。 */
    private boolean isWrench() {
        return weaponId.endsWith("_wrench");
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
        // 近战伤害由属性组件处理，这里不额外消耗（0.1.0：无限用，不加耐久）
    }

    /** 最小远程实现：8 格内找最近生物命中（0.1.0 不做弹道实体）。 */
    private void fireRivet(ServerPlayer sp) {
        var level = sp.level();
        var candidates = level.getEntities(sp, sp.getBoundingBox().inflate(8.0),
                e -> e instanceof LivingEntity && e != sp);
        double best = Double.MAX_VALUE;
        LivingEntity target = null;
        for (var e : candidates) {
            double d = e.distanceToSqr(sp);
            if (d < best) {
                best = d;
                target = (LivingEntity) e;
            }
        }
        if (target != null && best <= 64.0) { // 8 格 = 64 平方格
            target.hurt(level.damageSources().playerAttack(sp),
                    (float) Weapons.get(weaponId).attackDamage());
        }
        sp.sendSystemMessage(Component.translatable("weapon.astrofactory.rivet_gun.fire"));
    }
}
