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

    /**
     * 存档数据版本号（US-105 / US-108）。
     *
     * <p>写进 NBT 的 {@code DataVersion}，以后改结构时据此做迁移；
     * 老存档没有这个字段，读出来按 0 处理（= 迁移前格式）。</p>
     *
     * <p>v3（US-108）：开局解锁集定稿——新世界 / 老档 < v3 一律预置
     * {@link #STARTING_UNLOCKED}，保证 P0a 玩法不被科技倒锁。</p>
     */
    public static final int SAVE_VERSION = 3;

    /**
     * 开局默认已解锁的科技（US-108 定稿）。
     *
     * <p>预置「自动采矿」+「基础自动化」：开局即可放置采矿机、传送带、机械臂、
     * 储物箱，保证 P0a 玩法（燃料时代产线）不被科技倒锁。</p>
     */
    public static final String[] STARTING_UNLOCKED = {
            TechNodes.MINING,
            TechNodes.BASIC_AUTOMATION
    };

    /** 已解锁的科技 id → 已研究等级。 */
    private final Map<String, Integer> levels = new LinkedHashMap<>();

    /** 当前正在研究的科技 id（null 表示没有在研项目）。 */
    private String currentResearch = null;

    /** 当前在研科技已累积的科技点。 */
    private int researchProgress = 0;

    /** US-106 研究队列：按先进先出排队研究（队首完成自动开队首下一个）。 */
    private final java.util.ArrayDeque<String> researchQueue = new java.util.ArrayDeque<>();

    /** v0.6 触发型科技：物品 id → 已累计获得数（跨会话持久）。 */
    private final Map<String, Integer> triggerCounts = new LinkedHashMap<>();

    public TechTreeData() {
        // US-108 开局解锁集定稿：预置「自动采矿」与「基础自动化」，
        // 保证开局即可放置采矿机、传送带、机械臂、储物箱（P0a 玩法不被倒锁）。
        // 对应异星工厂「开局就有红矿物流」的体验，第一项研究从此起步。
        for (String id : STARTING_UNLOCKED) {
            int max = TechNodes.get(id) == null ? 1 : TechNodes.get(id).maxLevel();
            this.levels.put(id, max);
        }
    }

    // ---------------------------------------------------------------- 读取

    public static TechTreeData load(CompoundTag tag, HolderLookup.Provider registries) {
        // 先读版本号（老存档没有该字段 → 0）
        int oldVersion = tag.contains("DataVersion") ? tag.getInt("DataVersion") : 0;
        TechTreeData data = new TechTreeData();
        data.saveVersion = oldVersion;
        data.levels.clear();
        CompoundTag levelsTag = tag.getCompound("Levels");
        for (String key : levelsTag.getAllKeys()) {
            data.levels.put(key, levelsTag.getInt(key));
        }
        // US-108 迁移：v3 起开局默认已解锁 MINING + BASIC_AUTOMATION。
        // 老存档（< 3）也回填——只补缺失、不覆盖已有进度，避免 P0a 玩法被倒锁。
        if (oldVersion < 3) {
            for (String id : STARTING_UNLOCKED) {
                if (!data.levels.containsKey(id)) {
                    int max = TechNodes.get(id) == null ? 1 : TechNodes.get(id).maxLevel();
                    data.levels.put(id, max);
                }
            }
        }
        // v0.5 贫瘠开局：空 levels 即真正的开局状态（上面已按新开局集补齐）
        data.currentResearch = tag.contains("Current") ? tag.getString("Current") : null;
        data.researchProgress = tag.getInt("Progress");
        // US-106 研究队列（列表存）
        data.researchQueue.clear();
        var queueTag = tag.getList("Queue", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < queueTag.size(); i++) {
            data.researchQueue.add(queueTag.getString(i));
        }
        CompoundTag triggersTag = tag.getCompound("Triggers");
        for (String key : triggersTag.getAllKeys()) {
            data.triggerCounts.put(key, triggersTag.getInt(key));
        }
        // 读完之后统一标记为当前版本（下次保存就写新版本号）
        data.saveVersion = SAVE_VERSION;
        return data;
    }

    /** 存档版本号（US-105，用于后续迁移判定）。 */
    private int saveVersion = SAVE_VERSION;

    public int getSaveVersion() {
        return this.saveVersion;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", SAVE_VERSION);
        CompoundTag levelsTag = new CompoundTag();
        for (var entry : this.levels.entrySet()) {
            levelsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Levels", levelsTag);
        if (this.currentResearch != null) {
            tag.putString("Current", this.currentResearch);
        }
        tag.putInt("Progress", this.researchProgress);
        // US-106 研究队列（字符串列表）
        var queueTag = new net.minecraft.nbt.ListTag();
        for (String id : this.researchQueue) {
            queueTag.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        tag.put("Queue", queueTag);
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
        // US-105：数据驱动科技的成本来自它的 pack_count
        TechDef def = TechRegistry.get(this.currentResearch);
        if (def != null) {
            return def.packCount();
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

    /** 电力机械臂提速（US-108）：机械动力 I 提速 20% ⚙（数值进配置）。 */
    public double inserterSpeedMultiplier() {
        double bonus = isUnlocked(TechNodes.MECHANICAL_POWER)
                ? com.astrofactory.config.AstroFactoryConfig.MECHANICAL_POWER_INSERTER_BONUS.get() : 0.0;
        return 1.0 + bonus;
    }

    // ---------------------------------------------------------------- 写入

    /** 设置研究目标。已满级的科技不能再研究。 */
    public boolean setResearch(String techId) {
        if (techId == null) {
            return false;
        }
        // US-105：数据驱动科技走 TechRegistry 判定（前置 + 成本都来自数据）
        if (TechRegistry.exists(techId)) {
            if (!canResearchDataDriven(techId)) {
                return false;
            }
            this.currentResearch = techId;
            this.researchProgress = 0;
            setDirty();
            return true;
        }
        if (!TechNodes.exists(techId)) {
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

    // ---------------------------------------------------------------- 研究队列（US-106）

    /** 可研究状态（含数据驱动科技）。 */
    private boolean canResearchAny(String techId) {
        if (TechRegistry.exists(techId)) {
            return canResearchDataDriven(techId);
        }
        TechNodes.Node node = TechNodes.get(techId);
        return node != null && TechNodes.canResearch(techId, this);
    }

    /**
     * 把一项科技加入研究队列。校验：科技存在、可研究、且不在当前研究与队列中。
     *
     * @return 是否加入成功
     */
    public boolean enqueueResearch(String techId) {
        if (techId == null || !canResearchAny(techId)) {
            return false;
        }
        if (techId.equals(this.currentResearch) || this.researchQueue.contains(techId)) {
            return false;
        }
        this.researchQueue.addLast(techId);
        setDirty();
        return true;
    }

    /** 当前队列副本（只读，供 GUI / 网络同步）。 */
    public java.util.List<String> researchQueueView() {
        return java.util.List.copyOf(this.researchQueue);
    }

    /** 从队列移除指定科技（尚未开始排队研究时，即不在队首）。 */
    public boolean removeFromQueue(String techId) {
        if (techId == null) {
            return false;
        }
        boolean removed = this.researchQueue.removeIf(id -> id.equals(techId));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /**
     * 若当前无在研项目，从队首自动取出下一项开始研究。
     *
     * @return 本次是否因为队列而新开了研究
     */
    public boolean popNextFromQueue() {
        if (this.currentResearch != null) {
            return false;
        }
        while (!this.researchQueue.isEmpty()) {
            String next = this.researchQueue.pollFirst();
            // 可能已满级/前置变化导致不可研究，跳过
            if (canResearchAny(next)) {
                this.currentResearch = next;
                this.researchProgress = 0;
                setDirty();
                return true;
            }
        }
        setDirty();
        return false;
    }

    // ---------------------------------------------------------------- 数据驱动科技（US-105）

    /** 数据驱动科技能否开始研究：前置满足且未解锁、非创造旁路。 */
    public boolean canResearchDataDriven(String techId) {
        if (this.creativeBypass) {
            return false;
        }
        if (!TechRegistry.exists(techId)) {
            return false;
        }
        if (getLevel(techId) >= 1) {
            return false;
        }
        return TechRegistry.parentsSatisfied(techId, this);
    }

    /** 某数据驱动科技是否已解锁。 */
    public boolean isDataDrivenUnlocked(String techId) {
        return TechRegistry.exists(techId) && getLevel(techId) >= 1;
    }

    /** 直接解锁一项数据驱动科技（实验室完成研究时调用）。 */
    public void unlockDataDriven(String techId) {
        if (!TechRegistry.exists(techId)) {
            return;
        }
        this.levels.put(techId, 1);
        setDirty();
        notifyUnlockChanged();
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
            notifyUnlockChanged();
            // US-106：完成一项后自动开排队里的下一项
            popNextFromQueue();
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
        notifyUnlockChanged();
    }

    /** 清空当前研究目标与进度（测试复位、研究完成后由 addProgress 自行清理）。 */
    public void clearResearch() {
        this.currentResearch = null;
        this.researchProgress = 0;
        setDirty();
    }

    /** 测试用：直接设某科技等级（0 = 清空）。 */
    public void setLevelForTest(String techId, int level) {
        if (level <= 0) {
            this.levels.remove(techId);
        } else {
            this.levels.put(techId, level);
        }
        setDirty();
    }

    /**
     * 解锁状态变化后的回调（US-105）：让配方锁定立刻重算，不必等下次重载。
     *
     * <p>{@link TechTreeData} 是 SavedData，拿不到 server 引用，所以这里把
     * 「重算配方」委托给注册进来的回调，避免数据层反向依赖服务端。</p>
     */
    private static volatile java.util.function.Consumer<TechTreeData> unlockListener;

    public static void setUnlockListener(java.util.function.Consumer<TechTreeData> listener) {
        unlockListener = listener;
    }

    private void notifyUnlockChanged() {
        java.util.function.Consumer<TechTreeData> listener = unlockListener;
        if (listener != null) {
            listener.accept(this);
        }
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
