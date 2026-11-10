# 决策记录 · 休息结束后回到原应用

创建：2026-11-06
状态：讨论中（细问强度 3）

需求原文：「在休息计时到点后自动弹到点击去休息的前所使用的软件。」

---

## 已确定

### D1｜目标场景确认为 B（应用外收到横幅通知）

用户在应用 X 中 → 用眼到点 → 只出现横幅通知（未弹全屏）→ 点通知进入护眼时光
→ 点「开始休息」→ 休息倒计时结束 → **当前停在护眼时光，希望回到应用 X**。

**理由**：用户明确回答「Q3，B」。

**排除的其他场景**（不再讨论）：
- A（全屏已盖住但没回去）：悬浮窗撤除后下层应用天然露出，无须「跳回」。
- C（应用内点「现在休息一下」）：本来就在护眼时光里，无「上一个应用」概念。
- D（尚未遇到）：已排除。

### D2｜需要记住「进入护眼时光之前前台是哪个应用」

已查证：通知的 contentIntent 指向 `MainActivity` 自身
（`RestNotifier` 用 `PendingIntent.getActivity` + `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP`），
不是外部应用。因此「回去」必须由本应用主动记录来源。

### D3｜不申请新权限，复用现有的使用情况访问权限

`AndroidManifest.xml` 已声明 `PACKAGE_USAGE_STATS`，`ScreenTimeStore` 已在用
`UsageStatsManager.queryEvents` 读亮屏时长。查「最近切到前台的包名」可走同一条数据源，
**不引入新的敏感权限，也不引入第三方依赖**（符合项目「零第三方依赖」约束）。

### D4｜记录时机：改为「用户点击通知时」（v2.8.1 修订）

**原决定（v2.8.0，已废弃）**：在提醒发出那一刻记录，理由是「用户可能隔一段
时间才点通知，期间已切到别的应用」。

**实测结果**：该方案大多数情况不生效。原因：到点闹钟多在后台 / Doze 边缘投递，
那一刻 `UsageStatsManager` 的最新前台事件往往还没落盘，或落盘的是上一个应用。
表现为「偶尔成功、多数失败」。

**修订后**：改为在**用户点击通知横幅的那一刻**记录。此时用户刚操作过、
系统处于唤醒态，事件已落盘，查得准。

**代价**：若用户点了通知后长时间不开始休息、期间切了应用，记录的是「点通知时」
那个应用而非「提醒发出时」那个。此差异可接受——用户点通知的那一刻，
正是他决定要休息的时刻，此时他在用的应用更符合直觉。

**实现要点**：
- 记录入口 `EyeTimer.recordReturnTarget()`，由 `MainActivity` 在通知点击时调用，
  且必须在 `beginRest()` 之前（否则落盘带不上）。
- 通知 intent 增加显式标记 `EXTRA_FROM_REMINDER`。不能用 `action == null` 判断——
  从桌面图标正常打开应用时 action 同样为 null，会误记一个无关应用。

---

### D5｜休息最后几秒提示，结束后自动跳回（Q4 = C）

休息页在倒计时进入最后几秒时显示「即将返回 <应用名>」，倒计时结束自动拉起来源应用。

**理由**：纯自动跳转是「无预告的位置变化」，用户可能正在看知识卡被弹走；
先给提示能把意外变成预期。同时避免退化成「只有按钮」——那样等于把动作
又推回给用户，功能价值有限。

**未采用**：A（纯自动，无预告）、B（纯按钮）。

### D6｜识别失败时安静停留，并给一行说明（Q5 = B）

无法确定来源应用时，不跳转、不退桌面，只在休息结束处显示
「未识别到返回目标」。

**理由**：什么都不说会让用户以为功能坏了（Q5 的 A）；
退回桌面是擅自改变用户位置，风险更大（Q5 的 C）。

### D7｜做开关，放设置页「提醒」组，默认开启（Q6 = B）

**理由**：用户明确要求。虽然与近期「删设置项文字」的方向相反，
但这是新增可控行为，不是冗余说明——开关本身承载决策，不属于可删的装饰文字。

**实现约束**：开关关闭时不记录来源应用（而不是记录了但不跳），
避免无意义地查询使用记录。

---

### D8｜提示在最后 5 秒出现（Q7 = B）

默认休息 20 秒，5 秒足够读完提示并决定是否点「再多休息一会」；
3 秒偏紧，10 秒在短休息里占比过大。

### D9｜优先原样调回，接受少数情况退化（Q8 = A）

用系统机制把仍驻留后台的应用原样调回前台（不重建），语义是「继续刚才的事」，
而不是重新打开。

**明确接受的代价**：若来源应用已被系统回收，只能重新启动，落点不受控
（可能回到该应用首页而非原界面）。这种情况下仍算「已返回」，不额外提示。

### D10｜过滤规则：桌面 / SystemUI / 本应用自身不算来源

查询到的最近前台包名若属于上述三类，一律按 D6 处理（显示「未识别到返回目标」），
不尝试跳转。

### D11｜记录存放：复用 TimerStore，不新建存储

与计时状态同生命周期，休息结束时正好一起读；新增字段而不是新开一个
SharedPreferences 文件，避免状态分散。

---

## 待实现清单

1. `TimerStore` 增加「休息来源应用包名」字段（含记录时刻）。
2. 发出到点提醒时（`onWorkDeadline`）查询并写入该字段；开关关闭时跳过查询。
3. `SettingsStore` 增加开关 `returnToAppAfterRest`，默认 true。
4. 设置页「提醒」组新增该开关。
5. 休息页最后 5 秒显示「即将返回 <应用名>」。
6. 休息结束后调回该应用（原样优先）；失败或包名为空则显示「未识别到返回目标」。
7. 包名 → 应用名通过 `PackageManager` 解析，解析失败回退显示包名。

## 已知风险（实现时须验证）

- Android 10+ 后台启动 Activity 限制：休息页在前台可见时一般允许，
  息屏或本应用不可见时可能被拒。
- `UsageStatsManager` 需要「使用情况访问」权限；未授权时必然识别失败。
- 调回方式（原样 vs 重建）需实测确认在本机 HyperOS / Android 16 上的实际表现。

---

## 技术风险备忘

- Android 10+ 后台启动 Activity 受限。若休息页在护眼时光前台运行，
  从可见 Activity 启动外部应用一般允许；但若休息结束时本应用已不可见，跳转会失败。
- `UsageStatsManager` 的最近前台应用可能返回桌面、SystemUI 或本应用自身，需过滤。
- 若用户从未授予使用情况访问权限，则无法识别来源应用，必须有降级路径。

---

## 事故记录（v2.8.2 → v2.8.3）

### 现象

休息倒计时归零后，页面停在「0 秒」不动，且**每秒振动一次**；
点「我已休息好」能正常回到计时页。

### 根因

v2.8.2 为去掉一次重复的 SharedPreferences 写盘，重写了 `finishRest` 收尾段，
把 `endRestPhase(context, s)` 这一行**连同那次 save 一起删掉了**。

`endRestPhase` 是退出休息态的唯一步骤（置 `phaseResting=false`、撤销闹钟、
收起覆盖层、重排下一轮）。它没被调用 → `phaseResting` 恒为 true →
`clockTick` 每秒重判「休息到点」→ 每秒重跑 `finishRest` → 每秒振动、界面退不出。

副作用：`StatsStore.addRest` 也被每秒调用，当天休息次数/时长被刷成虚高值（次日自动归零）。

### 修法

1. 恢复 `endRestPhase` 调用。
2. **把它放进 `finally`**：无论提示音、通知、跳转哪一步失败或抛异常，
   状态机都必定退出休息态。这样同类遗漏不会再表现为「卡在 0 秒」。

### 教训

这次改动的目标是「删掉多余写盘」，但错误地删掉了相邻的**状态推进**。
两者在代码里紧挨着，性质却完全不同：写盘可以省，状态推进不能省。

以后在这类收尾段落做删减时，必须逐行确认删的是「重复的 IO」还是「唯一的状态变更」。

---

## 事故记录（v2.8.4 日志分析 → v2.8.5）

### 现象

「休息后返回原应用」多数不生效；且失败时界面上没有任何提示。

### 日志证据（2026-11-09）

| 时间 | 目标包名 | 结果 |
|---|---|---|
| 14:10 / 14:42 | `com.android.browser` | ✅ ok=true |
| 13:51 / 19:17 / 19:36 | `com.discord` | ❌ intent=null |
| 15:44 | `com.aaalice.nai_launcher` | ❌ intent=null |
| 16:08 | `com.tencent.mobileqq` | ❌ intent=null |
| 16:41 | `com.tencent.mm` | ❌ intent=null |

**记录环节 100% 正常**（`fg.lookup` / `returnTarget recorded` 每次都对），
失败全部在 `fg.launch ... intent=null`。

### 根因 1：Android 11+ 包可见性

`getLaunchIntentForPackage` 对**未在 Manifest 用 `<queries>` 声明**的应用返回 null。
本项目当时没有任何 `<queries>`，所以除浏览器外全部查不到。

**修法**：
1. `AndroidManifest.xml` 声明 `MAIN/LAUNCHER` 意图的 `<queries>` 可见性。
2. `launch()` 改为先用隐式 Intent（`ACTION_MAIN` + `CATEGORY_LAUNCHER` + `setPackage`）
   由系统解析，失败再退回 `getLaunchIntentForPackage`。
3. 放弃「从最近任务取 baseIntent」的方案：`getRunningTasks` / `getAppTasks`
   在 Android 5.0+ 对普通应用只返回本应用自己的任务，路径不通。

### 根因 2：失败提示从未显示

原实现：`finishRest` 里置 `returnNoticePending=true`，
`EyeCareApp` 用 `LaunchedEffect(viewModel.phase, returnNoticePending)` 弹 Snackbar。

两个问题：
1. `finishRest` 常由**后台闹钟**触发，那一刻本页面没有组合，Snackbar 无处可弹；
2. 先清内存标记再 `showSnackbar`，若 LaunchedEffect 被重组取消，提示永久丢失。

**修法**：改为「进入前台时补显示」——标记持久化在 `TimerStore`，
新增 `takeReturnNotice()` 原子地取出文本并清标记，
`LaunchedEffect(viewModel.phase)` 在 `phase == IDLE` 时取一次并显示。

---

## 事故记录（v2.8.6 → v2.8.8）：四条路径逐一被证伪

### 日志证据（2026-11-09 20:50–21:02）

| 应用 | 记录的 className | 结果 |
|---|---|---|
| 抖音 | `com.ss.android.ugc.aweme.main.MainActivity` | ✅ ok=true via=explicit |
| B站 | `com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity` | ❌ SecurityException |

**同一套代码，抖音成功、B站失败，差异只在类名。**

### 四条路径的结论

1. `getLaunchIntentForPackage` —— 全部 null（包可见性）
2. `<queries><intent>` + 隐式 Intent —— ActivityNotFoundException
   （HyperOS 未把 intent 声明转成可见性，`dumpsys package` 中
   `queries via package name:` 为空）
3. 用 UsageEvents 的 className 显式启动 —— 只对「恰好记录到入口页」的应用有效；
   记录到子页面时抛 SecurityException（子页面不导出）
4. 猜测常见入口类名 —— 不可靠，`MainActivityV2` 这类命名无法穷举

### 最终方案（v2.8.8）

声明 `QUERY_ALL_PACKAGES`，然后用 `queryIntentActivities` **精确查询**
目标应用真正声明的 `MAIN/LAUNCHER` 入口组件，`setClassName` 启动。
不再依赖任何猜测或历史记录。

**代价**：Google Play 对该权限有审核要求。用户明确同意，且本项目不分发至 Play。

**保留的兜底**：若解析仍返回 null（例如目标应用无 LAUNCHER 入口），
退回「历史最早 Activity」+ 缓存类名。

---

## D12｜能力边界：只拉回前台，不恢复具体子页面（2026-11-10 定案）

### 结论

本功能的终点是「把目标应用拉回前台」。**具体落在哪个页面由目标应用自己决定**，
不做进一步控制。

### 证据

B站实测：跳转成功，但落点是首页而非用户正在看的视频页。

设备侧查证：
```
Task #3604 tv.danmaku.bili
  栈顶 = com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity   ← 任务仍存活
dumpsys package tv.danmaku.bili | grep UnitedBizDetails → 0 条         ← 但查不到声明
```

该详情页是**动态组件**（由 `bilibili://united_video/...` 这类 scheme 构建），
不在静态 Activity 列表里，外部 `setClassName` 无法拉起。

抖音之所以能恢复原界面，是因为它的 `MainActivity` 自身会还原上次的状态；
这属于目标应用的实现差异，不是本应用可控的。

### 曾尝试并放弃

用「任务栈顶 Activity」启动来保住原页面（v2.8.9 一度实现）。
放弃原因：对动态组件必然失败，只会多一次无谓的尝试，且可能在部分 ROM 上
触发安全提示或短暂闪烁。

### 曾评估并否决的替代方案

用悬浮窗承载休息页（`RestOverlayService`），让原应用一直留在前台、
休息结束后撤窗即可完美恢复原界面。

**否决理由**：悬浮窗是**强制性**的，会在用户不方便休息时打断当前工作；
而通知是**可拒绝**的。这是产品语义的差别，不是技术优劣。
