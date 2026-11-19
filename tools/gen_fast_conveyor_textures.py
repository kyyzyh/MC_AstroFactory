"""生成 AstroFactory 高速传送带纹理（16x16）。"""
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


# ---------- 高速传送带顶面：冷蓝色带 + 箭头纹（表示高速） ----------
img = noise_base((46, 54, 72), 7)
d = ImageDraw.Draw(img)
for y in range(0, S, 4):
    d.line([(0, y), (S - 1, y)], fill=(32, 38, 54, 255))
    d.line([(0, y + 1), (S - 1, y + 1)], fill=(68, 80, 104, 255))
# 中央箭头（指向带的前进方向，north 朝上）
d.polygon([(7, 2), (11, 6), (9, 6), (9, 10), (5, 10), (5, 6), (3, 6)],
          fill=(96, 190, 255, 255))
d.polygon([(7, 4), (9, 6), (7, 6)], fill=(180, 226, 255, 255))
# 两侧导轨（亮蓝金属）
d.rectangle([0, 0, 1, S - 1], fill=(124, 160, 200, 255))
d.rectangle([S - 2, 0, S - 1, S - 1], fill=(124, 160, 200, 255))
d.rectangle([1, 0, 1, S - 1], fill=(84, 110, 148, 255))
d.rectangle([S - 2, 0, S - 2, S - 1], fill=(84, 110, 148, 255))
save(img, "fast_conveyor_top")

# ---------- 高速传送带侧面：蓝灰金属 + 散热槽 ----------
img = noise_base((92, 104, 124), 8)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(52, 62, 78, 255))
d.rectangle([0, 0, S - 1, 2], fill=(132, 150, 176, 255))
d.rectangle([0, S - 3, S - 1, S - 1], fill=(46, 54, 70, 255))
for y in range(5, 11, 3):
    d.line([(3, y), (S - 4, y)], fill=(60, 70, 88, 255))
save(img, "fast_conveyor_side")

print("all fast conveyor textures generated")
