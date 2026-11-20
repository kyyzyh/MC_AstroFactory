"""AstroFactory 贴图公共库（工业风 / 参考《异星工厂》观感）。

设计原则：
1. 体积感：深色外框 + 上亮下暗渐变 + 内斜面高光 + 面板接缝 + 四角铆钉；
2. 辨识色：每台机器一个主色，功能区用大色块 + 强对比，远看就能认出是什么机器；
3. 大色块优先：16×16 里细节容易糊成噪点，所以用"大面积功能窗"代替零碎花纹；
4. 整机贴图按 N*16 × M*16 画（列 = dx 机器右手，行 = dz 机器正面），
   由调用方旋转 + 切块，保证多方块拼接无缝。

只提供画笔函数，不写文件。
"""
from PIL import Image, ImageDraw

# ---------------------------------------------------------------- 调色板

IRON_DARK = (46, 49, 54)
IRON = (98, 103, 110)
IRON_LIGHT = (150, 157, 166)
STEEL = (176, 184, 194)
STEEL_LIGHT = (214, 222, 232)
COPPER = (188, 112, 62)
COPPER_LIGHT = (232, 158, 100)
COPPER_DARK = (108, 62, 32)
COAL = (26, 25, 25)
GLOW = (255, 206, 92)
GLOW_DEEP = (222, 112, 30)
HAZARD_Y = (218, 176, 48)
HAZARD_K = (40, 38, 36)
GREEN = (104, 220, 128)
GREEN_DEEP = (40, 128, 74)
CYAN = (124, 212, 232)
CYAN_DEEP = (40, 108, 136)
WATER = (66, 132, 202)
WATER_LIGHT = (140, 200, 240)
STONE = (120, 114, 106)
STONE_DARK = (78, 73, 67)
STONE_LIGHT = (158, 150, 138)
WOOD = (124, 92, 56)
WOOD_DARK = (84, 60, 36)
WOOD_LIGHT = (160, 124, 82)
RUBBER = (44, 47, 52)
RUBBER_LIGHT = (68, 72, 78)


# ---------------------------------------------------------------- 基础工具

def shade(color, f):
    """按系数调整明度。"""
    return tuple(max(0, min(255, int(c * f))) for c in color[:3])


def mix(a, b, t):
    """线性混色，t=0 取 a，t=1 取 b。"""
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _noise(img, amount=4):
    """确定性噪点：让纯色块有材质颗粒，避免"塑料感"。"""
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            d = ((x * 7 + y * 13 + (x * y) % 5) % (amount * 2 + 1)) - amount
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)),
                        max(0, min(255, b + d)), a)
    return img


def gradient_fill(w, h, color, top_f=1.12, bottom_f=0.84):
    """竖直渐变底色：上亮下暗，制造受光感。"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = shade(color, top_f + (bottom_f - top_f) * t)
        for x in range(w):
            px[x, y] = c + (255,)
    return img


def hline(d, x0, x1, y, c):
    d.line([x0, y, x1, y], fill=c[:3] + (255,))


def vline(d, x, y0, y1, c):
    d.line([x, y0, x, y1], fill=c[:3] + (255,))


def fill_rect(d, box, c):
    d.rectangle(box, fill=c[:3] + (255,))


def bevel(d, box, light, dark, width=1):
    """内斜面：上/左亮，下/右暗（width 层，逐层变淡）。"""
    x0, y0, x1, y1 = box
    for i in range(width):
        f = 1.0 - i * 0.28
        hline(d, x0 + i, x1 - i, y0 + i, shade(light, f))
        vline(d, x0 + i, y0 + i, y1 - i, shade(light, f))
        hline(d, x0 + i, x1 - i, y1 - i, shade(dark, 1.0 + i * 0.1))
        vline(d, x1 - i, y0 + i, y1 - i, shade(dark, 1.0 + i * 0.1))


def rivet(d, x, y, light, dark):
    """2×2 铆钉：左上高光、右下阴影。"""
    d.point((x, y), fill=light[:3] + (255,))
    d.point((x + 1, y), fill=shade(light, 0.82)[:3] + (255,))
    d.point((x, y + 1), fill=dark[:3] + (255,))
    d.point((x + 1, y + 1), fill=shade(dark, 0.82)[:3] + (255,))


def bolts(d, box, light, dark, inset=2):
    """矩形四角铆钉。"""
    x0, y0, x1, y1 = box
    for (bx, by) in ((x0 + inset, y0 + inset), (x1 - inset - 1, y0 + inset),
                     (x0 + inset, y1 - inset - 1), (x1 - inset - 1, y1 - inset - 1)):
        rivet(d, bx, by, light, dark)


def vents(d, x0, y0, cols, rows, gap=2, dark=COAL, light=None):
    """散热槽：一组深色横缝 + 上沿高光，是"机器"最省像素的特征。"""
    for i in range(rows):
        y = y0 + i * gap
        hline(d, x0, x0 + cols - 1, y, dark)
        if light is not None and i == 0:
            hline(d, x0, x0 + cols - 1, y - 1, light)


def hazard_stripes(d, box, c1=HAZARD_Y, c2=HAZARD_K, step=3):
    """黄黑斜警示条（像素阶梯近似 45°）。"""
    x0, y0, x1, y1 = box
    fill_rect(d, box, c1)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if ((x - x0) + (y - y0)) % (step * 2) < step:
                d.point((x, y), fill=c2[:3] + (255,))


def grate(d, box, bar, bg, spacing=3, vertical=True):
    """格栅：炉口 / 进水口。"""
    x0, y0, x1, y1 = box
    fill_rect(d, box, bg)
    if vertical:
        for x in range(x0 + 1, x1, spacing):
            vline(d, x, y0 + 1, y1 - 1, bar)
    else:
        for y in range(y0 + 1, y1, spacing):
            hline(d, x0 + 1, x1 - 1, y, bar)


def cylinder(img, x0, y0, x1, y1, color, horizontal=False):
    """在贴图上画一段圆柱：中间亮、两侧暗，模拟金属管。"""
    px = img.load()
    if horizontal:
        span = y1 - y0
        for y in range(y0, y1 + 1):
            t = abs((y - y0) / max(1, span) - 0.5) * 2.0
            f = 1.22 - 0.62 * t * t
            for x in range(x0, x1 + 1):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                px[x, y] = shade((r, g, b), f) + (255,)
    else:
        span = x1 - x0
        for x in range(x0, x1 + 1):
            t = abs((x - x0) / max(1, span) - 0.5) * 2.0
            f = 1.22 - 0.62 * t * t
            for y in range(y0, y1 + 1):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                px[x, y] = shade((r, g, b), f) + (255,)
    return img


# ---------------------------------------------------------------- 主题

THEMES = {
    # 采矿机：冷灰钢 + 黄黑警示 + 大钻头
    "miner": dict(base=(86, 91, 98), frame=(34, 36, 40), light=(150, 157, 166),
                  dark=(48, 52, 58), accent=HAZARD_Y, core=GLOW, style="miner"),
    # 石熔炉：砖石炉体 + 橙红炉膛
    "furnace": dict(base=(114, 106, 96), frame=(52, 47, 42), light=(164, 155, 142),
                    dark=(66, 61, 54), accent=(156, 96, 54), core=GLOW, style="furnace"),
    # 组装机：钢蓝机身 + 绿色电路窗
    "assembler": dict(base=(80, 92, 108), frame=(36, 44, 54), light=(146, 166, 186),
                      dark=(46, 56, 68), accent=GREEN, core=GREEN, style="assembler"),
    # 科研台：浅灰白机身 + 青色玻璃
    "lab": dict(base=(150, 162, 174), frame=(62, 74, 86), light=(212, 226, 236),
                dark=(78, 90, 102), accent=CYAN, core=CYAN, style="lab"),
    # 锅炉：铜色罐体 + 白色压力表
    "boiler": dict(base=(124, 82, 52), frame=(54, 34, 20), light=(208, 148, 96),
                   dark=(80, 52, 32), accent=COPPER_LIGHT, core=GLOW, style="boiler"),
    # 蒸汽机：浅钢 + 活塞缸 + 青白蒸汽
    "engine": dict(base=(138, 146, 158), frame=(58, 64, 74), light=(200, 210, 222),
                   dark=(80, 86, 96), accent=CYAN, core=CYAN, style="engine"),
    # 离岸泵：青绿机身 + 水蓝格栅
    "pump": dict(base=(58, 122, 114), frame=(26, 58, 54), light=(150, 220, 206),
                 dark=(36, 80, 74), accent=WATER, core=WATER, style="pump"),
}


def _core(d, img, box, theme, active):
    """中央功能区：按机器类型画大色块内容（远看可辨识的关键）。"""
    style = theme["style"]
    x0, y0, x1, y1 = box
    fill_rect(d, box, theme["frame"])
    bevel(d, box, theme["light"], theme["dark"])
    ix0, iy0, ix1, iy1 = x0 + 2, y0 + 2, x1 - 2, y1 - 2
    if ix1 - ix0 < 3 or iy1 - iy0 < 3:
        return

    if style == "miner":
        # 上半黄黑警示，下半钻头圆盘（激活时透橙光）
        mid = (iy0 + iy1) // 2
        if mid - iy0 >= 2:
            hazard_stripes(d, (ix0, iy0, ix1, mid - 1))
        ccx, ccy = (ix0 + ix1) // 2, (mid + iy1) // 2
        r = max(2, min(ix1 - ix0, iy1 - mid) // 2 - 1)
        d.ellipse([ccx - r, ccy - r, ccx + r, ccy + r],
                  fill=(shade(theme["dark"], 0.7) if not active else GLOW_DEEP)[:3] + (255,))
        d.ellipse([ccx - r, ccy - r, ccx + r, ccy + r],
                  outline=STEEL[:3] + (255,))
        if active:
            d.ellipse([ccx - r + 1, ccy - r + 1, ccx + r - 1, ccy + r - 1],
                      fill=GLOW_DEEP[:3] + (255,))
            d.point((ccx, ccy), fill=(255, 248, 214, 255))
        # 钻头齿
        for i in range(4):
            ang = i * 2
            d.point((ccx - r + 1 + ang % (r * 2), ccy), fill=STEEL_LIGHT[:3] + (255,))

    elif style == "furnace":
        # 炉膛：格栅 + 火光，下沿一条炽热亮线
        grate(d, (ix0, iy0, ix1, iy1), bar=theme["light"],
              bg=(GLOW_DEEP if active else COAL), spacing=3, vertical=True)
        if active:
            hline(d, ix0 + 1, ix1 - 1, iy1 - 1, GLOW)
            hline(d, ix0 + 1, ix1 - 1, iy0, shade(GLOW, 0.9))
        # 炉门框
        bevel(d, (ix0 - 1, iy0 - 1, ix1 + 1, iy1 + 1), theme["light"], theme["dark"])

    elif style == "assembler":
        # 电路窗：暗底 + 走线 + 中央芯片 + 指示灯
        fill_rect(d, (ix0, iy0, ix1, iy1), (22, 40, 32))
        col = GREEN if active else GREEN_DEEP
        for y in range(iy0 + 1, iy1, 2):
            hline(d, ix0 + 1, ix1 - 1, y, shade(col, 0.5))
        cw = max(3, (ix1 - ix0) // 3)
        mx0, my0 = (ix0 + ix1) // 2 - cw // 2, (iy0 + iy1) // 2 - cw // 2
        fill_rect(d, (mx0, my0, mx0 + cw, my0 + cw), col)
        bevel(d, (mx0, my0, mx0 + cw, my0 + cw), shade(col, 1.3), shade(col, 0.6))
        if active:
            for (px_, py_) in ((mx0 - 1, my0 + 1), (mx0 + cw + 1, my0 + 1),
                               (mx0 - 1, my0 + cw - 1), (mx0 + cw + 1, my0 + cw - 1)):
                if ix0 < px_ < ix1 and iy0 < py_ < iy1:
                    d.point((px_, py_), fill=GLOW[:3] + (255,))
        # 玻璃高光
        hline(d, ix0 + 1, ix1 - 1, iy0 + 1, shade(col, 1.4))

    elif style == "lab":
        # 玻璃观察窗 + 液泡
        fill_rect(d, (ix0, iy0, ix1, iy1), (26, 46, 56))
        col = CYAN if active else CYAN_DEEP
        fill_rect(d, (ix0 + 1, iy0 + 1, ix1 - 1, iy1 - 1), shade(col, 0.45))
        for (bx, by) in ((ix0 + 2, iy1 - 3), (ix0 + 4, iy0 + 2),
                         (ix1 - 3, iy0 + 3), (ix1 - 5, iy1 - 2)):
            d.point((bx, by), fill=col[:3] + (255,))
        hline(d, ix0 + 1, ix1 - 1, iy0 + 1, (226, 244, 250))
        vline(d, ix0 + 1, iy0 + 1, iy1 - 1, shade(col, 1.2))

    elif style == "boiler":
        # 压力表 + 上下铜箍
        fill_rect(d, (ix0, iy0, ix1, iy1), (72, 44, 26))
        r = max(2, min(ix1 - ix0, iy1 - iy0) // 2 - 1)
        ccx, ccy = (ix0 + ix1) // 2, (iy0 + iy1) // 2
        d.ellipse([ccx - r, ccy - r, ccx + r, ccy + r], fill=(232, 232, 222))
        d.ellipse([ccx - r, ccy - r, ccx + r, ccy + r], outline=theme["frame"][:3] + (255,))
        # 指针：未激活指左（低压），激活指右上并加红色高亮（明显差异）
        if active:
            d.line([ccx, ccy, ccx + max(1, r - 1), ccy - max(1, r - 1)],
                   fill=(224, 58, 44))
            d.ellipse([ccx - r, ccy - r, ccx + r, ccy + r],
                      outline=GLOW[:3] + (255,))
            d.rectangle([ix0, iy1 - 2, ix1, iy1], fill=GLOW_DEEP[:3] + (255,))
        else:
            d.line([ccx, ccy, ccx - max(1, r - 1), ccy], fill=(140, 44, 36))
        for yy in (iy0, iy1):
            hline(d, ix0, ix1, yy, COPPER_LIGHT)
            hline(d, ix0, ix1, yy - 1 if yy > iy0 else yy + 1, COPPER_DARK)

    elif style == "engine":
        # 活塞缸：竖条纹 + 亮环；激活时透青白蒸汽
        fill_rect(d, (ix0, iy0, ix1, iy1),
                  (152, 214, 228) if active else (66, 72, 82))
        for x in range(ix0 + 1, ix1, 3):
            vline(d, x, iy0 + 1, iy1 - 1, (238, 250, 255) if active else theme["light"])
        for yy in (iy0 + 1, iy1 - 1):
            hline(d, ix0 + 1, ix1 - 1, yy,
                  (70, 140, 168) if active else theme["dark"])
        if active:
            pcx, pcy = (ix0 + ix1) // 2, (iy0 + iy1) // 2
            fill_rect(d, (pcx - 1, pcy - 2, pcx + 1, pcy + 2), (255, 255, 255))

    elif style == "pump":
        # 进水格栅 + 水面高光
        grate(d, (ix0, iy0, ix1, iy1), bar=theme["light"],
              bg=(WATER if active else (34, 54, 70)), spacing=3, vertical=False)
        if active:
            hline(d, ix0 + 1, ix1 - 1, iy0 + 1, WATER_LIGHT)
            hline(d, ix0 + 1, ix1 - 1, iy1 - 1, WATER_LIGHT)


def _decor(d, w, h, theme, active):
    """主题专属的机体装饰：让同类机器在远看时也能区分。"""
    style = theme["style"]
    if style == "miner":
        # 底部黄黑裙边
        if h >= 16:
            hazard_stripes(d, (3, h - 6, w - 4, h - 4), step=3)
    elif style == "furnace":
        # 砖缝
        for y in range(5, h - 4, 5):
            hline(d, 4, w - 5, y, shade(theme["dark"], 0.9))
    elif style == "assembler":
        # 右侧竖直管路
        vline(d, w - 6, 4, h - 5, theme["dark"])
        vline(d, w - 5, 4, h - 5, theme["light"])
    elif style == "lab":
        # 左上角青色状态灯
        fill_rect(d, (4, 4, 6, 6), theme["accent"] if active else CYAN_DEEP)
    elif style == "boiler":
        # 铜箍横带
        for y in range(6, h - 4, 12):
            hline(d, 3, w - 4, y, COPPER_LIGHT)
            hline(d, 3, w - 4, y + 1, COPPER_DARK)
    elif style == "engine":
        # 顶部蒸汽出口
        fill_rect(d, (w // 2 - 2, 3, w // 2 + 2, 5), theme["dark"])
    elif style == "pump":
        # 底部水线
        if h >= 16:
            hline(d, 3, w - 4, h - 4, WATER_LIGHT if active else WATER)


# ---------------------------------------------------------------- 整机贴图

def machine_side(sx, sz, theme_key, active=False):
    """整机侧面（列 = dx 机器右手，行 = dz 机器正面）。"""
    theme = THEMES[theme_key]
    w, h = sx * 16, sz * 16
    img = gradient_fill(w, h, theme["base"])
    img = _noise(img, 4)
    d = ImageDraw.Draw(img)

    # 外框（2px 深色）+ 内斜面
    d.rectangle([0, 0, w - 1, h - 1], outline=theme["frame"] + (255,), width=2)
    bevel(d, (2, 2, w - 3, h - 3), theme["light"], theme["dark"])

    # 面板接缝：每格一条，做出"分区"而不是一整块塑料
    for k in range(1, sx):
        x = k * 16
        vline(d, x, 3, h - 4, theme["dark"])
        vline(d, x + 1, 3, h - 4, theme["light"])
    for k in range(1, sz):
        y = k * 16
        hline(d, 3, w - 4, y, theme["dark"])
        hline(d, 3, w - 4, y + 1, theme["light"])

    # 每格四角铆钉
    for cz in range(sz):
        for cx in range(sx):
            for (ox, oy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
                rivet(d, cx * 16 + ox, cz * 16 + oy, theme["light"], theme["frame"])

    # 中央功能区（占整机 60%，远看是"这台机器在干什么"）
    cw = max(12, int(min(w, h) * 0.60))
    ch = max(12, int(min(w, h) * 0.60))
    cw = min(cw, w - 10)
    ch = min(ch, h - 10)
    cx0 = w // 2 - cw // 2
    cy0 = h // 2 - ch // 2
    _core(d, img, (cx0, cy0, cx0 + cw - 1, cy0 + ch - 1), theme, active)

    _decor(d, w, h, theme, active)

    # 左上角散热槽（大机器才画，避免小图糊）
    if w >= 32 and h >= 32:
        vents(d, 5, 6, 10, 3, gap=2, dark=COAL, light=theme["light"])

    return img


def _top_decor(d, w, h, theme, active, hx0, hy0, hx1, hy1):
    """顶面主题装饰：在中央接口周围画结构，避免'大面积纯色板'。"""
    style = theme["style"]
    d.rectangle([hx0 - 2, hy0 - 2, hx1 + 2, hy1 + 2], outline=theme["frame"][:3] + (255,))
    for (x, y) in ((hx0 - 2, hy0 - 2), (hx1 + 2, hy0 - 2),
                   (hx0 - 2, hy1 + 2), (hx1 + 2, hy1 + 2)):
        rivet(d, x, y, theme["light"], theme["dark"])
    if style == "miner":
        for (ax, ay, bx, by) in ((3, 3, 7, 7), (w - 8, 3, w - 4, 7),
                                 (3, h - 8, 7, h - 4), (w - 8, h - 8, w - 4, h - 4)):
            hazard_stripes(d, (ax, ay, bx, by), step=2)
        for i in range(4, min(w, h) // 2 - 2):
            d.point((8 + i, 8 + i), fill=shade(theme["dark"], 1.1)[:3] + (255,))
            d.point((w - 9 - i, 8 + i), fill=shade(theme["dark"], 1.1)[:3] + (255,))
    elif style == "furnace":
        cx, cy = w // 2, h // 2
        fill_rect(d, (cx - 3, cy - 3, cx + 3, cy + 3), theme["frame"])
        d.rectangle([cx - 3, cy - 3, cx + 3, cy + 3], outline=theme["light"][:3] + (255,))
        fill_rect(d, (cx - 2, cy - 2, cx + 2, cy + 2),
                  (GLOW_DEEP if active else COAL))
        for k in range(1, max(w, h) // 8):
            x = (w // 8) * k
            if x < w - 3:
                vline(d, x, 4, h - 5, shade(theme["dark"], 0.9))
    elif style == "assembler":
        for x in (w // 3, 2 * w // 3):
            vline(d, x, 4, h - 5, theme["dark"])
            vline(d, x + 1, 4, h - 5, theme["light"])
        fill_rect(d, (w // 4, h // 2 - 1, 3 * w // 4, h // 2),
                  GREEN if active else GREEN_DEEP)
    elif style == "lab":
        fill_rect(d, (4, h // 2 - 1, w - 5, h // 2),
                  theme["accent"] if active else CYAN_DEEP)
        for (x, y) in ((4, 4), (w - 5, 4), (4, h - 5), (w - 5, h - 5)):
            d.point((x, y), fill=(theme["accent"] if active else CYAN_DEEP)[:3] + (255,))
    elif style == "boiler":
        fill_rect(d, (w // 2 - 3, h // 2 - 3, w // 2 + 3, h // 2 + 3), COPPER_DARK)
        d.ellipse([w // 2 - 2, h // 2 - 2, w // 2 + 2, h // 2 + 2],
                  fill=(COPPER_LIGHT if active else COPPER))
        for (x, y) in ((4, 4), (w - 5, 4), (4, h - 5), (w - 5, h - 5)):
            d.rectangle([x, y, x + 2, y + 2], fill=theme["frame"][:3] + (255,))
            d.point((x + 1, y + 1), fill=(GLOW if active else COAL)[:3] + (255,))
    elif style == "engine":
        d.ellipse([w // 2 - 5, h // 2 - 5, w // 2 + 5, h // 2 + 5],
                  outline=theme["dark"][:3] + (255,))
        d.ellipse([w // 2 - 4, h // 2 - 4, w // 2 + 4, h // 2 + 4],
                  outline=theme["light"][:3] + (255,))
        d.point((w // 2, h // 2), fill=theme["light"][:3] + (255,))
        for y in (6, h - 7):
            vents(d, 5, y, w - 10, 2, gap=3, dark=theme["dark"], light=theme["light"])
    elif style == "pump":
        fill_rect(d, (w // 2 - 3, h // 2 - 3, w // 2 + 3, h // 2 + 3), theme["frame"])
        fill_rect(d, (w // 2 - 2, h // 2 - 2, w // 2 + 2, h // 2 + 2),
                  WATER if active else (36, 58, 74))
        for y in (5, h - 6):
            hline(d, 5, w - 6, y, WATER_LIGHT if active else WATER)


def machine_top(sx, sz, theme_key, active=False):
    """整机顶面：检修盖板 + 四角螺栓 + 中央接口。"""
    theme = THEMES[theme_key]
    w, h = sx * 16, sz * 16
    img = gradient_fill(w, h, shade(theme["base"], 0.92))
    img = _noise(img, 4)
    d = ImageDraw.Draw(img)

    d.rectangle([0, 0, w - 1, h - 1], outline=theme["frame"] + (255,), width=2)
    bevel(d, (2, 2, w - 3, h - 3), theme["light"], theme["dark"])

    # 检修盖板（内缩一圈）
    inset = 4
    box = (inset, inset, w - 1 - inset, h - 1 - inset)
    fill_rect(d, box, shade(theme["base"], 1.05))
    bevel(d, box, theme["light"], theme["dark"])
    bolts(d, box, theme["light"], theme["frame"], inset=2)

    # 盖板上的加强筋
    for k in range(1, sx):
        vline(d, k * 16, inset + 1, h - 2 - inset, shade(theme["dark"], 1.05))

    # 中央接口（管道口 / 线缆口）
    hole = max(3, min(w, h) // 7)
    hx0, hy0 = w // 2 - hole, h // 2 - hole
    hx1, hy1 = w // 2 + hole, h // 2 + hole

    # 主题专属顶面装饰（画在接口之下，避免盖住接口）
    _top_decor(d, w, h, theme, active, hx0, hy0, hx1, hy1)

    fill_rect(d, (hx0, hy0, hx1, hy1), theme["frame"])
    bevel(d, (hx0, hy0, hx1, hy1), theme["dark"], theme["light"])
    inner = theme["core"] if active else COAL
    fill_rect(d, (hx0 + 1, hy0 + 1, hx1 - 1, hy1 - 1), inner)
    if active:
        fill_rect(d, (hx0 + 2, hy0 + 2, hx1 - 2, hy1 - 2), shade(theme["core"], 0.72))
    # 接口法兰
    d.rectangle([hx0 - 1, hy0 - 1, hx1 + 1, hy1 + 1],
                outline=shade(theme["light"], 0.9)[:3] + (255,))
    return img


# ---------------------------------------------------------------- 单格贴图

def paint_metal_tile(tint=IRON, w=16, h=16, seam=True):
    """通用金属板（法兰 / 支架用）。"""
    img = gradient_fill(w, h, tint)
    img = _noise(img, 4)
    d = ImageDraw.Draw(img)
    if seam:
        d.rectangle([0, 0, w - 1, h - 1], outline=shade(tint, 0.5)[:3] + (255,))
        hline(d, 1, w - 2, 1, shade(tint, 1.35))
        vline(d, 1, 1, h - 2, shade(tint, 1.22))
        hline(d, 1, w - 2, h - 2, shade(tint, 0.6))
    return img


def paint_belt_top(fast=False):
    """带面：橡胶履带 + 纵向接缝 + 两端滚轴 + 中央流向箭头。

    AI 审图指出横向平行条纹像"百叶窗"；改用纵向接缝（皮带行进方向的接缝）
    + 两端滚轴，这是传送带正确的视觉语言。
    """
    base = RUBBER if not fast else (38, 44, 58)
    edge = shade(base, 1.35)
    mark = HAZARD_Y if not fast else (118, 168, 226)
    img = Image.new("RGBA", (16, 16), base + (255,))
    img = _noise(img, 2)
    d = ImageDraw.Draw(img)

    vline(d, 0, 0, 15, edge)
    vline(d, 15, 0, 15, edge)
    vline(d, 1, 0, 15, shade(base, 0.72))
    vline(d, 14, 0, 15, shade(base, 0.72))

    # 两端滚轴
    for cx in (2, 13):
        d.ellipse([cx - 1, 1, cx + 1, 14], fill=shade(base, 1.22)[:3] + (255,))
        d.ellipse([cx - 1, 1, cx + 1, 14], outline=shade(base, 0.7)[:3] + (255,))
        d.point((cx, 7), fill=shade(base, 0.6)[:3] + (255,))
        d.point((cx, 8), fill=shade(base, 1.5)[:3] + (255,))

    # 纵向接缝
    for x in (5, 7, 8, 10):
        vline(d, x, 2, 13, shade(base, 1.12) if x in (7, 8) else shade(base, 0.84))

    # 中央流向箭头
    for cy in (4, 11):
        d.point((7, cy), fill=mark[:3] + (255,))
        d.point((8, cy), fill=mark[:3] + (255,))
        d.point((6, cy + 1), fill=shade(mark, 0.82)[:3] + (255,))
        d.point((9, cy + 1), fill=shade(mark, 0.82)[:3] + (255,))
    return img


def paint_belt_side(fast=False):
    """带侧：钢裙边 + 一条指示色 + 底部阴影。"""
    tint = IRON if not fast else (126, 138, 156)
    stripe = HAZARD_Y if not fast else (118, 168, 226)
    img = gradient_fill(16, 16, tint, 1.10, 0.76)
    img = _noise(img, 3)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=shade(tint, 0.45)[:3] + (255,))
    hline(d, 0, 15, 1, shade(tint, 1.34))
    hline(d, 0, 15, 14, shade(tint, 0.55))
    # 指示色条（腰线）
    hline(d, 0, 15, 10, stripe)
    hline(d, 0, 15, 11, shade(stripe, 0.72))
    # 螺栓
    rivet(d, 2, 4, shade(tint, 1.4), shade(tint, 0.5))
    rivet(d, 12, 4, shade(tint, 1.4), shade(tint, 0.5))
    return img


def paint_belt_rail(fast=False):
    """护栏：镂空栏杆（上下横杆 + 两根立柱），每根杆件带高光/暗边体现厚度。

    高速带用冷蓝钢（tint 偏蓝 + 蓝色端帽），与普通带的钢灰拉开区分。
    """
    tint = STEEL if not fast else (132, 176, 224)
    gap = RUBBER if not fast else (38, 44, 58)
    img = Image.new("RGBA", (16, 16), gap + (255,))
    img = _noise(img, 2)
    d = ImageDraw.Draw(img)

    for y0 in (1, 12):
        fill_rect(d, (0, y0, 15, y0 + 1), tint)
        hline(d, 0, 15, y0, shade(tint, 1.3))
        hline(d, 0, 15, y0 + 1, shade(tint, 0.55))
    for x0 in (2, 11):
        fill_rect(d, (x0, 0, x0 + 2, 15), tint)
        vline(d, x0, 0, 15, shade(tint, 1.28))
        vline(d, x0 + 2, 0, 15, shade(tint, 0.5))
    for x0 in (2, 11):
        rivet(d, x0 + 1, 6, shade(tint, 1.45), shade(tint, 0.4))
    # 高速带额外加蓝色端帽，进一步区分
    if fast:
        for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
            fill_rect(d, (x, y, x, y), (118, 168, 226))
    else:
        for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
            d.point((x, y), fill=shade(tint, 1.2)[:3] + (255,))
    return img


def paint_pipe_body():
    """管道中段：金属圆管（强明暗过渡，明确是圆柱不是扁片）。"""
    img = gradient_fill(16, 16, (136, 142, 150), 1.06, 0.86)
    img = _noise(img, 2)
    # 圆柱受光：中心亮、两侧明显暗（f 从 1.35 到 0.55）
    for x in range(16):
        t = abs((x - 7.5) / 8.0)
        f = 1.35 - 0.80 * t * t
        for y in range(16):
            r, g, b, a2 = img.getpixel((x, y))
            if a2:
                img.putpixel((x, y), shade((r, g, b), f) + (255,))
    d = ImageDraw.Draw(img)
    hline(d, 0, 15, 0, (52, 56, 62))
    hline(d, 0, 15, 15, (52, 56, 62))
    # 高光条（圆柱最亮处）
    vline(d, 6, 1, 14, (206, 214, 224))
    return img


def paint_pipe_flange():
    """法兰盘：圆环 + 四颗螺栓孔（一眼能认出是"接头"）。"""
    img = gradient_fill(16, 16, STEEL, 1.12, 0.82)
    img = _noise(img, 3)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(78, 84, 92, 255))
    # 外圆环
    d.ellipse([1, 1, 14, 14], fill=shade(STEEL, 0.94)[:3] + (255,))
    d.ellipse([1, 1, 14, 14], outline=STEEL_LIGHT[:3] + (255,))
    # 内孔（深色）
    d.ellipse([5, 5, 10, 10], fill=(52, 56, 62, 255))
    d.ellipse([5, 5, 10, 10], outline=shade(STEEL, 0.5)[:3] + (255,))
    # 四颗螺栓孔
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.rectangle([x, y, x + 1, y + 1], fill=(70, 74, 80, 255))
        d.point((x, y), fill=STEEL_LIGHT[:3] + (255,))
    return img


def paint_pipe_glow():
    """管道内侧（蒸汽流动时的亮铜色）。"""
    img = gradient_fill(16, 16, COPPER, 1.16, 0.82)
    img = _noise(img, 3)
    d = ImageDraw.Draw(img)
    cylinder(img, 0, 0, 15, 15, COPPER, horizontal=False)
    hline(d, 0, 15, 0, COPPER_DARK)
    hline(d, 0, 15, 15, COPPER_DARK)
    return img


def paint_wood_tile(w=16, h=16):
    """木纹（电线杆）：圆柱受光 + 断续木纹 + 上下端面环。"""
    img = gradient_fill(w, h, WOOD, 1.14, 0.80)
    img = _noise(img, 2)
    for x in range(w):
        t = abs((x - (w - 1) / 2) / (w / 2))
        f = 1.26 - 0.5 * t * t
        for y in range(h):
            r, g, b, a = img.getpixel((x, y))
            if a:
                img.putpixel((x, y), shade((r, g, b), f) + (255,))
    d = ImageDraw.Draw(img)
    for (x, y0, y1) in ((3, 1, 6), (3, 9, 15), (7, 0, 4), (7, 7, 12),
                        (11, 2, 8), (11, 11, 15), (13, 4, 9)):
        vline(d, x, y0, y1, shade(WOOD_DARK, 1.0))
    for (x, y0, y1) in ((4, 3, 9), (9, 1, 5), (9, 10, 15), (12, 0, 3)):
        vline(d, x, y0, y1, shade(WOOD_LIGHT, 0.9))
    hline(d, 2, 13, 0, shade(WOOD_DARK, 1.1))
    hline(d, 2, 13, 1, shade(WOOD_DARK, 0.8))
    hline(d, 2, 13, h - 2, shade(WOOD_DARK, 1.1))
    hline(d, 2, 13, h - 1, shade(WOOD_DARK, 0.8))
    return img


def paint_steel_tile(tint=STEEL, bracket=False):
    """通用金属板；bracket=True 时画 L 形支架（有加工痕迹，不是纯色块）。"""
    if not bracket:
        return paint_metal_tile(tint)
    img = gradient_fill(16, 16, tint, 1.16, 0.76)
    img = _noise(img, 3)
    d = ImageDraw.Draw(img)
    fill_rect(d, (2, 2, 5, 15), shade(tint, 1.1))
    fill_rect(d, (2, 12, 14, 15), shade(tint, 1.1))
    vline(d, 2, 2, 15, shade(tint, 1.4))
    vline(d, 5, 2, 15, shade(tint, 0.55))
    hline(d, 2, 14, 12, shade(tint, 1.4))
    hline(d, 2, 14, 15, shade(tint, 0.55))
    for (x, y) in ((3, 4), (3, 13), (13, 13)):
        d.rectangle([x, y, x + 1, y + 1], fill=shade(tint, 0.45)[:3] + (255,))
        d.point((x, y), fill=shade(tint, 1.3)[:3] + (255,))
    d.rectangle([0, 0, 15, 15], outline=shade(tint, 0.42)[:3] + (255,))
    return img


def paint_arm_metal(tint=IRON):
    """机械臂杆件：圆柱金属条（中心亮带 + 上下暗边，明确是圆杆）。"""
    img = gradient_fill(16, 16, tint, 1.16, 0.80)
    img = _noise(img, 2)
    for y in range(16):
        t = abs((y - 7.5) / 8.0)
        f = 1.32 - 0.74 * t * t
        for x in range(16):
            r, g, b, a = img.getpixel((x, y))
            if a:
                img.putpixel((x, y), shade((r, g, b), f) + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=shade(tint, 0.42)[:3] + (255,))
    hline(d, 0, 15, 3, shade(tint, 1.45))
    hline(d, 0, 15, 12, shade(tint, 0.58))
    return img


def paint_inserter_top():
    """机械臂底座顶面：转台 + 中央轴心（16×16 下只保留可辨认的粗结构）。"""
    img = gradient_fill(16, 16, IRON, 1.10, 0.82)
    img = _noise(img, 2)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=IRON_DARK[:3] + (255,))
    # 转台
    d.ellipse([1, 1, 14, 14], fill=shade(IRON, 0.86)[:3] + (255,))
    d.ellipse([1, 1, 14, 14], outline=STEEL[:3] + (255,))
    # 中央轴心（实心，远看不糊）
    d.ellipse([5, 5, 10, 10], fill=HAZARD_Y[:3] + (255,))
    d.ellipse([6, 6, 9, 9], fill=IRON_DARK[:3] + (255,))
    d.point((7, 7), fill=STEEL_LIGHT[:3] + (255,))
    # 四角固定螺栓
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(d, x, y, STEEL_LIGHT, IRON_DARK)
    return img


def paint_inserter_side():
    """机械臂底座侧面：金属板 + 底部黄黑警示带 + 铆钉。"""
    img = gradient_fill(16, 16, IRON, 1.08, 0.80)
    img = _noise(img, 2)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=IRON_DARK[:3] + (255,))
    hazard_stripes(d, (0, 10, 15, 13), step=3)
    hline(d, 0, 15, 9, STEEL)
    hline(d, 0, 15, 14, IRON_DARK)
    rivet(d, 3, 3, STEEL_LIGHT, IRON_DARK)
    rivet(d, 11, 3, STEEL_LIGHT, IRON_DARK)
    return img


def paint_insulator():
    """绝缘子：白瓷三层伞裙（上宽下窄）+ 顶部钢帽 + 中央钢脚。"""
    img = gradient_fill(16, 16, (214, 210, 202), 1.10, 0.86)
    img = _noise(img, 2)
    d = ImageDraw.Draw(img)
    for (y0, y1, inset) in ((2, 5, 1), (6, 10, 2), (11, 14, 3)):
        fill_rect(d, (inset, y0, 15 - inset, y1), (222, 218, 210))
        hline(d, inset, 15 - inset, y0, (246, 242, 234))
        hline(d, inset, 15 - inset, y1, (168, 162, 152))
        hline(d, inset, 15 - inset, y1 - 1, (200, 195, 186))
    fill_rect(d, (6, 0, 9, 2), STEEL)
    hline(d, 6, 9, 0, STEEL_LIGHT)
    hline(d, 6, 9, 2, shade(STEEL, 0.6))
    fill_rect(d, (7, 14, 8, 15), IRON_DARK)
    return img


def paint_ore(kind, richness):
    """矿脉贴图：石底 + 矿粒团簇（richness 决定矿粒密度）。

    kind: iron / copper / coal / stone
    richness: rich / normal / poor / depleted
    """
    ore_col = {
        "iron": (206, 172, 148),
        "copper": (206, 124, 74),
        "coal": (34, 33, 33),
        "stone": (128, 124, 118),
    }[kind]
    hi = {
        "iron": (238, 214, 190),
        "copper": (240, 168, 110),
        "coal": (72, 70, 70),
        "stone": (166, 162, 156),
    }[kind]
    lo = {
        "iron": (146, 116, 96),
        "copper": (140, 78, 44),
        "coal": (16, 15, 15),
        "stone": (92, 88, 82),
    }[kind]

    # 石底
    img = gradient_fill(16, 16, STONE, 1.06, 0.86)
    img = _noise(img, 6)
    d = ImageDraw.Draw(img)
    # 石纹
    for (x, y, w_, h_) in ((2, 3, 4, 3), (9, 1, 4, 3), (4, 10, 5, 3), (11, 9, 3, 4)):
        fill_rect(d, (x, y, x + w_ - 1, y + h_ - 1), shade(STONE, 0.94))
        hline(d, x, x + w_ - 1, y, STONE_LIGHT)

    # 矿粒团簇
    clusters = {
        "rich": [(2, 2), (9, 3), (4, 9), (11, 10), (7, 6)],
        "normal": [(3, 3), (10, 4), (5, 10), (11, 11)],
        "poor": [(4, 4), (10, 9)],
        "depleted": [(5, 6)],
    }[richness]

    for (cx, cy) in clusters:
        fill_rect(d, (cx, cy, cx + 2, cy + 2), ore_col)
        hline(d, cx, cx + 2, cy, hi)
        hline(d, cx, cx + 2, cy + 2, lo)
        d.point((cx + 1, cy + 1), fill=hi[:3] + (255,))
        # 边缘散点，破掉方块感
        d.point((cx - 1, cy + 1), fill=lo[:3] + (255,)) if cx > 0 else None
        d.point((cx + 3, cy + 2), fill=lo[:3] + (255,)) if cx < 13 else None

    if richness == "depleted":
        # 采空：只剩矿渣与裂纹
        vline(d, 7, 4, 12, shade(STONE, 0.7))
        vline(d, 8, 6, 13, shade(STONE, 0.76))
    return img
