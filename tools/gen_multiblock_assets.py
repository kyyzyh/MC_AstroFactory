"""为多方块机器生成"一体机"贴图与分块模型。

设计要点（规格 §6「2×2/3×3 要有'一体机'观感」+ Q4「每个 dummy 各渲染自己那份模型」）：

1. 先生成 N*16 × N*16 的整机贴图（机器本地坐标系：列 = dx 机器右手方向，行 = dz 机器正面方向）；
2. 朝向烘焙：按 FACING 对整机图整体旋转/翻转，得到该朝向"从上看"的正确排布；
3. 再按 N×N 切成 N*N 张 16×16 象限贴图；
4. 每格一个 cube 模型，用自己那张象限贴图 —— 拼起来视觉上是连续的一台机器。

关键：blockstate **不做 y 旋转**，旋转已在贴图里烘焙好，
否则每个方块绕自身中心旋转，拼缝会错位。

贴图画法统一走 tools/tex_common.py（工业风 / 参考《异星工厂》）。

用法：python tools/gen_multiblock_assets.py
"""
import json
import os

from PIL import Image

import tex_common as T

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
ITEMS = os.path.join(ASSETS, "items")

FACINGS = ["north", "east", "south", "west"]


def oriented(img, facing):
    """把本地坐标系的整机图转成该朝向"从上看"的排布。

    本地图：列 = dx（机器右手），行 = dz（机器正面），上方 = 北。
      north: 上下翻转（正面朝北，dz 增长朝南）
      south: 左右翻转
      east : 逆时针 90
      west : 顺时针 90
    """
    if facing == "north":
        return img.transpose(Image.FLIP_TOP_BOTTOM)
    if facing == "south":
        return img.transpose(Image.FLIP_LEFT_RIGHT)
    if facing == "east":
        return img.transpose(Image.ROTATE_90)
    if facing == "west":
        return img.transpose(Image.ROTATE_270)
    return img


def split_save(img, size, name, facing):
    """切 N×N 象限并保存，返回 part -> 贴图文件名。"""
    out = {}
    for dz in range(size):
        for dx in range(size):
            part = dz * size + dx
            quad = img.crop((dx * 16, dz * 16, dx * 16 + 16, dz * 16 + 16))
            fn = f"{name}_{part}_{facing}"
            quad.save(os.path.join(TEX_BLOCK, fn + ".png"))
            out[part] = fn
    return out


def write_model(prefix, part, facing, side_file, top_file, active_suffix=""):
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"astrofactory:block/{side_file}",
            "down": f"astrofactory:block/{side_file}",
            "up": f"astrofactory:block/{top_file}",
            "north": f"astrofactory:block/{side_file}",
            "south": f"astrofactory:block/{side_file}",
            "east": f"astrofactory:block/{side_file}",
            "west": f"astrofactory:block/{side_file}",
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
    path = os.path.join(MODELS_BLOCK, f"{prefix}{active_suffix}_{part}_{facing}.json")
    json.dump(model, open(path, "w", encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)


def build(name, size, theme_key, has_active=True):
    """生成全部资源。name 同时用作方块 ID 与模型前缀。"""
    variants = {}
    active_values = [False, True] if has_active else [None]

    for active in active_values:
        suffix = "_active" if active else ""
        base_side = T.machine_side(size, size, theme_key, bool(active))
        base_top = T.machine_top(size, size, theme_key, bool(active))
        for facing in FACINGS:
            side_files = split_save(oriented(base_side, facing), size,
                                    f"{name}_side{suffix}", facing)
            top_files = split_save(oriented(base_top, facing), size,
                                   f"{name}_top{suffix}", facing)
            for part in range(size * size):
                write_model(name, part, facing, side_files[part],
                            top_files[part], suffix)
                key = f"facing={facing},part={part}"
                if has_active:
                    key += f",active={'true' if active else 'false'}"
                variants[key] = {"model": f"astrofactory:block/{name}{suffix}_{part}_{facing}"}

    json.dump({"variants": variants},
              open(os.path.join(BLOCKSTATES, name + ".json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    # 方块物品定义（part=0 / facing=north 的模型做图标）
    json.dump({"model": {"type": "minecraft:model",
                         "model": f"astrofactory:block/{name}_0_north"}},
              open(os.path.join(ITEMS, name + ".json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print(f"built {name}: {size}x{size}, {size * size} parts, {len(variants)} variants")


def main():
    build("burner_miner", 2, "miner", has_active=True)
    build("stone_furnace", 2, "furnace", has_active=True)
    build("assembler", 3, "assembler", has_active=True)
    build("lab", 3, "lab", has_active=False)


if __name__ == "__main__":
    main()
