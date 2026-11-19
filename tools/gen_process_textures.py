"""生成 AstroFactory 熔炼炉与制作台纹理（16x16）。"""
import os
import random

from PIL import Image, ImageDraw

OUT = r"src/main/resources/assets/astrofactory/textures/block"
os.makedirs(OUT, exist_ok=True)
S = 16


def noise_base(color, seed=0):
    img = Image.new("RGBA", (S, S), color)
    px = img.load()
    rnd = random.Random(seed)
    r, g, b = color[:3]
    for y in range(S):
        for x in range(S):
            d = rnd.randint(-9, 9)
            px[x, y] = (max(0, min(255, r + d)),
                        max(0, min(255, g + d)),
                        max(0, min(255, b + d)), 255)
    return img


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)


# ---------- 熔炼炉侧面：砖石 + 炉口 ----------
img = noise_base((104, 88, 78), 41)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(62, 52, 46, 255))
# 砖缝
for y in (5, 10):
    d.line([(1, y), (S - 2, y)], fill=(74, 62, 54, 255))
for x in (5, 10):
    d.line([(x, 1), (x, 4)], fill=(74, 62, 54, 255))
# 炉口（下方开口）
d.rectangle([4, 8, 11, 13], fill=(38, 32, 30, 255))
d.rectangle([4, 8, 11, 13], outline=(150, 132, 118, 255))
save(img, "smelter_side")

# ---------- 熔炼炉激活态：炉口发橙光 ----------
img = Image.open(os.path.join(OUT, "smelter_side.png")).convert("RGBA")
d = ImageDraw.Draw(img)
d.rectangle([5, 9, 10, 12], fill=(240, 140, 40, 255))
d.rectangle([6, 10, 9, 12], fill=(255, 208, 96, 255))
save(img, "smelter_active")

# ---------- 熔炼炉顶面：金属盖 + 排气孔 ----------
img = noise_base((118, 120, 126), 42)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(72, 74, 80, 255))
d.ellipse([4, 4, 11, 11], fill=(72, 74, 80, 255))
d.ellipse([6, 6, 9, 9], fill=(150, 152, 158, 255))
for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
    d.point((x, y), fill=(168, 170, 176, 255))
save(img, "smelter_top")

# ---------- 制作台侧面：木纹 + 工具槽 ----------
img = noise_base((126, 96, 62), 43)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(78, 58, 36, 255))
for y in range(2, S - 1, 4):
    d.line([(1, y), (S - 2, y)], fill=(104, 78, 50, 255))
    d.line([(1, y + 1), (S - 2, y + 1)], fill=(146, 112, 74, 255))
# 台面横条
d.rectangle([0, 0, S - 1, 3], fill=(158, 124, 84, 255))
d.rectangle([0, 3, S - 1, 4], fill=(94, 70, 44, 255))
save(img, "crafter_side")

# ---------- 制作台激活态：绿色指示 ----------
img = Image.open(os.path.join(OUT, "crafter_side.png")).convert("RGBA")
d = ImageDraw.Draw(img)
d.rectangle([2, 1, S - 3, 2], fill=(120, 220, 130, 255))
save(img, "crafter_active")

# ---------- 制作台顶面：工作台格纹 ----------
img = noise_base((150, 116, 78), 44)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(88, 66, 42, 255))
# 3x3 网格
for i in range(1, 3):
    p = i * 5 + 1
    d.line([(p, 1), (p, S - 2)], fill=(104, 78, 50, 255))
    d.line([(1, p), (S - 2, p)], fill=(104, 78, 50, 255))
save(img, "crafter_top")

print("all textures generated")
