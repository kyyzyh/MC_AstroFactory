package com.astrofactory.tech;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 科技树全局进度（存档级）。
 *
 * <p>借鉴《异星工厂》的分时代推进：开局是「燃料时代」，机器烧木头和煤，
 * 运转偏慢；研究出「电力」后进入「电力时代」，可用扳手把机器升级为电动，
 * 速度更快且不再烧燃料。</p>
 *
 * <p>v0.4：支持等级科技（高效采矿 I-III）与多前置 DAG 判定。
 * 存为世界级 SavedData，而不是玩家级——工厂是共享基础设施，
 * 科技进度理应属于整个世界。</p>
 */
public class TechTreeData extends SavedData {

    public static final String DATA_NAME = "astrofactory_tech";

    /** 已解锁的科技 id → 已研究等级。 */
    private final Map<String, Integer> levels = new LinkedHashMap<>();

    /** 当前正在研究的科技 id（null 表示没有在研项目）。 */
    private String currentResearch = null;

    /** 当前在研科技已累积的科技点。 */
    private int researchProgress = 0;

    /** v0.6 触发型科技：物品 id → 已累计获得数（跨会话持久）。 */
    private final Map<String, Integer> triggerCounts = new LinkedHashMap<>();

    public TechTreeData() {
        // v0.5 贫瘠开局：不自带任何科技，对应异星工厂「徒手伐木」起点。
        // 开局可用：研究台、研究包、扳手、木箱（这些方块不加科技锁）。
    }

    // ---------------------------------------------------------------- 读取

    public static TechTreeData load(CompoundTag tag, HolderLookup.Provider registries) {
        TechTreeData data = new TechTreeData();
        data.levels.clear();
        CompoundTag levelsTag = tag.getCompound("Levels");
        for (String key : levelsTag.getAllKeys()) {
            data.levels.put(key, levelsTag.getInt(key));
        }
        // v0.5 贫瘠开局：空 levels 即真正的开局状态，不再回填基础自动化
        data.currentResearch = tag.contains("Current") ? tag.getString("Current") : null;
        data.researchProgress = tag.getInt("Progress");
        CompoundTag triggersTag = tag.getCompound("Triggers");
        for (String key : triggersTag.getAllKeys()) {
            data.triggerCounts.put(key, triggersTag.getInt(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag levelsTag = new CompoundTag();
        for (var entry : this.levels.entrySet()) {
            levelsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Levels", levelsTag);
        if (this.currentResearch != null) {
            tag.putString("Current", this.currentResearch);
        }
        tag.putInt("Progress", this.researchProgress);
        CompoundTag triggersTag = new CompoundTag();
        for (var entry : this.triggerCounts.entrySet()) {
            triggersTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Triggers", triggersTag);
        return tag;
    }

    /** 从服务器取全局实例。 */
    public static TechTreeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TechTreeData::new, TechTreeData::load),
                DATA_NAME);
    }

    // ---------------------------------------------------------------- 查询

    /**
     * 某科技的已研究等级（0 = 未研究）。
     *
     * <p>创造模式下直接返回满级，这样科技树界面全绿、依赖等级的
     * 速度加成也自动拉满，玩家不必先研究一轮再试玩机器。</p>
     */
    public int getLevel(String techId) {
        if (this.creativeBypass) {
            TechNodes.Node node = TechNodes.get(techId);
            return node == null ? 1 : node.maxLevel();
        }
        return this.levels.getOrDefault(techId, 0);
    }

    /**
     * 创造模式旁路开关。
     *
     * <p>创造模式是「沙盒试玩」场景，玩家要能直接摆出任何机器看效果，
     * 不该被科技树卡住。开启后所有科技视为已满级：方块能放、扳手能升级、
     * 钢材配方能用、科技树界面全绿。</p>
     *
     * <p>只在服务端内存里生效，<b>不写存档</b>——退出创造模式就恢复真实进度。</p>
     */
    private boolean creativeBypass = false;

    public void setCreativeBypass(boolean value) {
        if (this.creativeBypass == value) {
            return;
        }
        this.creativeBypass = value;
        // 故意不调 setDirty()：这是会话态，不该污染存档
    }

    public boolean isCreativeBypass() {
        return this.creativeBypass;
    }

    /** 兼容旧接口：等级 >= 1 视为已解锁。创造模式下一律已解锁。 */
    public boolean isUnlocked(String techId) {
        if (this.creativeBypass) {
            return true;
        }
        return getLevel(techId) >= 1;
    }

    /** 电力时代是否已经开启（所有电动化的前提）。 */
    public boolean isElectricEra() {
        return isUnlocked(TechNodes.ELECTRIC_POWER);
    }

    public String getCurrentResearch() {
        return this.currentResearch;
    }

    public int getResearchProgress() {
        return this.researchProgress;
    }

    /** 当前在研科技本级的总成本。 */
    public int getCurrentCost() {
        if (this.currentResearch == null) {
            return 0;
        }
        return TechNodes.costOf(this.currentResearch);
    }

    /** 当前在研科技还需要多少点。 */
    public int getRemainingCost() {
        return Math.max(0, getCurrentCost() - this.researchProgress);
    }

    // ---------------------------------------------------------------- 科技效果（供机器查询）

    /** 采矿速度加成：每级高效采矿 +20%。返回 1.0 = 无加成。 */
    public double miningSpeedMultiplier() {
        // getLevel 在创造模式下已返回满级，无需额外判断
        return 1.0 + 0.2 * getLevel(TechNodes.EFFICIENT_MINING);
    }

    /** 熔炉速度加成：高效冶炼 +30%。 */
    public double smeltingSpeedMultiplier() {
        // isUnlocked 在创造模式下恒为 true
        return isUnlocked(TechNodes.EFFICIENT_SMELTING) ? 1.3 : 1.0;
    }

    /** 传送带速度加成：物流优化 +50%。 */
    public double conveyorSpeedMultiplier() {
        return isUnlocked(TechNodes.LOGISTICS) ? 1.5 : 1.0;
    }

    // ---------------------------------------------------------------- 写入

    /** 设置研究目标。已满级的科技不能再研究。 */
    public boolean setResearch(String techId) {
        if (techId == null || !TechNodes.exists(techId)) {
            return false;
        }
        if (!TechNodes.canResearch(techId, this)) {
            return false;
        }
        this.currentResearch = techId;
        this.researchProgress = 0;
        setDirty();
        return true;
    }

    /**
     * 投入研究点，返回是否在本次调用中完成了某级科技。
     *
     * @return 本次完成的 (科技 id, 完成的等级)，没有则返回 null
     */
    public Map.Entry<String, Integer> addProgress(int points) {
        if (this.currentResearch == null) {
            return null;
        }
        this.researchProgress += points;
        int cost = getCurrentCost();
        if (this.researchProgress >= cost) {
            String done = this.currentResearch;
            int newLevel = getLevel(done) + 1;
            this.levels.put(done, newLevel);
            this.currentResearch = null;
            this.researchProgress = 0;
            setDirty();
            return Map.entry(done, newLevel);
        }
        setDirty();
        return null;
    }

    /** 直接解锁到满级（调试或事件奖励用）。 */
    public void unlock(String techId) {
        TechNodes.Node node = TechNodes.get(techId);
        int max = node == null ? 1 : node.maxLevel();
        this.levels.put(techId, max);
        setDirty();
    }

    // ---------------------------------------------------------------- 触发型科技（v0.6）

    /** 查询某触发物品的累计数（GUI 显示用）。 */
    public int getTriggerCount(String itemId) {
        return this.triggerCounts.getOrDefault(itemId, 0);
    }

    /** 全部触发计数（网络同步用，只读视图）。 */
    public java.util.Map<String, Integer> triggerCountsView() {
        return java.util.Collections.unmodifiableMap(this.triggerCounts);
    }

    /**
     * 记录「做出了某物品」（合成 / 熔炼产出 / 机器产出都会调用）。
     * 累计数达到触发型科技阈值且前置满足时自动解锁，不消耗研究包。
     *
     * @return 本次调用触发解锁的科技 id，没有则返回 null
     */
    public String onItemCrafted(String itemId, int count) {
        String result = null;
        for (TechNodes.Node node : TechNodes.all().values()) {
            if (node.triggerItem() == null || !node.triggerItem().equals(itemId)) {
                continue;
            }
            if (getLevel(node.id()) >= node.maxLevel()) {
                continue;
            }
            int now = this.triggerCounts.merge(itemId, count, Integer::sum);
            setDirty();
            // 前置必须满足才触发（对应异星工厂：蒸汽动力仍需先有冶炼基础）
            boolean parentsOk = true;
            for (String parent : node.parents()) {
                if (getLevel(parent) < 1) {
                    parentsOk = false;
                    break;
                }
            }
            if (parentsOk && now >= node.triggerCount()) {
                this.levels.put(node.id(), node.maxLevel());
                setDirty();
                result = node.id();
            }
        }
        return result;
    }
}
