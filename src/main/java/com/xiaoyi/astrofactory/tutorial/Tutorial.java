package com.xiaoyi.astrofactory.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.xiaoyi.astrofactory.Astrofactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新手教程：18 步任务清单（规格 §5.7）。
 *
 * <h2>形式（用户已确认）</h2>
 * <ul>
 *   <li>右上角<b>可折叠任务卡</b>，只显示当前这一步，不铺满屏幕</li>
 *   <li>目标方块用<b>世界线框</b>高亮（{@code Gizmos.cuboid}），<b>不用粒子</b></li>
 *   <li><b>不强制弹窗、不锁操作、不加遮罩</b> —— 玩家随时可以干别的</li>
 *   <li>完成一步发 EXP 奖励，走完 18 步给里程碑</li>
 * </ul>
 *
 * <h2>步骤顺序的硬约束（规格 §5.7，不许改）</h2>
 * <p>第 13–15 步必须<b>先让玩家放下组装机、发现它不转，然后才告诉他需要发电</b>。
 * 这个「先撞墙再给梯子」的顺序是教学设计的核心 —— 如果先讲电力再放组装机，
 * 玩家不知道电力是干什么用的。</p>
 *
 * <h2>状态存在哪</h2>
 * <p>玩家 attachment（{@link Data}），codec 序列化，{@code copyOnDeath()}。
 * 教程进度是玩家级而非世界级：换世界重来，死亡不掉。</p>
 */
public final class Tutorial {
    private Tutorial() {}

    /** 步骤定义。 */
    public record Step(
            /** 稳定 id，存档用，<b>不要改</b>。 */
            String id,
            /** 标题 lang key。 */
            String titleKey,
            /** 说明 lang key（告诉玩家具体做什么）。 */
            String descKey,
            /** 高亮目标：方块注册名（不含命名空间），null 表示不高亮。 */
            String highlightBlock,
            /** 完成时奖励 EXP。 */
            int expReward
    ) {}

    /** 18 步。顺序即教学顺序，不可重排（见类注释）。 */
    private static final List<Step> STEPS = new ArrayList<>();

    static {
        // 起点是「放第一台采矿机」（规格 §5.7）—— 开局包已发采矿机、熔炉、20 条
        // 传送带，所以前几步是「用起来」而不是「做出来」。

        // ---- 阶段 1：让采矿机跑起来（1-3）----
        add("place_miner", 20);
        add("miner_fuel", 20);
        add("first_ore", 25);

        // ---- 阶段 2：把矿运出来（4-5）----
        add("place_belt", 20);
        add("belt_deliver", 25);

        // ---- 阶段 3：熔炼（6-8）----
        add("place_furnace", 20);
        add("belt_to_furnace", 30);
        add("get_ingot", 30);

        // ---- 阶段 4：做出第一批零件（9-11）----
        add("craft_gear", 25);
        add("craft_inserter", 25);
        add("place_inserter", 25);

        // ---- 阶段 5：科研（12）----
        add("craft_lab", 30);

        // ---- 阶段 6：撞墙（13-15）★ 顺序不许改 ----
        // 13. 先让玩家做并放下组装机
        add("craft_assembler", 30);
        // 14. 组装机放下了才发现不转（此步不告诉原因）
        add("assembler_stuck", 20);
        // 15. 才告诉玩家：组装机是电动的，需要发电
        add("need_power", 30);

        // ---- 阶段 7：发电（16-17）----
        add("craft_boiler", 35);
        add("steam_running", 50);

        // ---- 阶段 8：自动化闭环（18）----
        add("auto_line", 100);
    }

    private static void add(String id, int exp) {
        String base = "tutorial.astrofactory." + id;
        STEPS.add(new Step(id, base + ".title", base + ".desc", null, exp));
    }

    /** 全部步骤（只读）。 */
    public static List<Step> steps() {
        return List.copyOf(STEPS);
    }

    public static int count() {
        return STEPS.size();
    }

    /** 按序号取步骤；越界返回最后一步（教程走完后的稳定态）。 */
    public static Step stepAt(int index) {
        if (STEPS.isEmpty()) {
            throw new IllegalStateException("tutorial steps not initialised");
        }
        int i = Math.max(0, Math.min(index, STEPS.size() - 1));
        return STEPS.get(i);
    }

    /** 按 id 取序号；找不到返回 -1。 */
    public static int indexOf(String id) {
        for (int i = 0; i < STEPS.size(); i++) {
            if (STEPS.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ 玩家进度

    /**
     * 教程进度（玩家 attachment）。
     *
     * <p>字段只加不改 —— 改名会让老存档读不出来（规格 §6 存档兼容）。</p>
     */
    public static final class Data {
        public static final MapCodec<Data> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("step", 0).forGetter(d -> d.step),
                Codec.BOOL.optionalFieldOf("done", false).forGetter(d -> d.done),
                Codec.BOOL.optionalFieldOf("collapsed", false).forGetter(d -> d.collapsed),
                // 已完成步骤 id：防止「回退重放」重复发奖励
                Codec.STRING.listOf().optionalFieldOf("completed", List.of())
                        .forGetter(d -> new ArrayList<>(d.completed))
        ).apply(instance, (step, done, collapsed, completed) -> {
            Data d = new Data();
            d.step = step;
            d.done = done;
            d.collapsed = collapsed;
            d.completed.addAll(completed);
            return d;
        }));

        /** 当前步骤序号（0 基）。 */
        public int step = 0;
        /** 18 步是否全部走完。 */
        public boolean done = false;
        /** 任务卡是否折叠（客户端按钮切换，同步回来）。 */
        public boolean collapsed = false;
        /** 已完成的步骤 id。 */
        public final List<String> completed = new ArrayList<>();

        /** 当前步骤；已完成时返回最后一步。 */
        public Step current() {
            return stepAt(step);
        }

        /** 进度文本用的已完成数（0..18）。 */
        public int completedCount() {
            return Math.min(completed.size(), STEPS.size());
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Astrofactory.MODID);

    /**
     * 同步编解码：只传 HUD 需要的字段，不传整个 completed 列表。
     *
     * <p>{@code AttachmentType.Builder.sync} 收的是 {@link StreamCodec}
     * （{@code (buf, value)} 编码 / {@code (buf)} 解码），不是两个方法引用。</p>
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, Data> SYNC_CODEC =
            StreamCodec.of(
                    (buf, data) -> {
                        buf.writeVarInt(data.step);
                        buf.writeBoolean(data.done);
                        buf.writeBoolean(data.collapsed);
                        buf.writeVarInt(data.completed.size());
                    },
                    buf -> {
                        Data d = new Data();
                        d.step = buf.readVarInt();
                        d.done = buf.readBoolean();
                        d.collapsed = buf.readBoolean();
                        int n = buf.readVarInt();
                        // 客户端只需要数量做进度显示；id 列表只有服务端有意义
                        for (int i = 0; i < n; i++) {
                            d.completed.add("__client__");
                        }
                        return d;
                    });

    /** 客户端要读 step/collapsed，所以必须 sync。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Data>> TUTORIAL =
            ATTACHMENT_TYPES.register("tutorial",
                    () -> AttachmentType.builder(Data::new)
                            .serialize(Data.MAP_CODEC)
                            .sync(SYNC_CODEC)
                            .copyOnDeath()
                            .build());


    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    /** 取玩家进度（不存在则创建默认值）。 */
    public static Data of(net.minecraft.world.entity.player.Player player) {
        return player.getData(TUTORIAL);
    }
}
