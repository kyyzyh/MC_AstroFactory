"""生成 AstroFactory 的 16x16 像素贴图（程序化，可重复生成）。

用法：
    python tools/gen_textures.py

风格取向：工业金属 + 少量警示黄，尽量贴近原版 16px 像素风。
所有噪声使用固定种子，保证每次生成结果一致。
"""

from __future__ import annotations

import os
from PIL import Image

SIZE = 16
OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "astrofactory", "textures", "block",
)


def _hash(x: int, y: int, seed: int = 0) -> int:
    """确定性伪随机，返回 0..255。"""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return (n ^ (n >> 16)) & 0xFF


def _shade(rgb, amount: int):
    """整体提亮/压暗，amount 为 -255..255。"""
    return tuple(max(0, min(255, c + amount)) for c in rgb)


def _noise(rgb, x, y, strength=10, seed=0):
    """加一点噪点，避免大色块死板。"""
    d = (_hash(x, y, seed) % (strength * 2 + 1)) - strength
    return _shade(rgb, d)


def make_image(painter) -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            color = painter(x, y)
            if color is None:
                color = (0, 0, 0, 0)
            elif len(color) == 3:
                color = (*color, 255)
            px[x, y] = color
    return img


# ---------------------------------------------------------------- 调色板
STEEL_DARK = (58, 62, 68)
STEEL = (86, 92, 100)
STEEL_LIGHT = (124, 132, 142)
RUBBER = (38, 38, 40)
RUBBER_LIGHT = (56, 56, 60)
BRASS = (186, 132, 58)
BRASS_LIGHT = (222, 172, 92)
WARN_YELLOW = (216, 172, 52)
STONE_DARK = (74, 70, 66)
STONE = (104, 99, 93)
STONE_LIGHT = (134, 128, 120)
WOOD_DARK = (96, 68, 42)
WOOD = (128, 94, 58)
WOOD_LIGHT = (154, 118, 76)
FIRE = (232, 128, 32)
FIRE_HOT = (255, 196, 64)


# ---------------------------------------------------------------- 传送带
def conveyor_top(x, y):
    # 横向滚轮纹 + 中间行进方向箭头
    base = RUBBER if (y % 4) else RUBBER_LIGHT
    # 中线附近画 V 形箭头，指示前进方向（朝向 +Z 即图像下方）
    cx = 8
    # 箭头尖在底部
    for tip_y in (13, 6):
        spread = tip_y - y
        if 0 <= spread <= 3:
            if abs(x - cx) == spread:
                return _noise(WARN_YELLOW, x, y, 8, 3)
    # 两侧导轨
    if x <= 1 or x >= 14:
        return _noise(STEEL, x, y, 8, 5)
    return _noise(base, x, y, 7, 1)


def conveyor_side(x, y):
    if y <= 2:
        # 顶面边缘（带子厚度）
        return _noise(RUBBER_LIGHT, x, y, 6, 2)
    if y <= 5:
        return _noise(STEEL_LIGHT, x, y, 8, 4)
    # 下方机身 + 铆钉
    if y in (9, 10) and x % 5 == 2:
        return STEEL_LIGHT
    return _noise(STEEL_DARK, x, y, 9, 6)


# ---------------------------------------------------------------- 机械臂
def inserter_base(x, y):
    # 圆形底座 + 四角螺栓
    cx, cy, r = 7.5, 7.5, 6.0
    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
    if d > r:
        return _noise(STEEL_DARK, x, y, 8, 11)
    if d > r - 1.2:
        return _noise(STEEL_LIGHT, x, y, 8, 12)
    if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        return BRASS_LIGHT
    return _noise(STEEL, x, y, 9, 13)


def inserter_arm(x, y):
    # 黄铜关节 + 条纹
    if y % 3 == 0:
        return _noise(BRASS_LIGHT, x, y, 8, 21)
    return _noise(BRASS, x, y, 9, 22)


# ---------------------------------------------------------------- 采矿机
def miner_side(x, y):
    # 面板分格 + 散热孔
    if y in (0, 15) or x in (0, 15):
        return _noise(STEEL_LIGHT, x, y, 7, 31)
    if y in (7, 8):
        return _noise(STEEL_DARK, x, y, 7, 32)
    # 下半部分散热孔
    if y > 9 and (x % 3 == 1):
        return STEEL_DARK
    return _noise(STEEL, x, y, 8, 33)


def miner_top(x, y):
    # 中央圆形进气口
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    if d < 2.5:
        return _noise(RUBBER, x, y, 8, 41)
    if d < 3.8:
        return _noise(STEEL_LIGHT, x, y, 8, 42)
    if x in (0, 15) or y in (0, 15):
        return _noise(STEEL_DARK, x, y, 7, 43)
    return _noise(STEEL, x, y, 8, 44)


def miner_front(x, y):
    # 钻头 + 警示条
    if 11 <= y <= 14:
        return _noise(WARN_YELLOW, x, y, 8, 51) if (x // 2) % 2 == 0 else _noise(RUBBER, x, y, 8, 52)
    d = abs(x - 7.5)
    # 倒三角钻头
    if 3 <= y <= 10 and d <= (11 - y) * 0.9:
        return _noise(STEEL_LIGHT, x, y, 10, 53)
    if y in (0, 1):
        return _noise(STEEL_DARK, x, y, 7, 54)
    return _noise(STEEL, x, y, 8, 55)


# ---------------------------------------------------------------- 自动熔炉
def _stone_base(x, y, seed):
    # 砖块式石纹
    row = y // 4
    offset = 0 if row % 2 == 0 else 4
    if y % 4 == 0:
        return _noise(STONE_DARK, x, y, 8, seed)
    if (x + offset) % 8 == 0:
        return _noise(STONE_DARK, x, y, 8, seed + 1)
    base = STONE if _hash(x, y, seed + 2) % 3 else STONE_LIGHT
    return _noise(base, x, y, 10, seed + 3)


def smelter_side(x, y):
    return _stone_base(x, y, 61)


def smelter_top(x, y):
    # 中央烟道
    if 4 <= x <= 11 and 4 <= y <= 11:
        return _noise((26, 24, 24), x, y, 8, 71)
    return _stone_base(x, y, 72)


def smelter_front(x, y):
    # 炉门 + 栅格
    if 3 <= x <= 12 and 5 <= y <= 13:
        if y in (5, 13) or x in (3, 12):
            return _noise(STEEL_DARK, x, y, 8, 81)
        if (x % 3 == 0) or (y % 3 == 0):
            return _noise(STEEL, x, y, 8, 82)
        return _noise((30, 26, 24), x, y, 9, 83)
    return _stone_base(x, y, 84)


def smelter_front_lit(x, y):
    # 同样炉门，但内部透出火光
    if 3 <= x <= 12 and 5 <= y <= 13:
        if y in (5, 13) or x in (3, 12):
            return _noise(STEEL_DARK, x, y, 8, 81)
        if (x % 3 == 0) or (y % 3 == 0):
            return _noise(STEEL, x, y, 8, 82)
        # 火焰：中心最亮
        d = ((x - 7.5) ** 2 + (y - 10.0) ** 2) ** 0.5
        if d < 2.2:
            return _noise(FIRE_HOT, x, y, 12, 91)
        if d < 4.0:
            return _noise(FIRE, x, y, 14, 92)
        return _noise((150, 62, 20), x, y, 10, 93)
    return _stone_base(x, y, 84)


# ---------------------------------------------------------------- 储物箱
def crate_side(x, y):
    # 木板 + 上下金属包边
    if y <= 1 or y >= 14:
        return _noise(STEEL, x, y, 7, 101)
    # 板缝
    if y in (5, 6, 10, 11):
        return _noise(WOOD_DARK, x, y, 8, 102)
    if x in (0, 15):
        return _noise(WOOD_DARK, x, y, 8, 103)
    base = WOOD if _hash(x, y, 104) % 4 else WOOD_LIGHT
    return _noise(base, x, y, 12, 105)


def crate_top(x, y):
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(WOOD_DARK, x, y, 8, 111)
    if x in (7, 8) or y in (7, 8):
        return _noise(WOOD_DARK, x, y, 8, 112)
    base = WOOD if _hash(x, y, 113) % 4 else WOOD_LIGHT
    return _noise(base, x, y, 12, 114)


# ---------------------------------------------------------------- 发电机
COPPER = (188, 112, 66)
COPPER_LIGHT = (222, 148, 96)
COPPER_DARK = (132, 76, 42)


def generator_side(x, y):
    """侧面：钢板机身 + 散热格栅 + 底部警示条。"""
    if y <= 1 or y >= 14 or x <= 0 or x >= 15:
        return _noise(STEEL_DARK, x, y, 6, 201)
    # 底部警示斜纹
    if y >= 12:
        if (x + y) % 4 < 2:
            return _noise(WARN_YELLOW, x, y, 8, 202)
        return _noise(STEEL_DARK, x, y, 6, 203)
    # 中部散热格栅：横向深色条
    if 4 <= y <= 10:
        if y % 2 == 0:
            return _noise(STEEL_DARK, x, y, 5, 204)
        return _noise(STEEL, x, y, 9, 205)
    base = STEEL if _hash(x, y, 206) % 5 else STEEL_LIGHT
    return _noise(base, x, y, 10, 207)


def generator_top(x, y):
    """顶面：铜制排气管 + 钢制外壳。"""
    if x <= 0 or x >= 15 or y <= 0 or y >= 15:
        return _noise(STEEL_DARK, x, y, 6, 211)
    # 中心铜色圆管
    dx = x - 7.5
    dy = y - 7.5
    if dx * dx + dy * dy <= 12:
        if dx * dx + dy * dy <= 3:
            return _noise(COPPER_DARK, x, y, 6, 212)
        base = COPPER if _hash(x, y, 213) % 4 else COPPER_LIGHT
        return _noise(base, x, y, 10, 214)
    base = STEEL if _hash(x, y, 215) % 5 else STEEL_LIGHT
    return _noise(base, x, y, 9, 216)


def generator_front(x, y):
    """正面：燃烧口 + 火焰窗。"""
    if x <= 0 or x >= 15 or y <= 0 or y >= 15:
        return _noise(STEEL_DARK, x, y, 6, 221)
    # 中央燃烧窗口
    if 3 <= x <= 12 and 5 <= y <= 11:
        if x in (3, 12) or y in (5, 11):
            return _noise(STEEL_DARK, x, y, 6, 222)
        # 火焰纹理
        h = _hash(x, y, 223) % 10
        if h < 3:
            return _noise(FIRE_HOT, x, y, 12, 224)
        if h < 7:
            return _noise(FIRE, x, y, 14, 225)
        return _noise((96, 40, 16), x, y, 10, 226)
    if y >= 13:
        if (x + y) % 4 < 2:
            return _noise(WARN_YELLOW, x, y, 8, 227)
        return _noise(STEEL_DARK, x, y, 6, 228)
    base = STEEL if _hash(x, y, 229) % 5 else STEEL_LIGHT
    return _noise(base, x, y, 10, 230)


def generator_front_lit(x, y):
    """正面（燃烧中）：火焰更亮。"""
    if x <= 0 or x >= 15 or y <= 0 or y >= 15:
        return _noise(STEEL_DARK, x, y, 6, 221)
    if 3 <= x <= 12 and 5 <= y <= 11:
        if x in (3, 12) or y in (5, 11):
            return _noise(STEEL_DARK, x, y, 6, 222)
        h = _hash(x, y, 231) % 10
        if h < 5:
            return _noise((255, 240, 180), x, y, 10, 232)
        if h < 8:
            return _noise(FIRE_HOT, x, y, 12, 233)
        return _noise(FIRE, x, y, 14, 234)
    if y >= 13:
        if (x + y) % 4 < 2:
            return _noise(WARN_YELLOW, x, y, 8, 227)
        return _noise(STEEL_DARK, x, y, 6, 228)
    base = STEEL if _hash(x, y, 229) % 5 else STEEL_LIGHT
    return _noise(base, x, y, 10, 230)


# ---------------------------------------------------------------- 电缆
def cable_side(x, y):
    """电缆侧面：橙色绝缘皮 + 纵向高光。"""
    if y <= 2 or y >= 13:
        return _noise((0, 0, 0, 0), x, y, 0, 301)
    if x in (0, 15):
        return _noise((0, 0, 0, 0), x, y, 0, 302)
    # 中央高光
    if 6 <= y <= 9:
        base = COPPER_LIGHT if _hash(x, y, 303) % 3 else COPPER
        return _noise(base, x, y, 10, 304)
    base = COPPER if _hash(x, y, 305) % 4 else COPPER_DARK
    return _noise(base, x, y, 10, 306)


def cable_top(x, y):
    """电缆顶面：铜芯截面。"""
    dx = x - 7.5
    dy = y - 7.5
    if dx * dx + dy * dy > 20:
        return (0, 0, 0, 0)
    if dx * dx + dy * dy <= 4:
        return _noise(COPPER_LIGHT, x, y, 8, 311)
    base = COPPER if _hash(x, y, 312) % 4 else COPPER_DARK
    return _noise(base, x, y, 10, 313)


# ---------------------------------------------------------------- 研究台
PAPER = (238, 236, 226)
PAPER_DARK = (198, 194, 180)
INK = (44, 52, 68)
CYAN_GLOW = (96, 216, 232)


def research_side(x, y):
    """侧面：木框 + 内部书页。"""
    if y <= 2 or y >= 13:
        return _noise(WOOD_DARK, x, y, 8, 401)
    if x <= 1 or x >= 14:
        return _noise(WOOD, x, y, 8, 402)
    # 书页横线
    if y % 3 == 0:
        return _noise(PAPER_DARK, x, y, 6, 403)
    base = PAPER if _hash(x, y, 404) % 5 else PAPER_DARK
    return _noise(base, x, y, 6, 405)


def research_top(x, y):
    """顶面：摊开的图纸 + 中央青色核心。"""
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(WOOD_DARK, x, y, 8, 411)
    dx = x - 7.5
    dy = y - 7.5
    d2 = dx * dx + dy * dy
    if d2 <= 3:
        return _noise(CYAN_GLOW, x, y, 14, 412)
    if d2 <= 9:
        return _noise((64, 148, 168), x, y, 12, 413)
    # 图纸网格
    if x % 4 == 0 or y % 4 == 0:
        return _noise(PAPER_DARK, x, y, 5, 414)
    base = PAPER if _hash(x, y, 415) % 6 else PAPER_DARK
    return _noise(base, x, y, 7, 416)


# ---------------------------------------------------------------- 研究包
def research_pack(x, y):
    """研究包物品贴图：牛皮纸袋 + 青色封条。"""
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(WOOD_DARK, x, y, 8, 421)
    # 中央封条
    if 6 <= x <= 9:
        if y <= 3 or y >= 12:
            return _noise(CYAN_GLOW, x, y, 10, 422)
        return _noise((64, 148, 168), x, y, 10, 423)
    if y <= 3 or y >= 12:
        return _noise(WOOD, x, y, 8, 424)
    base = PAPER if _hash(x, y, 425) % 5 else PAPER_DARK
    return _noise(base, x, y, 8, 426)


# ---------------------------------------------------------------- 组装机
def assembler_side(x, y):
    """侧面：钢壳 + 上下警示条。"""
    if y <= 1 or y >= 14:
        return _noise(WARN_YELLOW, x, y, 8, 501) if (x // 2) % 2 == 0 else _noise(STEEL_DARK, x, y, 8, 502)
    if x <= 1 or x >= 14:
        return _noise(STEEL_DARK, x, y, 8, 503)
    # 散热格栅
    if 4 <= y <= 11 and x % 3 == 0:
        return _noise(RUBBER, x, y, 6, 504)
    base = STEEL if _hash(x, y, 505) % 4 else STEEL_LIGHT
    return _noise(base, x, y, 9, 506)


def assembler_top(x, y):
    """顶面：钢壳 + 中央 3x3 网格暗示。"""
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 8, 511)
    # 3x3 网格线（4/8/12）
    if x in (4, 8, 12) or y in (4, 8, 12):
        return _noise(RUBBER_LIGHT, x, y, 6, 512)
    base = STEEL if _hash(x, y, 513) % 3 else STEEL_LIGHT
    return _noise(base, x, y, 8, 514)


def assembler_front(x, y):
    """正面：钢壳 + 中央机械爪窗口。"""
    if y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 8, 521)
    if x <= 1 or x >= 14:
        return _noise(STEEL_DARK, x, y, 8, 522)
    # 中央窗口
    if 4 <= x <= 11 and 4 <= y <= 11:
        if (x - 4) % 3 == 2 or (y - 4) % 3 == 2:
            return _noise(RUBBER, x, y, 6, 523)
        return _noise(STEEL_LIGHT, x, y, 8, 524)
    base = STEEL if _hash(x, y, 525) % 4 else STEEL_LIGHT
    return _noise(base, x, y, 9, 526)


def assembler_front_lit(x, y):
    """正面（工作中）：窗口透出蓝光。"""
    if y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 8, 521)
    if x <= 1 or x >= 14:
        return _noise(STEEL_DARK, x, y, 8, 522)
    if 4 <= x <= 11 and 4 <= y <= 11:
        if (x - 4) % 3 == 2 or (y - 4) % 3 == 2:
            return _noise((40, 70, 110), x, y, 8, 531)
        return _noise((90, 160, 255), x, y, 16, 532)
    base = STEEL if _hash(x, y, 525) % 4 else STEEL_LIGHT
    return _noise(base, x, y, 9, 526)


# ---------------------------------------------------------------- 高级研究包
def advanced_research_pack(x, y):
    """高级研究包：深色盒 + 粉色封条（对应异星工厂绿瓶的进阶感）。"""
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise((52, 44, 66), x, y, 8, 541)
    # 中央封条
    if 6 <= x <= 9:
        if y <= 3 or y >= 12:
            return _noise((255, 123, 172), x, y, 10, 542)
        return _noise((180, 70, 120), x, y, 10, 543)
    if y <= 3 or y >= 12:
        return _noise((90, 76, 110), x, y, 8, 544)
    base = (120, 100, 148) if _hash(x, y, 545) % 5 else (100, 84, 126)
    return _noise(base, x, y, 8, 546)


# ---------------------------------------------------------------- 科技终端
def tech_terminal(x, y):
    """科技终端物品：手持平板 + 青色屏幕。"""
    # 外壳
    if x <= 2 or x >= 13 or y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 8, 551)
    # 屏幕
    if 4 <= x <= 11 and 3 <= y <= 11:
        # 屏幕上的科技树小图（节点 + 连线）
        if (x, y) in ((5, 5), (9, 5), (7, 8)):
            return _noise((80, 255, 180), x, y, 10, 552)
        if (5 < x < 9 and y == 5) or (x == 7 and 5 < y < 8):
            return _noise((40, 120, 90), x, y, 8, 553)
        return _noise((16, 40, 52), x, y, 8, 554)
    # 底部按钮
    if 6 <= x <= 9 and 12 <= y <= 13:
        return _noise(CYAN_GLOW, x, y, 10, 555)
    return _noise(STEEL, x, y, 8, 556)


# ---------------------------------------------------------------- 扳手
def wrench(x, y):
    """扳手物品贴图：斜置的钢制扳手，透明背景。"""
    # 用简单的对角形状勾勒扳手轮廓
    # 手柄：从左下到右上
    on_handle = abs((x - 4) - (y - 11)) <= 1 and 3 <= x <= 11 and 4 <= y <= 12
    # 头部：右上的开口
    dx = x - 11.5
    dy = y - 4.5
    d2 = dx * dx + dy * dy
    on_head = 1.5 <= d2 <= 9.0
    if not (on_handle or on_head):
        return (0, 0, 0, 0)
    if on_head and d2 < 4.0:
        return (0, 0, 0, 0)  # 开口镂空
    base = STEEL_LIGHT if _hash(x, y, 431) % 3 else STEEL
    return _noise(base, x, y, 10, 432)


# ---------------------------------------------------------------- v0.5 木箱
def wood_crate_side(x, y):
    # 纯木板 + 板缝，无金属包边（开局廉价感）
    if y in (0, 15) or x in (0, 15):
        return _noise(WOOD_DARK, x, y, 8, 201)
    if y in (5, 10):
        return _noise(WOOD_DARK, x, y, 8, 202)
    base = WOOD if _hash(x, y, 203) % 4 else WOOD_LIGHT
    return _noise(base, x, y, 12, 204)


def wood_crate_top(x, y):
    if x in (0, 15) or y in (0, 15):
        return _noise(WOOD_DARK, x, y, 8, 205)
    if x in (7, 8) or y in (7, 8):
        return _noise(WOOD_DARK, x, y, 8, 206)
    base = WOOD if _hash(x, y, 207) % 4 else WOOD_LIGHT
    return _noise(base, x, y, 12, 208)


# ---------------------------------------------------------------- v0.5 铁箱
def iron_crate_side(x, y):
    # 铁板 + 铆钉 + 中央把手
    if y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 7, 211)
    if y in (7, 8) and 5 <= x <= 10:
        return _noise(BRASS, x, y, 8, 212)  # 把手
    if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        return _noise(STEEL_LIGHT, x, y, 6, 213)  # 铆钉
    base = STEEL if _hash(x, y, 214) % 4 else STEEL_LIGHT
    return _noise(base, x, y, 10, 215)


def iron_crate_top(x, y):
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 7, 216)
    if x in (7, 8) or y in (7, 8):
        return _noise(STEEL_DARK, x, y, 7, 217)
    base = STEEL if _hash(x, y, 218) % 4 else STEEL_LIGHT
    return _noise(base, x, y, 10, 219)


# ---------------------------------------------------------------- v0.5 分流器
def splitter_top(x, y):
    # 传送带面 + 中央分流箭头（左右各一）
    base = RUBBER if (y % 4) else RUBBER_LIGHT
    # 中央机体条
    if 6 <= x <= 9:
        return _noise(STEEL, x, y, 8, 221)
    # 左箭头（指向左下）
    if 2 <= x <= 5 and abs((x - 2) - (y - 13)) <= 1:
        return WARN_YELLOW
    # 右箭头
    if 10 <= x <= 13 and abs((x - 13) - (y - 13)) <= 1:
        return WARN_YELLOW
    return _noise(base, x, y, 8, 222)


def splitter_side(x, y):
    if y <= 1:
        return _noise(STEEL_LIGHT, x, y, 6, 223)
    base = STEEL if _hash(x, y, 224) % 4 else STEEL_DARK
    return _noise(base, x, y, 8, 225)


# ---------------------------------------------------------------- v0.5 地下传送带
def underground_entrance(x, y):
    # 传送带面 + 中央下陷暗口
    base = RUBBER if (y % 4) else RUBBER_LIGHT
    if 5 <= x <= 10 and 5 <= y <= 10:
        return _noise((20, 20, 22), x, y, 6, 231)  # 暗口
    return _noise(base, x, y, 8, 232)


def underground_exit(x, y):
    # 传送带面 + 中央上凸亮口
    base = RUBBER if (y % 4) else RUBBER_LIGHT
    if 5 <= x <= 10 and 5 <= y <= 10:
        return _noise(STEEL_LIGHT, x, y, 8, 233)  # 亮口
    return _noise(base, x, y, 8, 234)


def underground_side(x, y):
    base = STEEL if _hash(x, y, 235) % 4 else STEEL_DARK
    return _noise(base, x, y, 8, 236)


# ---------------------------------------------------------------- v0.5 抽水机
WATER = (52, 96, 158)
WATER_LIGHT = (78, 128, 190)


def water_pump_side(x, y):
    # 蓝色机体 + 中央出水口
    if 5 <= x <= 10 and 4 <= y <= 11:
        return _noise(WATER, x, y, 8, 241)
    if (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        return _noise(WATER_LIGHT, x, y, 6, 242)
    base = STEEL if _hash(x, y, 243) % 4 else STEEL_DARK
    return _noise(base, x, y, 8, 244)


def water_pump_top(x, y):
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 7, 245)
    # 中央圆形泵口
    dx, dy = x - 7.5, y - 7.5
    d2 = dx * dx + dy * dy
    if d2 <= 9:
        return _noise(WATER, x, y, 8, 246)
    if d2 <= 16:
        return _noise(STEEL_LIGHT, x, y, 6, 247)
    base = STEEL if _hash(x, y, 248) % 4 else STEEL_LIGHT
    return _noise(base, x, y, 8, 249)


# ---------------------------------------------------------------- v0.5 锅炉
def boiler_side(x, y):
    # 石材压力容器 + 水位窗
    if y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 7, 251)
    if 6 <= x <= 9 and 6 <= y <= 9:
        return _noise(WATER, x, y, 8, 252)  # 水位窗
    base = STONE if _hash(x, y, 253) % 4 else STONE_LIGHT
    return _noise(base, x, y, 10, 254)


def boiler_top(x, y):
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 7, 255)
    # 圆形检修盖
    dx, dy = x - 7.5, y - 7.5
    d2 = dx * dx + dy * dy
    if d2 <= 9:
        return _noise(STEEL, x, y, 8, 256)
    base = STONE if _hash(x, y, 257) % 4 else STONE_LIGHT
    return _noise(base, x, y, 10, 258)


def boiler_front(x, y):
    return boiler_side(x, y)


def boiler_front_lit(x, y):
    # 燃烧时水位窗透出火光
    if 6 <= x <= 9 and 6 <= y <= 9:
        return _noise(FIRE_HOT, x, y, 10, 261)
    return boiler_side(x, y)


# ---------------------------------------------------------------- v0.5 蒸汽机
def steam_engine_side(x, y):
    # 铜色机身 + 飞轮
    dx, dy = x - 11.5, y - 6.5
    d2 = dx * dx + dy * dy
    if d2 <= 9:
        return _noise(COPPER_DARK, x, y, 8, 271)  # 飞轮
    if 2 <= x <= 7 and 4 <= y <= 10:
        return _noise(COPPER, x, y, 8, 272)  # 汽缸
    base = STEEL if _hash(x, y, 273) % 4 else STEEL_DARK
    return _noise(base, x, y, 8, 274)


def steam_engine_top(x, y):
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        return _noise(STEEL_DARK, x, y, 7, 275)
    # 汽缸顶
    dx, dy = x - 7.5, y - 7.5
    d2 = dx * dx + dy * dy
    if d2 <= 9:
        return _noise(COPPER, x, y, 8, 276)
    base = STEEL if _hash(x, y, 277) % 4 else STEEL_LIGHT
    return _noise(base, x, y, 8, 278)


# ---------------------------------------------------------------- v0.6 产物链
def steel_plate(x, y):
    # 钢板：亮银色斜纹金属板 + 四角铆钉
    if (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        return BRASS_LIGHT
    # 斜向拉丝纹理
    if (x + y) % 4 == 0:
        return _noise((168, 176, 188), x, y, 6, 281)
    return _noise((140, 148, 160), x, y, 6, 282)


def copper_cable(x, y):
    # 铜缆：橙红色绝缘线缆盘绕
    COPPER = (196, 108, 60)
    COPPER_LIGHT = (232, 146, 92)
    # 两条盘绕的缆线
    d1 = ((x - 4) ** 2 + (y - 8) ** 2) ** 0.5
    d2 = ((x - 12) ** 2 + (y - 8) ** 2) ** 0.5
    if 2.0 < d1 < 4.5 or 2.0 < d2 < 4.5:
        return _noise(COPPER, x, y, 8, 283)
    if d1 <= 2.0 or d2 <= 2.0:
        return _noise(COPPER_LIGHT, x, y, 8, 284)
    # 中间连接线
    if 4 <= x <= 12 and y in (7, 8):
        return _noise(COPPER, x, y, 6, 285)
    return None


def circuit(x, y):
    # 电路板：绿色基板 + 金色走线 + 中央芯片
    BOARD = (32, 96, 56)
    BOARD_LIGHT = (44, 120, 70)
    TRACE = (216, 172, 52)
    # 中央芯片（4x4）
    if 6 <= x <= 9 and 6 <= y <= 9:
        return _noise((60, 60, 66), x, y, 5, 286)
    # 金色走线（十字 + 引脚）
    if x in (3, 12) and 4 <= y <= 11:
        return _noise(TRACE, x, y, 6, 287)
    if y in (3, 12) and 4 <= x <= 11:
        return _noise(TRACE, x, y, 6, 288)
    # 基板噪点
    if _hash(x, y, 289) % 5 == 0:
        return _noise(BOARD_LIGHT, x, y, 5, 290)
    return _noise(BOARD, x, y, 6, 291)


TEXTURES = {    "conveyor_top": conveyor_top,
    "conveyor_side": conveyor_side,
    "inserter_base": inserter_base,
    "inserter_arm": inserter_arm,
    "miner_side": miner_side,
    "miner_top": miner_top,
    "miner_front": miner_front,
    "smelter_side": smelter_side,
    "smelter_top": smelter_top,
    "smelter_front": smelter_front,
    "smelter_front_lit": smelter_front_lit,
    "crate_side": crate_side,
    "crate_top": crate_top,
    "generator_side": generator_side,
    "generator_top": generator_top,
    "generator_front": generator_front,
    "generator_front_lit": generator_front_lit,
    "cable_side": cable_side,
    "cable_top": cable_top,
    "research_side": research_side,
    "research_top": research_top,
    "research_pack": research_pack,
    "wrench": wrench,
    "assembler_side": assembler_side,
    "assembler_top": assembler_top,
    "assembler_front": assembler_front,
    "assembler_front_lit": assembler_front_lit,
    "advanced_research_pack": advanced_research_pack,
    "tech_terminal": tech_terminal,
    # v0.5
    "wood_crate_side": wood_crate_side,
    "wood_crate_top": wood_crate_top,
    "iron_crate_side": iron_crate_side,
    "iron_crate_top": iron_crate_top,
    "splitter_top": splitter_top,
    "splitter_side": splitter_side,
    "underground_entrance": underground_entrance,
    "underground_exit": underground_exit,
    "underground_side": underground_side,
    "water_pump_side": water_pump_side,
    "water_pump_top": water_pump_top,
    "boiler_side": boiler_side,
    "boiler_top": boiler_top,
    "boiler_front": boiler_front,
    "boiler_front_lit": boiler_front_lit,
    "steam_engine_side": steam_engine_side,
    "steam_engine_top": steam_engine_top,
    # v0.6
    "steel_plate": steel_plate,
    "copper_cable": copper_cable,
    "circuit": circuit,
}


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, painter in TEXTURES.items():
        path = os.path.join(OUT_DIR, f"{name}.png")
        make_image(painter).save(path)
        print(f"wrote {path}")
    print(f"done: {len(TEXTURES)} textures")


if __name__ == "__main__":
    main()
