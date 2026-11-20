# 第三方库在 Minecraft 26.3 / NeoForge 上的可用性查证

查证时间：2026-11-20
查证方式：Modrinth API (`/v2/project/{id}/version?game_versions=["26.3"]&loaders=["neoforge"]`)
          + 各库官方 Maven `maven-metadata.xml` + 实际 HTTP 拉取 jar 验证
目标环境：Minecraft 26.3 / NeoForge 26.3.0.113 / Java 25

---

## 一、结论总表

| 用途 | 首选库 | 26.3 可用 | 版本 | Maven 坐标 | 状态 |
|---|---|---|---|---|---|
| 配置界面 | **Cloth Config** | ✅ 有 | `26.3.159` | `me.shedaniel.cloth:cloth-config-neoforge:26.3.159` | 已接入 |
| 配置界面（备选） | YACL | ✅ 有 | `3.9.7+26.3-neoforge` | `maven.modrinth:yacl:3.9.7+26.3-neoforge` | 未接入 |
| 准星信息 HUD | **Jade** | ✅ 有 | `26.3.5+neoforge` | `maven.modrinth:jade:26.3.5+neoforge` | 已接入 |
| 配方查看器 | **JEI** | ✅ 有 | `31.18.0.118` | `mezz.jei:jei-26.3-neoforge-api:31.18.0.118` | 已接入 |
| 配方查看器（备选） | REI | ✅ 有 | `26.3.824` | `me.shedaniel:RoughlyEnoughItems-api-neoforge:26.3.824` | 已接入 |
| 饰品槽 | **Curios** | ✅ 有 | `17.0.1+26.3` | `top.theillusivec4.curios:curios-neoforge:17.0.1+26.3` | 已接入 |
| 带动画实体 | GeckoLib | ✅ 有 | `26.3-5.5.7` | `maven.modrinth:geckolib:2s1O42FX`（version id） | 待启用 |
| 模组列表配置按钮 | **ModMenu** | ❌ **无** | 仅 fabric/quilt | — | 不可用 |
| 游戏内手册 | **Patchouli** | ❌ **无** | 最高 `26.1-94-beta` | — | 不可用 |
| 饰品槽（备选） | Trinkets | ❌ **无** | 仅 fabric | — | 不可用 |
| 能量/流体 | Team Reborn Energy | ❌ **无** | Modrinth 无此项目 | — | 不需要 |

**8 个类别里 6 个可用（其中 5 个已接入构建），3 个不可用。**

---

## 二、逐个查证记录

### ✅ Cloth Config — 可用

```
GET https://api.modrinth.com/v2/project/cloth-config/version?game_versions=["26.3"]&loaders=["neoforge"]
→ [{"game_versions":["26.3"],"loaders":["neoforge"],
    "version_number":"26.3.159+neoforge",
    "date_published":"2026-09-23", "downloads":305853}]
```

Maven 实测：
```
https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config-neoforge/26.3.159/...jar  → 200
```

### ✅ YACL — 可用（但本工程不用）

之前一次查证曾误判为「只到 26.2」，实际用 `game_versions=["26.3"]` 过滤后确认有：

```
version_number: "3.9.7+26.3-neoforge"
changelog 中的目标矩阵明确写着 26.3 NeoForge ✅
```

**修正说明**：早前用 `?game_versions=["26.3"]` 之外的查询方式，返回的是未过滤列表，
误读为「最新只到 26.2」。此处以精确过滤结果为准。

⚠️ **陷阱**：Modrinth Maven 的 artifact 文件名是 `yet_another_config_lib_v3-...`，
不是 `yet-another-config-lib-...`，所以坐标要写 `maven.modrinth:yacl`（按 project slug），
Gradle 才能正确拼路径。isxander 官方 maven 上 26.3 的 jar 路径已失效（404）。

### ✅ Jade — 可用

```
version_number: "26.3.5+neoforge",  date_published: 2026-09-22
filename: "Jade-mc26.3-NeoForge-26.3.5.jar"
```
Modrinth Maven 拉取实测 200。

### ✅ JEI — 可用

```
version_number: "31.18.0.118",  game_versions: ["26.3"],  version_type: "beta"
date_published: 2026-11-19
```

Maven 实测：
```
https://maven.blamejared.com/mezz/jei/jei-26.3-neoforge-api/31.18.0.118/...jar  → 200
```
注意要用 `jei-26.3-neoforge-api`（API 包），不要引本体 `jei-26.3-neoforge`。

### ✅ REI — 可用

```
version_number: "26.3.824+neoforge",  date_published: 2026-10-07
filename: "RoughlyEnoughItems-26.3.824.jar"
```
API 坐标 `me.shedaniel:RoughlyEnoughItems-api-neoforge:26.3.824` 实测 200。
REI 依赖 Cloth Config 与 Architectury API（`lhGA9TYQ`），Gradle 会自动带上。

### ✅ Curios — 可用

```
version_number: "17.0.1+26.3",  date_published: 2026-11-09
```
官方 maven `maven.theillusivec4.top` 上该版本的 `.pom` 实测 200。
注意：该 maven 的 `latest` 标签仍停在 `9.6.0+1.21.1`（未更新），
**不能按 `latest` 写版本，必须写死 `17.0.1+26.3`。**

### ✅ GeckoLib — 可用，暂不启用

```
version_number: "5.5.7",  name: "NeoForge 26.3-5.5.7"
filename: "geckolib-neoforge-26.3-5.5.7.jar"
```
Modrinth Maven 上该 artifact 的坐标版本是 **version id**（`2s1O42FX`），不是版本号：
```
maven.modrinth:geckolib:2s1O42FX  → 200 ✅
maven.modrinth:geckolib:26.3-5.5.7 → 404 ❌
```
本工程当前没有动画实体，先在 build.gradle 里注释保留。

### ❌ ModMenu — 不可用

```
GET .../modmenu/version?game_versions=["26.3"]
→ [{"game_versions":["26.3"], "loaders":["fabric","quilt"], ...}]
```
**只有 fabric / quilt 加载器，没有 neoforge。** ModMenu 是 Fabric 生态专属。

### ❌ Patchouli — 不可用

```
GET .../patchouli/version?game_versions=["26.3"]&loaders=["neoforge"]
→ []
```
项目全量版本列表里 NeoForge 最高只到 **26.1.2**（`26.1-94-beta`），26.3 无发布。

### ❌ Trinkets — 不可用

```
GET .../trinkets/version?game_versions=["26.3"]&loaders=["neoforge"]
→ []
```
`loaders` 字段只有 `["fabric"]`。Trinkets 同样是 Fabric 专属（Curios 的 Fabric 对应物）。

### ❌ Team Reborn Energy — 不存在

Modrinth 搜索 `team reborn energy` 返回 `total_hits: 0`，project slug 404。
这是 Fabric 时代的能量 API，NeoForge 上本就有官方 `EnergyHandler`（26.3 已实测可用，
见 `docs/api-26.3-notes.md`），不需要引入。

---

## 三、不可用库的替代方案

| 需求 | 原方案 | 替代 |
|---|---|---|
| 模组列表配置按钮 | ModMenu | **NeoForge 自带** `IConfigScreenFactory` 注册配置界面，在「Mods」列表里原生就有「Config」按钮，不需要 ModMenu |
| 游戏内手册 | Patchouli | 沿用现有 `GuideBookItem`（右键发 10 步文案）；若要富文本手册，可自建 `Screen`，或等 Patchouli 出 26.3 |
| 饰品槽 | Trinkets | 已用 **Curios**（26.3 可用，本工程已接入依赖） |
| 能量/流体 | Team Reborn Energy | 用 **NeoForge 官方 `EnergyHandler` / `Capabilities.Energy`**（本工程蒸汽电力系统已在用） |

---

## 四、build.gradle 接入情况

已加入的仓库：

```groovy
exclusiveContent {
    forRepository { maven { name = 'Modrinth'; url = 'https://api.modrinth.com/maven' } }
    filter { includeGroup 'maven.modrinth' }
}
maven { url = 'https://maven.shedaniel.me/' }        // Cloth Config / REI
maven { url = 'https://maven.blamejared.com/' }      // JEI
maven { url = 'https://maven.theillusivec4.top/' }   // Curios
```

已加入的依赖（全部 `compileOnly`）：

```groovy
compileOnly "me.shedaniel.cloth:cloth-config-neoforge:26.3.159"
compileOnly "maven.modrinth:jade:26.3.5+neoforge"
compileOnly "mezz.jei:jei-26.3-neoforge-api:31.18.0.118"
compileOnly "me.shedaniel:RoughlyEnoughItems-api-neoforge:26.3.824"
compileOnly "top.theillusivec4.curios:curios-neoforge:17.0.1+26.3"
```

**为什么是 `compileOnly`**：这些库目前还没有任何功能代码调用它们。
用 `compileOnly` 意味着：
- 编译期能 import 到 API（后续写集成代码时可直接用）；
- 不打进 jar（`jarJar` 不打包）；
- 运行时不需要玩家安装，玩家没装也不会崩。

等真正接入某个功能时，再把对应那一行改成 `implementation`（或加 `jarJar`）。

---

## 五、验证记录

```
$ ./gradlew dependencies --configuration compileClasspath
+--- maven.modrinth:jade:26.3.5+neoforge
+--- mezz.jei:jei-26.3-neoforge-api:31.18.0.118
|    \--- mezz.jei:jei-26.3-common-api:31.18.0.118
+--- me.shedaniel:RoughlyEnoughItems-api-neoforge:26.3.824
|    +--- me.shedaniel.cloth:cloth-config-neoforge:26.3.159
+--- top.theillusivec4.curios:curios-neoforge:17.0.1+26.3
+--- me.shedaniel.cloth:cloth-config-neoforge:26.3.159
```
无 `FAILED`，全部 resolve 成功。

```
$ ./gradlew build
BUILD SUCCESSFUL in 41s
```
`dist/astrofactory-0.1.0.jar` 正常产出，体积 824 KB（未因新依赖膨胀，符合 `compileOnly` 预期）。

---

## 六、后续启用时的注意事项

1. **JEI 用 API 包**：`jei-26.3-neoforge-api`，不要用 `jei-26.3-neoforge`（那是本体）。
2. **GeckoLib 坐标是 version id**：`maven.modrinth:geckolib:2s1O42FX`，写版本号会 404。
3. **Curios 不能用 latest**：官方 maven 的 `latest` 标签没更新，必须写死 `17.0.1+26.3`。
4. **Cloth Config 与 YACL 二选一**：同时接入两套配置框架会出现配置界面入口冲突。
   本工程选 Cloth Config，因为 REI 也依赖它，少一个依赖。
5. **NeoForge 配置界面不需要 ModMenu**：用 `IConfigScreenFactory` 注册即可在原生
   Mods 列表出现 Config 按钮，见 NeoForge 官方文档。
6. **`--offline` 构建**：新增依赖后第一次必须**去掉 `--offline`** 让 Gradle 拉取，
   之后可以恢复离线构建（构件已在 `.gradle-home` 缓存里）。
