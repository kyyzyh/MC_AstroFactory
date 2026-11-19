"""程序化生成 AstroFactory 的工业风贴图（16x16）。

统一风格（规格 §6）：铁灰、铜橙、煤黑、带面深灰 + 黄条纹。
本脚本只补齐新增物品/方块缺少的贴图，已有贴图不覆盖。
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_BLOCK = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory", "textures", "block")
TEX_ITEM = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory", "textures", "item")

IRON_DARK = (74, 78, 84)
IRON = (108, 113, 120)
IRON_LIGHT = (140, 146, 154)
COPPER = (196, 116, 58)
COPPER_LIGHT = (226, 150, 88)
COAL = (38, 36, 36)
BELT_DARK = (52, 55, 60)
YELLOW = (214, 176, 52)
RED = (168, 52, 46)
GREEN = (86, 168, 96)


def noise(img, amount=10):
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


def base(color, amount=8):
    img = Image.new("RGBA", (16, 16), color + (255,))
    return noise(img, amount)


def frame(img, color, width=1):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=color + (255,), width=width)
    return img


def save(img, path, name):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path):
        print("skip (exists)", name)
        return
    img.save(path)
    print("wrote", name)


def item_automation_pack():
    """红瓶：深红瓶身 + 铜色瓶盖 + 高光。"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([5, 6, 10, 14], fill=(150, 40, 36, 255), outline=(96, 24, 22, 255))
    d.rectangle([5, 6, 10, 8], fill=(178, 58, 52, 255))
    d.rectangle([6, 15, 9, 15], fill=(110, 30, 28, 255))
    d.rectangle([6, 3, 9, 5], fill=COPPER + (255,), outline=(140, 78, 36, 255))
    d.point((7, 4), fill=COPPER_LIGHT + (255,))
    d.point((6, 9), fill=(214, 118, 110, 255))
    d.point((6, 10), fill=(214, 118, 110, 255))
    return img


def item_copper_cable():
    """铜线：卷绕的铜色线轴。"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([4, 3, 11, 12], fill=COPPER + (255,), outline=(132, 74, 34, 255))
    for y in range(5, 12, 2):
        d.line([4, y, 11, y], fill=COPPER_LIGHT + (255,))
    d.rectangle([6, 2, 9, 3], fill=IRON + (255,))
    d.rectangle([6, 12, 9, 13], fill=IRON + (255,))
    return img


def item_electronic_circuit():
    """电路板：绿板 + 金色走线 + 芯片。"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 3, 14, 12], fill=(36, 92, 58, 255), outline=(24, 64, 40, 255))
    for x in (3, 6, 9, 12):
        d.line([x, 4, x, 11], fill=YELLOW + (255,))
    d.rectangle([5, 6, 10, 9], fill=(28, 30, 34, 255), outline=(70, 74, 80, 255))
    d.point((6, 7), fill=GREEN + (255,))
    d.point((9, 8), fill=RED + (255,))
    return img


def block_stone_furnace_side():
    img = base(IRON_DARK, 10)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 13, 13], outline=(96, 92, 86, 255))
    d.rectangle([5, 7, 10, 13], fill=COAL + (255,))
    d.rectangle([6, 8, 9, 12], fill=(96, 44, 28, 255))
    for x in range(2, 14, 3):
        d.line([x, 3, x, 5], fill=IRON_LIGHT + (255,))
    return img


def block_stone_furnace_top():
    img = base((92, 90, 86), 10)
    d = ImageDraw.Draw(img)
    d.rectangle([3, 3, 12, 12], outline=(66, 64, 60, 255))
    d.rectangle([6, 6, 9, 9], fill=COAL + (255,))
    return img


def block_stone_furnace_active():
    img = base((92, 90, 86), 10)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 13, 13], outline=(96, 92, 86, 255))
    d.rectangle([5, 7, 10, 13], fill=(226, 128, 40, 255))
    d.rectangle([6, 9, 9, 13], fill=(248, 196, 72, 255))
    return img


def main():
    save(item_automation_pack(), os.path.join(TEX_ITEM, "automation_pack.png"), "item/automation_pack")
    save(item_copper_cable(), os.path.join(TEX_ITEM, "copper_cable.png"), "item/copper_cable")
    save(item_electronic_circuit(), os.path.join(TEX_ITEM, "electronic_circuit.png"), "item/electronic_circuit")
    save(block_stone_furnace_side(), os.path.join(TEX_BLOCK, "stone_furnace_side.png"), "block/stone_furnace_side")
    save(block_stone_furnace_top(), os.path.join(TEX_BLOCK, "stone_furnace_top.png"), "block/stone_furnace_top")
    save(block_stone_furnace_active(), os.path.join(TEX_BLOCK, "stone_furnace_active.png"), "block/stone_furnace_active")


if __name__ == "__main__":
    main()
