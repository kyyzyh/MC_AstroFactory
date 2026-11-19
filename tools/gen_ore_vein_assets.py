"""为矿脉方块生成 blockstate / 模型 / 贴图。

矿脉有 kind (iron/copper/coal/stone) × richness (rich/normal/poor/depleted) = 16 变体。
每个变体一张 16x16 贴图，一个 cube 模型。
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

# 矿种基础色
KIND_COLORS = {
    "iron":   (180, 120, 70),   # 铁锈橙
    "copper": (200, 130, 60),   # 铜绿
    "coal":   (45, 42, 42),     # 煤黑
    "stone":  (130, 128, 124),  # 石头灰
}
RICHNESS = ["rich", "normal", "poor", "depleted"]
# richness → 亮度系数
RICHNESS_BRIGHT = {"rich": 1.3, "normal": 1.0, "poor": 0.7, "depleted": 0.4}


def noise(img, amount=8):
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            d = ((x * 7 + y * 13) % (amount * 2 + 1)) - amount
            px[x, y] = (
                max(0, min(255, r + d)),
                max(0, min(255, g + d)),
                max(0, min(255, b + d)),
                a,
            )
    return img


def make_ore_texture(kind, richness):
    base = KIND_COLORS[kind]
    bright = RICHNESS_BRIGHT[richness]
    color = tuple(max(0, min(255, int(c * bright))) for c in base)
    img = Image.new("RGBA", (16, 16), color + (255,))
    d = ImageDraw.Draw(img)
    # 矿点斑点
    if richness == "rich":
        spots = 8
    elif richness == "normal":
        spots = 5
    elif richness == "poor":
        spots = 3
    else:
        spots = 1
    import random
    rng = random.Random(hash((kind, richness)) & 0xFFFF)
    spot_color = tuple(max(0, min(255, int(c * 1.6))) for c in color)
    for _ in range(spots):
        x = rng.randint(2, 13)
        y = rng.randint(2, 13)
        r = rng.randint(1, 2)
        d.ellipse([x - r, y - r, x + r, y + r], fill=spot_color + (255,))
    # 边框
    d.rectangle([0, 0, 15, 15], outline=tuple(max(0, min(255, int(c * 0.5))) for c in color) + (255,), width=1)
    return noise(img, 6)


def build():
    os.makedirs(TEX_BLOCK, exist_ok=True)
    os.makedirs(MODELS_BLOCK, exist_ok=True)
    os.makedirs(BLOCKSTATES, exist_ok=True)

    # 1. 生成贴图
    tex_names = {}
    for kind in KIND_COLORS:
        for r in RICHNESS:
            name = f"ore_vein_{kind}_{r}"
            img = make_ore_texture(kind, r)
            img.save(os.path.join(TEX_BLOCK, name + ".png"))
            tex_names[(kind, r)] = name
    print(f"wrote {len(tex_names)} ore textures")

    # 2. blockstate: kind × richness = 16 variants
    variants = {}
    for kind in KIND_COLORS:
        for r in RICHNESS:
            key = f"kind={kind},richness={r}"
            variants[key] = {"model": "astrofactory:block/ore_vein"}
    blockstate = {"variants": variants}
    with open(os.path.join(BLOCKSTATES, "ore_vein.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(blockstate, f, ensure_ascii=False, indent=2)
    print(f"built ore_vein blockstate: {len(variants)} variants")

    # 3. 模型：用 custom data 的 kind/richness 选贴图
    # 26.3 不支持自定义模型在 blockstate 里直接用 texture 变量，
    # 所以我们为每个变体生成一个独立模型文件
    for kind in KIND_COLORS:
        for r in RICHNESS:
            tex = tex_names[(kind, r)]
            model = {
                "parent": "minecraft:block/block",
                "textures": {
                    "particle": f"astrofactory:block/{tex}",
                    "down": f"astrofactory:block/{tex}",
                    "up": f"astrofactory:block/{tex}",
                    "north": f"astrofactory:block/{tex}",
                    "south": f"astrofactory:block/{tex}",
                    "east": f"astrofactory:block/{tex}",
                    "west": f"astrofactory:block/{tex}",
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
            path = os.path.join(MODELS_BLOCK, f"ore_vein_{kind}_{r}.json")
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(model, f, ensure_ascii=False, indent=2)
    print(f"wrote {len(KIND_COLORS) * len(RICHNESS)} ore models")

    # 4. 重写 blockstate 指向每个变体的独立模型
    variants = {}
    for kind in KIND_COLORS:
        for r in RICHNESS:
            key = f"kind={kind},richness={r}"
            variants[key] = {"model": f"astrofactory:block/ore_vein_{kind}_{r}"}
    blockstate = {"variants": variants}
    with open(os.path.join(BLOCKSTATES, "ore_vein.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(blockstate, f, ensure_ascii=False, indent=2)
    print(f"final ore_vein blockstate: {len(variants)} variants")


if __name__ == "__main__":
    build()
