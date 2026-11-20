"""为 stage5 蒸汽电力方块生成资源。

覆盖：
- boiler        2x3 多方块（sizeX=2, sizeZ=3）
- steam_engine  3x5 多方块（sizeX=3, sizeZ=5）
- offshore_pump 1x2 多方块（sizeX=1, sizeZ=2）
- steam_pipe    单格真管道（十字/直管几何 + 法兰）
- power_pole    单格真电线杆（细杆 + 横担 + 绝缘子）

矩形机器不烘焙朝向：所有 facing 共用同一套分块模型（最小实现，视觉可接受），
避免旋转后拼缝错位。part 索引 = dz*sizeX + dx，与 Java 端一致。

贴图画法统一走 tools/tex_common.py（工业风 / 参考《异星工厂》）。

用法：python tools/gen_power_assets.py
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

FACINGS = ["north", "east", "south", "west"]


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


def build_multiblock(name, sx, sz, theme_key, has_active=True):
    variants = {}
    active_values = [False, True] if has_active else [None]

    for active in active_values:
        suffix = "_active" if active else ""
        base_side = T.machine_side(sx, sz, theme_key, bool(active))
        base_top = T.machine_top(sx, sz, theme_key, bool(active))
        # 矩形机器不烘焙朝向：同一套模型用于 4 个 facing
        for facing in FACINGS:
            side_files = split_save(base_side, sx, sz, f"{name}_side{suffix}", facing)
            top_files = split_save(base_top, sx, sz, f"{name}_top{suffix}", facing)
            for part in range(sx * sz):
                write_model(name, part, facing, side_files[part],
                            top_files[part], suffix)

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
    json.dump({"model": {"type": "minecraft:model",
                         "model": f"astrofactory:block/{name}_0_north"}},
              open(os.path.join(ITEMS, name + ".json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print(f"built {name}: {sx}x{sz}, {sx * sz} parts, {len(variants)} variants")


# ---------------------------------------------------------------- 蒸汽管道

def build_pipe():
    """真管道：中心细管 + 六个方向的连接管段 + 法兰。

    26.3 的 `steam_pipe` 没有连接属性（Java 端只有 BlockEntity），
    所以这里用**固定十字管**模型：中心 + 四向水平 + 上下，视觉上任意方向都能接上，
    不会出现"断头"。
    """
    T.paint_pipe_body().save(os.path.join(TEX_BLOCK, "steam_pipe_body.png"))
    T.paint_pipe_flange().save(os.path.join(TEX_BLOCK, "steam_pipe_flange.png"))

    body = "#body"
    flange = "#flange"

    def elem(frm, to, tex, cull=None, uv=None):
        faces = {}
        for f in ("down", "up", "north", "south", "east", "west"):
            face = {"texture": tex}
            if uv:
                face["uv"] = uv
            faces[f] = face
        if cull:
            for f in cull:
                faces[f]["cullface"] = f
        return {"from": frm, "to": to, "faces": faces}

    elements = [
        # 中心接头（法兰块）
        elem([5, 5, 5], [11, 11, 11], flange),
        # 四个水平管段（半径 2px 的圆管近似）
        elem([0, 6, 6], [5, 10, 10], body),
        elem([11, 6, 6], [16, 10, 10], body),
        elem([6, 6, 0], [10, 10, 5], body),
        elem([6, 6, 11], [10, 10, 16], body),
        # 上下管段
        elem([6, 0, 6], [10, 5, 10], body),
        elem([6, 11, 6], [10, 16, 10], body),
        # 四向法兰环
        elem([0, 5, 5], [1, 11, 11], flange),
        elem([15, 5, 5], [16, 11, 11], flange),
        elem([5, 5, 0], [11, 11, 1], flange),
        elem([5, 5, 15], [11, 11, 16], flange),
        elem([5, 0, 5], [11, 1, 11], flange),
        elem([5, 15, 5], [11, 16, 11], flange),
    ]
    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": "astrofactory:block/steam_pipe_body",
            "body": "astrofactory:block/steam_pipe_body",
            "flange": "astrofactory:block/steam_pipe_flange",
        },
        "elements": elements,
    }
    json.dump(model, open(os.path.join(MODELS_BLOCK, "steam_pipe.json"), "w",
                          encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    # 物品模型：用同一模型（拿在手上是管道）
    json.dump({"parent": "astrofactory:block/steam_pipe"},
              open(os.path.join(MODELS_BLOCK, "steam_pipe_item.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    json.dump({"model": {"type": "minecraft:model",
                         "model": "astrofactory:block/steam_pipe"}},
              open(os.path.join(ITEMS, "steam_pipe.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    json.dump({"variants": {"": {"model": "astrofactory:block/steam_pipe"}}},
              open(os.path.join(BLOCKSTATES, "steam_pipe.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print("built steam_pipe: cross pipe")


# ---------------------------------------------------------------- 电线杆

def build_pole():
    """真电线杆：木杆 + 顶部钢横担 + 两个绝缘子 + 底部基座。"""
    T.paint_wood_tile().save(os.path.join(TEX_BLOCK, "power_pole_wood.png"))
    T.paint_metal_tile(T.STEEL).save(os.path.join(TEX_BLOCK, "power_pole_steel.png"))
    T.paint_metal_tile(T.IRON_DARK).save(os.path.join(TEX_BLOCK, "power_pole_base.png"))
    T.paint_insulator().save(os.path.join(TEX_BLOCK, "power_pole_insulator.png"))

    wood = "#wood"
    steel = "#steel"
    base = "#base"
    insu = "#insu"

    def elem(frm, to, tex, cull=None):
        faces = {f: {"texture": tex} for f in ("down", "up", "north", "south", "east", "west")}
        if cull:
            for f in cull:
                faces[f]["cullface"] = f
        return {"from": frm, "to": to, "faces": faces}

    elements = [
        # 石/铁基座
        elem([5, 0, 5], [11, 3, 11], base, cull=["down"]),
        # 木杆主体
        elem([6, 3, 6], [10, 15, 10], wood),
        # 顶部钢横担（沿 X）
        elem([1, 12, 7], [15, 14, 9], steel),
        # 斜撑
        elem([7, 11, 7], [9, 12, 9], steel),
        # 两个绝缘子
        elem([2, 14, 7], [4, 16, 9], insu, cull=["up"]),
        elem([12, 14, 7], [14, 16, 9], insu, cull=["up"]),
        # 杆顶帽
        elem([6, 15, 6], [10, 16, 10], steel, cull=["up"]),
    ]
    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": "astrofactory:block/power_pole_wood",
            "wood": "astrofactory:block/power_pole_wood",
            "steel": "astrofactory:block/power_pole_steel",
            "base": "astrofactory:block/power_pole_base",
            "insu": "astrofactory:block/power_pole_insulator",
        },
        "elements": elements,
    }
    json.dump(model, open(os.path.join(MODELS_BLOCK, "power_pole.json"), "w",
                          encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    json.dump({"parent": "astrofactory:block/power_pole"},
              open(os.path.join(MODELS_BLOCK, "power_pole_item.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    json.dump({"model": {"type": "minecraft:model",
                         "model": "astrofactory:block/power_pole"}},
              open(os.path.join(ITEMS, "power_pole.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    json.dump({"variants": {"": {"model": "astrofactory:block/power_pole"}}},
              open(os.path.join(BLOCKSTATES, "power_pole.json"), "w",
                   encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print("built power_pole: wooden pole + crossarm")


def main():
    build_multiblock("boiler", 2, 3, "boiler", has_active=True)
    build_multiblock("steam_engine", 3, 5, "engine", has_active=True)
    build_multiblock("offshore_pump", 1, 2, "pump", has_active=True)
    build_pipe()
    build_pole()


if __name__ == "__main__":
    main()
