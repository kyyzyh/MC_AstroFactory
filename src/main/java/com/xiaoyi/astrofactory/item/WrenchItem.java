package com.xiaoyi.astrofactory.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.xiaoyi.astrofactory.block.BurnerInserterBlockEntity;
import com.xiaoyi.astrofactory.machine.AbstractMachineEntity;
import com.xiaoyi.astrofactory.multiblock.MultiBlockMachineBlock;
import com.xiaoyi.astrofactory.multiblock.MultiBlockHelper;

/**
 * 扳手：诊断工具。
 *
 * <h2>为什么扳手是「诊断工具」而不是「拆卸工具」</h2>
 * <p>很多模组的扳手是「右键旋转方块 / 潜行右键拆卸」。但对一个<b>新手向</b>
 * 工厂模组，最有价值的右键动作是<b>问「你为什么不动」</b>。</p>
 *
 * <p>Pipez 的扳手右键看管道内容、AE2 的网络工具右键看频道占用、
 * EnderIO 的导管探测器右键看吞吐速率 —— 这是物流类模组里
 * <b>单位工作量收益最高</b>的功能，因为 90% 的「不工作」求助帖
 * 都能被自助解决。</p>
 *
 * <h2>输出什么</h2>
 * <ul>
 *   <li><b>多方块机器</b>：尺寸、是否通电、当前状态、缺什么材料；</li>
 *   <li><b>机械臂</b>：当前状态、过滤规则、数量档、Keep N、手上拿着什么；</li>
 *   <li><b>其他</b>：一句话说明「这不是本模组的机器」。</li>
 * </ul>
 *
 * <p>全部走 {@code player.sendSystemMessage}，不刷屏（每次右键只发 3-5 行）。</p>
 */
public final class WrenchItem {
    private WrenchItem() {}

    /**
     * 对指定方块做一次诊断。
     *
     * @return 是否认出了这是本模组的机器（true = 已输出诊断信息）
     */
    public static boolean tryDiagnose(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return true; // 客户端也吃掉这次右键，避免二次触发
        }

        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);

        // 多方块：点到 dummy 也要能定位 master
        BlockPos masterPos = clicked;
        if (state.getBlock() instanceof MultiBlockMachineBlock mb) {
            masterPos = MultiBlockHelper.masterOf(clicked, state.getValue(MultiBlockMachineBlock.FACING),
                    mb.sizeX(), mb.sizeZ(), state.getValue(mb.partProperty()));
        }

        BlockEntity be = level.getBlockEntity(masterPos);
        if (be instanceof AbstractMachineEntity machine) {
            diagnoseMachine(sp, level, masterPos, state, machine);
            return true;
        }
        if (be instanceof BurnerInserterBlockEntity arm) {
            diagnoseArm(sp, masterPos, arm);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 多方块机器

    private static void diagnoseMachine(ServerPlayer sp, Level level, BlockPos pos, BlockState state,
                                 AbstractMachineEntity machine) {
        String machineName = state.getBlock().getDescriptionId();

        // 1) 尺寸
        if (state.getBlock() instanceof MultiBlockMachineBlock mb) {
            int cells = mb.sizeX() * mb.sizeZ();
            sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.mb_size",
                    Component.translatable(machineName), mb.sizeX(), mb.sizeZ(), cells));
        } else {
            sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.machine",
                    Component.translatable(machineName)));
        }

        // 2) 电力/燃料状态
        sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.status",
                machine.getPowerStatusText()));

        // 3) 运行状态
        boolean active = isActive(level, pos, state);
        sp.sendSystemMessage(Component.translatable(active
                ? "msg.astrofactory.wrench.running"
                : "msg.astrofactory.wrench.stopped"));

        // 4) 缺什么（具体到物品）
        String missing = machine.describeMissingInput();
        if (missing != null && !missing.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.missing",
                    Component.translatable(missing)));
        }
    }

    private static boolean isActive(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof MultiBlockMachineBlock mb) {
            var prop = mb.activeProperty();
            if (prop != null && state.hasProperty(prop)) {
                return state.getValue(prop);
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 机械臂

    private static void diagnoseArm(ServerPlayer sp, BlockPos pos, BurnerInserterBlockEntity arm) {
        sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.arm_status",
                Component.translatable("gui.astrofactory.inserter.reason."
                        + arm.getStatus().name().toLowerCase())));

        sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.arm_filter",
                Component.translatable(arm.isWhitelist()
                        ? "gui.astrofactory.inserter.filter.whitelist"
                        : "gui.astrofactory.inserter.filter.blacklist"),
                arm.filterIsEmpty()
                        ? Component.translatable("msg.astrofactory.wrench.arm_filter_none")
                        : Component.literal(countFilter(arm) + "")));

        sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.arm_limit",
                arm.getMoveLimit(), arm.getKeepN()));

        var held = arm.getHeld();
        if (!held.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("msg.astrofactory.wrench.arm_held",
                    held.getHoverName(), held.getCount()));
        }
    }

    private static int countFilter(BurnerInserterBlockEntity arm) {
        int n = 0;
        for (int i = BurnerInserterBlockEntity.FILTER_START;
             i < BurnerInserterBlockEntity.FILTER_START + BurnerInserterBlockEntity.FILTER_SLOTS; i++) {
            if (!arm.getContainer().getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }
}
