"""为矿脉方块生成 blockstate / 模型 / 贴图。

矿脉有 kind (iron/copper/coal/stone) × richness (rich/normal/poor/depleted) = 16 变体。
每个变体一张 16x16 贴图，一个 cube 模型。

贴图画法走 tools/tex_common.paint_ore（石底 + 矿粒团簇，采空态只剩矿渣裂纹）。
"""
import json
import os

import tex_common as T

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

KINDS = ["iron", "copper", "coal", "stone"]
RICHNESS = ["rich", "normal", "poor", "depleted"]


def build():
    os.makedirs(TEX_BLOCK, exist_ok=True)
    os.makedirs(MODELS_BLOCK, exist_ok=True)
    os.makedirs(BLOCKSTATES, exist_ok=True)

    for kind in KINDS:
        for r in RICHNESS:
            name = f"ore_vein_{kind}_{r}"
            T.paint_ore(kind, r).save(os.path.join(TEX_BLOCK, name + ".png"))
            model = {
                "parent": "minecraft:block/block",
                "textures": {
                    "particle": f"astrofactory:block/{name}",
                    "down": f"astrofactory:block/{name}",
                    "up": f"astrofactory:block/{name}",
                    "north": f"astrofactory:block/{name}",
                    "south": f"astrofactory:block/{name}",
                    "east": f"astrofactory:block/{name}",
                    "west": f"astrofactory:block/{name}",
                },
                "elements": [{
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {
                        "down": {"texture": "#down", "cullface": "down"},
                        "up": {"texture": "#up", "cullface": "up"},
                        "north": {"texture": "#north", "cullface": "north"},
                        "south": {"texture": "#south", "cullface": "south"},
                        "east": {"texture": "#east", "cullface": "east"},
                        "west": {"texture": "#west", "cullface": "west"},
                    },
                }],
            }
            json.dump(model, open(os.path.join(MODELS_BLOCK, f"ore_vein_{kind}_{r}.json"),
                                  "w", encoding="utf-8", newline="\n"),
                      ensure_ascii=False, indent=2)

    variants = {f"kind={kind},richness={r}":
                {"model": f"astrofactory:block/ore_vein_{kind}_{r}"}
                for kind in KINDS for r in RICHNESS}
    json.dump({"variants": variants},
              open(os.path.join(BLOCKSTATES, "ore_vein.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    # 方块物品图标：用铁富矿模型（创造栏/手持显示）
    items_dir = os.path.join(ASSETS, "items")
    os.makedirs(items_dir, exist_ok=True)
    json.dump({"model": {"type": "minecraft:model",
                         "model": "astrofactory:block/ore_vein_iron_rich"}},
              open(os.path.join(items_dir, "ore_vein.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print(f"built ore_vein: {len(variants)} variants + item model")


if __name__ == "__main__":
    build()
