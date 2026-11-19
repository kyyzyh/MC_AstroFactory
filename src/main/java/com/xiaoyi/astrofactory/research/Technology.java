package com.xiaoyi.astrofactory.research;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 科技树节点定义（内置）。
 *
 * 异星工厂式科研：每个科技需要消耗一定数量的"科研包"，
 * 科研包由组装机产出；解锁后开启对应机器/配方。
 */
public record Technology(
        String id,
        String displayKey,
        List<String> prerequisites,
        int cost,
        List<String> unlocks) {

    public Identifier icon() {
        return Identifier.fromNamespaceAndPath("astrofactory", "textures/gui/tech/" + id + ".png");
    }
}
