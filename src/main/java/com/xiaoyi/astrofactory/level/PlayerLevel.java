package com.xiaoyi.astrofactory.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.bus.api.IEventBus;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.research.ResearchProgress;
import com.xiaoyi.astrofactory.research.Technologies;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 角色成长：玩家等级 + 经验值（EXP）。
 *
 * <p>设计目标：把「自动化产线规模」映射为「角色成长」，让玩家在搭产线的
 * 过程中有可见的数值反馈。经验来源全部是模组内的产出动作，不靠杀怪
 * —— 与《异星工厂》的「工厂就是角色」一致。</p>
 *
 * <p>升级规则：每级所需 EXP = 10 × level（1 级→10，2 级→20，3 级→30…）。
 * 这是灰区数值，可在规格范围内单独说明后调整。</p>
 *
 * <p>等级作用（0.1.0 范围内）：仅做数值反馈 + 解锁武器等级门槛。
 * 不加属性、不加血量 —— MC 的属性系统不是本模组的范围。</p>
 */
public final class PlayerLevel {
    private PlayerLevel() {}

    /** 成长数据载体，持久化在玩家身上（跨死亡保留）。 */
    public static final class Data {
        public static final com.mojang.serialization.MapCodec<Data> MAP_CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.optionalFieldOf("level", 1).forGetter(d -> d.level),
                        Codec.INT.optionalFieldOf("exp", 0).forGetter(d -> d.exp),
                        Codec.INT.optionalFieldOf("total_mined", 0).forGetter(d -> d.totalMined),
                        Codec.INT.optionalFieldOf("total_crafted", 0).forGetter(d -> d.totalCrafted),
                        Codec.STRING.listOf().optionalFieldOf("milestones", java.util.List.of())
                                .forGetter(d -> new java.util.ArrayList<>(d.milestones))
                ).apply(instance, (level, exp, mined, crafted, ms) -> {
                    Data d = new Data();
                    d.level = level;
                    d.exp = exp;
                    d.totalMined = mined;
                    d.totalCrafted = crafted;
                    d.milestones.addAll(ms);
                    return d;
                }));

        /** 当前等级（≥1）。 */
        public int level = 1;
        /** 当前经验值。 */
        public int exp = 0;
        /** 累计采矿数（经验来源之一）。 */
        public int totalMined = 0;
        /** 累计制作数（经验来源之一）。 */
        public int totalCrafted = 0;
        /** 已达成里程碑 id（防重复发奖励）。 */
        public final Set<String> milestones = new HashSet<>();

        /** 升到下一级所需 EXP。 */
        public int expToNext() {
            return EXP_PER_LEVEL * level;
        }

        /** 是否满级（0.1.0 上限 20 级）。 */
        public boolean isMaxLevel() {
            return level >= MAX_LEVEL;
        }

        /**
         * 增加经验并处理升级，返回本次升级的次数（0 表示没升级）。
         * 调用方负责把「加了多少 exp」传进来。
         */
        public int addExp(int amount) {
            if (amount <= 0 || isMaxLevel()) {
                return 0;
            }
            exp += amount;
            int ups = 0;
            while (!isMaxLevel() && exp >= expToNext()) {
                exp -= expToNext();
                level++;
                ups++;
            }
            return ups;
        }

        /** 经验进度条百分比（0-100）。 */
        public int expPercent() {
            int need = expToNext();
            if (need <= 0) {
                return 100;
            }
            return Math.min(100, exp * 100 / need);
        }
    }

    /** 0.1.0 等级上限（灰区，可单独说明后调）。 */
    public static final int MAX_LEVEL = 20;

    /** 每级 EXP 系数（灰区，可单独说明后调）。 */
    public static final int EXP_PER_LEVEL = 10;

    /** 里程碑定义：id → {触发条件描述, 奖励等级加成}。 */
    public static final Map<String, Milestone> MILESTONES = new LinkedHashMap<>();

    /** 里程碑记录。 */
    public record Milestone(String id, String displayKey, int expReward) {}

    static {
        // milestone id 与语言文件 quest.astrofactory.milestone.<id> 对应
        MILESTONES.put("first_mine", new Milestone("first_mine", "quest.astrofactory.milestone.first_mine", 50));
        MILESTONES.put("first_tech", new Milestone("first_tech", "quest.astrofactory.milestone.first_tech", 100));
        MILESTONES.put("first_power", new Milestone("first_power", "quest.astrofactory.milestone.first_power", 150));
        MILESTONES.put("full_line", new Milestone("full_line", "quest.astrofactory.milestone.full_line", 300));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Astrofactory.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Data>> LEVEL =
            ATTACHMENT_TYPES.register("level",
                    () -> AttachmentType.builder(Data::new)
                            .serialize(Data.MAP_CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    public static Data of(net.minecraft.world.entity.player.Player player) {
        return player.getData(LEVEL);
    }

    /**
     * 触发里程碑：未达成过则发放 EXP 奖励，返回是否本次达成。
     * 调用方负责判定触发条件（如「首次研究科技」）。
     */
    public static boolean awardMilestone(net.minecraft.world.entity.player.Player player, String id) {
        Data data = of(player);
        Milestone m = MILESTONES.get(id);
        if (m == null || data.milestones.contains(id)) {
            return false;
        }
        data.milestones.add(id);
        data.addExp(m.expReward());
        return true;
    }

    /** 已解锁科技数（供任务进度判定）。 */
    public static int unlockedTechCount(net.minecraft.world.entity.player.Player player) {
        return ResearchProgress.of(player).unlocked.size();
    }
}
