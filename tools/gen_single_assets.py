"""单格方块（传送带 / 机械臂）的贴图与模型。

覆盖：
- transport_belt / fast_transport_belt：带面 + 两侧护栏（模型多元素）
- burner_inserter：底座 + 立柱 + 横臂（贴图换新画法，模型结构保持不变）

贴图画法统一走 tools/tex_common.py（工业风 / 参考《异星工厂》）。
护栏高度 4px（0.25），低于物品渲染高度（y=0.20 起，约 0.4 高），不会挡住带面物品。

用法：python tools/gen_single_assets.py
"""
import json
import os

import tex_common as T

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
ITEMS = os.path.join(ASSETS, "items")


def _save(img, name):
    img.save(os.path.join(TEX_BLOCK, name + ".png"))


def _dump(obj, path):
    json.dump(obj, open(path, "w", encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)


def build_belt(name, fast):
    """传送带：带面（0..3）+ 两侧护栏（0..4，厚 1px）。"""
    _save(T.paint_belt_top(fast), name + "_top")
    _save(T.paint_belt_side(fast), name + "_side")
    _save(T.paint_belt_rail(fast), name + "_rail")

    top = f"astrofactory:block/{name}_top"
    side = f"astrofactory:block/{name}_side"
    rail = f"astrofactory:block/{name}_rail"

    elements = [
        {
            "from": [0, 0, 0],
            "to": [16, 3, 16],
            "faces": {
                "up": {"texture": "#top", "uv": [0, 0, 16, 16], "cullface": "up"},
                "down": {"texture": "#side", "uv": [0, 0, 16, 16], "cullface": "down"},
                "north": {"texture": "#side", "uv": [0, 13, 16, 16]},
                "south": {"texture": "#side", "uv": [0, 13, 16, 16]},
                "east": {"texture": "#side", "uv": [0, 13, 16, 16]},
                "west": {"texture": "#side", "uv": [0, 13, 16, 16]},
            },
        },
        # 左护栏
        {
            "from": [0, 3, 0],
            "to": [1, 4, 16],
            "faces": {
                "up": {"texture": "#rail", "uv": [0, 0, 1, 16]},
                "north": {"texture": "#rail", "uv": [0, 12, 1, 13]},
                "south": {"texture": "#rail", "uv": [0, 12, 1, 13]},
                "east": {"texture": "#rail", "uv": [0, 12, 16, 13]},
                "west": {"texture": "#rail", "uv": [0, 12, 16, 13]},
            },
        },
        # 右护栏
        {
            "from": [15, 3, 0],
            "to": [16, 4, 16],
            "faces": {
                "up": {"texture": "#rail", "uv": [0, 0, 1, 16]},
                "north": {"texture": "#rail", "uv": [0, 12, 1, 13]},
                "south": {"texture": "#rail", "uv": [0, 12, 1, 13]},
                "east": {"texture": "#rail", "uv": [0, 12, 16, 13]},
                "west": {"texture": "#rail", "uv": [0, 12, 16, 13]},
            },
        },
    ]

    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": top, "top": top, "side": side, "rail": rail},
        "elements": elements,
    }
    _dump(model, os.path.join(MODELS_BLOCK, name + ".json"))
    _dump({"model": {"type": "minecraft:model",
                     "model": f"astrofactory:block/{name}"}},
          os.path.join(ITEMS, name + ".json"))
    variants = {}
    for i, facing in enumerate(("north", "east", "south", "west")):
        entry = {"model": f"astrofactory:block/{name}"}
        if i:
            entry["y"] = 90 * i
        variants[f"facing={facing}"] = entry
    _dump({"variants": variants}, os.path.join(BLOCKSTATES, name + ".json"))
    print(f"built {name}: belt with rails")


def build_inserter():
    """机械臂：底座 + 立柱 + 横臂 + 抓取头（结构与现有模型一致，只换贴图）。"""
    _save(T.paint_inserter_top(), "burner_inserter_top")
    _save(T.paint_inserter_side(), "burner_inserter_side")
    _save(T.paint_arm_metal(), "burner_inserter_arm")

    top = "astrofactory:block/burner_inserter_top"
    side = "astrofactory:block/burner_inserter_side"
    arm = "astrofactory:block/burner_inserter_arm"

    def elem(frm, to, faces_map):
        return {"from": frm, "to": to, "faces": faces_map}

    def all_faces(tex, uv=None):
        out = {}
        for f in ("down", "up", "north", "south", "east", "west"):
            face = {"texture": tex}
            if uv:
                face["uv"] = uv
            out[f] = face
        return out

    elements = [
        # 底座
        elem([4, 0, 4], [12, 3, 12], {
            "up": {"texture": "#top", "uv": [4, 4, 12, 12]},
            "down": {"texture": "#side", "uv": [4, 4, 12, 12]},
            "north": {"texture": "#side", "uv": [4, 13, 12, 16]},
            "south": {"texture": "#side", "uv": [4, 13, 12, 16]},
            "east": {"texture": "#side", "uv": [4, 13, 12, 16]},
            "west": {"texture": "#side", "uv": [4, 13, 12, 16]},
        }),
        # 立柱
        elem([6, 3, 6], [10, 14, 10], {
            "up": {"texture": "#arm", "uv": [6, 6, 10, 10]},
            "down": {"texture": "#arm", "uv": [6, 6, 10, 10]},
            "north": {"texture": "#arm", "uv": [6, 2, 10, 13]},
            "south": {"texture": "#arm", "uv": [6, 2, 10, 13]},
            "east": {"texture": "#arm", "uv": [6, 2, 10, 13]},
            "west": {"texture": "#arm", "uv": [6, 2, 10, 13]},
        }),
        # 横臂（朝 -Z 伸出）
        elem([6, 11, 0], [10, 14, 6], {
            "up": {"texture": "#arm", "uv": [6, 0, 10, 6]},
            "down": {"texture": "#arm", "uv": [6, 0, 10, 6]},
            "north": {"texture": "#arm", "uv": [6, 11, 10, 14]},
            "south": {"texture": "#arm", "uv": [6, 11, 10, 14]},
            "east": {"texture": "#arm", "uv": [0, 11, 6, 14]},
            "west": {"texture": "#arm", "uv": [0, 11, 6, 14]},
        }),
        # 前端抓取头
        elem([5, 10, 0], [11, 15, 2], all_faces("#side", [5, 10, 11, 15])),
    ]

    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": side, "top": top, "side": side, "arm": arm},
        "elements": elements,
    }
    _dump(model, os.path.join(MODELS_BLOCK, "burner_inserter.json"))
    _dump({"model": {"type": "minecraft:model",
                     "model": "astrofactory:block/burner_inserter"}},
          os.path.join(ITEMS, "burner_inserter.json"))

    # blockstate：水平只用 y 旋，竖直用 x 旋（模型朝 -Z，FACING=north 时横臂朝北）
    variants = {
        "facing=north": {"model": "astrofactory:block/burner_inserter"},
        "facing=east": {"model": "astrofactory:block/burner_inserter", "y": 90},
        "facing=south": {"model": "astrofactory:block/burner_inserter", "y": 180},
        "facing=west": {"model": "astrofactory:block/burner_inserter", "y": 270},
        "facing=up": {"model": "astrofactory:block/burner_inserter", "x": 90},
        "facing=down": {"model": "astrofactory:block/burner_inserter", "x": 270},
    }
    _dump({"variants": variants},
          os.path.join(BLOCKSTATES, "burner_inserter.json"))
    print("built burner_inserter: base + column + arm")


def main():
    build_belt("transport_belt", fast=False)
    build_belt("fast_transport_belt", fast=True)
    build_inserter()


if __name__ == "__main__":
    main()
