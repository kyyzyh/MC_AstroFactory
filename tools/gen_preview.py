"""生成 textures-preview.png：把全部 49 张 16x16 贴图拼成一张带标签的预览图。

用法：
    python tools/gen_preview.py
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "astrofactory", "textures", "block")
OUT_PATH = os.path.join(ROOT, "textures-preview.png")

# 展示顺序：物流 -> 机器 -> 电力 -> 蒸汽链 -> 科技
NAMES = [
    "conveyor_top", "conveyor_side",
    "inserter_base", "inserter_arm",
    "wood_crate_side", "wood_crate_top",
    "crate_side", "crate_top",
    "iron_crate_side", "iron_crate_top",
    "splitter_top", "splitter_side",
    "underground_entrance", "underground_exit", "underground_side",
    "miner_side", "miner_top", "miner_front",
    "smelter_side", "smelter_top", "smelter_front", "smelter_front_lit",
    "generator_side", "generator_top", "generator_front", "generator_front_lit",
    "cable_side", "cable_top",
    "water_pump_side", "water_pump_top",
    "boiler_side", "boiler_top", "boiler_front", "boiler_front_lit",
    "steam_engine_side", "steam_engine_top",
    "research_side", "research_top",
    "research_pack", "advanced_research_pack", "wrench", "tech_terminal",
    "steel_plate", "copper_cable", "circuit",
    "assembler_side", "assembler_top", "assembler_front", "assembler_front_lit",
]

SCALE = 4          # 每张贴图放大到 64x64
PAD = 8            # 贴图之间留白
LABEL_H = 14       # 标签高度
COLS = 6

BG = (34, 36, 40)
FG = (200, 204, 210)


def main() -> None:
    cell_w = 16 * SCALE
    cell_h = cell_w + LABEL_H
    rows = (len(NAMES) + COLS - 1) // COLS
    img = Image.new("RGB", (COLS * cell_w + (COLS + 1) * PAD,
                            rows * cell_h + (rows + 1) * PAD), BG)
    draw = ImageDraw.Draw(img)

    for i, name in enumerate(NAMES):
        tex = Image.open(os.path.join(TEX_DIR, f"{name}.png")).convert("RGBA")
        tex = tex.resize((cell_w, cell_w), Image.NEAREST)
        col = i % COLS
        row = i // COLS
        x = PAD + col * (cell_w + PAD)
        y = PAD + row * (cell_h + PAD)
        img.paste(tex, (x, y), tex)
        draw.text((x + 2, y + cell_w + 1), name, fill=FG)

    img.save(OUT_PATH)
    print(f"wrote {OUT_PATH} ({len(NAMES)} textures, {img.size[0]}x{img.size[1]})")


if __name__ == "__main__":
    main()
