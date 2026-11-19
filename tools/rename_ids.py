"""一次性改名脚本：把现有 ID / 类名统一到开发提示词 v3 的规格命名。

对应决策 Q10「按照开发提示词统一改名」。
- 删除 crafter 全链路（Q1）
- 方块/物品 ID 改为规格名
- Java 类名同步改名，避免长期维护混淆
- 资源文件名、blockstate/model/items 引用、lang 键、data json 全部同步
"""
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java")
RES = os.path.join(ROOT, "src", "main", "resources")

# ---------------------------------------------------------------- 要删除的
DELETE_FILES = [
    "src/main/java/com/xiaoyi/astrofactory/block/CrafterBlock.java",
    "src/main/java/com/xiaoyi/astrofactory/block/CrafterBlockEntity.java",
    "src/main/java/com/xiaoyi/astrofactory/menu/CrafterMenu.java",
    "src/main/java/com/xiaoyi/astrofactory/client/CrafterScreen.java",
    "src/main/resources/data/astrofactory/recipe/crafter.json",
    "src/main/resources/data/astrofactory/advancement/crafter.json",
    "src/main/resources/data/astrofactory/loot_table/blocks/crafter.json",
    "src/main/resources/assets/astrofactory/blockstates/crafter.json",
    "src/main/resources/assets/astrofactory/items/crafter.json",
    "src/main/resources/assets/astrofactory/models/block/crafter.json",
    "src/main/resources/assets/astrofactory/models/block/crafter_active.json",
    "src/main/resources/assets/astrofactory/textures/block/crafter_side.png",
    "src/main/resources/assets/astrofactory/textures/block/crafter_top.png",
    "src/main/resources/assets/astrofactory/textures/block/crafter_active.png",
]

# ---------------------------------------------------------------- 类名替换（长名优先）
JAVA_TOKENS = [
    ("FastConveyorBlockEntity", "FastTransportBeltBlockEntity"),
    ("FastConveyorBlock", "FastTransportBeltBlock"),
    ("ConveyorBlockEntity", "TransportBeltBlockEntity"),
    ("ConveyorBlock", "TransportBeltBlock"),
    ("ConveyorRenderer", "TransportBeltRenderer"),
    ("RoboticArmBlockEntity", "BurnerInserterBlockEntity"),
    ("RoboticArmBlock", "BurnerInserterBlock"),
    ("MinerBlockEntity", "BurnerMinerBlockEntity"),
    ("MinerBlock", "BurnerMinerBlock"),
    ("SmelterBlockEntity", "StoneFurnaceBlockEntity"),
    ("SmelterBlock", "StoneFurnaceBlock"),
    ("SmelterMenu", "StoneFurnaceMenu"),
    ("SmelterScreen", "StoneFurnaceScreen"),
    ("ResearchTableBlockEntity", "LabBlockEntity"),
    ("ResearchTableBlock", "LabBlock"),
    ("ResearchMenu", "LabMenu"),
    ("ResearchScreen", "LabScreen"),
    # 注册字段名
    ("FAST_CONVEYOR_BE", "FAST_TRANSPORT_BELT_BE"),
    ("FAST_CONVEYOR_ITEM", "FAST_TRANSPORT_BELT_ITEM"),
    ("FAST_CONVEYOR", "FAST_TRANSPORT_BELT"),
    ("CONVEYOR_BE", "TRANSPORT_BELT_BE"),
    ("CONVEYOR_ITEM", "TRANSPORT_BELT_ITEM"),
    ("CONVEYOR", "TRANSPORT_BELT"),
    ("ROBOTIC_ARM_BE", "BURNER_INSERTER_BE"),
    ("ROBOTIC_ARM_ITEM", "BURNER_INSERTER_ITEM"),
    ("ROBOTIC_ARM", "BURNER_INSERTER"),
    ("MINER_BE", "BURNER_MINER_BE"),
    ("MINER_ITEM", "BURNER_MINER_ITEM"),
    ("MINER", "BURNER_MINER"),
    ("SMELTER_BE", "STONE_FURNACE_BE"),
    ("SMELTER_ITEM", "STONE_FURNACE_ITEM"),
    ("SMELTER_MENU", "STONE_FURNACE_MENU"),
    ("SMELTER", "STONE_FURNACE"),
    ("RESEARCH_TABLE_BE", "LAB_BE"),
    ("RESEARCH_TABLE_ITEM", "LAB_ITEM"),
    ("RESEARCH_TABLE", "LAB"),
    ("RESEARCH_MENU", "LAB_MENU"),
    ("COPPER_COIL", "COPPER_CABLE"),
    ("CIRCUIT_BOARD", "ELECTRONIC_CIRCUIT"),
]

# ---------------------------------------------------------------- 资源 ID 替换（长名优先）
RES_TOKENS = [
    # 贴图名（不带方块前缀的）先处理，避免被后面的词根替换打乱
    ("block/arm_side", "block/burner_inserter_side"),
    ("block/arm_top", "block/burner_inserter_top"),
    ("block/research_side", "block/lab_side"),
    ("block/research_top", "block/lab_top"),
    ("fast_conveyor", "fast_transport_belt"),
    ("conveyor", "transport_belt"),
    ("robotic_arm", "burner_inserter"),
    ("miner", "burner_miner"),
    ("smelter", "stone_furnace"),
    ("research_table", "lab"),
    ("copper_coil", "copper_cable"),
    ("circuit_board", "electronic_circuit"),
]

# 资源文件改名映射（旧相对路径 -> 新相对路径）
RENAME_RES = {
    "assets/astrofactory/blockstates/fast_conveyor.json": "assets/astrofactory/blockstates/fast_transport_belt.json",
    "assets/astrofactory/blockstates/conveyor.json": "assets/astrofactory/blockstates/transport_belt.json",
    "assets/astrofactory/blockstates/robotic_arm.json": "assets/astrofactory/blockstates/burner_inserter.json",
    "assets/astrofactory/blockstates/miner.json": "assets/astrofactory/blockstates/burner_miner.json",
    "assets/astrofactory/blockstates/smelter.json": "assets/astrofactory/blockstates/stone_furnace.json",
    "assets/astrofactory/blockstates/research_table.json": "assets/astrofactory/blockstates/lab.json",
    "assets/astrofactory/items/fast_conveyor.json": "assets/astrofactory/items/fast_transport_belt.json",
    "assets/astrofactory/items/conveyor.json": "assets/astrofactory/items/transport_belt.json",
    "assets/astrofactory/items/robotic_arm.json": "assets/astrofactory/items/burner_inserter.json",
    "assets/astrofactory/items/miner.json": "assets/astrofactory/items/burner_miner.json",
    "assets/astrofactory/items/smelter.json": "assets/astrofactory/items/stone_furnace.json",
    "assets/astrofactory/items/research_table.json": "assets/astrofactory/items/lab.json",
    "assets/astrofactory/items/copper_coil.json": "assets/astrofactory/items/copper_cable.json",
    "assets/astrofactory/items/circuit_board.json": "assets/astrofactory/items/electronic_circuit.json",
    "assets/astrofactory/models/block/fast_conveyor.json": "assets/astrofactory/models/block/fast_transport_belt.json",
    "assets/astrofactory/models/block/conveyor.json": "assets/astrofactory/models/block/transport_belt.json",
    "assets/astrofactory/models/block/robotic_arm.json": "assets/astrofactory/models/block/burner_inserter.json",
    "assets/astrofactory/models/block/miner.json": "assets/astrofactory/models/block/burner_miner.json",
    "assets/astrofactory/models/block/miner_active.json": "assets/astrofactory/models/block/burner_miner_active.json",
    "assets/astrofactory/models/block/smelter.json": "assets/astrofactory/models/block/stone_furnace.json",
    "assets/astrofactory/models/block/smelter_active.json": "assets/astrofactory/models/block/stone_furnace_active.json",
    "assets/astrofactory/models/block/research_table.json": "assets/astrofactory/models/block/lab.json",
    "assets/astrofactory/models/item/copper_coil.json": "assets/astrofactory/models/item/copper_cable.json",
    "assets/astrofactory/models/item/circuit_board.json": "assets/astrofactory/models/item/electronic_circuit.json",
    "data/astrofactory/recipe/fast_conveyor.json": "data/astrofactory/recipe/fast_transport_belt.json",
    "data/astrofactory/recipe/conveyor.json": "data/astrofactory/recipe/transport_belt.json",
    "data/astrofactory/recipe/robotic_arm.json": "data/astrofactory/recipe/burner_inserter.json",
    "data/astrofactory/recipe/miner.json": "data/astrofactory/recipe/burner_miner.json",
    "data/astrofactory/recipe/smelter.json": "data/astrofactory/recipe/stone_furnace.json",
    "data/astrofactory/recipe/research_table.json": "data/astrofactory/recipe/lab.json",
    "data/astrofactory/recipe/copper_coil.json": "data/astrofactory/recipe/copper_cable.json",
    "data/astrofactory/recipe/circuit_board.json": "data/astrofactory/recipe/electronic_circuit.json",
    "data/astrofactory/advancement/fast_conveyor.json": "data/astrofactory/advancement/fast_transport_belt.json",
    "data/astrofactory/advancement/conveyor.json": "data/astrofactory/advancement/transport_belt.json",
    "data/astrofactory/advancement/robotic_arm.json": "data/astrofactory/advancement/burner_inserter.json",
    "data/astrofactory/advancement/miner.json": "data/astrofactory/advancement/burner_miner.json",
    "data/astrofactory/advancement/smelter.json": "data/astrofactory/advancement/stone_furnace.json",
    "data/astrofactory/advancement/research_table.json": "data/astrofactory/advancement/lab.json",
    "data/astrofactory/advancement/circuit_board.json": "data/astrofactory/advancement/electronic_circuit.json",
    "data/astrofactory/loot_table/blocks/fast_conveyor.json": "data/astrofactory/loot_table/blocks/fast_transport_belt.json",
    "data/astrofactory/loot_table/blocks/conveyor.json": "data/astrofactory/loot_table/blocks/transport_belt.json",
    "data/astrofactory/loot_table/blocks/robotic_arm.json": "data/astrofactory/loot_table/blocks/burner_inserter.json",
    "data/astrofactory/loot_table/blocks/miner.json": "data/astrofactory/loot_table/blocks/burner_miner.json",
    "data/astrofactory/loot_table/blocks/smelter.json": "data/astrofactory/loot_table/blocks/stone_furnace.json",
    "data/astrofactory/loot_table/blocks/research_table.json": "data/astrofactory/loot_table/blocks/lab.json",
    "assets/astrofactory/textures/block/fast_conveyor_top.png": "assets/astrofactory/textures/block/fast_transport_belt_top.png",
    "assets/astrofactory/textures/block/fast_conveyor_side.png": "assets/astrofactory/textures/block/fast_transport_belt_side.png",
    "assets/astrofactory/textures/block/conveyor_top.png": "assets/astrofactory/textures/block/transport_belt_top.png",
    "assets/astrofactory/textures/block/conveyor_side.png": "assets/astrofactory/textures/block/transport_belt_side.png",
    "assets/astrofactory/textures/block/arm_top.png": "assets/astrofactory/textures/block/burner_inserter_top.png",
    "assets/astrofactory/textures/block/arm_side.png": "assets/astrofactory/textures/block/burner_inserter_side.png",
    "assets/astrofactory/textures/block/miner_side.png": "assets/astrofactory/textures/block/burner_miner_side.png",
    "assets/astrofactory/textures/block/miner_active.png": "assets/astrofactory/textures/block/burner_miner_active.png",
    "assets/astrofactory/textures/block/smelter_side.png": "assets/astrofactory/textures/block/stone_furnace_side.png",
    "assets/astrofactory/textures/block/smelter_top.png": "assets/astrofactory/textures/block/stone_furnace_top.png",
    "assets/astrofactory/textures/block/smelter_active.png": "assets/astrofactory/textures/block/stone_furnace_active.png",
    "assets/astrofactory/textures/block/research_side.png": "assets/astrofactory/textures/block/lab_side.png",
    "assets/astrofactory/textures/block/research_top.png": "assets/astrofactory/textures/block/lab_top.png",
    "assets/astrofactory/textures/item/copper_coil.png": "assets/astrofactory/textures/item/copper_cable.png",
    "assets/astrofactory/textures/item/circuit_board.png": "assets/astrofactory/textures/item/electronic_circuit.png",
    # Java 文件改名
    "src/main/java/com/xiaoyi/astrofactory/block/ConveyorBlock.java": "src/main/java/com/xiaoyi/astrofactory/block/TransportBeltBlock.java",
    "src/main/java/com/xiaoyi/astrofactory/block/ConveyorBlockEntity.java": "src/main/java/com/xiaoyi/astrofactory/block/TransportBeltBlockEntity.java",
    "src/main/java/com/xiaoyi/astrofactory/block/FastConveyorBlock.java": "src/main/java/com/xiaoyi/astrofactory/block/FastTransportBeltBlock.java",
    "src/main/java/com/xiaoyi/astrofactory/block/FastConveyorBlockEntity.java": "src/main/java/com/xiaoyi/astrofactory/block/FastTransportBeltBlockEntity.java",
    "src/main/java/com/xiaoyi/astrofactory/block/RoboticArmBlock.java": "src/main/java/com/xiaoyi/astrofactory/block/BurnerInserterBlock.java",
    "src/main/java/com/xiaoyi/astrofactory/block/RoboticArmBlockEntity.java": "src/main/java/com/xiaoyi/astrofactory/block/BurnerInserterBlockEntity.java",
    "src/main/java/com/xiaoyi/astrofactory/block/MinerBlock.java": "src/main/java/com/xiaoyi/astrofactory/block/BurnerMinerBlock.java",
    "src/main/java/com/xiaoyi/astrofactory/block/MinerBlockEntity.java": "src/main/java/com/xiaoyi/astrofactory/block/BurnerMinerBlockEntity.java",
    "src/main/java/com/xiaoyi/astrofactory/block/SmelterBlock.java": "src/main/java/com/xiaoyi/astrofactory/block/StoneFurnaceBlock.java",
    "src/main/java/com/xiaoyi/astrofactory/block/SmelterBlockEntity.java": "src/main/java/com/xiaoyi/astrofactory/block/StoneFurnaceBlockEntity.java",
    "src/main/java/com/xiaoyi/astrofactory/block/ResearchTableBlock.java": "src/main/java/com/xiaoyi/astrofactory/block/LabBlock.java",
    "src/main/java/com/xiaoyi/astrofactory/block/ResearchTableBlockEntity.java": "src/main/java/com/xiaoyi/astrofactory/block/LabBlockEntity.java",
    "src/main/java/com/xiaoyi/astrofactory/menu/SmelterMenu.java": "src/main/java/com/xiaoyi/astrofactory/menu/StoneFurnaceMenu.java",
    "src/main/java/com/xiaoyi/astrofactory/menu/ResearchMenu.java": "src/main/java/com/xiaoyi/astrofactory/menu/LabMenu.java",
    "src/main/java/com/xiaoyi/astrofactory/client/SmelterScreen.java": "src/main/java/com/xiaoyi/astrofactory/client/StoneFurnaceScreen.java",
    "src/main/java/com/xiaoyi/astrofactory/client/ResearchScreen.java": "src/main/java/com/xiaoyi/astrofactory/client/LabScreen.java",
    "src/main/java/com/xiaoyi/astrofactory/client/ConveyorRenderer.java": "src/main/java/com/xiaoyi/astrofactory/client/TransportBeltRenderer.java",
}


def apply_tokens(text, tokens):
    for old, new in tokens:
        text = text.replace(old, new)
    return text


def main():
    # 1. 删除 crafter 相关文件
    for rel in DELETE_FILES:
        p = os.path.join(ROOT, rel.replace("/", os.sep))
        if os.path.exists(p):
            os.remove(p)
            print("deleted", rel)

    # 2. 改写 Java 源码内容
    for dirpath, _, files in os.walk(JAVA):
        for f in files:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dirpath, f)
            with open(p, "r", encoding="utf-8") as fh:
                src = fh.read()
            new = apply_tokens(src, JAVA_TOKENS)
            if new != src:
                with open(p, "w", encoding="utf-8", newline="\n") as fh:
                    fh.write(new)
                print("java  ", os.path.relpath(p, ROOT))

    # 3. 改写资源内容（lang / json）
    for dirpath, _, files in os.walk(RES):
        for f in files:
            if not f.endswith((".json", ".mcmeta", ".toml")):
                continue
            p = os.path.join(dirpath, f)
            with open(p, "r", encoding="utf-8") as fh:
                src = fh.read()
            new = apply_tokens(src, RES_TOKENS)
            if new != src:
                with open(p, "w", encoding="utf-8", newline="\n") as fh:
                    fh.write(new)
                print("res   ", os.path.relpath(p, ROOT))

    # 4. 文件改名
    for old, new in RENAME_RES.items():
        src = os.path.join(ROOT, old.replace("/", os.sep))
        dst = os.path.join(ROOT, new.replace("/", os.sep))
        if os.path.exists(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if os.path.exists(dst):
                os.remove(dst)
            shutil.move(src, dst)
            print("rename", old, "->", new)


if __name__ == "__main__":
    main()
