# AstroFactory

《异星工厂》风格的 Minecraft 自动化模组 —— 采矿、运输、熔炼、制作、组装，五段完整产线。

| 项 | 值 |
|---|---|
| mod_id | `astrofactory` |
| 版本 | 0.1.0 |
| 目标 | Minecraft **26.3** + NeoForge **26.3.0.113** |
| 构建 | Gradle 9.2.1 + moddev 2.0.148，Java 25 toolchain |
| Java | 本机 JDK 25 在 `jdk-25.0.5+8`（构建用）；`JAVA_HOME` 未设，构建脚本显式指定 |
| 代码量 | 33 个 Java 文件 / 约 3500 行 |

## 构建

```sh
# Windows（本机已离线配置好 GRADLE_USER_HOME 与 JDK）
set GRADLE_USER_HOME=%CD%\.gradle-home
set JAVA_HOME=%CD%\jdk-25.0.5+8
gradlew.bat build --offline
```

产物 `build/libs/astrofactory-0.1.0.jar`，已同步到 `dist/astrofactory-0.1.0.jar`。

- `gradlew.bat runClient --offline` — 启动客户端实测
- `gradlew.bat runServer --offline` — 启动服务端
- 注意：`runData` 在本项目不可用（moddev 2.0.148 未定义 data run 类型）

## id 对照（规格 vs 代码）

规格文档里的展示名与注册 id 不同，按 **注册 id** 为准：

| 规格展示名 | 注册 id | 说明 |
|---|---|---|
| 传送带 `conveyor` | `transport_belt` | 2 条并行轨道 |
| 高速传送带 `fast_conveyor` | `fast_transport_belt` | 带速 0.188 |
| / | `burner_inserter` | 燃烧机械臂 |
| 采矿机 `miner` | `burner_miner` | 2×2 多方块 |
| 熔炼炉 `smelter` | `stone_furnace` | 2×2 多方块 |
| 制作台 `crafter` | — | **已删除**（Q1 决定） |
| 组装机 `assembler` | `assembler` | 3×3 多方块，9 格能出能进 |
| 科研台 `research_table` | `lab` | 3×3 多方块 |
| 铜线圈 `copper_coil` | `copper_cable` | 中文 lang 显示「铜线」，文案写「线圈」是历史遗留（见已知限制） |

## 五段产线

```
① 采矿          ② 运输           ③ 熔炼        ④ 制作        ⑤ 组装
采矿机    ──>  传送带/机械臂  熔炼炉        熔炼炉直接出锭  组装机
原矿           连续移动           锭           （无独立制作台） 成品
                                                              │
                                                              └─> 科研台推进科技树
```

> 阶段④「制作台」已被 Q1 删除：零件（铁齿轮/铜线圈）现在是**熔炼炉的熔炼产物**
> （内置配方 `iron_ingot_to_iron_gear` / `copper_ingot_to_copper_cable`，`MachineType.CRAFTING`），
> `stone_furnace` 同时承担 ③ 原矿→锭 与 ④ 锭→零件 两段。

### ① 采矿

| 方块 | 说明 |
|---|---|
| 采矿机 `burner_miner` | 2×2 多方块，烧煤（1 煤 = 7 次产出，80 tick/次）。**产出必须来自脚下矿脉**：在机器下方 4 格、水平外扩 1 格范围内找 `ore_vein`，每产 1 个扣 1 点储量；矿脉采空后停机且**不再烧煤**，需把机器搬到新矿田 |
| 矿脉 `ore_vein` | 单格方块 + BlockEntity，初始储量 300/格，四态 richness 切贴图（rich / normal / poor / depleted），采空变空气。`OreVeinData` 在出生点 64 格内幂等放置 4 片圆形矿田（铁:铜:煤:石 = 3:2:2:2） |

### ② 运输

| 方块 | 说明 |
|---|---|
| 传送带 `transport_belt` | 真连续移动，**2 条**并行轨道，支持汇流与 backpressure，物品按 progress 平滑插值渲染。带速 0.094/tick（约 10.6 tick 走一格） |
| 高速传送带 `fast_transport_belt` | 带速 0.188（2 倍），由 `logistics_1` 解锁 |
| 机械臂 `burner_inserter` | **自己烧煤 + stolen 燃料**，背面抓、正面放，遵守容器的 `canPlaceItem`（不会塞进输出槽）；目标满了拿在手上等，不丢物品 |

### ③ 熔炼（原矿 → 锭）

| 方块 | 说明 |
|---|---|
| 熔炼炉 `stone_furnace` | 2×2 多方块，3 槽（0 输入 / 1 输出 / 2 燃料），烧煤。原矿→锭 60 tick |

配方：粗铁→铁锭、粗铜→铜锭、粗金→金锭、圆石→石头、沙子→玻璃

### ④ 制作（锭 → 零件）

**无独立方块。** 复用熔炼炉的 `MachineType.CRAFTING` 通道：

| 产物 | 配方 | 耗时 |
|---|---|---|
| 铁齿轮 `iron_gear` | 铁锭 ×2 | 40 tick |
| 铜线圈 `copper_cable` | 铜锭 ×3 | 40 tick |

### ⑤ 组装（零件 → 成品）

| 方块 | 说明 |
|---|---|
| 组装机 `assembler` | 3×3 多方块，**9 格输入 + 1 输出**（Q2 决定 9 格能出能进），**电力驱动**，断电停机，30 tick 合成 1 个 |
| 科研台 `lab` | 3×3 多方块，1 个科研包输入槽，**电力驱动**，投入电路板推进科技树 |

配方：铁齿轮 ×2 + 铜线圈 ×2 → 电路板

电路板是**唯一的科研包**，强制玩家把整条五段产线搭起来才能研究。

### ⑥ 蒸汽电力（stage5）

| 方块 | 说明 |
|---|---|
| 锅炉 `boiler` | 2×3 多方块，烧煤产蒸汽，1 煤 = 1600 tick，缓冲 100 |
| 蒸汽机 `steam_engine` | 3×5 多方块，耗 1 汽发 2 能 |
| 离岸泵 `offshore_pump` | 1×2 多方块，贴水 20 tick 产 1 汽，不耗燃料 |
| 蒸汽管道 `steam_pipe` | 单格，容量 20，空管不 tick |
| 电线杆 `power_pole` | 单格，容量 200，5 格曼哈顿供电 |

## 中间物品

| 物品 | 产出环节 | 配方 |
|---|---|---|
| 铁齿轮 `iron_gear` | ④ 熔炼炉（crafting 通道） | 铁锭 ×2 |
| 铜线圈 `copper_cable` | ④ 熔炼炉（crafting 通道） | 铜锭 ×3 |
| 电路板 `electronic_circuit` | ⑤ 组装机 | 铁齿轮 ×2 + 铜线圈 ×2 |
| 自动化科技包 `automation_pack` | —（已注册，尚未配方） | — |

## 科技树

共 9 个节点，全部已接入 `Technologies.java` 与科研台界面。

| 科技 | 消耗 | 前置 | 解锁 |
|---|---|---|---|
| automation_1 自动化 I | 10 | — | 传送带、机械臂 |
| electronics 电子学 | 10 | automation_1 | 电路板、电动臂、铁箱、科研台 |
| mining_1 采矿 I | 20 | automation_1 | 采矿机 |
| assembly_1 组装 I | 20 | automation_1 | 组装机 |
| steam_power 蒸汽动力 | 50 | electronics | 锅炉、蒸汽机、离岸泵、管道、电线杆 |
| logistics_1 物流 I | 50 | mining_1 + assembly_1 | 高速传送带 |
| research_1 科研 I | 50 | assembly_1 | 科研台 |
| automation_2 自动化 II | 40 | assembly_1 | 组装机 2 |
| steel_processing 钢铁加工 | 50 | steam_power | 钢熔炉、钢箱 |

> **消耗单位 = 科研包个数**（1 个包 = 1 点）。科研包有两种：**电路板** 与 **红瓶**（`automation_pack`），
> 都能在科研台投入。数值取规格 §4 的门槛量级，属灰区。
>
> **同时可研究多项时**：科研台界面用 `<` / `>` 按钮在可研究列表里切换目标，
> 顶部显示「第 n / m 项」。列表顺序 = `Technologies.all()` 的定义顺序。
>
> **解锁项只做展示**，不做硬门禁 —— 方块全部已注册可直接合成，避免破坏既有存档与产线。

科研进度存在玩家 attachment（`ResearchProgress`），换机器不丢，死亡保留。
`/astrofactory tech` 可在聊天框打印整棵树（`[OK]` 已解锁 / `[  ]` 可研究 / `[--]` 前置未满足）。

## 目录

```
src/main/java/com/xiaoyi/astrofactory/
├── Astrofactory.java          @Mod 主类
├── ModRegistry.java           注册总入口（转发别名，保持旧代码 ModRegistry.XXX 可用）
├── registry/                  按职责拆分的注册类：ModBlocks / ModItems /
│                              ModBlockEntities / ModMenus / ModCreativeTab
├── machine/                   机器公共骨架：AbstractMachineEntity（容器+多方块）、
│                              MachineStorage（新旧存档兼容）、MachineOutput、
│                              MultiBlockOps、OreVeinProspector（矿脉查找）
├── block/                     13 方块 + 12 方块实体
├── power/                     PowerSource / FuelPowerSource / Steam / Electric 网络
├── multiblock/                MultiBlockMachineBlock + MultiBlockHelper
├── client/                    传送带渲染器 + 4 个 Screen
├── menu/                      Assembler / Lab / Boiler / StoneFurnace 四个菜单
├── recipe/                    机器配方数据化（内置 + 数据包 machine_recipes）
├── research/                  科技树定义（9 节点）与玩家进度
└── world/                     OreVeinData 矿田幂等生成
```

```
src/main/resources/assets/astrofactory/   模型/贴图/语言（中英双语）
docs/api-26.3-notes.md                    26.3 API 实测笔记（javap 反编译得出）
tools/                                   贴图生成脚本（PIL 程序化绘制）
```

## 26.3 关键 API 变更

详见 `docs/api-26.3-notes.md`，核心几条：

- `ResourceLocation` → **`Identifier`**（旧类完全不存在）
- `BlockEntityType.Builder` 已删除，改用公开构造器 `new BlockEntityType<>(supplier, Set<Block>)`
- BlockEntity 存档从 `CompoundTag` 改为 **`ValueInput` / `ValueOutput`**
- GUI 渲染是 **extract 式**（`GuiGraphicsExtractor`），不是 `render(GuiGraphics)`
- `ContainerData` 服务端 `get()` 必须实时读真实数据，否则同步永远不更新
- 26.3 能量系统：`EnergyHandler` / `SimpleEnergyHandler` / `Capabilities.Energy.BLOCK`（`IEnergyStorage` 已删）

## 已知限制

- 阶段④「制作台」已删，零件走熔炼炉 crafting 通道；README 与部分历史文案仍写「制作台」
- `copper_cable` 注册名与中文显示「铜线」不一致，配方/成就/指南文案统一写「铜线圈」（历史遗留，待统一）
- 科技树 9 个节点已全部接入，但 `unlocks` 里的 `assembler_2` / `steel_furnace` / `steel_chest` 等**尚无对应方块**（0.1.0 范围外，仅作展示）
- 斜坡传送带（SLOPE 属性 + 线性插值 + 上下交接）未实现（规格新增待办）
- 出生点包（1 采矿机 + 1 石熔炉 + 20 传送带 + 1 手册）未实现
- 配方表内置部分是硬编码 Java，数据包 `machine_recipes` 已有 3 条示例（含红瓶）
- 机械臂的抓取/投放逻辑较简单，未做多方块结构与朝向寻址（`canPlaceItem` 已遵守）
- 已用 git 版本控制并推送（`https://github.com/kyyzyh/MC_AstroFactory.git`）
