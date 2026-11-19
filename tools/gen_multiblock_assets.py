"""为多方块机器生成"一体机"贴图与分块模型。

设计要点（规格 §6「2×2/3×3 要有'一体机'观感」+ Q4「每个 dummy 各渲染自己那份模型」）：

1. 先生成 N*16 × N*16 的整机贴图（机器本地坐标系：列 = dx 机器右手方向，行 = dz 机器正面方向）；
2. 朝向烘焙：按 FACING 对整机图整体旋转/翻转，得到该朝向"从上看"的正确排布；
3. 再按 N×N 切成 N*N 张 16×16 象限贴图；
4. 每格一个 cube 模型，用自己那张象限贴图 —— 拼起来视觉上是连续的一台机器。

关键：blockstate **不做 y 旋转**，旋转已在贴图里烘焙好，
否则每个方块绕自身中心旋转，拼缝会错位。

用法：python tools/gen_multiblock_assets.py
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

IRON_DARK = (66, 70, 76)
IRON = (104, 109, 116)
IRON_LIGHT = (146, 152, 160)
COAL = (34, 32, 32)
GLOW = (248, 196, 72)
GLOW_DEEP = (226, 128, 40)
STEEL = (168, 176, 186)

FACINGS = ["north", "east", "south", "west"]


def _noise(img, amount=6):
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            d = ((x * 7 + y * 13) % (amount * 2 + 1)) - amount
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)),
                        max(0, min(255, b + d)), a)
    return img


def machine_side(size, active=False):
    """整机侧面（本地坐标：列 = dx 机器右手方向，行 = dz 机器正面方向）。"""
    px = size * 16
    img = _noise(Image.new("RGBA", (px, px), IRON + (255,)), 6)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, px - 1, px - 1], outline=IRON_DARK + (255,), width=2)
    d.rectangle([4, 4, px - 5, px - 5], outline=IRON_LIGHT + (255,), width=1)
    cx0, cy0 = px // 2 - px // 5, px // 2 - px // 5
    cx1, cy1 = px // 2 + px // 5, px // 2 + px // 5
    d.rectangle([cx0, cy0, cx1, cy1], fill=IRON_DARK + (255,), outline=STEEL + (255,))
    if active:
        d.rectangle([cx0 + 2, cy0 + 2, cx1 - 2, cy1 - 2], fill=GLOW_DEEP + (255,))
        d.rectangle([cx0 + 4, cy0 + 4, cx1 - 4, cy1 - 4], fill=GLOW + (255,))
    else:
        for y in range(cy0 + 2, cy1 - 1, 3):
            d.line([cx0 + 2, y, cx1 - 2, y], fill=COAL + (255,))
    for (rx, ry) in ((6, 6), (px - 9, 6), (6, px - 9), (px - 9, px - 9)):
        d.point((rx, ry), fill=STEEL + (255,))
        d.point((rx + 1, ry), fill=IRON_LIGHT + (255,))
    return img


def machine_top(size, active=False):
    """整机顶面。"""
    px = size * 16
    img = _noise(Image.new("RGBA", (px, px), IRON_DARK + (255,)), 6)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, px - 1, px - 1], outline=(48, 51, 56, 255), width=2)
    inset = px // 4
    d.rectangle([inset, inset, px - 1 - inset, px - 1 - inset],
                fill=IRON + (255,), outline=STEEL + (255,))
    hole = px // 8
    d.rectangle([px // 2 - hole, px // 2 - hole, px // 2 + hole, px // 2 + hole],
                fill=(GLOW if active else COAL) + (255,))
    d.line([px // 2, 3, px // 2, px - 4], fill=IRON_LIGHT + (255,))
    d.line([3, px // 2, px - 4, px // 2], fill=IRON_LIGHT + (255,))
    return img


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


def build(name, size, has_active=True):
    """生成全部资源。name 同时用作方块 ID 与模型前缀。"""
    variants = {}
    active_values = [False, True] if has_active else [None]

    for active in active_values:
        suffix = "_active" if active else ""
        base_side = machine_side(size, active)
        base_top = machine_top(size, active)
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
    print(f"built {name}: {size}x{size}, {size * size} parts, {len(variants)} variants")


def main():
    build("burner_miner", 2, has_active=True)


if __name__ == "__main__":
    main()
