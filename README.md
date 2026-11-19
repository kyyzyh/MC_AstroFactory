# AstroFactory

《异星工厂》风格的 Minecraft 自动化模组 —— 采矿、运输、熔炼、制作、组装，五段完整产线。

| 项 | 值 |
|---|---|
| mod_id | `astrofactory` |
| 版本 | 0.1.0 |
| 目标 | Minecraft **26.3** + NeoForge **26.3.0.113** |
| 构建 | Gradle 9.2.1 + moddev 2.0.148，Java 25 toolchain |
| 代码量 | 33 个 Java 文件 / 约 3500 行 |

## 构建

```sh
# Windows（本机已离线配置好 GRADLE_USER_HOME 与 JDK）
set GRADLE_USER_HOME=%CD%\.gradle-home
set JAVA_HOME=%CD%\jdk-21.0.13+9
gradlew.bat build --offline
```

产物 `build/libs/astrofactory-0.1.0.jar`，已同步到 `dist/astrofactory-0.1.0.jar`。

- `gradlew.bat runClient --offline` — 启动客户端实测
- `gradlew.bat runServer --offline` — 启动服务端
- 注意：`runData` 在本项目不可用（moddev 2.0.148 未定义 data run 类型）

## 五段产线

```
① 采矿          ② 运输           ③ 熔炼        ④ 制作        ⑤ 组装
采矿机    ──>  传送带/机械臂  ──>  熔炼炉  ──>  制作台  ──>  组装机
原矿           连续移动           锭           零件          成品
                                                              │
                                                              └─> 科研台推进科技树
```

### ① 采矿

| 方块 | 说明 |
|---|---|
| 采矿机 `miner` | 无需矿脉，每 40 tick 产出 1 个**原矿**；产物池：粗铁、粗铜、粗金、煤、圆石 |

### ② 运输

| 方块 | 说明 |
|---|---|
| 传送带 `conveyor` | 真连续移动，3 条并行轨道，支持汇流与 backpressure，物品按 progress 平滑插值渲染 |
| 高速传送带 `fast_conveyor` | 带速 2 倍（0.10/tick），由 `logistics_1` 解锁 |
| 机械臂 `robotic_arm` | 抓取 / 投放，遵守容器的 `canPlaceItem`（不会塞进输出槽） |

### ③ 熔炼（原矿 → 锭）

| 方块 | 说明 |
|---|---|
| 熔炼炉 `smelter` | 1 输入 + 1 输出，每 60 tick 熔炼 1 个；带 GUI 与进度条 |

配方：粗铁→铁锭、粗铜→铜锭、粗金→金锭、圆石→石头、沙子→玻璃

### ④ 制作（锭 → 零件）

| 方块 | 说明 |
|---|---|
| 制作台 `crafter` | 1 输入 + 1 输出，每 40 tick 成型 1 个；带 GUI 与进度条 |

配方：铁锭 ×2 → 铁齿轮、铜锭 ×3 → 铜线圈

### ⑤ 组装（零件 → 成品）

| 方块 | 说明 |
|---|---|
| 组装机 `assembler` | 3×3 输入 + 1 输出，每 30 tick 合成 1 个；带 GUI |
| 科研台 `research_table` | 带 GUI，投入电路板推进科技树 |

配方：铁齿轮 ×2 + 铜线圈 ×2 → 电路板

电路板是**唯一的科研包**，强制玩家把整条五段产线搭起来才能研究。

## 中间物品

| 物品 | 产出环节 | 配方 |
|---|---|---|
| 铁齿轮 `iron_gear` | ④ 制作台 | 铁锭 ×2 |
| 铜线圈 `copper_coil` | ④ 制作台 | 铜锭 ×3 |
| 电路板 `circuit_board` | ⑤ 组装机 | 铁齿轮 ×2 + 铜线圈 ×2 |

## 科技树

| 科技 | 消耗 | 前置 | 解锁 |
|---|---|---|---|
| automation_1 自动化 I | 10 | — | 传送带、机械臂 |
| mining_1 采矿 I | 20 | automation_1 | 采矿机 |
| assembly_1 组装 I | 20 | automation_1 | 组装机 |
| logistics_1 物流 I | 50 | mining_1 + assembly_1 | 高速传送带 |
| research_1 科研 I | 50 | assembly_1 | 科研台 |

科研进度存在玩家 attachment（`ResearchProgress`），换机器不丢，死亡保留。

## 目录

```
src/main/java/com/xiaoyi/astrofactory/
├── Astrofactory.java          @Mod 主类
├── ModRegistry.java           全部注册入口（方块/物品/BE/菜单/创造页）
├── block/                     8 方块 + 7 方块实体
├── client/                    ConveyorRenderer（26.3 extract/submit 管线）、4 个 Screen
├── menu/                      Assembler / Research / Smelter / Crafter 四个菜单
├── recipe/                    组装机内置配方
└── research/                  科技树定义与玩家进度

src/main/resources/assets/astrofactory/   模型/贴图/语言（中英双语）
docs/api-26.3-notes.md                    26.3 API 实测笔记（javap 反编译得出）
tools/                                    贴图生成脚本（PIL 程序化绘制）
```

## 26.3 关键 API 变更

详见 `docs/api-26.3-notes.md`，核心几条：

- `ResourceLocation` → **`Identifier`**（旧类完全不存在）
- `BlockEntityType.Builder` 已删除，改用公开构造器 `new BlockEntityType<>(supplier, Set<Block>)`
- BlockEntity 存档从 `CompoundTag` 改为 **`ValueInput` / `ValueOutput`**
- GUI 渲染是 **extract 式**（`GuiGraphicsExtractor`），不是 `render(GuiGraphics)`
- `ContainerData` 服务端 `get()` 必须实时读真实数据，否则同步永远不更新

## 已知限制

- 配方表是硬编码的 Java 内置表，尚未迁移到 JSON recipe + 数据生成
- 熔炼炉/制作台只有 1 个输入槽，无法处理需要多种原料的配方
- 机械臂的抓取/投放逻辑仍较简单，未做多方块结构与朝向寻址
- 无版本控制（`git init` 未执行）
