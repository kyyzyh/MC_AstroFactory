"""为 stage5 蒸汽电力方块生成资源。

覆盖：
- boiler        2x3 多方块（sizeX=2, sizeZ=3）
- steam_engine  3x5 多方块（sizeX=3, sizeZ=5）
- offshore_pump 1x2 多方块（sizeX=1, sizeZ=2）
- steam_pipe    单格 cube_all
- power_pole    单格 cube_all

矩形机器不烘焙朝向：所有 facing 共用同一套分块模型（最小实现，视觉可接受），
避免旋转后拼缝错位。part 索引 = dz*sizeX + dx，与 Java 端一致。

用法：python tools/gen_power_assets.py
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
ITEMS = os.path.join(ASSETS, "items")

IRON_DARK = (66, 70, 76)
IRON = (104, 109, 116)
IRON_LIGHT = (146, 152, 160)
COPPER = (196, 118, 66)
COPPER_LIGHT = (232, 156, 100)
COAL = (34, 32, 32)
GLOW = (248, 196, 72)
GLOW_DEEP = (226, 128, 40)
STEEL = (168, 176, 186)
WATER = (66, 128, 200)
PIPE = (120, 126, 134)
POLE_WOOD = (126, 96, 60)

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


def machine_side(sx, sz, active=False, tint=IRON):
    """整机侧面（列 = dx，行 = dz）。"""
    w, h = sx * 16, sz * 16
    img = _noise(Image.new("RGBA", (w, h), tint + (255,)), 6)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=IRON_DARK + (255,), width=2)
    d.rectangle([4, 4, w - 5, h - 5], outline=IRON_LIGHT + (255,), width=1)
    # 中央核心
    cw, ch = max(6, w // 3), max(6, h // 3)
    cx0, cy0 = w // 2 - cw // 2, h // 2 - ch // 2
    cx1, cy1 = cx0 + cw, cy0 + ch
    d.rectangle([cx0, cy0, cx1, cy1], fill=IRON_DARK + (255,), outline=STEEL + (255,))
    if active:
        pad = max(1, cw // 8)
        d.rectangle([cx0 + pad, cy0 + pad, cx1 - pad, cy1 - pad], fill=GLOW_DEEP + (255,))
        pad2 = max(1, cw // 6)
        if cx1 - pad2 > cx0 + pad2 and cy1 - pad2 > cy0 + pad2:
            d.rectangle([cx0 + pad2, cy0 + pad2, cx1 - pad2, cy1 - pad2], fill=GLOW + (255,))
    else:
        for y in range(cy0 + 2, cy1 - 1, 3):
            d.line([cx0 + 2, y, cx1 - 2, y], fill=COAL + (255,))
    # 螺栓
    for (rx, ry) in ((7, 7), (w - 9, 7), (7, h - 9), (w - 9, h - 9)):
        d.point((rx, ry), fill=STEEL + (255,))
    # 横向管路
    for y in range(10, h - 8, 24):
        d.line([6, y, w - 7, y], fill=IRON_DARK + (255,))
        d.line([6, y + 1, w - 7, y + 1], fill=IRON_LIGHT + (255,))
    return img


def machine_top(sx, sz, active=False):
    w, h = sx * 16, sz * 16
    img = _noise(Image.new("RGBA", (w, h), IRON_DARK + (255,)), 6)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=(48, 51, 56, 255), width=2)
    inset = max(4, w // 8)
    d.rectangle([inset, inset, w - 1 - inset, h - 1 - inset],
                fill=IRON + (255,), outline=STEEL + (255,))
    hole = max(4, min(w, h) // 8)
    d.rectangle([w // 2 - hole, h // 2 - hole, w // 2 + hole, h // 2 + hole],
                fill=(GLOW if active else COAL) + (255,))
    return img


def split_save(img, sx, sz, name, facing):
    out = {}
    for dz in range(sz):
        for dx in range(sx):
            part = dz * sx + dx
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


def build_multiblock(name, sx, sz, has_active=True, tint=IRON):
    variants = {}
    active_values = [False, True] if has_active else [None]

    for active in active_values:
        suffix = "_active" if active else ""
        base_side = machine_side(sx, sz, bool(active), tint)
        base_top = machine_top(sx, sz, bool(active))
        # 矩形机器不烘焙朝向：同一套模型用于 4 个 facing
        per_facing_side = {}
        per_facing_top = {}
        for facing in FACINGS:
            per_facing_side[facing] = split_save(base_side, sx, sz,
                                                 f"{name}_side{suffix}", facing)
            per_facing_top[facing] = split_save(base_top, sx, sz,
                                                f"{name}_top{suffix}", facing)
            for part in range(sx * sz):
                write_model(name, part, facing, per_facing_side[facing][part],
                            per_facing_top[facing][part], suffix)

        for facing in FACINGS:
            for part in range(sx * sz):
                key = f"facing={facing},part={part}"
                if has_active:
                    key += f",active={'true' if active else 'false'}"
                variants[key] = {"model": f"astrofactory:block/{name}{suffix}_{part}_{facing}"}

    json.dump({"variants": variants},
              open(os.path.join(BLOCKSTATES, name + ".json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    # 方块物品定义（用 part=0 / facing=north 的模型做图标）
    json.dump({"model": {"type": "minecraft:model",
                         "model": f"astrofactory:block/{name}_0_north"}},
              open(os.path.join(ITEMS, name + ".json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print(f"built {name}: {sx}x{sz}, {sx * sz} parts, {len(variants)} variants")


def build_simple_cube(name, side_painter):
    """单格方块：cube_all + 一张 16x16 贴图。"""
    tex = _noise(side_painter(), 4)
    tex.save(os.path.join(TEX_BLOCK, name + ".png"))

    model = {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"astrofactory:block/{name}"},
    }
    json.dump(model, open(os.path.join(MODELS_BLOCK, name + ".json"), "w",
                          encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    # 方块物品模型
    item_model = {"parent": f"astrofactory:block/{name}"}
    json.dump(item_model, open(os.path.join(MODELS_BLOCK, name + "_item.json"), "w",
                               encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    # 方块物品定义（26.3：items/<id>.json 指向模型）
    json.dump({"model": {"type": "minecraft:model",
                         "model": f"astrofactory:block/{name}"}},
              open(os.path.join(ITEMS, name + ".json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    # blockstate
    json.dump({"variants": {"": {"model": f"astrofactory:block/{name}"}}},
              open(os.path.join(BLOCKSTATES, name + ".json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print(f"built {name}: single cube")


def paint_pipe():
    img = Image.new("RGBA", (16, 16), PIPE + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(84, 88, 94, 255), width=1)
    # 横向铜管 + 法兰
    d.rectangle([0, 6, 15, 9], fill=COPPER + (255,))
    d.line([0, 6, 15, 6], fill=COPPER_LIGHT + (255,))
    for x in (3, 12):
        d.rectangle([x, 5, x + 1, 10], fill=IRON_DARK + (255,))
    return img


def paint_pole():
    img = Image.new("RGBA", (16, 16), POLE_WOOD + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(92, 68, 40, 255), width=1)
    # 竖木纹
    for x in (3, 8, 12):
        d.line([x, 1, x, 14], fill=(104, 78, 48, 255))
    # 顶部金属横担
    d.rectangle([1, 4, 14, 6], fill=STEEL + (255,))
    d.rectangle([1, 9, 14, 11], fill=STEEL + (255,))
    # 绝缘子
    for x in (2, 13):
        d.rectangle([x, 2, x + 1, 4], fill=(70, 74, 80, 255))
    return img


def main():
    build_multiblock("boiler", 2, 3, has_active=True, tint=IRON)
    build_multiblock("steam_engine", 3, 5, has_active=True, tint=STEEL)
    build_multiblock("offshore_pump", 1, 2, has_active=True, tint=(96, 120, 140))
    build_simple_cube("steam_pipe", paint_pipe)
    build_simple_cube("power_pole", paint_pole)


if __name__ == "__main__":
    main()
