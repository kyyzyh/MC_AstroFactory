package com.xiaoyi.astrofactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import com.xiaoyi.astrofactory.world.OreVeinData;

/**
 * AstroFactory —— 异星工厂式自动化模组。
 *
 * 模块：
 * - 传送带（真连续移动 + 汇流 + backpressure）
 * - 机械臂（抓取 / 投放）
 * - 采矿机（无限矿脉）
 * - 组装机（配方自动化）
 * - 科研与科技树（后续）
 */
@Mod(Astrofactory.MODID)
public class Astrofactory {
    public static final String MODID = "astrofactory";
    public static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    public Astrofactory(IEventBus modEventBus) {
        ModRegistry.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 配方表依赖 ItemStack 数据组件，改为在首次使用时延迟初始化，
        // 此处不做预加载，避免 "Components not bound yet"。
    }

    /**
     * 服务器启动后幂等放置矿田（规格 §5.6）。
     *
     * <p>用 {@link OreVeinData} SavedData 保证幂等：只在首次启动时放置 4 片矿田，
     * 重载不再重复。</p>
     */
    private void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        var level = server.overworld();
        if (level == null) {
            return;
        }
        var spawn = level.getLevelData().getRespawnData().pos();
        var data = level.getDataStorage().computeIfAbsent(OreVeinData.TYPE);
        data.generateIfAbsent(level, spawn);
    }

    /**
     * chunk 首次加载时按确定性概率散布矿田。
     *
     * <p>出生点保底只有 4 片矿，挖完就没了。这一层让「扩张找矿」成立：
     * 玩家越往外走，越能遇到新矿田。</p>
     *
     * <p>只在 {@link ChunkEvent.Load#isNewChunk()} 为 true 时尝试，
     * 避免玩家来回走动时反复触发。放置本身还有 SavedData 幂等保护，
     * 双重保险。</p>
     */
    private void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (!event.isNewChunk()) {
            return;
        }
        var chunk = event.getChunk();
        var spawn = serverLevel.getLevelData().getRespawnData().pos();
        var spawnChunk = new net.minecraft.world.level.ChunkPos(spawn.getX(), spawn.getZ());
        var data = serverLevel.getDataStorage().computeIfAbsent(OreVeinData.TYPE);
        if (data.tryGenerateForChunk(serverLevel, chunk.getPos(), spawnChunk)) {
            LOGGER.debug("[AstroFactory] 散布矿田生成于 chunk {}", chunk.getPos());
        }
    }
}
