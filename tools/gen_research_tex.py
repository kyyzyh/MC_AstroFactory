import os, random
from PIL import Image, ImageDraw
OUT = r"src/main/resources/assets/astrofactory/textures/block"
S = 16

def base(color, seed):
    img = Image.new("RGBA", (S, S), color)
    px = img.load()
    rnd = random.Random(seed)
    r, g, b = color[:3]
    for y in range(S):
        for x in range(S):
            d = rnd.randint(-8, 8)
            px[x, y] = (max(0,min(255,r+d)), max(0,min(255,g+d)), max(0,min(255,b+d)), 255)
    return img

# 桌面：木质 + 网格（研究图纸）
img = base((150, 118, 78), 11)
d = ImageDraw.Draw(img)
d.rectangle([0,0,S-1,S-1], outline=(96, 72, 46, 255))
for x in range(0, S, 4):
    d.line([(x,0),(x,S-1)], fill=(128, 100, 66, 255))
for y in range(0, S, 4):
    d.line([(0,y),(S-1,y)], fill=(128, 100, 66, 255))
# 中心图纸方块
d.rectangle([5,5,10,10], fill=(226, 222, 208, 255))
d.line([(6,7),(9,7)], fill=(120, 140, 180, 255))
d.line([(6,9),(9,9)], fill=(120, 140, 180, 255))
img.save(os.path.join(OUT, "research_top.png"))

# 侧面：木纹
img = base((118, 90, 58), 12)
d = ImageDraw.Draw(img)
d.rectangle([0,0,S-1,S-1], outline=(78, 58, 36, 255))
for y in range(1, S-1, 3):
    d.line([(1,y),(S-2,y)], fill=(100, 76, 48, 255))
img.save(os.path.join(OUT, "research_side.png"))
print("research textures generated")
