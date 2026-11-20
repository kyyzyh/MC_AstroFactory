package com.xiaoyi.astrofactory.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.xiaoyi.astrofactory.Astrofactory;
import com.xiaoyi.astrofactory.block.AssemblerBlockEntity;
import com.xiaoyi.astrofactory.block.BoilerBlockEntity;
import com.xiaoyi.astrofactory.block.BurnerInserterBlockEntity;
import com.xiaoyi.astrofactory.block.BurnerMinerBlockEntity;
import com.xiaoyi.astrofactory.block.FastTransportBeltBlockEntity;
import com.xiaoyi.astrofactory.block.LabBlockEntity;
import com.xiaoyi.astrofactory.block.OffshorePumpBlockEntity;
import com.xiaoyi.astrofactory.block.OreVeinBlockEntity;
import com.xiaoyi.astrofactory.block.PowerPoleBlockEntity;
import com.xiaoyi.astrofactory.block.SteamEngineBlockEntity;
import com.xiaoyi.astrofactory.block.SteamPipeBlockEntity;
import com.xiaoyi.astrofactory.block.StoneFurnaceBlockEntity;
import com.xiaoyi.astrofactory.block.TransportBeltBlockEntity;

import java.util.Set;

/**
 * 方块实体类型注册。
 *
 * <p>26.3 注意：{@link BlockEntityType} 已无 Builder，直接用公开构造器
 * {@code (supplier, Set<Block>)}。</p>
 */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Astrofactory.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransportBeltBlockEntity>> TRANSPORT_BELT_BE =
            BLOCK_ENTITY_TYPES.register("transport_belt",
                    () -> new BlockEntityType<>(TransportBeltBlockEntity::new,
                            Set.of(ModBlocks.TRANSPORT_BELT.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FastTransportBeltBlockEntity>> FAST_TRANSPORT_BELT_BE =
            BLOCK_ENTITY_TYPES.register("fast_transport_belt",
                    () -> new BlockEntityType<>(FastTransportBeltBlockEntity::new,
                            Set.of(ModBlocks.FAST_TRANSPORT_BELT.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerInserterBlockEntity>> BURNER_INSERTER_BE =
            BLOCK_ENTITY_TYPES.register("burner_inserter",
                    () -> new BlockEntityType<>(BurnerInserterBlockEntity::new,
                            Set.of(ModBlocks.BURNER_INSERTER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerMinerBlockEntity>> BURNER_MINER_BE =
            BLOCK_ENTITY_TYPES.register("burner_miner",
                    () -> new BlockEntityType<>(BurnerMinerBlockEntity::new,
                            Set.of(ModBlocks.BURNER_MINER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_BE =
            BLOCK_ENTITY_TYPES.register("assembler",
                    () -> new BlockEntityType<>(AssemblerBlockEntity::new,
                            Set.of(ModBlocks.ASSEMBLER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneFurnaceBlockEntity>> STONE_FURNACE_BE =
            BLOCK_ENTITY_TYPES.register("stone_furnace",
                    () -> new BlockEntityType<>(StoneFurnaceBlockEntity::new,
                            Set.of(ModBlocks.STONE_FURNACE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabBlockEntity>> LAB_BE =
            BLOCK_ENTITY_TYPES.register("lab",
                    () -> new BlockEntityType<>(LabBlockEntity::new,
                            Set.of(ModBlocks.LAB.get())));

    /** 矿脉方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreVeinBlockEntity>> ORE_VEIN_BE =
            BLOCK_ENTITY_TYPES.register("ore_vein",
                    () -> new BlockEntityType<>(OreVeinBlockEntity::new,
                            Set.of(ModBlocks.ORE_VEIN.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> BOILER_BE =
            BLOCK_ENTITY_TYPES.register("boiler",
                    () -> new BlockEntityType<>(BoilerBlockEntity::new,
                            Set.of(ModBlocks.BOILER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENGINE_BE =
            BLOCK_ENTITY_TYPES.register("steam_engine",
                    () -> new BlockEntityType<>(SteamEngineBlockEntity::new,
                            Set.of(ModBlocks.STEAM_ENGINE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OffshorePumpBlockEntity>> OFFSHORE_PUMP_BE =
            BLOCK_ENTITY_TYPES.register("offshore_pump",
                    () -> new BlockEntityType<>(OffshorePumpBlockEntity::new,
                            Set.of(ModBlocks.OFFSHORE_PUMP.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamPipeBlockEntity>> STEAM_PIPE_BE =
            BLOCK_ENTITY_TYPES.register("steam_pipe",
                    () -> new BlockEntityType<>(SteamPipeBlockEntity::new,
                            Set.of(ModBlocks.STEAM_PIPE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerPoleBlockEntity>> POWER_POLE_BE =
            BLOCK_ENTITY_TYPES.register("power_pole",
                    () -> new BlockEntityType<>(PowerPoleBlockEntity::new,
                            Set.of(ModBlocks.POWER_POLE.get())));
}
