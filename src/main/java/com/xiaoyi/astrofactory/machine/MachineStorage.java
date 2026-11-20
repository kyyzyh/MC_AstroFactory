package com.xiaoyi.astrofactory.machine;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 机器容器存档工具：统一走官方 {@link ContainerHelper}，并兼容 0.1.0 早期的旧格式。
 *
 * <p><b>为什么需要兼容</b>：0.1.0 早期把物品按「顺序列表」写进 {@code Items}
 * （{@code output.list("Items", ItemStack.CODEC)}），官方
 * {@link ContainerHelper#saveAllItems} 写的是「带槽位号」的
 * {@link net.minecraft.world.ItemStackWithSlot}。两种格式不兼容：直接换会让旧存档的
 * 物品全部落到槽位 0 而丢失。这里读档时探测一次字段名，决定走哪条路径。</p>
 *
 * <p>写出统一用官方格式（新存档都是标准格式），旧存档只在第一次读取时按顺序还原，
 * 之后随正常保存升级为新格式。</p>
 */
public final class MachineStorage {
    private MachineStorage() {}

    /** 官方格式写出（空槽不写，避免 ItemStack.CODEC 拒绝 air）。 */
    public static void saveItems(ValueOutput output, NonNullList<ItemStack> items) {
        ContainerHelper.saveAllItems(output, items, false);
    }

    /** 读取：自动识别新（带 Slot）/ 旧（按顺序）两种格式。 */
    public static void loadItems(ValueInput input, NonNullList<ItemStack> items) {
        items.clear();
        if (usesSlotField(input)) {
            ContainerHelper.loadAllItems(input, items);
        } else {
            loadLegacyOrdered(input, items);
        }
    }

    /**
     * 探测 {@code Items} 的元素里是否带 {@code Slot} 字段。
     *
     * <p>无 {@code Items} 节点时返回 {@code true}（走官方路径，结果同样是全空，无副作用）。</p>
     */
    private static boolean usesSlotField(ValueInput input) {
        var listOpt = input.childrenList("Items");
        if (listOpt.isEmpty()) {
            return true;
        }
        for (ValueInput child : listOpt.get()) {
            // 只需要看第一个元素即可判定格式
            return child.getInt("Slot").isPresent();
        }
        return true;
    }

    /** 旧格式：{@code Items} 是按槽位顺序排列的 ItemStack 列表（与旧写出路径对称）。 */
    private static void loadLegacyOrdered(ValueInput input, NonNullList<ItemStack> items) {
        int slot = 0;
        for (ItemStack stack : input.listOrEmpty("Items", ItemStack.CODEC)) {
            if (slot >= items.size()) {
                break;
            }
            if (!stack.isEmpty()) {
                items.set(slot++, stack);
            }
        }
    }
}
