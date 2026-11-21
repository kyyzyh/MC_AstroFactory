"""一次性脚本：把手册参考章节的 lang 键写入 zh_cn / en_us。"""
import io
import json
import collections
import os

ZH = {
    "book.astrofactory.ref.machines.title": "机器速查",
    "book.astrofactory.ref.machines.intro": "每台机器一页。拿不准它干什么，翻这里。",
    "book.astrofactory.ref.items.title": "物品链",
    "book.astrofactory.ref.items.intro": "从原料到成品的顺序。缺哪一环就补哪一环。",
    "book.astrofactory.ref.trouble.title": "机器不动怎么办",
    "book.astrofactory.ref.trouble.intro": "先看症状，再对原因。多数情况不用拆。",
    "book.astrofactory.ref.usage_label": "怎么用：",

    "book.astrofactory.ref.machine.burner_miner.name": "燃烧采矿机",
    "book.astrofactory.ref.machine.burner_miner.body": "2x2 多方块。放在矿脉上，烧煤自动出矿。\n每 80 tick（4 秒）产一个。一煤约烧 12 个。",
    "book.astrofactory.ref.machine.burner_miner.usage": "正对矿脉放第一块（master），其余三块自动补齐。\n燃料槽放煤。矿脉枯竭后要换地方。",

    "book.astrofactory.ref.machine.transport_belt.name": "传送带",
    "book.astrofactory.ref.machine.transport_belt.body": "两列并行，物品真连续移动。免费，不需要电。\n下游堵住会往回压，一直压到采矿机停。",
    "book.astrofactory.ref.machine.transport_belt.usage": "顺着方向连成一条线。上坡会自动变成斜坡（1 格高差）。\n末端接机器或漏斗都行。",

    "book.astrofactory.ref.machine.funnel.name": "漏斗",
    "book.astrofactory.ref.machine.funnel.body": "1 格被动转移。不用电、不用燃料、没有界面。\n从上方和背面吸，往朝向吐。",
    "book.astrofactory.ref.machine.funnel.usage": "机器出口下面放一个、朝下对着箱子，就这么简单。\n要跨格或者要过滤，才需要机械臂。",

    "book.astrofactory.ref.machine.burner_inserter.name": "燃烧机械臂",
    "book.astrofactory.ref.machine.burner_inserter.body": "跨格搬运。自己烧煤，一煤约搬 7 次。\n方向由摆放决定：背面抓，正面放。",
    "book.astrofactory.ref.machine.burner_inserter.usage": "右键打开界面：9 格过滤 + 只搬这些/不搬这些 + 一次搬 1/8/64 + Keep N。\n放不下就不抓，所以物品不会卡在半路。",

    "book.astrofactory.ref.machine.stone_furnace.name": "石熔炉",
    "book.astrofactory.ref.machine.stone_furnace.body": "2x2 多方块。原矿炼成锭，一煤约炼 12 个。\n铁锭再炼一次（320 tick）得到钢锭。",
    "book.astrofactory.ref.machine.stone_furnace.usage": "原料放输入槽，煤放燃料槽。\n产物要手动取，或者接漏斗/机械臂。",

    "book.astrofactory.ref.machine.assembler.name": "组装机",
    "book.astrofactory.ref.machine.assembler.body": "3x3 多方块，按 3x3 网格匹配配方。\n是电动的 —— 没有电就完全不动。",
    "book.astrofactory.ref.machine.assembler.usage": "把零件按配方摆进 9 格。断电时界面会显示「无电力」。\n电路板、科技包都在这里做。",

    "book.astrofactory.ref.machine.lab.name": "科研台",
    "book.astrofactory.ref.machine.lab.body": "3x3 多方块。投入科技包解锁科技。\n同样需要电，断电不能投入。",
    "book.astrofactory.ref.machine.lab.usage": "放进科技包，按「投入」。用左右箭头切换研究目标。\n科技包可以手搓，也可以自动化生产。",

    "book.astrofactory.ref.machine.boiler.name": "锅炉",
    "book.astrofactory.ref.machine.boiler.body": "2x3 多方块。烧煤把水变成蒸汽。\n蒸汽是发电的原料。",
    "book.astrofactory.ref.machine.boiler.usage": "接上水泵供水，蒸汽管道接出去。\n没水不产蒸汽，别只放煤。",

    "book.astrofactory.ref.machine.steam_engine.name": "蒸汽机",
    "book.astrofactory.ref.machine.steam_engine.body": "3x5 多方块。吃蒸汽发电，是整套电力的来源。\n一台供不起太多机器，扩张时要加。",
    "book.astrofactory.ref.machine.steam_engine.usage": "蒸汽管道从锅炉接进来。\n电线杆插在旁边，5 格范围内的机器都能取电。",

    "book.astrofactory.ref.machine.offshore_pump.name": "水泵",
    "book.astrofactory.ref.machine.offshore_pump.body": "1x2。放在水边抽水，供水给锅炉。\n没有它锅炉只是烧煤不产蒸汽。",
    "book.astrofactory.ref.machine.offshore_pump.usage": "贴着水源放。用蒸汽管道把水送到锅炉。",

    "book.astrofactory.ref.machine.steam_pipe.name": "蒸汽管道",
    "book.astrofactory.ref.machine.steam_pipe.body": "输送蒸汽和水。自身不产也不耗。\n连成一条线即可，不需要电。",
    "book.astrofactory.ref.machine.steam_pipe.usage": "水泵 → 管道 → 锅炉 → 管道 → 蒸汽机。",

    "book.astrofactory.ref.machine.power_pole.name": "电线杆",
    "book.astrofactory.ref.machine.power_pole.body": "单格能量节点，5 格范围供电。\n不需要接线，插在机器附近就行。",
    "book.astrofactory.ref.machine.power_pole.usage": "放在蒸汽机旁边，再放到用电机器旁边。\n机器离得太远就多插几根。",

    "book.astrofactory.ref.machine.ore_vein.name": "矿脉",
    "book.astrofactory.ref.machine.ore_vein.body": "世界生成时埋在地下，有储量。\n采矿机会把它一点点挖空，枯竭后变成贫矿。",
    "book.astrofactory.ref.machine.ore_vein.usage": "用采矿机挖。枯竭了就得去别处找 —— 这是扩张的动力。\n矿田之间隔 40~64 格。",

    "book.astrofactory.ref.item.iron_gear.name": "铁齿轮",
    "book.astrofactory.ref.item.iron_gear.body": "铁锭 x2 做 1 个。工作台或熔炉的零件通道都能做。\n几乎所有机器的合成都要它。",
    "book.astrofactory.ref.item.iron_gear.usage": "开局先攒一堆，后面到处都要用。",

    "book.astrofactory.ref.item.copper_cable.name": "铜线",
    "book.astrofactory.ref.item.copper_cable.body": "铜锭 x3 做 1 个。\n电路板、电线杆、机械臂都要。",
    "book.astrofactory.ref.item.copper_cable.usage": "铜矿和铁矿一样重要，别只挖铁。",

    "book.astrofactory.ref.item.electronic_circuit.name": "电路板",
    "book.astrofactory.ref.item.electronic_circuit.body": "铁齿轮 x2 + 铜线 x2，在组装机里按 3x3 网格拼。\n这是「电气化」的门槛物品。",
    "book.astrofactory.ref.item.electronic_circuit.usage": "做它需要电，所以要先发电。这就是第 13-15 步撞的那堵墙。",

    "book.astrofactory.ref.item.automation_pack.name": "自动化科技包（红瓶）",
    "book.astrofactory.ref.item.automation_pack.body": "铜锭 x2 + 铁齿轮 x1 做 2 个，工作台可手搓。\n投进科研台解锁科技。",
    "book.astrofactory.ref.item.automation_pack.usage": "手搓只能应急，量大了要在组装机里自动生产。",

    "book.astrofactory.ref.item.steel_ingot.name": "钢锭",
    "book.astrofactory.ref.item.steel_ingot.body": "铁锭在石熔炉里再炼一次，320 tick（16 秒）出一个。\n比铁慢 5 倍，但钢扳手、动力手套、铆钉枪都要它。",
    "book.astrofactory.ref.item.steel_ingot.usage": "炼钢很慢，专门给它配一台熔炉，别和炼铁抢。",

    "book.astrofactory.ref.trouble.machine_stopped.name": "机器不转，但看不出原因",
    "book.astrofactory.ref.trouble.machine_stopped.body": "拿铁扳手或钢扳手右键机器。\n会直接告诉你：动力状态、运行状态、缺什么原料。",
    "book.astrofactory.ref.trouble.machine_stopped.usage": "这是最快的排查方式，比拆了重放快得多。",

    "book.astrofactory.ref.trouble.no_power.name": "界面显示「无电力」",
    "book.astrofactory.ref.trouble.no_power.body": "组装机和科研台是电动的，不烧煤。\n要发电：水泵 → 锅炉 → 蒸汽机 → 电线杆。",
    "book.astrofactory.ref.trouble.no_power.usage": "先确认蒸汽机在转（有蒸汽进去），再看电线杆离机器是不是超过 5 格。",

    "book.astrofactory.ref.trouble.arm_stuck.name": "机械臂抓了东西但放不下",
    "book.astrofactory.ref.trouble.arm_stuck.body": "目标满了，或者目标不是容器。\n机械臂会一直拿着，不会丢，但也不干活。",
    "book.astrofactory.ref.trouble.arm_stuck.usage": "右键机械臂看状态。如果是「输出侧放不下」，去清目标容器。\n设了 Keep N 的话，目标够数本来就会停，那是正常的。",

    "book.astrofactory.ref.trouble.belt_backup.name": "传送带上的东西堆着不动",
    "book.astrofactory.ref.trouble.belt_backup.body": "下游堵了。压力会一路往回传，最后采矿机也停。\n这不是 bug，是设计 —— 让堵塞看得见。",
    "book.astrofactory.ref.trouble.belt_backup.usage": "从末端往前查：末端机器是不是满了？没电？输出没人取？",

    "book.astrofactory.ref.trouble.vein_depleted.name": "采矿机还在转但不出矿",
    "book.astrofactory.ref.trouble.vein_depleted.body": "矿脉挖空了。\n采矿机不会自己搬家，也不会自动找新矿。",
    "book.astrofactory.ref.trouble.vein_depleted.usage": "去别处找新矿脉（矿田之间隔 40~64 格），把采矿机挪过去。",
}

EN = {
    "book.astrofactory.ref.machines.title": "Machine Reference",
    "book.astrofactory.ref.machines.intro": "One page per machine. If you are unsure what it does, look here.",
    "book.astrofactory.ref.items.title": "Item Chain",
    "book.astrofactory.ref.items.intro": "From raw material to finished product. Fix the missing link.",
    "book.astrofactory.ref.trouble.title": "When a Machine Stops",
    "book.astrofactory.ref.trouble.intro": "Find the symptom, then the cause. Most cases need no rebuild.",
    "book.astrofactory.ref.usage_label": "How to use: ",

    "book.astrofactory.ref.machine.burner_miner.name": "Burner Miner",
    "book.astrofactory.ref.machine.burner_miner.body": "2x2 multiblock. Place it on an ore vein, feed coal, it mines.\nOne ore every 80 ticks. One coal lasts about 12 ores.",
    "book.astrofactory.ref.machine.burner_miner.usage": "Place the first block facing the vein (master); the other three fill in.\nPut coal in the fuel slot. Move it when the vein runs dry.",

    "book.astrofactory.ref.machine.transport_belt.name": "Transport Belt",
    "book.astrofactory.ref.machine.transport_belt.body": "Two lanes, items move continuously. Free, no power needed.\nA blocked downstream backs up all the way to the miner.",
    "book.astrofactory.ref.machine.transport_belt.usage": "Chain them in the direction of travel. A 1-block rise becomes a slope.\nEnd it at a machine or a funnel.",

    "book.astrofactory.ref.machine.funnel.name": "Funnel",
    "book.astrofactory.ref.machine.funnel.body": "One-block passive transfer. No power, no fuel, no GUI.\nPulls from above and behind, pushes toward its facing.",
    "book.astrofactory.ref.machine.funnel.usage": "Put one under a machine output, facing down into a chest. Done.\nUse an arm only when you need range or filtering.",

    "book.astrofactory.ref.machine.burner_inserter.name": "Burner Inserter",
    "book.astrofactory.ref.machine.burner_inserter.body": "Moves items across blocks. Burns coal, about 7 moves per coal.\nOrientation decides direction: grabs at the back, drops at the front.",
    "book.astrofactory.ref.machine.burner_inserter.usage": "Right-click: 9 filter slots, only/not-these, batch 1/8/64, Keep N.\nIt never grabs what will not fit, so items cannot get stuck.",

    "book.astrofactory.ref.machine.stone_furnace.name": "Stone Furnace",
    "book.astrofactory.ref.machine.stone_furnace.body": "2x2 multiblock. Raw ore to ingot, about 12 per coal.\nSmelt an iron ingot again (320 ticks) to get steel.",
    "book.astrofactory.ref.machine.stone_furnace.usage": "Input slot for ore, fuel slot for coal.\nCollect output by hand or connect a funnel/arm.",

    "book.astrofactory.ref.machine.assembler.name": "Assembler",
    "book.astrofactory.ref.machine.assembler.body": "3x3 multiblock, matches recipes on the 3x3 grid.\nIt is electric - with no power it will not move at all.",
    "book.astrofactory.ref.machine.assembler.usage": "Lay the parts out on the 9 slots. Shows 'No power' when unpowered.\nCircuit boards and tech packs are made here.",

    "book.astrofactory.ref.machine.lab.name": "Research Table",
    "book.astrofactory.ref.machine.lab.body": "3x3 multiblock. Feed tech packs to unlock technologies.\nAlso needs power; unpowered means no contribution.",
    "book.astrofactory.ref.machine.lab.usage": "Insert a tech pack and press Contribute. Arrows switch targets.\nTech packs can be hand-crafted or automated.",

    "book.astrofactory.ref.machine.boiler.name": "Boiler",
    "book.astrofactory.ref.machine.boiler.body": "2x3 multiblock. Burns coal to turn water into steam.\nSteam is the fuel for power generation.",
    "book.astrofactory.ref.machine.boiler.usage": "Connect the pump for water, pipe the steam out.\nNo water means no steam - coal alone is not enough.",

    "book.astrofactory.ref.machine.steam_engine.name": "Steam Engine",
    "book.astrofactory.ref.machine.steam_engine.body": "3x5 multiblock. Consumes steam to make power.\nOne engine will not run many machines; add more as you expand.",
    "book.astrofactory.ref.machine.steam_engine.usage": "Pipe steam in from the boiler.\nPut a power pole next to it; machines within 5 blocks draw power.",

    "book.astrofactory.ref.machine.offshore_pump.name": "Water Pump",
    "book.astrofactory.ref.machine.offshore_pump.body": "1x2. Place it by water to pump water for the boiler.\nWithout it the boiler just burns coal and produces nothing.",
    "book.astrofactory.ref.machine.offshore_pump.usage": "Place it against a water source. Pipe the water to the boiler.",

    "book.astrofactory.ref.machine.steam_pipe.name": "Steam Pipe",
    "book.astrofactory.ref.machine.steam_pipe.body": "Carries steam and water. Produces and consumes nothing.\nChain them in a line; no power needed.",
    "book.astrofactory.ref.machine.steam_pipe.usage": "Pump -> pipe -> boiler -> pipe -> steam engine.",

    "book.astrofactory.ref.machine.power_pole.name": "Power Pole",
    "book.astrofactory.ref.machine.power_pole.body": "Single-block energy node, supplies within 5 blocks.\nNo wiring needed; just place it near the machine.",
    "book.astrofactory.ref.machine.power_pole.usage": "One next to the steam engine, one next to the consumers.\nToo far apart means add more poles.",

    "book.astrofactory.ref.machine.ore_vein.name": "Ore Vein",
    "book.astrofactory.ref.machine.ore_vein.body": "Generated underground with a finite reserve.\nThe miner drains it; when empty it becomes a poor vein.",
    "book.astrofactory.ref.machine.ore_vein.usage": "Mine it with a miner. When it runs dry, find another one.\nVein fields are 40-64 blocks apart.",

    "book.astrofactory.ref.item.iron_gear.name": "Iron Gear",
    "book.astrofactory.ref.item.iron_gear.body": "2 iron ingots make 1. Craftable at a table or in the furnace part channel.\nAlmost every machine recipe needs it.",
    "book.astrofactory.ref.item.iron_gear.usage": "Stock up early; you will need a lot.",

    "book.astrofactory.ref.item.copper_cable.name": "Copper Cable",
    "book.astrofactory.ref.item.copper_cable.body": "3 copper ingots make 1.\nUsed by circuit boards, power poles and inserters.",
    "book.astrofactory.ref.item.copper_cable.usage": "Copper matters as much as iron - do not only mine iron.",

    "book.astrofactory.ref.item.electronic_circuit.name": "Circuit Board",
    "book.astrofactory.ref.item.electronic_circuit.body": "2 iron gears + 2 copper cables on the assembler 3x3 grid.\nThis is the gate to electrification.",
    "book.astrofactory.ref.item.electronic_circuit.usage": "Making it needs power, so generate power first. That is the wall at steps 13-15.",

    "book.astrofactory.ref.item.automation_pack.name": "Automation Pack (red flask)",
    "book.astrofactory.ref.item.automation_pack.body": "2 copper ingots + 1 iron gear make 2, craftable by hand.\nFeed to the research table to unlock tech.",
    "book.astrofactory.ref.item.automation_pack.usage": "Hand-crafting is a stopgap; automate it in the assembler for volume.",

    "book.astrofactory.ref.item.steel_ingot.name": "Steel Ingot",
    "book.astrofactory.ref.item.steel_ingot.body": "Smelt an iron ingot again in the stone furnace, 320 ticks each.\nFive times slower than iron, but wrenches and guns need it.",
    "book.astrofactory.ref.item.steel_ingot.usage": "Steel is slow - give it a dedicated furnace so it does not compete with iron.",

    "book.astrofactory.ref.trouble.machine_stopped.name": "A machine is not running",
    "book.astrofactory.ref.trouble.machine_stopped.body": "Right-click it with an iron or steel wrench.\nIt reports power state, run state, and what input is missing.",
    "book.astrofactory.ref.trouble.machine_stopped.usage": "Fastest way to diagnose - far quicker than breaking and replacing.",

    "book.astrofactory.ref.trouble.no_power.name": "The GUI says 'No power'",
    "book.astrofactory.ref.trouble.no_power.body": "Assembler and research table are electric; they do not burn coal.\nGenerate power: pump -> boiler -> steam engine -> power pole.",
    "book.astrofactory.ref.trouble.no_power.usage": "Check the engine is turning (steam going in), then check the pole is within 5 blocks.",

    "book.astrofactory.ref.trouble.arm_stuck.name": "The arm grabbed something but will not drop it",
    "book.astrofactory.ref.trouble.arm_stuck.body": "The target is full, or the target is not a container.\nThe arm holds on - nothing is lost, but it stops working.",
    "book.astrofactory.ref.trouble.arm_stuck.usage": "Right-click the arm to see its state. If it says output full, clear the target.\nWith Keep N set, stopping at the limit is normal.",

    "book.astrofactory.ref.trouble.belt_backup.name": "Items pile up on the belt",
    "book.astrofactory.ref.trouble.belt_backup.body": "Downstream is blocked. The backup travels back and the miner stops.\nThis is intended - it makes blockage visible.",
    "book.astrofactory.ref.trouble.belt_backup.usage": "Check from the far end backwards: is the last machine full? Unpowered? Output not collected?",

    "book.astrofactory.ref.trouble.vein_depleted.name": "The miner runs but produces nothing",
    "book.astrofactory.ref.trouble.vein_depleted.body": "The vein is exhausted.\nThe miner will not relocate or find a new vein on its own.",
    "book.astrofactory.ref.trouble.vein_depleted.usage": "Find another vein (fields are 40-64 blocks apart) and move the miner there.",
}

BASE = os.path.join("src", "main", "resources", "assets", "astrofactory", "lang")

for lang, kv in (("zh_cn", ZH), ("en_us", EN)):
    path = os.path.join(BASE, lang + ".json")
    with io.open(path, encoding="utf-8") as f:
        d = json.load(f, object_pairs_hook=collections.OrderedDict)
    before = len(d)
    for k, v in kv.items():
        d[k] = v
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    print(lang, before, "->", len(d))
