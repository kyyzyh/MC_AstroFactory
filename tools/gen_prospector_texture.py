"""一次性脚本：生成探矿仪 16x16 贴图（磁针罗盘造型）。

风格对齐 astrofactory 现有物品：硬边像素、无抗锯齿、暗色描边。
输出：assets/astrofactory/textures/item/prospector.png
"""
from PIL import Image, ImageDraw
import os

SIZE = 16
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 调色板
EDGE = (58, 52, 44, 255)       # 深棕描边（和现有工具一致）
RIM = (150, 120, 62, 255)      # 黄铜外圈
RIM_HI = (198, 164, 92, 255)   # 黄铜高光
FACE = (222, 214, 196, 255)    # 表盘米白
FACE_SH = (186, 176, 156, 255) # 表盘阴影
NEEDLE_N = (176, 48, 40, 255)  # 指北红针
NEEDLE_S = (92, 96, 104, 255)  # 指南灰针
TICK = (110, 100, 84, 255)     # 刻度

# 圆形罗盘外圈（半径 6.6，中心 8,8）
cx, cy = 7.5, 7.5
R_OUT = 6.9
R_IN = 5.3

for y in range(SIZE):
    for x in range(SIZE):
        dx = x - cx
        dy = y - cy
        dist = (dx * dx + dy * dy) ** 0.5
        if dist <= R_OUT:
            if dist <= R_IN:
                # 表盘：左上亮，右下暗
                img.putpixel((x, y), FACE if (dx + dy) < 1.5 else FACE_SH)
            else:
                # 外圈：左上高光
                img.putpixel((x, y), RIM_HI if (dx + dy) < -1.0 else RIM)

# 外描边（最外一圈）
for y in range(SIZE):
    for x in range(SIZE):
        dx = x - cx
        dy = y - cy
        dist = (dx * dx + dy * dy) ** 0.5
        if R_OUT - 0.9 < dist <= R_OUT:
            img.putpixel((x, y), EDGE)

# 4 个方位刻度
for tx, ty in ((7, 2), (7, 13), (2, 7), (13, 7)):
    img.putpixel((tx, ty), TICK)

# 指针：指北（红）从中心向上 3 格，指南（灰）向下 2 格
d.line([(7, 5), (7, 7)], fill=NEEDLE_N)
d.line([(8, 5), (8, 7)], fill=NEEDLE_N)
d.line([(7, 8), (7, 9)], fill=NEEDLE_S)
d.line([(8, 8), (8, 9)], fill=NEEDLE_S)
# 中心轴
d.point((7, 7), fill=EDGE)
d.point((8, 7), fill=EDGE)

# 顶部小环（挂绳孔）
d.point((7, 0), fill=EDGE)
d.point((8, 0), fill=EDGE)

out = os.path.join("src", "main", "resources", "assets", "astrofactory", "textures", "item", "prospector.png")
os.makedirs(os.path.dirname(out), exist_ok=True)
img.save(out)
print("saved", out, os.path.getsize(out), "bytes")
