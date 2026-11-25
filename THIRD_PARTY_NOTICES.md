# 第三方组件、代码与素材声明

本文件记录「护眼时光」在实现中实际使用的第三方内容、它们的许可证，以及本项目的使用方式。
核查日期：**2026-11-22**。

> 这份清单只覆盖**本次核查能够确认**的内容。标为「待核实」的条目表示缺少证据，
> 不代表没有问题；发现新的第三方内容时应追加，而不是重写本文件。

## 一、一句话结论

| 类别 | 结论 |
|---|---|
| 本项目许可证 | GPL-3.0（根目录 `LICENSE`，全文与 FSF 官方文本逐字节一致） |
| 代码级复用 | **只有一处**：切页时长算式改写自 HuaweiPods（GPL-3.0，与本项目同许可，已注明出处与修改） |
| 依赖 | Miuix（Apache-2.0）、AndroidX / Kotlin / KotlinX（Apache-2.0）、MaterialKolor（MIT） |
| 素材 | 通知栏小图标来自 Google Material Icons（Apache-2.0）；启动图标与 5 个提示音均为作者用 AI 生成 |
| 许可证冲突 | 未发现。Apache-2.0 / MIT 均可并入 GPL-3.0 分发 |

---

## 二、通过公开 API 使用的依赖

这些是以编译/运行时依赖的形式引入的库，**没有把它们的源码复制进本仓库**。

| 组件 | 版本 | 许可证 | 用途 | 证据 |
|---|---|---|---|---|
| `top.yukonga.miuix.kmp:miuix-ui` / `-core` / `-preference` / `-icons` / `-squircle` / `-shader` / `-blur` | 0.9.4 | Apache-2.0 | 组件库、主题、图标、圆角裁剪、背景模糊 | `app/build.gradle.kts`、`gradle/libs.versions.toml`；POM `<licenses>` 声明 |
| `top.yukonga.miuix.kmp:miuix-navigation3-ui` | 0.9.3 | Apache-2.0 | 二级页导航与转场（`NavDisplay`） | 同上；该模块是 `androidx.navigation3` 的发行分支 |
| `androidx.navigation3:navigation3-runtime` | 1.1.4 | Apache-2.0 | 上述导航的运行时 | `app/build.gradle.kts`；Google Maven |
| AndroidX（activity / compose / lifecycle / core / annotation 等） | 见 lockfile 解析结果 | Apache-2.0 | 基础框架 | Gradle 解析出的 `debugRuntimeClasspath` |
| `org.jetbrains.kotlin:kotlin-stdlib`、`kotlinx-coroutines`、`kotlinx-serialization-core` | 见解析结果 | Apache-2.0 | 语言与并发运行时（部分为传递依赖） | 同上 |
| `com.materialkolor:material-color-utilities` | 5.0.1 | **MIT**（Copyright © 2026 Jordon de Hoog） | 由 Miuix 传递引入，用于动态取色 | POM `<licenses>` 声明；`LICENSES/MIT-materialkolor.txt` |

上游来源：

- Miuix：<https://github.com/compose-miuix-ui/miuix>，标签 `v0.9.4` → 提交 `39c40f998442`；
  许可证全文见 `LICENSES/Apache-2.0.txt`（取自该标签的 `LICENSE`）。
- MaterialKolor：<https://github.com/jordond/materialkolor>，`material-color-utilities:5.0.1`；
  许可证全文见 `LICENSES/MIT-materialkolor.txt`（取自该仓库 `LICENSE`）。

两个上游仓库都**没有** `NOTICE` 文件，因此没有需要额外保留的 NOTICE 内容。

---

## 三、代码级复用（唯一一处）

### 3.1 HuaweiPods —— 切页时长算式

| 项目 | 内容 |
|---|---|
| 上游 | <https://github.com/Nshpiter/HuaweiPods> |
| 版本 | 提交 `e2cf07eda485`（2026-11-13），`e2cf07e` |
| 许可证 | GPL-3.0（`LICENSES/` 未重复存放：本项目根目录 `LICENSE` 即同一份 GPL-3.0 全文） |
| 上游位置 | `app/src/main/java/moe/chenxy/huaweipods/ui/MainTabsPagerState.kt` |
| 本项目位置 | `app/src/main/java/com/java/myapplication/ui/EyeCareApp.kt`（`goTo` 中的切页调用） |
| 使用方式 | **改写**：把「按跨页数给时长」的算式移植到本项目的 Pager 调用上 |
| 修改说明 | 上游用 `animateScrollBy(像素, tween(...))` 自己算滚动像素；本项目改用 `animateScrollToPage(...)`，只保留时长算式 `100 * abs(Δpage).coerceAtLeast(2) + 100` 与 `EaseInOut`；补充了系统「减少动态效果」时的直接落页分支 |
| 修改日期 | 2026-11-21（该算式引入本仓库的日期，见 `docs/decisions-hyperos-visual.md` D8） |
| 许可义务 | 因为本项目同样是 GPL-3.0，二者兼容；需保留出处与修改说明（已在上表与本项目源码注释中标注） |

除这一处外，HuaweiPods 的内容只作为**视觉与动效参考**（悬浮底栏 + 内容从栏下穿过、参数小瓦片、按压下沉、
按距离给时长的切页节奏）。参考的是做法与观感，没有复制其源码文件。

### 3.2 HyperIsland —— 仅版式参考

| 项目 | 内容 |
|---|---|
| 上游 | <https://github.com/1812z/HyperIsland> |
| 版本 | 读取时的 `main`；仓库当前 HEAD 为 `bbd81b670b2f`（2026-11-22）。**读取当时的精确提交未记录，待核实** |
| 许可证 | MIT（Copyright © 2026 1812z），全文见 `LICENSES/MIT-HyperIsland.txt` |
| 使用方式 | **仅参考版式**：关于页「居中头图 → 分组列表」的结构。未复制其代码、未移植其文件 |
| 许可义务 | 因未包含其代码，MIT 的署名义务不因代码分发而触发；仍在此列出以说明来源，并随附全文备查 |

### 3.3 代码重合度核查方法

为避免「凭感觉判断有没有抄」，本次做了机械化比对：

1. 取本项目 `app/src/main/java` 下全部 42 个 `.kt` 文件；
2. 与两个参考仓库（HuaweiPods `e2cf07eda485`、HyperIsland `bbd81b670b2f`）共 563 个 `.kt` 文件比对；
3. 比对两轮：**去空白后长度 ≥ 50 字符的整行**、**长度 ≥ 10 字符的字符串字面量**。

结果：共享的长行 95 条、共享字符串 13 条，逐条检查后**全部是通用样板**
（`import androidx.compose...`、`getSystemService(Context.NOTIFICATION_SERVICE)`、
`PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`、`override fun onReceive(...)`、
`Arrangement.spacedBy(8.dp)`、`"DEPRECATION"`、`"text/plain"`、系统包名等），
没有任何一条属于对方的业务逻辑或界面结构。

**结论：未发现逐字复制的第三方源码文件。** 该结论的证据范围限于上述两个仓库与上述两种比对方式。

---

## 四、素材

| 素材 | 位置 | 来源 | 许可证 | 说明 |
|---|---|---|---|---|
| 通知栏小图标（眼睛） | `app/src/main/res/drawable/ic_eye_care.xml` | Google **Material Icons** 的 `visibility`（24dp） | Apache-2.0，Copyright © Google | 路径数据与上游 `24px.svg` 逐点一致（仅逗号/空格排版不同） |
| 提示音 5 个 `.ogg` | `app/src/main/res/raw/rest_end_*.ogg` | **作者用 AI 生成**（五个全部；2026-11-22 作者确认） | 随本项目 GPL-3.0 | 无第三方素材授权问题。作者已不记得当时调用的具体工具，工具本身不产生署名义务，故不记录 |
| 启动图标 | `app/src/main/res/mipmap-*`（含自适应前景/背景） | **作者使用 AI 生成**（2026-11-22 作者确认） | 随本项目 GPL-3.0 | 无第三方素材授权问题 |
| 科普文案 / 界面上所有中文文案 | `data/EyeTips.kt`、各页面 | 本项目撰写 | 随本项目 GPL-3.0 | — |

字体：本项目不打包任何自定义字体，全部使用系统字体，无独立授权问题。

---

## 五、随 APK 分发的许可文本

GPL-3.0 要求分发时随附许可证。本项目采取「仓库 + 应用内」双份：

| 文本 | 仓库位置 | APK 内位置 |
|---|---|---|
| GPL-3.0 全文 | `LICENSE` | `assets/licenses/gpl-3.0.txt` |
| Apache-2.0 全文 | `LICENSES/Apache-2.0.txt` | `assets/licenses/apache-2.0.txt` |
| MIT（MaterialKolor） | `LICENSES/MIT-materialkolor.txt` | `assets/licenses/mit-materialkolor.txt` |
| MIT（HyperIsland，参考） | `LICENSES/MIT-HyperIsland.txt` | `assets/licenses/mit-hyperisland.txt` |

应用内入口：**设置 → 关于 → 开源许可**，可滚动阅读上述全文。

---

## 六、待核实项

1. **`CHANGELOG.md` 的旧记载已过时**：v2.3.12 写的是「音效由 Python 合成正弦波 + 衰减包络」，
   而作者 2026-11-22 确认 5 个提示音**全部为 AI 生成**。该文件按项目规矩只在发布时补写，
   本次未改；以本文件的记载为准。
2. **HyperIsland 的读取版本**：只记录了「读取时 main 的最新提交未保存」，当前列出的是核查当日 HEAD。
   由于未复制其代码，此项不影响许可义务，但若日后要引用具体版本，应补记提交哈希。
3. **Miuix 各模块在 0.9.4 中是否含有第三方内嵌代码**：本次只核对了 POM 声明的许可证与仓库 `LICENSE`，
   未逐文件审计其内部是否包含其他来源的代码。这属于上游的责任范围，本项目以其声明为准。

## 七、复核方法

```bash
# 依赖与版本
./gradlew :app:dependencies --configuration debugRuntimeClasspath

# 许可证文件是否随包分发
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep assets/licenses

# 本项目 LICENSE 是否为 FSF 官方 GPL-3.0 原文（35149 字节）
sha256sum LICENSE
```
