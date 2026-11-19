"""生成 AstroFactory 中间物品纹理（16x16，程序化绘制，透明背景）。"""
import os
import math
import random

from PIL import Image, ImageDraw

OUT = r"src/main/resources/assets/astrofactory/textures/item"
os.makedirs(OUT, exist_ok=True)
S = 16


def new_img():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def shade(color, d):
    r, g, b = color[:3]
    return (max(0, min(255, r + d)),
            max(0, min(255, g + d)),
            max(0, min(255, b + d)), 255)


def grain(img, color, seed=0, amount=8):
    """给不透明像素加细微噪点，模拟原版材质颗粒感。"""
    rnd = random.Random(seed)
    px = img.load()
    for y in range(S):
        for x in range(S):
            p = px[x, y]
            if p[3] == 0:
                continue
            d = rnd.randint(-amount, amount)
            px[x, y] = shade(color if p[:3] == color[:3] else p, d)


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)


# ---------------------------------------------------------------- 铁齿轮
# 8 齿圆盘：中心孔 + 内圈 + 外齿
GEAR = (176, 180, 188)
GEAR_DARK = (108, 112, 120)
GEAR_LIGHT = (222, 226, 232)

img = new_img()
d = ImageDraw.Draw(img)
cx = cy = 7.5

# 外齿（8 个方向的小方块）
for k in range(8):
    ang = math.radians(k * 45)
    tx = cx + math.cos(ang) * 6.2
    ty = cy + math.sin(ang) * 6.2
    d.rectangle([tx - 1, ty - 1, tx + 1, ty + 1], fill=GEAR)
# 齿轮本体
d.ellipse([1, 1, 14, 14], fill=GEAR)
# 内圈暗环
d.ellipse([2, 2, 13, 13], outline=GEAR_DARK)
# 中心孔
d.ellipse([6, 6, 9, 9], fill=(0, 0, 0, 0))
d.ellipse([6, 6, 9, 9], outline=GEAR_DARK)
# 高光（左上弧）
d.arc([2, 2, 13, 13], 150, 250, fill=GEAR_LIGHT)
d.arc([3, 3, 12, 12], 160, 240, fill=GEAR_LIGHT)
grain(img, GEAR, 11, 7)
save(img, "iron_gear")

# ---------------------------------------------------------------- 铜线圈
# 螺旋缠绕：横向铜条 + 左右绕线柱
COPPER = (206, 122, 74)
COPPER_DARK = (138, 74, 40)
COPPER_LIGHT = (238, 166, 118)

img = new_img()
d = ImageDraw.Draw(img)
# 绕线柱（左右竖条）
d.rectangle([2, 3, 3, 12], fill=COPPER_DARK)
d.rectangle([12, 3, 13, 12], fill=COPPER_DARK)
# 缠绕的铜线（横条，交替明暗模拟绕圈）
for i, y in enumerate(range(3, 13, 2)):
    d.rectangle([2, y, 13, y + 1], fill=COPPER if i % 2 == 0 else shade(COPPER, -22))
    d.line([(3, y), (12, y)], fill=COPPER_LIGHT)
# 两端引出线
d.line([(13, 4), (15, 4)], fill=COPPER_LIGHT)
d.line([(0, 11), (2, 11)], fill=COPPER_LIGHT)
# 铁芯
d.rectangle([7, 1, 8, 14], fill=(150, 154, 162))
d.rectangle([7, 1, 7, 14], fill=(196, 200, 208))
grain(img, COPPER, 21, 6)
save(img, "copper_coil")

# ---------------------------------------------------------------- 电路板
# 绿色基板 + 金色走线 + 焊点
BOARD = (46, 108, 62)
BOARD_DARK = (28, 70, 40)
GOLD = (222, 176, 58)

img = new_img()
d = ImageDraw.Draw(img)
d.rectangle([1, 2, 14, 13], fill=BOARD)
d.rectangle([1, 2, 14, 13], outline=BOARD_DARK)
# 走线
d.line([(3, 4), (12, 4)], fill=GOLD)
d.line([(12, 4), (12, 8)], fill=GOLD)
d.line([(3, 7), (9, 7)], fill=GOLD)
d.line([(3, 10), (12, 10)], fill=GOLD)
d.line([(6, 7), (6, 10)], fill=GOLD)
# 芯片
d.rectangle([7, 5, 10, 8], fill=(38, 38, 44))
d.point((8, 6), fill=(150, 150, 160))
# 焊点
for (x, y) in [(3, 4), (12, 8), (3, 10), (9, 7)]:
    d.rectangle([x - 1, y - 1, x, y], fill=(246, 232, 180))
grain(img, BOARD, 31, 6)
save(img, "circuit_board")

print("all item textures generated")
