"""生成 AstroFactory 方块纹理（16x16，程序化绘制，风格贴近原版工业材质）。"""
import os
from PIL import Image, ImageDraw

OUT = r"src/main/resources/assets/astrofactory/textures/block"
os.makedirs(OUT, exist_ok=True)
S = 16

def noise_base(color, seed=0):
    """基础色 + 细微噪点，模拟原版材质颗粒感。"""
    img = Image.new("RGBA", (S, S), color)
    px = img.load()
    import random
    rnd = random.Random(seed)
    r, g, b = color[:3]
    a = 255
    for y in range(S):
        for x in range(S):
            d = rnd.randint(-9, 9)
            px[x, y] = (max(0, min(255, r + d)),
                        max(0, min(255, g + d)),
                        max(0, min(255, b + d)), a)
    return img

def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)

# ---------- 传送带顶面：深灰带 + 横向条纹 + 侧边导轨 ----------
img = noise_base((52, 55, 60), 1)
d = ImageDraw.Draw(img)
for y in range(0, S, 4):
    d.line([(0, y), (S - 1, y)], fill=(38, 40, 45, 255))
    d.line([(0, y + 1), (S - 1, y + 1)], fill=(72, 76, 84, 255))
# 两侧导轨
d.rectangle([0, 0, 1, S - 1], fill=(112, 116, 124, 255))
d.rectangle([S - 2, 0, S - 1, S - 1], fill=(112, 116, 124, 255))
d.rectangle([1, 0, 1, S - 1], fill=(84, 88, 96, 255))
d.rectangle([S - 2, 0, S - 2, S - 1], fill=(84, 88, 96, 255))
save(img, "conveyor_top")

# ---------- 传送带侧面：金属边框 + 中间暗带 ----------
img = noise_base((88, 92, 100), 2)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(58, 61, 68, 255))
d.rectangle([0, 0, S - 1, 2], fill=(120, 124, 132, 255))
d.rectangle([0, S - 3, S - 1, S - 1], fill=(52, 55, 60, 255))
save(img, "conveyor_side")

# ---------- 机械臂侧面：金属 + 铆钉 ----------
img = noise_base((140, 145, 152), 3)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(92, 96, 104, 255))
for (cx, cy) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
    d.rectangle([cx - 1, cy - 1, cx, cy], fill=(190, 194, 200, 255))
    d.point((cx, cy), fill=(96, 100, 108, 255))
d.rectangle([4, 7, 11, 9], fill=(70, 74, 82, 255))
save(img, "arm_side")

# ---------- 机械臂顶面：圆形轴心 ----------
img = noise_base((160, 165, 172), 4)
d = ImageDraw.Draw(img)
d.ellipse([4, 4, 11, 11], fill=(96, 100, 108, 255))
d.ellipse([6, 6, 9, 9], fill=(198, 202, 208, 255))
save(img, "arm_top")

# ---------- 采矿机侧面：深色石质 + 散热孔 ----------
img = noise_base((70, 72, 76), 5)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(44, 46, 50, 255))
for y in range(3, 13, 3):
    d.line([(2, y), (S - 3, y)], fill=(104, 108, 114, 255))
    d.line([(2, y + 1), (S - 3, y + 1)], fill=(52, 54, 58, 255))
save(img, "miner_side")

# ---------- 采矿机激活态：加橙色指示 ----------
img = Image.open(os.path.join(OUT, "miner_side.png")).convert("RGBA")
d = ImageDraw.Draw(img)
d.rectangle([2, 1, S - 3, 2], fill=(240, 150, 40, 255))
save(img, "miner_active")

# ---------- 组装机侧面：金属 + 网格 ----------
img = noise_base((96, 104, 116), 6)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, S - 1, S - 1], outline=(60, 66, 76, 255))
for x in range(2, S - 1, 4):
    d.line([(x, 2), (x, S - 3)], fill=(72, 78, 88, 255))
for y in range(2, S - 1, 4):
    d.line([(2, y), (S - 3, y)], fill=(72, 78, 88, 255))
d.rectangle([6, 6, 9, 9], fill=(150, 158, 170, 255))
save(img, "assembler_side")

# ---------- 组装机激活态：绿色指示 ----------
img = Image.open(os.path.join(OUT, "assembler_side.png")).convert("RGBA")
d = ImageDraw.Draw(img)
d.rectangle([2, 1, S - 3, 2], fill=(90, 220, 120, 255))
save(img, "assembler_active")

print("all textures generated")
