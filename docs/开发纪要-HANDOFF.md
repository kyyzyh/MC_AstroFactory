# AstroFactory 开发纪要（HANDOFF）

> **给接手的 AI / 开发者**：本文是「从头到现在做了什么」的完整记录。
> 想快速了解项目看 `README.md`；想知道**为什么这样做、踩过什么坑、还差什么**，看本文。
>
> 最后更新：2026-11-20 18:56
> 当前提交：`032b368`（工作区干净）

---

## 0. 三十秒速览

| 项 | 值 |
|---|---|
| 项目 | AstroFactory —— 《异星工厂》风格的 Minecraft 自动化模组 |
| 工程路径 | `E:\AI Work\XiaoYi\MC Mod` |
| 目标环境 | Minecraft **26.3** + NeoForge **26.3.0.113** |
| 构建 | Gradle 9.2.1 + moddev 2.0.148，Java **25** toolchain |
| 规模 | 73 个 Java 文件 / 7620 行；13 方块 / 22 物品 / 13 方块实体 |
| 版本 | 0.1.0（20 次提交，`0118c2a` → `7e28df6`） |
| 仓库 | `https://github.com/kyyzyh/MC_AstroFactory.git` |
| 最新产物 | `build/libs/astrofactory-0.1.0.jar`（830 KB） |
| 状态 | 可构建、可运行、旧存档兼容验证通过 |

**一句话**：规格 v3.2 的 8 个阶段里，阶段 1–6 已实现并验证；阶段 7（科技树重写）、阶段 8（新手教程 HUD）部分完成。

---

## 1. 构建与运行（必读，否则第一步就卡住）

```bat
cd /d "E:\AI Work\XiaoYi\MC Mod"
set GRADLE_USER_HOME=%CD%\.gradle-home
set JAVA_HOME=%CD%\jdk-25.0.5+8
gradlew.bat build --offline
```

**为什么必须这样设**：

- `JAVA_HOME` 系统环境变量**没有设置**，不显式指定 Gradle 会找错 JDK 或联网下载 toolchain。
- `.gradle-home`（1.7 GB）是预置的离线依赖缓存。**不加 `--offline` 会去连 maven.neoforged.net，本机实测只有 ~16 KB/s，必然超时。**
- `local-maven/`（95 MB）是本地镜像仓库，存放从腾讯云镜像预先拉下来的大构件，`build.gradle` 里排在第一优先级。

**运行**：

```bat
gradlew.bat runClient --offline    :: 客户端，1280x720
gradlew.bat runServer --offline    :: 服务端，--nogui
```

**已知坑**：

- `runData` **不可用**（moddev 2.0.148 没定义 data run 类型），所有资源都是手写或脚本生成。
- 用 bash 后台跑 `gradlew.bat` 容易留下僵尸 JVM 占住 25565 端口，第二次启动会 `BindException`。杀进程：`taskkill //PID <pid> //F`。查端口占用：`netstat -ano | Select-String ":25565"`。
- `gradlew.bat runClient --args="..."` **不生效**（会被当成主类名）。要传参必须在 `build.gradle` 里新建一个 run 配置。

**构建流程里自动做的两件事**（`032b368` 起）：

| 任务 | 时机 | 作用 |
|---|---|---|
| `validateJson` | 挂在 `check`，`build` 会跑 | 递归校验 `src/main/resources` 下全部 `.json`（当前 495 个）。**Gradle 打包不校验 JSON 内容**，语法错会一路带进游戏。 |
| `syncJar` | `build` 后自动触发 | 把 jar 复制到 ①`game_mods_dir`（`gradle.properties` 配置，当前指向 HMCL 的 `26.3-NeoForge/mods`）②`dist/` ③`../MC/McMod/`；并清理同 modId 的旧版本 jar。目标目录不存在则跳过。 |

> **真实事故**：`zh_cn.json` 少一个逗号 → `gradlew build` 成功 → 进游戏才报
> `MalformedJsonException` → 整个语言文件被丢弃 → **界面全部回落成英文**。
> 这就是加 `validateJson` 的原因。同类问题（模型/配方/成就 JSON）都会静默通过构建。

---

## 2. 时间线：按顺序做了什么

### 11-19 23:00 · 初始骨架（`2be53ee`）

从零搭 NeoForge 工程，实现第一版：传送带、机械臂、采矿机、熔炼炉、组装机、科研台、5 段产线、科技树雏形。

### 11-20 00:30 · 接入 git（`d11c380`）

`git init -b main`，加 `.gitignore`，关联远程 `https://github.com/kyyzyh/-.git`（后改名为 `MC_AstroFactory`），用 `--allow-unrelated-histories` 合并远程占位提交，解决 README 冲突后推送。

### 11-20 00:50 · 新手教程（`26e3c7e`）

- 8 个方块的掉落表（`data/astrofactory/loot_table/`）
- 11 条原版合成配方（`data/astrofactory/recipe/`，现共 15 条）
- 10 个成就（`data/astrofactory/advancement/`，现共 9 个 JSON，root + 8）
- `/astrofactory guide` 命令 + 全量中英文教程文案

### 11-20 01:56 · 规格对齐（`664c176`）

按用户答复的 Q1–Q10 做统一改名，**这是全项目最重要的一次结构性决定**：

| 编号 | 决定 | 影响 |
|---|---|---|
| Q1 | **删除制作台 `crafter`** | 零件改由熔炼炉的 `MachineType.CRAFTING` 通道产出 |
| Q2 | 组装机 **9 格能出能进** | 3×3 输入 + 1 输出，共 10 槽 |
| Q3 | 采矿机出矿口 **1 格宽** | 2×2 机器只在 FACING 面一格输出 |
| Q4 | 多方块渲染 **B 方案** | 每个 dummy 各渲染自己那份模型 |
| Q5 | 高速带速度 **0.188** | 普通带 0.094，正好 2 倍 |
| Q6 | 允许 worldgen 探针 | 出生点 64 格内幂等放置 4 片矿田 |
| Q7 | 科技计数 = **熔炉产出时** | 不是采矿时 |
| Q9 | `runData` 不可用 | 确认后所有资源改手写/脚本生成 |
| Q10 | 统一改成规格 ID | 见下方 id 对照表 |

**id 对照（规格展示名 ≠ 注册 id，别搞混）**：

| 规格名 | 注册 id |
|---|---|
| conveyor | `transport_belt` |
| fast_conveyor | `fast_transport_belt` |
| miner | `burner_miner` |
| smelter | `stone_furnace` |
| crafter | **已删除** |
| research_table | `lab` |
| copper_coil | `copper_cable` |

### 11-20 03:26 · 多方块系统（`e82ea3b`）

**架构：master + dummy，整台机器只有 1 个 BlockEntity**（在 master 格）。

- `MultiBlockMachineBlock` 基类：`FACING` + `PART`（0=master，1..N=dummy）+ 可选 `ACTIVE`
- `MultiBlockHelper.allCells()` 按 FACING 和尺寸算出整机格子列表
- `repair()` 在区块加载时自修复：dummy 缺了补上，位置被占则整机清掉（规格 §5.1「不留幽灵」）
- 先用 2×2 采矿机验证，自检 5/5 通过

### 11-20 03:40 · 其余机器多方块化（`75d051d`）

熔炼炉 2×2、组装机 3×3、科研台 3×3 全部套上同一套基类。顺手修了科研台没有 `ACTIVE` 属性导致的崩溃。

### 11-20 03:59 · 矿脉与世界生成（`0649add`）

- `OreVeinBlock` 单格方块 + BlockEntity，初始储量 300/格，四态 richness 切贴图
- `OreVeinData`（SavedData）在出生点 64 格内幂等放置 4 片圆形矿田，铁:铜:煤:石 = 3:2:2:2
- **幂等**是关键：重复进入世界不会重复刷矿

### 11-20 04:08 · 燃料系统（`557fe5a`）

`PowerSource` 抽象 + `FuelPowerSource` 实现：

- 燃料槽 → 燃烧 tick 预算 → 每 tick 扣 1
- **规格红线**：燃烧期必须吃煤；机械臂会「偷」手上搬的燃料（`stealFromCarried`）
- 煤续航：采矿机 7 次 / 熔炉 12 次 / 锅炉 58 次
- 预算存进存档，跨区块卸载不重置

### 11-20 07:50 · 蒸汽电力闭环（`cfc508c`）

规格 v3.2 强制要求的系统：

```
锅炉(烧煤) → 蒸汽管道 → 蒸汽机 → 电线杆 → 组装机/科研台
                    ↑
              离岸泵(贴水产汽)
```

- `SteamNetwork.equalizeFromCells()` 从整机边缘格子向邻居均摊蒸汽
- `ElectricNetwork.pushEnergyFromCells()` 同理推电力
- 26.3 的 `IEnergyStorage` / `EnergyStorage` **已被 `EnergyHandler` 取代**，这里用自研的轻量节点接口 `IEnergyNode` / `ISteamNode`，不走 capability

### 11-20 09:53 · 两个运行时 bug（`0f78155`）

1. **传送带物品不同步到客户端** —— 补 `getUpdateTag` / `getUpdatePacket`，脏标记触发
2. **存档写空栈刷警告** —— `ItemStack.CODEC` 拒绝 `count=0` 的 air，改成空槽不写

### 11-20 09:56 · 截图交付（`a4a7d53`）

写了临时 `AutoDemo` 自动演示钩子：自动建创造世界 → 铺平台 → 搭 3 个场景 → 7 个机位截图 → 退出。

- 产物：`run/screenshots/astrofactory_1..7_*.png`
- **教训**：必须 `server.execute(...)` 在服务端线程改世界，渲染线程直接 `setBlock` 只写进客户端副本，服务端不认，产线永远不动
- AutoDemo 已移出源码树到 `tools/demo/AutoDemo.java.txt`（备份，不参与构建）

### 11-20 10:08 · 机械臂模型 bug（`6e1be76`）

`blockstate` 给水平朝向多加了 `"x": 90`，导致模型被放倒成悬空横杆。删掉即修复。

### 11-20 10:48 · 全量美术重做（`134d101`）

参考《异星工厂》：工业风、金属机身、大色块功能区、黑底散热槽、铆钉、体积感。

- 全部贴图由 PIL 程序化绘制，脚本在 `tools/gen_*.py` + 共享库 `tools/tex_common.py`（32 KB，最核心）
- **改贴图请改脚本重跑，不要手画**——手画的下次重跑就没了

### 11-20 11:15 · 矿脉枯竭闭环（`94dc0a2`）

采矿机从「凭空产矿」改为「必须踩在矿脉上」：

- `OreVeinProspector` 在机器下方 4 格 + 水平外扩 1 格找 `ore_vein`
- 每产 1 个扣 1 点储量；采空后停机且**不再烧煤**
- 背压（缓存满）时也不扣储量，避免白白吃矿

### 11-20 12:02 · 文档回写（`b17d548`）

补 guide step6/7，README 全面改写成规格 ID，铜线圈命名统一。

### 11-20 13:27 · 出生点包（`1e0ec4c`）

首次登录发 1 采矿机 + 1 石熔炉 + 20 传送带 + 1 手册。

- 用 `getPersistentData()` 打标 `astrofactory:starter_received`，跨死亡/重连保留，只发一次
- 背包满则掉脚边，不丢物品

### 11-20 14:01 · 故事/任务/成长/武器（`01bf7da`）

规格 §7 的内容层：

- `story/Story.java` —— 赫斯珀里亚星背景，5 段文案
- `quest/Quests.java` —— 18 步任务清单，`/astrofactory quests` 查看
- `level/PlayerLevel.java` —— 玩家等级 + EXP，用 NeoForge `AttachmentType` 存玩家身上
- `weapon/Weapons.java` —— 4 把工业工具当武器（铁扳手/钢扳手/动力拳套/铆钉枪），带等级门槛

### 11-20 15:12 · 第三方库接入（`86d264d`）

逐个查证 Modrinth API + 官方 Maven + 实际拉取 jar，结论写进 `docs/third-party-26.3.md`：

| 库 | 26.3 状态 | 坐标 |
|---|---|---|
| Cloth Config | ✅ 已接入 | `me.shedaniel.cloth:cloth-config-neoforge:26.3.159` |
| Jade | ✅ 已接入 | `maven.modrinth:jade:26.3.5+neoforge` |
| JEI | ✅ 已接入 | `mezz.jei:jei-26.3-neoforge-api:31.18.0.118` |
| REI | ✅ 已接入 | `me.shedaniel:RoughlyEnoughItems-api-neoforge:26.3.824` |
| Curios | ✅ 已接入 | `top.theillusivec4.curios:curios-neoforge:17.0.1+26.3` |
| GeckoLib | ⏸ 有但未启用 | `maven.modrinth:geckolib` |
| **ModMenu** | ❌ **无 NeoForge 版** | — |
| **Patchouli** | ❌ **最高只到 26.1** | — |

### 11-20 17:17 · 化整为零重构（`7e28df6`）—— 最近一次提交

用户要求「优化代码，化整为零，尽量使用官方标准，方便后续开发」。做了四件事：

**① 拆 ModRegistry（401 → 181 行）**

```
registry/ModBlocks.java        方块
registry/ModItems.java         方块物品 + 中间物品 + 武器
registry/ModBlockEntities.java 方块实体类型
registry/ModMenus.java         GUI 菜单类型
registry/ModCreativeTab.java   创造模式标签页
```

`ModRegistry` 保留**同名字段做转发别名**，所以既有的 `ModRegistry.TRANSPORT_BELT` 等调用**零改动**。新代码建议直接用 `ModBlocks.XXX`。

**② 新增 `machine/` 包（5 个类）**

| 类 | 作用 |
|---|---|
| `AbstractMachineEntity` | 继承**官方 `BaseContainerBlockEntity`**，统一容器样板（`getItems/setItems` 即可）、MenuProvider 转调、多方块 `onLoad` 自修复、`setActive` 整机同步 |
| `MachineStorage` | 物品存档走官方 `ContainerHelper.saveAllItems/loadAllItems`，**同时兼容 0.1.0 早期的「按顺序 Items」旧格式** |
| `MachineOutput` | 把物品推入相邻传送带/容器 |
| `MultiBlockOps` | 静态版多方块操作，给**非容器**机器（蒸汽机、离岸泵）复用 |
| `OreVeinProspector` | 矿脉查找 + 扣减，带位置缓存与重扫冷却 |

**③ 7 台机器全部改成继承 `AbstractMachineEntity`**

石熔炉 / 组装机 / 锅炉 / 科研台 / 蒸汽机 / 离岸泵 / 采矿机，各自只留业务 tick 逻辑。

行数变化：
- `BurnerMinerBlockEntity` 473 → 324
- `AssemblerBlockEntity` 491 → 193
- `StoneFurnaceBlockEntity` ~250 → 162

净减 1644 行重复代码。

**④ 修了两个隐藏问题**

- `zh_cn.json` / `en_us.json` 各缺一个逗号，**两个语言文件根本解析不了**（会丢全部中文/英文文案）。已修复并校验 JSON 合法。
- `MultiBlockMachineBlock` 的 javadoc 引用了不存在的类名 `AbstractMultiBlockEntity`，已改。

**这次重构的验证（重要）**：不只是编译通过，还做了**真实旧存档回归**：

- 拿改动前生成的 `run/saves/astro_compat`（物品是老格式）跑服务端 + 客户端
- 结果：采矿机燃料槽 coal×61/62/63、锅炉 coal×62/63、熔炉 slot0 coal×64、组装机 slot0 electronic_circuit×16 **全部正确还原**
- 102 个 AstroFactory 方块实体全部正常实例化，零 exception
- 存档回写后自动升级成官方带 `Slot` 字段的新格式（老档只读一次即升级）

### 11-20 18:53 · 修「启动是英文」+ 构建流程加固（`032b368`）—— 最新提交

**现象**：用户启动游戏，模组内容全是英文（`Transport Belt` 而非「传送带」），但 `options.txt` 里 `lang:zh_cn` 正常。

**根因链**：

```
14:00 打的 dist/ jar 里 zh_cn.json / en_us.json 各缺一个逗号
   ↓（Gradle 打包不校验 JSON 内容，构建显示 SUCCESSFUL）
用户复制进 E:\Game\MC\.minecraft\versions\26.3-NeoForge\mods\
   ↓
18:45 启动 → 日志 WARN: Unable to load languages: [en_us, zh_cn]
              MalformedJsonException: Unterminated object at line 70 column 4
              path $.item.astrofactory.ore_vein
   ↓
整个语言文件被丢弃 → 所有名字回落成英文注册名
```

**处理**：

1. 用 `a5e5e88cbc45`（17:17 重构后含修复的构建）替换游戏 mods 目录里的坏 jar，旧的备份到 `mods/_backup/`。
2. 新增 `validateJson` 任务并挂到 `check`——递归校验 495 个 JSON，失败时给文件路径 + 行号 + 上下文。**已故意复现原始 bug 实测能拦住。**
3. 新增 `syncJar` 任务，`build` 后自动同步到游戏 mods 目录 / `dist/` / `../MC/McMod/`，并清理同 modId 旧 jar。
4. `gradle.properties` 加 `game_mods_dir=E:/Game/MC/.minecraft/versions/26.3-NeoForge/mods`。

**教训**：JSON 类资源「打包成功」≠「内容合法」。构建通过不等于游戏能跑。

---

## 3. 当前代码结构

```
src/main/java/com/xiaoyi/astrofactory/
├── Astrofactory.java          54 行   @Mod 主类
├── ModRegistry.java          181 行   注册总入口（转发别名）
├── ModEvents.java            159 行   命令 / 配方重载 / 出生点包
├── registry/                 444 行   按职责拆分的 5 个注册类
├── machine/                  551 行   机器公共骨架（见上）
├── block/                   2761 行   13 方块 + 13 方块实体
├── multiblock/               385 行   MultiBlockMachineBlock + Helper
├── power/                    608 行   Fuel / Steam / Electric 三套动力
├── menu/                     639 行   4 个 GUI 菜单
├── client/                   386 行   传送带渲染器 + 4 个 Screen
├── recipe/                   521 行   配方数据化（内置 + 数据包）
├── research/                 167 行   科技树 + 玩家研究进度
├── level/                    162 行   玩家等级 + EXP
├── quest/                    148 行   任务清单
├── story/                     46 行   故事背景
├── weapon/                    97 行   4 把武器
├── item/                     136 行   GuideBookItem + WeaponItem
└── world/                    175 行   OreVeinData 矿田幂等生成
```

**资源**（1329 个文件）：

- `assets/astrofactory/` —— 模型/贴图/中英语言
- `data/astrofactory/recipe/` —— 15 条原版合成配方
- `data/astrofactory/advancement/` —— 9 个成就
- `data/astrofactory/loot_table/` —— 8 个方块掉落
- `data/astrofactory/machine_recipes/` —— 2 条数据包机器配方示例（另有 8 条内置在 Java 里）

**文档**：

- `README.md`（184 行）—— 项目说明、五段产线、id 对照、已知限制
- `docs/AstroFactory-规格.md`（222 行）—— 需求规格 v3.2
- `docs/api-26.3-notes.md`（297 行）—— **26.3 API 实测笔记，写代码前必看**
- `docs/third-party-26.3.md`（223 行）—— 第三方库查证

---

## 4. Minecraft 26.3 的坑（违反必失败）

这部分全部是 `javap` 反编译实测得来，**不要凭记忆写**。详见 `docs/api-26.3-notes.md`。

| 坑 | 正确写法 |
|---|---|
| `ResourceLocation` | **完全不存在**，用 `net.minecraft.resources.Identifier` |
| `BlockEntityType.Builder` | **已删除**，用 `new BlockEntityType<>(supplier, Set<Block>)` |
| BlockEntity 存档 | `CompoundTag` → **`ValueInput` / `ValueOutput`** |
| GUI 渲染 | **extract 式**（`GuiGraphicsExtractor`），不是 `render(GuiGraphics)` |
| `ContainerData.get()` | 服务端必须**实时读真实数据**，否则同步永远不更新 |
| `IEnergyStorage` | **已删除**，用 `EnergyHandler` / `SimpleEnergyHandler` / `Capabilities.Energy.BLOCK` |
| 客户端同步 | 需要覆写 `getUpdateTag` + `getUpdatePacket`，并手动 `level.sendBlockUpdated` |

---

## 5. 已知限制 / 未完成

按优先级排：

1. **科技树只实现 5 个**（automation_1 / mining_1 / assembly_1 / logistics_1 / research_1）。规格 v3.2 还要求 `steam_power`（50 铁板）、`electronics`（10 铜板）、`automation_2`（40 红瓶）、`steel_processing`（50 红瓶），未接入 `Technologies.java` 与 `LabMenu`。
2. **斜坡传送带未实现**（规格新增待办）：`SLOPE` 属性（flat/up/down）+ 物品 y 坐标线性插值 + 上下游必须同向 + 斜坡不设独立 BlockEntity。设计思路：可借鉴铁轨的斜放方式。
3. **新手教程 HUD 未做**（规格阶段 8）：用户已确认形式是「任务清单 + 高亮 + 奖励」，不强制弹窗、不锁操作、不加遮罩，高亮用**世界线框**而非粒子。目前只有 `/astrofactory quests` 文字版。
4. **配方硬编码**：内置 8 条在 Java 里，数据包 `machine_recipes` 只放了 2 条示例。
5. **`copper_cable` 命名不一致**：注册名 cable，中文显示「铜线」，配方/成就/指南文案写「铜线圈」。历史遗留。
6. **机械臂寻址较简单**：未做多方块结构感知（但遵守了 `canPlaceItem`）。
7. **`automation_pack`（红瓶）** 已注册但无配方。

**已明确否决、不要做的**：

- ❌ 机器超频功能（用户明确说「机器超频不用」）
- ❌ 3D 自由曲线布线、传送带直插机器、资源无限（《幸福工厂》借鉴边界外）

---

## 6. 给接手者的建议

**动手前**：

1. 读 `docs/api-26.3-notes.md`——26.3 变更极大，凭记忆写必错
2. 读 `README.md` 的 id 对照表——规格展示名和注册 id 完全不同
3. 用 `./gradlew.bat build --offline` 先确认基线能过

**改代码时**：

- 新机器请继承 `machine/AbstractMachineEntity`，只写 `tickServer` + `saveMachineData/loadMachineData`
- 非容器的多方块机器用 `machine/MultiBlockOps` 静态方法
- 注册新内容放 `registry/ModBlocks` 等，**不要往 `ModRegistry` 里加**
- 存档字段**只加不改名**，改名会破坏旧存档（`MachineStorage` 的旧格式兼容只在读档时探测一次）

**改资源时**：

- 贴图改 `tools/gen_*.py` 重跑，不要手画
- `runData` 不可用，JSON 要手写

**验收标准**（用户明确要求）：

- `build` 通过 **且** `runClient` 实测正常
- 旧存档必须能读（规格 §红线：读不到用默认值，不许崩）

**执行权限**：用户已授权「所有关于制作 mod 的请求完全同意」，可在灰区范围内自行判断调整，验收以用户实测为准。

---

## 7. 关键路径速查

| 要找什么 | 去哪 |
|---|---|
| 规格需求 | `docs/AstroFactory-规格.md` |
| 26.3 API 怎么写 | `docs/api-26.3-notes.md` |
| 第三方库能不能用 | `docs/third-party-26.3.md` |
| 玩法/产线/id 对照 | `README.md` |
| 机器基类 | `machine/AbstractMachineEntity.java` |
| 多方块机制 | `multiblock/MultiBlockMachineBlock.java` + `MultiBlockHelper.java` |
| 动力系统 | `power/`（Fuel / Steam / Electric） |
| 贴图生成 | `tools/tex_common.py`（共享库）+ `tools/gen_*.py` |
| 自动截图工具 | `tools/demo/AutoDemo.java.txt`（备份，需手动放回 `src/`） |
| 交付产物 | `build/libs/astrofactory-0.1.0.jar` |
