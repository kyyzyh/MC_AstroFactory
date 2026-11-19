package com.xiaoyi.astrofactory.recipe;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一机器配方（熔炼 / 制作 / 组装共用一套模型）。
 *
 * <p><b>为什么存 {@link Item} 而不是 {@link ItemStack}：</b>
 * 数据包重载（含启动期首次加载）发生在物品数据组件绑定之前，
 * 此时构造 ItemStack 会抛 {@code NullPointerException: Components not bound yet}。
 * 因此配方只保存物品与数量，实际 ItemStack 在查询时按需生成。</p>
 *
 * @param id     配方标识，内置配方用 {@code astrofactory:builtin/xxx}，数据包配方用文件路径
 * @param type   机器类型
 * @param inputs 输入：单输入类型长度为 1；组装类型长度为 9（3x3 行优先，null 表示该格不放）
 * @param result 产物物品
 * @param count  产物数量
 * @param ticks  耗时（tick）
 */
public record MachineRecipe(Identifier id, MachineType type, List<Ingredient> inputs,
                            Item result, int count, int ticks) {

    /** 单个输入格：物品 + 需求量。 */
    public record Ingredient(Item item, int count) {
        public ItemStack toStack() {
            return new ItemStack(item, count);
        }
    }

    public MachineRecipe {
        // 组装网格允许 null 表示"该格不放东西"，因此不能用 List.copyOf（会拒绝 null）
        inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
    }

    /** 产物的 ItemStack 副本（每次新建，调用方可随意修改）。 */
    public ItemStack resultStack() {
        return new ItemStack(result, count);
    }

    /** 单输入匹配：物品相同且数量足够。 */
    public boolean matchesSingle(ItemStack actual) {
        if (actual.isEmpty() || inputs.isEmpty()) {
            return false;
        }
        Ingredient need = inputs.get(0);
        if (need == null) {
            return false;
        }
        return actual.getItem() == need.item() && actual.getCount() >= need.count();
    }

    /**
     * 3x3 网格匹配。
     * 配方要求为空的格子不限制实际内容（宽松摆放），非空格必须物品相同且数量足够。
     */
    public boolean matchesGrid(List<ItemStack> actual) {
        if (actual.size() < inputs.size()) {
            return false;
        }
        for (int i = 0; i < inputs.size(); i++) {
            Ingredient need = inputs.get(i);
            if (need == null) {
                continue;
            }
            ItemStack got = actual.get(i);
            if (got.isEmpty() || got.getItem() != need.item() || got.getCount() < need.count()) {
                return false;
            }
        }
        return true;
    }

    /** 产物能否放进给定输出槽。 */
    public boolean fitsInto(ItemStack outputSlot) {
        if (outputSlot.isEmpty()) {
            return true;
        }
        if (outputSlot.getItem() != result) {
            return false;
        }
        return outputSlot.getCount() + count <= outputSlot.getMaxStackSize();
    }
}
