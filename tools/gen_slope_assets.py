"""生成 AstroFactory 斜坡传送带的模型与 blockstate。

几何依据（TransportBeltBlock.Slope，设计 A：所有入口都在本格带面 3px）：
    flat : 带面 y 3px 恒定，出口同层
    up   : 带面从 y=3px 升到 y=19px（物品爬到上一格带面），出口在上一格
    down : 带面 y 3px，物品从本格带面向下斜穿，出口在下一格带面。
           落差发生在**本格下方空间**，模型画不到 y<0，所以 down 模型
           不做假斜坡，而是做成「带面前端收口 + 两侧挡板下落的落料口」：
           玩家一眼能看出这条带子是把物品往下倒的。

三种朝向由 blockstate 的 y 旋转复用同一模型（与 flat 一致），
所以只需要为 up / down 各生成 1 个模型，blockstate 里给出 12 个变体。

模型用 8 段台阶近似 45 度斜面（Minecraft 模型不支持任意角度旋转，
台阶是标准做法）。每段 z 宽 2px，顶面高度按中位线取整。
"""
import io
import json
import os

MODELS = r"src/main/resources/assets/astrofactory/models/block"
STATES = r"src/main/resources/assets/astrofactory/blockstates"

STEPS = 8
STEP_Z = 16 // STEPS          # 2px
BASE_Y = 3                    # 平坦带面
RISE = 16                     # 一格高度差（16px）


def step_top(i, up=True):
    """第 i 段台阶的顶面高度（px）。

    取「段中点」而非段端：物品渲染是线性插值，路径中线正好过段中点，
    因此两端误差对称（各 1px），视觉上完全看不出。若取段端，会出现
    一端贴住、另一端悬空 2px 的明显台阶感。

    up  ：物品 y 从 3 升到 19（本格带面 -> 上一格带面）
          段 i 中点 y = 3 + 2i + 1
    down：物品 y 从 3 降到 -13（本格带面 -> 下一格带面）
          但 Minecraft 模型不能画到 y<0，所以坡面只画本格内可见的那一段：
          从 19（格顶，对应入口上游）降到 3（本格带面），
          也就是把 3..19 这 16px 均分 8 段，反向排列。
          段 i 中点 y = 3 + 2(7-i) + 1
    """
    if up:
        return BASE_Y + 2 * i + 1
    return BASE_Y + 2 * (STEPS - 1 - i) + 1


def ramp_elements(up=True):
    """生成台阶元素（坡面主体）。

    z 从 16 递减到 0，每段 2px 宽。
    """
    els = []
    for i in range(STEPS):
        z1 = 16 - i * STEP_Z
        z0 = z1 - STEP_Z
        top = step_top(i, up)
        els.append({
            "from": [0, 0, z0],
            "to": [16, top, z1],
            "faces": {
                "up": {"texture": "#top", "uv": [0, z0, 16, z1]},
                "down": {"texture": "#side", "uv": [0, 0, 16, 2]},
                "north": {"texture": "#side", "uv": [0, 14, 16, 16]},
                "south": {"texture": "#side", "uv": [0, 14, 16, 16]},
                "east": {"texture": "#side", "uv": [0, 16 - top, 2, 16]},
                "west": {"texture": "#side", "uv": [0, 16 - top, 2, 16]},
            },
        })
    return els


def rail_elements(up=True):
    """两侧护栏：跟着斜面走的两条斜条（同样用台阶）。"""
    els = []
    for i in range(STEPS):
        z1 = 16 - i * STEP_Z
        z0 = z1 - STEP_Z
        top = step_top(i, up)
        for x0 in (0, 15):
            els.append({
                "from": [x0, top - 1, z0],
                "to": [x0 + 1, top, z1],
                "faces": {
                    "up": {"texture": "#rail", "uv": [0, z0, 1, z1]},
                    "north": {"texture": "#rail", "uv": [0, 0, 1, 1]},
                    "south": {"texture": "#rail", "uv": [0, 0, 1, 1]},
                    "east": {"texture": "#rail", "uv": [0, 0, 2, 1]},
                    "west": {"texture": "#rail", "uv": [0, 0, 2, 1]},
                },
            })
    return els


LIP = 4          # down 落料口的前端开口宽度（px）


def down_elements():
    """下坡落料口（NORTH 朝向下 front = z=0）。

    - 主体带面：z 4..16，y 0..3
    - 前端收口底板：z 0..4，y 0..1（比带面低 2px，暗示往下倒）
    - 两侧挡板：主段跟着带面，前端段向下压到 y=1
    """
    els = []

    def box(x0, y0, z0, x1, y1, z1, tex, uv_top=None, uv_side=None):
        faces = {
            "up": {"texture": tex, "uv": uv_top or [x0, z0, x1, z1]},
            "down": {"texture": tex, "uv": uv_side or [x0, z0, x1, z1]},
            "north": {"texture": tex, "uv": uv_side or [x0, y0, x1, y1]},
            "south": {"texture": tex, "uv": uv_side or [x0, y0, x1, y1]},
            "east": {"texture": tex, "uv": uv_side or [z0, y0, z1, y1]},
            "west": {"texture": tex, "uv": uv_side or [z0, y0, z1, y1]},
        }
        els.append({"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces})

    # 主体带面（后段）
    els.append({
        "from": [0, 0, LIP],
        "to": [16, 3, 16],
        "faces": {
            "up": {"texture": "#top", "uv": [0, LIP, 16, 16]},
            "down": {"texture": "#side", "uv": [0, 0, 16, 16]},
            "north": {"texture": "#side", "uv": [0, 0, 16, 3]},
            "south": {"texture": "#side", "uv": [0, 13, 16, 16]},
            "east": {"texture": "#side", "uv": [LIP, 13, 16, 16]},
            "west": {"texture": "#side", "uv": [LIP, 13, 16, 16]},
        },
    })
    # 前端收口底板（低 2px，形成往下倒的开口）
    els.append({
        "from": [0, 0, 0],
        "to": [16, 1, LIP],
        "faces": {
            "up": {"texture": "#top", "uv": [0, 0, 16, LIP]},
            "down": {"texture": "#side", "uv": [0, 0, 16, LIP]},
            "north": {"texture": "#side", "uv": [0, 0, 16, 1]},
            "south": {"texture": "#side", "uv": [0, 15, 16, 16]},
            "east": {"texture": "#side", "uv": [0, 15, LIP, 16]},
            "west": {"texture": "#side", "uv": [0, 15, LIP, 16]},
        },
    })
    # 两侧挡板：主段（y 3..4）+ 前端下落段（y 1..4）
    for x0 in (0, 15):
        els.append({
            "from": [x0, 3, LIP],
            "to": [x0 + 1, 4, 16],
            "faces": {
                "up": {"texture": "#rail", "uv": [0, LIP, 1, 16]},
                "north": {"texture": "#rail", "uv": [0, 0, 1, 1]},
                "south": {"texture": "#rail", "uv": [0, 0, 1, 1]},
                "east": {"texture": "#rail", "uv": [0, 0, 16 - LIP, 1]},
                "west": {"texture": "#rail", "uv": [0, 0, 16 - LIP, 1]},
            },
        })
        els.append({
            "from": [x0, 1, 0],
            "to": [x0 + 1, 4, LIP],
            "faces": {
                "up": {"texture": "#rail", "uv": [0, 0, 1, LIP]},
                "north": {"texture": "#rail", "uv": [0, 0, 1, 3]},
                "south": {"texture": "#rail", "uv": [0, 0, 1, 3]},
                "east": {"texture": "#rail", "uv": [0, 0, LIP, 3]},
                "west": {"texture": "#rail", "uv": [0, 0, LIP, 3]},
            },
        })
    return els


def build_model(name, top_tex, side_tex, rail_tex, up=True):
    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": top_tex,
            "top": top_tex,
            "side": side_tex,
            "rail": rail_tex,
        },
        "elements": (ramp_elements(True) + rail_elements(True)) if up else down_elements(),
    }
    path = os.path.join(MODELS, name + ".json")
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("wrote model", name)


def build_blockstate(name, base_model):
    """12 个变体：facing(4) x slope(3)。"""
    variants = {}
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rotations.items():
        for slope, suffix in (("flat", ""), ("up", "_up"), ("down", "_down")):
            key = "facing=%s,slope=%s" % (facing, slope)
            entry = {"model": base_model + suffix}
            if y:
                entry["y"] = y
            variants[key] = entry
    path = os.path.join(STATES, name + ".json")
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"variants": variants}, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("wrote blockstate", name, len(variants), "variants")


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(STATES, exist_ok=True)

    # 普通传送带
    build_model("transport_belt_up",
                "astrofactory:block/transport_belt_top",
                "astrofactory:block/transport_belt_side",
                "astrofactory:block/transport_belt_rail", up=True)
    build_model("transport_belt_down",
                "astrofactory:block/transport_belt_top",
                "astrofactory:block/transport_belt_side",
                "astrofactory:block/transport_belt_rail", up=False)
    build_blockstate("transport_belt", "astrofactory:block/transport_belt")

    # 高速传送带
    build_model("fast_transport_belt_up",
                "astrofactory:block/fast_transport_belt_top",
                "astrofactory:block/fast_transport_belt_side",
                "astrofactory:block/fast_transport_belt_rail", up=True)
    build_model("fast_transport_belt_down",
                "astrofactory:block/fast_transport_belt_top",
                "astrofactory:block/fast_transport_belt_side",
                "astrofactory:block/fast_transport_belt_rail", up=False)
    build_blockstate("fast_transport_belt", "astrofactory:block/fast_transport_belt")


if __name__ == "__main__":
    main()
