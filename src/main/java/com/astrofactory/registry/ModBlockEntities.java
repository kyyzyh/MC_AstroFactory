package com.astrofactory.registry;

import com.astrofactory.AstroFactory;
import com.astrofactory.blockentity.CrateBlockEntity;
import com.astrofactory.blockentity.GeneratorBlockEntity;
import com.astrofactory.blockentity.ResearchTableBlockEntity;
import com.astrofactory.blockentity.InserterBlockEntity;
import com.astrofactory.blockentity.MinerBlockEntity;
import com.astrofactory.blockentity.SmelterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体类型注册。
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AstroFactory.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MinerBlockEntity>> MINER =
            BLOCK_ENTITIES.register("miner", () -> BlockEntityType.Builder
                    .of(MinerBlockEntity::new, ModBlocks.MINER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InserterBlockEntity>> INSERTER =
            BLOCK_ENTITIES.register("inserter", () -> BlockEntityType.Builder
                    .of(InserterBlockEntity::new, ModBlocks.INSERTER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelterBlockEntity>> SMELTER =
            BLOCK_ENTITIES.register("smelter", () -> BlockEntityType.Builder
                    .of(SmelterBlockEntity::new, ModBlocks.SMELTER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrateBlockEntity>> CRATE =
            BLOCK_ENTITIES.register("crate", () -> BlockEntityType.Builder
                    .of(CrateBlockEntity::new, ModBlocks.CRATE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR =
            BLOCK_ENTITIES.register("generator", () -> BlockEntityType.Builder
                    .of(GeneratorBlockEntity::new, ModBlocks.GENERATOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchTableBlockEntity>> RESEARCH_TABLE =
            BLOCK_ENTITIES.register("research_table", () -> BlockEntityType.Builder
                    .of(ResearchTableBlockEntity::new, ModBlocks.RESEARCH_TABLE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.astrofactory.blockentity.AssemblerBlockEntity>> ASSEMBLER =
            BLOCK_ENTITIES.register("assembler", () -> BlockEntityType.Builder
                    .of(com.astrofactory.blockentity.AssemblerBlockEntity::new, ModBlocks.ASSEMBLER.get())
                    .build(null));

    // ------------------------------------------------------------ v0.5 新增

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.astrofactory.blockentity.WoodCrateBlockEntity>> WOOD_CRATE =
            BLOCK_ENTITIES.register("wood_crate", () -> BlockEntityType.Builder
                    .of(com.astrofactory.blockentity.WoodCrateBlockEntity::new, ModBlocks.WOOD_CRATE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.astrofactory.blockentity.IronCrateBlockEntity>> IRON_CRATE =
            BLOCK_ENTITIES.register("iron_crate", () -> BlockEntityType.Builder
                    .of(com.astrofactory.blockentity.IronCrateBlockEntity::new, ModBlocks.IRON_CRATE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.astrofactory.blockentity.WaterPumpBlockEntity>> WATER_PUMP =
            BLOCK_ENTITIES.register("water_pump", () -> BlockEntityType.Builder
                    .of(com.astrofactory.blockentity.WaterPumpBlockEntity::new, ModBlocks.WATER_PUMP.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.astrofactory.blockentity.BoilerBlockEntity>> BOILER =
            BLOCK_ENTITIES.register("boiler", () -> BlockEntityType.Builder
                    .of(com.astrofactory.blockentity.BoilerBlockEntity::new, ModBlocks.BOILER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.astrofactory.blockentity.SteamEngineBlockEntity>> STEAM_ENGINE =
            BLOCK_ENTITIES.register("steam_engine", () -> BlockEntityType.Builder
                    .of(com.astrofactory.blockentity.SteamEngineBlockEntity::new, ModBlocks.STEAM_ENGINE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.astrofactory.blockentity.PowerPoleBlockEntity>> POWER_POLE =
            BLOCK_ENTITIES.register("power_pole", () -> BlockEntityType.Builder
                    .of(com.astrofactory.blockentity.PowerPoleBlockEntity::new, ModBlocks.POWER_POLE.get())
                    .build(null));

    private ModBlockEntities() {}
}
