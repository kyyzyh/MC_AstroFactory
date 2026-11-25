# AstroFactory 最新版开发与工业模组功能对照

> 生成日期：2026-11-25  
> 项目基线：AstroFactory v0.7.0，Minecraft 1.21.1，NeoForge 21.1.248  
> 本文目标：核实工业模组清单、确认最新版开发平台，并把成熟模组机制对到 AstroFactory 的现有实现上。

---

## 一、结论先行

### 1.1 原清单中两个存疑项的核实结果

| 原清单条目 | 核实结论 | 后续用途 |
|---|---|---|
| **Factor'I/O：Minecraft 1.20.1 Forge，3 级传送带、7 种机械臂、模块、双车道** | **未找到符合这些描述的 Minecraft 模组。** Modrinth 对 `Factor I/O` 与 `Manufactio` 的搜索均为 0 条；网页精确搜索只找到 Reika 的 **Factor-I/O for Factorio**，它是给异星工厂添加电路网络传感器的模组，与 Minecraft 无关。名称可能记错、来源可能是小众 CurseForge 项目，也可能混入了其他项目。 | **不要把原清单中的 Factor'I/O 参数当成已证实事实，更不要据此设计兼容。** 如果用户能提供原网页链接，再做精确核实。 |
| **Manufactio Reforged：1.21.1 NeoForge，污染、虫巢、研究包、炮塔** | **没有检索到该名称对应的 1.21.1 NeoForge 项目。** 原版 Manufactio 常指 1.12.2 的异星工厂风格整合包；本次没有足够证据确认一个名为 “Manufactio Reforged” 的新版本。 | 不纳入 v0.8 的直接依赖或版本目标。污染/虫巢只能作为远期设计方向，不能因为这条未证实信息就排期。 |

### 1.2 真正可直接参考的项目

以下项目由 Modrinth API 实际检索到，可作为参考，但它们不是原清单中的 Factor'I/O：

| 项目 | 平台 | 参考价值 |
|---|---|---|
| [Researchd](https://modrinth.com/mod/researchd) | NeoForge 1.21.1 | 异星工厂式科研；可参考其节点、科研配方与进度展示 |
| [Factori-0 Rebuild](https://modrinth.com/mod/factori0rebird) | NeoForge 1.21.1 | 扫描矿脉密度、燃煤采矿机；与 AstroFactory 的矿田/探矿方向接近 |
| [Advanced Ore Deposits](https://modrinth.com/mod/advanced-ore-deposits) | NeoForge 1.21.1 | 异星工厂式矿床世界生成 |
| [Minetorio](https://modrinth.com/plugin/minetorio) | Bukkit/Paper 插件，1.20 至 26.3 | 40+ 自动化设备的概念参考；不是 NeoForge 模组，不能直接作为代码/API 依赖 |
| [Create: Buzzy Bees](https://modrinth.com/mod/create-buzzy-bees) | Forge/NeoForge，1.20.1 / 1.21.1 | Create 附属，异星工厂式机器人；仅在决定突破“纯物理传送带”边界时参考 |

### 1.3 “用最新版开发”的可执行解释

**推荐：继续用 Minecraft 1.21.1 开发，但升级到该游戏版本上的最新稳定 NeoForge 21.1.x 与对应工具链。**

原因不是“保守”，而是生态数据：Create、Mekanism、Immersive Engineering、Industrial Foregoing 当前都停在 1.21.1，且这一代只有 NeoForge 版本；只有 AE2 已经进入 26.3。若把 AstroFactory 迁到最新 MC 26.3，会失去最重要的参照与联动生态，并再次引入之前 26.3/1.21.1 错配一类问题。

因此本文的“最新版”指：

- **游戏版本**：固定 1.21.1；
- **加载器**：升级到最新稳定 21.1.x，而不是固定停留在 21.1.248；
- **模组版本**：参考各项目在 1.21.1 NeoForge 上的最新版本；
- **AstroFactory**：下一个功能版本建议为 v0.8.0，不迁 MC 大版本。

> 版本升级应先查 NeoForge 21.1.x 最新 release 与 ModDevGradle 兼容矩阵，再单独提交，不与功能开发混在一起。本次尝试读取 NeoForge Maven 元数据但连接超时，**因此没有擅自改动当前可构建的 21.1.248，也不虚构一个“最新版本号”**。

---

## 二、主流工业模组最新版本矩阵

以下数据来自本次实际调用的 Modrinth API v2。CurseForge 仅列链接，不作为本次版本号证据。

| 模组 | 1.21.1 NeoForge 最新版本 | 渠道 | 最新 MC 支持 | 1.21.1 Forge | 加载器/备注 |
|---|---:|---|---|---|---|
| [Create 机械动力](https://modrinth.com/mod/create) | **6.0.10+mc1.21.1** | Release | 1.21.1 | 无 | Forge / NeoForge；1.21.1 走 NeoForge |
| [Mekanism 通用机械](https://modrinth.com/mod/mekanism) | **10.7.19.85** | Release | 1.21.1 | 无 | Forge / NeoForge；1.21.1 走 NeoForge |
| [Immersive Engineering 沉浸工程](https://modrinth.com/mod/immersiveengineering) | **12.4.2-194** | Release | 1.21.1 | 无 | Forge / NeoForge；Modrinth slug 无连字符 |
| [Industrial Foregoing 工业先锋](https://modrinth.com/mod/industrial-foregoing) | **1.21-3.6.39** | **Beta** | 1.21.1 | 无 | 依赖 Titanium；1.20.1 另有 3.5.28 回补版本 |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | **19.2.20** | Release | **26.3** | 无 | 1.21.1 有 NeoForge；26.3.2-alpha；Fabric 线停在 1.20.1 |

### 对开发决策的影响

1. **1.21.1 NeoForge 是工业模组的事实停留版本**，适合做 AstroFactory 的主线。
2. 不需要 Forge 兼容层；1.21.1 上上述五模组均未提供 Forge 版本。
3. 现在迁到 26.3，Create/Mekanism/IE/IF 都没有可参照的正式版本；收益低于成本。
4. IF 的 1.21.1 仅 beta，若未来做联动，不应把它的 API 当稳定契约。

---

## 三、AstroFactory v0.7.0 实际功能基线

> 基线来自源码扫描，而非根据历史规划推测。当前约 7119 行 Java，52 个类；含 865 行自检代码。

### 3.1 已有方块与机器

| 分类 | 已有实现 | 源码证据 |
|---|---|---|
| 物流 | 水平传送带、机械臂、分流器、入口/出口配对地下传送带 | `ConveyorBlock`、`InserterBlockEntity`、`SplitterBlock`、`UndergroundConveyorBlock` |
| 资源 | 按下方方块产矿的采矿机；煤可优先互喂相邻燃料机器/锅炉 | `MinerBlockEntity` |
| 加工 | 燃料/电力熔炉，映射表熔炼；钢板二次冶炼；3×3 组装机，配方锁定 | `SmelterBlockEntity`、`AssemblerBlockEntity` |
| 存储 | 木箱 9 格、储物箱 27 格、铁箱 54 格；Container + 原版 ChestMenu | `CrateBlockEntity`、`CrateBlock` |
| 蒸汽 | 抽水机 → 锅炉 → 蒸汽机；整数水/蒸汽，邻接推送，无通用流体管道 | `WaterPumpBlockEntity`、`BoilerBlockEntity`、`SteamEngineBlockEntity` |
| 电力 | 燃料发电机、蒸汽机、六方向电缆；BFS 找源/负载，坐标最小电缆负责分配 | `GeneratorBlockEntity`、`CableBlock` |
| 科技 | 11 个 DAG 节点、3 时代、2 级研究包、等级科技、触发型科技、GUI、同步 | `TechNodes`、`TechTreeData`、`TechTreeScreen`、`ModNetwork` |
| 工具 | 扳手切换燃料/电力模式；科技终端打开科技树；机器放置科技锁 | `WrenchItem`、`TechTerminalItem`、`TechGatedBlockItem` |
| 配方 | 22 个合成 JSON；钢板、铜缆、电路板、两级研究包 | `resources/data/astrofactory/recipe/` |
| 验证 | 服务端自检 39/39（v0.7.0） | `SelfTest.java` |

### 3.2 当前关键数值与限制

- 传送带：单速 `0.08 block/tick`，高度 2/16，物品以 `ItemEntity` 被移动；无内部带槽库存、无双车道、无速度等级。
- 机械臂：单类型，`10 tick` 周期，每次耗电 20 FE；无过滤器、无长短臂、无堆叠臂。
- 分流器：左右交替，`next_left` 状态；无输入/输出优先级、无按物品过滤。
- 地下带：最大长度 8，入口/出口配对；无高速等级。
- 采矿机：按下方单个方块映射产物；不是矿脉储量模型，部分非矿石实心方块会兜底产圆石。
- 熔炉：内部映射表，不走通用熔炼 RecipeManager；组装机走原版 `RecipeType.CRAFTING`。
- 电缆：最大 BFS 512 根，每根每 tick 搬运上限 256 FE；无电压、损耗、变电站或网络统计。
- 蒸汽：自定义整数储罐与相邻机器直推；不是 NeoForge 通用 `IFluidHandler` 网络。
- 研究 GUI：11 节点采用手工 `LAYOUT`，固定 360×200 区域；没有自动布局、缩放、搜索。
- 没有：模块、插件升级、机器人、火车、蓝图、污染、虫巢、战斗防御、通用流体 API、JEI/EMI 集成、Ponder 教程、性能面板。

---

## 四、Create × AstroFactory 功能对照

> 原清单中的 Factor'I/O 描述未获证实，因此不伪造一张“Factor'I/O 逐项对照表”。若原意就是异星工厂机制，则第五节的 Factorio 行可覆盖其参考价值。

| 机制 | Create 的做法 | AstroFactory 现状 | 判断 | 建议落地 |
|---|---|---|---|---|
| 动力传播 | RPM + Stress，沿真实机械传动结构传播 | FE 电网 + 燃料；电缆 BFS 分配 | **差异化** | 不加入 RPM/齿轮体系；AstroFactory 应保持异星工厂电网逻辑 |
| 负载约束 | Stress 是产线全局预算，超负荷停机 | 有储能/耗电，没有网络级“总需求 vs 总供给”可视化 | **该抄** | 先做电网统计与欠电降速/停机提示，不引入应力单位 |
| 物流可视化 | 实体传动、置物台、机械臂、传送带动画 | ItemEntity 沿水平带移动，机械臂有逻辑但表现较简单 | **已有 + 该抄** | 优先补传送带连接/运动视觉与机械臂动作反馈，而非增加抽象 GUI |
| 交互教学 | Ponder 场景化 3D 教程 | 科技树 tooltip 与终端，无教程系统 | **该抄** | 做轻量“教程场景”而非直接绑定 Ponder：采矿→带→熔炉→箱子四幕 |
| 物流分流 | 智能溜槽、黄铜漏斗、属性/物品过滤 | 只有左右交替 Splitter | **该抄** | v0.8 增加过滤分流、优先输出；保持可见物理物流 |
| 机器功能扩展 | 不同机器/附属，不采用 Factorio 模块槽为核心 | 科技提供速度倍率；单机无槽位升级 | **差异化/可选** | 不照搬 Create；模块系统更适合按 Factorio 设计 |
| 大型结构 | Contraption 可把结构整体运动 | 全部固定方块/BE | **差异化** | 不做移动结构，技术复杂且偏离工厂规划主线 |
| 跨基地运输 | 列车、站台、包裹、Stock Keeper | 无 | **远期可选** | 当前不做；先把本地总线与电网做好 |
| 机器摆放反馈 | 连接、方向、可用性有即时视觉反馈 | FACING 与简单 GUI；扳手仅切换能源模式 | **该抄** | 扳手增加诊断：能量、进度、输入/输出、堵塞、配对状态 |
| 自动化生产 | 机器 + 物流 + 组装 | 已有矿、熔、组装闭环 | **已有** | 不再横向铺机器数量，优先提高可读性和可调试性 |

### 最值得借鉴的不是 Create 的机器数量

Create 最值得抄的是**状态可见**：玩家不打开 GUI 也知道机器是否在动、为什么停、动力往哪里走。AstroFactory 已有物理物流优势，下一步应把“堵塞、缺料、欠电、未研究、地下带未配对”都做成可读反馈。

---

## 五、Factorio 机制 × AstroFactory 功能对照

这张表替代未证实的 Factor'I/O 参数表，用来确定 AstroFactory 的差异化主线。

| Factorio 核心机制 | AstroFactory 现状 | 判断 | 优先级建议 |
|---|---|---|---|
| 水平传送带 | 已有单速实体带 | 已有 | 保留 |
| 地下传送带 | 已有，最长 8 | 已有 | 后续加等级和可视化配对 |
| 分流器 | 已有左右交替 | 已有但不完整 | **P0：过滤 + 优先级** |
| 机械臂 | 单类型、短距离、无过滤 | 已有但不完整 | **P0：过滤；P1：长臂/快速臂/堆叠臂** |
| 采矿机 | 按下方方块无限产矿 | 有闭环，缺资源压力 | **P0：接入矿田/矿脉储量，避免任意石头无限圆石** |
| 熔炉 | 燃料/电力、钢材配方 | 已有 | 后续统一配方数据驱动 |
| 组装机 | 3×3、锁配方、原版合成配方 | 已有 | **P0：专用机器配方类型与配方选择 GUI** |
| 科技树 | 11 节点、科研包、触发型、GUI | **核心优势** | **P0：扩展可维护性与内容，不要重写成别的系统** |
| 多级科研包 | 初级/高级 | 已有但仅两级 | P1：按电子/化工/生产逐步扩展，不一次全做 |
| 速度/产能/效率模块 | 无 | 明显缺口 | **P1：三类模块 + 插槽 + 功耗/速度取舍** |
| 传送带等级 | 无 | 明显缺口 | P1：黄/红/蓝三级，配套地下带/分流器等级 |
| 双车道 | 无，实体居中运输 | 明显缺口 | P2：需要重做带内物品表现与吞吐模型，不能简单贴两条线 |
| 火车物流 | 无 | 远期缺口 | P2/P3，不作为 v0.8 目标 |
| 机器人物流 | 无 | 与当前物理物流偏好冲突 | 不做或极后期独立科技，不进 v0.8 |
| 污染 | 无 | 可形成差异化，但涉及世界模拟 | P3 |
| 虫巢/袭击/炮塔 | 无 | 战斗系统会大幅扩张范围 | P3，等工厂闭环稳定后 |
| 蓝图/复制粘贴 | 无 | 大型工厂必需，但实现复杂 | P2：先做扳手复制设置，再考虑区域蓝图 |
| 火箭终点 | 无 | 缺少长期胜利目标 | P2：先定终局产物链，再实现发射事件 |

---

## 六、其他成熟模组该抄什么、不该抄什么

| 模组 | 该借鉴 | 不该照搬 |
|---|---|---|
| **Mekanism** | 多级产线、副产物闭环、机器升级槽、并行处理槽 | 五倍矿全套、庞大化工树、无线/数字物流 |
| **Immersive Engineering** | 可见线缆、接线器、Item Router 过滤、机器体积感 | 高电压/压降全套与大型多方块绑定；会稀释轻量流水线体验 |
| **Industrial Foregoing** | 通用机器基座 + Range/Efficiency Addon 的扩展架构 | “万物一键自动化”；会削弱资源规划与产线瓶颈 |
| **AE2** | 请求式生产、网络带宽/拓扑约束的概念 | 数字存储、ME 网络、无线物流；与用户明确偏好冲突 |

---

## 七、v0.8.0 推荐开发切片

### 原则

不要同时做“三级传送带 + 七种机械臂 + 模块 + 火车 + 污染”。那会把一个可玩版本拆成五条未闭环支线。

### 推荐范围：物流可读性 + 生产可靠性

1. **机械臂过滤器与分流器优先级**
   - 机械臂：白名单/黑名单、一次搬运数量；
   - 分流器：左右优先级、按物品过滤；
   - 自检覆盖：空过滤、满过滤、目标堵塞、优先级交替。

2. **扳手诊断**
   - 显示：能源模式、储能、配方、进度、输入/输出方向、堵塞原因；
   - 地下带：显示配对坐标、距离与方向；
   - 电网：显示发电/用电/储能汇总；
   - 这是借鉴 Create 的“可见状态”，但实现成本低、收益高。

3. **组装机专用配方系统**
   - 新增 `RecipeType<AssemblerRecipe>` 与 JSON；
   - 明确输入、输出、耗时、耗电、科技要求；
   - GUI 支持锁配方/查看缺料；
   - 保留原版工作台配方兼容作为过渡，不长期把所有机器配方塞进 3×3 CRAFTING。

4. **采矿资源约束**
   - 与矿田/矿脉储量对接；
   - 删除“未知实心方块无限产圆石”的兜底，改为明确可采列表；
   - 让双采矿机互喂煤仍有意义，但不再无限无中生有。

5. **可维护性与验收**
   - 39 项自检保留，新增上述场景；
   - 构建通过后 runServer 自检，再部署 runClient；
   - 用户实测标准：一条煤矿→燃料采矿→传送带→熔炉→箱子产线，能连续运行并能用扳手定位每一种停机原因。

### v0.9 候选

- 三类模块与插槽；
- 黄/红/蓝三级带及配套地下带/分流器；
- 长臂/快速臂，而不是一口气七种；
- 科技树自动布局、滚动/缩放、搜索。

### 暂不做

- 迁移 MC 26.3；
- Create 式 RPM/Stress；
- ME/无线物流；
- 大型火车、蓝图、虫巢战争；
- 未经证实的 Factor'I/O 或 Manufactio Reforged 兼容。

---

## 八、证据与限制

- 版本矩阵依据 Modrinth 项目与版本 API；关键过滤包括 `game_versions=1.21.1`、`loaders=neoforge/forge`。
- Create、Mekanism、IE、IF、AE2 在 1.21.1 NeoForge 均有文件；同条件 Forge 均无文件。
- 1.21.4 查询五者均无文件；26.3 查询只有 AE2 有文件。
- 本次未能验证原清单 Factor'I/O 与 Manufactio Reforged 的来源，因此结论只能写“未找到符合描述的项目”，不能断言全球不存在任何同名私有项目。
- AstroFactory 的功能基线来自当前源码扫描，最终可玩性仍以 runClient 用户实测为准。
