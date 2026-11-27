package com.astrofactory.jei;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 实验室研究消耗条目（US-107，纯展示用，非真实配方）。
 *
 * <p>实验室不参与 {@code RecipeManager} 配方——它每 60 tick 消耗 1 个红瓶给当前研究
 * 加 10 点。为了让 JEI 能展示「研究要多少红瓶」，这里用轻量条目描述一条研究的消耗：
 * {@code inputs} = 需要的科技包（红瓶），{@code result} = 研究进度里程碑。</p>
 *
 * <p>本卡（US-107）只展示占位研究（每项研究成本红瓶数来自数据驱动科技定义），
 * 供玩家在 JEI 里快速查看「我该备多少红瓶」。</p>
 */
public record LabResearch(String techId, List<ItemStack> inputs, ItemStack result) {
}
