"""一次性脚本：为探矿仪补充 lang 键（中/英），并修正矿田间距描述。

为什么用脚本而不是手改 JSON：两个语言文件各 300+ 键，手改容易漏逗号。
用 json 库读改写，保证语法合法。
"""
import json
import os

BASE = os.path.join("src", "main", "resources", "assets", "astrofactory", "lang")

ZH = {
    "item.astrofactory.prospector": "探矿仪",
    "msg.astrofactory.prospector.header": "探矿仪：附近 %s 片矿田",
    "msg.astrofactory.prospector.line": "  %s 约 %s 格",
    "msg.astrofactory.prospector.none": "探矿仪：%s 格内没探到矿田",
    "msg.astrofactory.prospector.hint": "继续往外走。平均每 %s 个区块有一片矿田（约 260 格见方一片）。",
    "dir.astrofactory.north": "北",
    "dir.astrofactory.northeast": "东北",
    "dir.astrofactory.east": "东",
    "dir.astrofactory.southeast": "东南",
    "dir.astrofactory.south": "南",
    "dir.astrofactory.southwest": "西南",
    "dir.astrofactory.west": "西",
    "dir.astrofactory.northwest": "西北",
    # 修正：矿田现在全世界散布，不再是「40~64 格」这个已过期的数字
    "book.astrofactory.ref.trouble.vein_depleted.usage":
        "用探矿仪（右键）找最近的矿田，把采矿机挪过去。矿田全世界都有，越往外越多。",
}

EN = {
    "item.astrofactory.prospector": "Prospector",
    "msg.astrofactory.prospector.header": "Prospector: %s ore field(s) nearby",
    "msg.astrofactory.prospector.line": "  %s, about %s blocks",
    "msg.astrofactory.prospector.none": "Prospector: no ore field within %s blocks",
    "msg.astrofactory.prospector.hint": "Keep walking. One ore field per %s chunks on average (~260 blocks apart).",
    "dir.astrofactory.north": "north",
    "dir.astrofactory.northeast": "northeast",
    "dir.astrofactory.east": "east",
    "dir.astrofactory.southeast": "southeast",
    "dir.astrofactory.south": "south",
    "dir.astrofactory.southwest": "southwest",
    "dir.astrofactory.west": "west",
    "dir.astrofactory.northwest": "northwest",
    "book.astrofactory.ref.trouble.vein_depleted.usage":
        "Use the Prospector (right-click) to find the nearest ore field, then move the miner there. Ore fields exist worldwide.",
}


def patch(filename, additions):
    path = os.path.join(BASE, filename)
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    before = len(data)
    data.update(additions)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{filename}: {before} -> {len(data)} keys (+{len(data) - before})")


patch("zh_cn.json", ZH)
patch("en_us.json", EN)
