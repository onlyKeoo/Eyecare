# 开发者文档 · 护眼时光

本文件面向**要修改、构建或深入了解实现**的人。

普通用户请回到 [README](README.md) —— 那里只讲怎么用。

---

## 项目结构

```text
app/src/main/java/com/java/myapplication/
├── MainActivity.kt              单 Activity 入口
├── EyeCareApplication.kt        应用级初始化（动态注册屏幕状态广播）
├── data/                        设置 / 统计 / 计时状态 / 科普文案 / 应用信息 / 免责声明 / 引导标记
├── timer/                       状态机、闹钟调度、广播接收器、久坐提醒
├── notify/                      通知、提示音、振动、全屏休息悬浮窗服务
└── ui/                          各页面 Composable、轮播组件、自检面板、首次引导、主题
```

---

## 构建

需要 Android SDK 与 JDK 17：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew testDebugUnitTest --offline   # 单元测试（纯逻辑，无需真机）
./gradlew assembleDebug   --offline     # 打包 Debug APK
./gradlew assembleRelease --offline     # 打包 Release APK（已签名）

# 产物：
#   app/build/outputs/apk/debug/app-debug.apk
#   app/build/outputs/apk/release/app-release.apk
```

加 `--offline` 是因为依赖已在本地 Gradle 缓存里。

**`gradle.properties` 里的两行配置不能删**：

- 关掉了 AAPT2 守护进程；
- `android.enableResourceOptimizations=false`

后者是为了避开 AGP 9 的资源优化——在 proot 环境下它会打出**缺少 `AndroidManifest` 的空 APK**（能编译、装不上，且不报错，极难排查）。

单元测试覆盖免打扰时段判断、设置项解析与格式化、超时统计、跨天切分、分时数据切分等纯函数。

### aarch64 主机的 AAPT2

在 aarch64（arm64）上构建时，AAPT2 需要额外处理，否则会在 `processDebugResources` 处失败，报错是：

```
AAPT2 aapt2-9.0.0-14304508-linux Daemon #0: Daemon startup failed
```

这个报错完全不指向真正原因，排查时容易误判成 Gradle 或资源问题。实际情况是：**AGP 9 的 aapt2 没有发布 `linux-aarch64` 分类器**——Maven 上只有无分类器、`-linux`、`-osx`、`-windows`，`-linux-aarch64` 是 404。所以 aarch64 主机上根本没有官方可用的 aapt2。

> 历史注记：`app/build.gradle.kts` 里曾有一条 `useTarget("...:aapt2:$version:linux-aarch64")` 的解析规则。由于该分类器不存在，它从未生效，只是让构建看起来"已经处理过"。现已删除。

有两条可用路径，按优先级：

**1. 内置 aarch64 aapt2（仓库维护者本机）**

`setup_android_env.sh` 会优先使用 `tools/aapt2/aapt2-arm64-v8a`，校验 SHA-256 后替换 SDK 与 Gradle 缓存里的 aapt2。

⚠️ **`tools/` 被 `.gitignore` 排除，从源码压缩包解出来的副本里没有这个文件。** 早期版本在这里直接 `fail` 中断整个环境搭建；现在会打印警告并降级到路径 2。

**2. qemu 兜底（任何人）**

用 qemu-user 跑 AGP 自带的 x86-64 aapt2，通过 AGP 官方的 `android.aapt2FromMavenOverride` 注入——**不改仓库、不改 Gradle 缓存**。

```bash
./setup_android_env.sh            # 自动检测并配置（aarch64 且无内置二进制时走这条路）
# 或手动配置：
#   ~/.gradle/gradle.properties
#   android.aapt2FromMavenOverride=$HOME/android-tools/bin/aapt2
```

需要 `qemu-user-static` 与 `libc6-amd64-cross`（脚本会自动装）。

两个容易踩的点：

- AGP 会校验该路径**以 `aapt2` 结尾**且可执行。所以包装脚本必须叫 `bin/aapt2`，叫 `aapt2-qemu` 会被拒绝（`Custom AAPT2 location does not point to an AAPT2 executable`）。
- 本机 `binfmt_misc` 若挂不上（proot 环境下常见，没有 `CAP_SYS_ADMIN`），就无法让内核直接执行 x86-64 二进制，因此必须显式用 `qemu-x86_64-static -L /usr/x86_64-linux-gnu` 包一层。

---

## 版本历史

| 版本 | versionCode | APK MD5 |
|---|---:|---|
| **v2.9.0** | 37 | `e9ce6338d4a8c9ae82ba55afa27f75e3` |
| v2.7.1 | 36 | `6f561e3e7c2c379e77a815fd11fd2bbd` |
| v2.7.0 | 35 | `497dc33da4b378ba7f40b2aca3853d22` |
| v2.6.11 | 34 | `1a019c962f046fc9c01ac24abb54db95` |
| v2.4.3 | 33 | `8c9b1226b32e47826e4958e96d2e5596` |
| v2.4.2 | 32 | `9b70603091d9272f1e1df716a0b9ad17` |
| v2.4.0 | 30 | `c937b2ec574a61d486440f15e1166780` |
| v2.3.15 | 29 | `4ab377a275c93b705e6fb634dc563bd7` |
| v2.3.14 | 28 | `01ab98d6879b622a89eba03059976713` |
| v2.3.13 | 27 | `fbd5f22bbfc9d99557414df718c56206` |
| v2.3.12 | 26 | `1e5c6ba3e48290c67d6842c78247bbe8` |
| v2.3.11 | 25 | `53a83e5e82eb3f87c0739f19a6540f9b` |
| v2.3.10 | 24 | `cb8c1f0bb17f1d8e9750a05f5fcdf4dd` |
| v2.3.9 | 23 | `2b3eb122cf6a2d0a9ee8f54f6eef16fe` |
| v2.3.8 | 22 | `df9117b4bf73902745abdb11369b2b34` |
| v2.3.7 | 21 | `161f056758210493244de8902048656c` |
| v2.3.6 | 20 | `1a6ba6cc728714c9541fcebbf61d1a6e` |
| v2.3.5 | 19 | `45eec9b41a21d6f9ef668d84ee142071` |
| v2.3.4 | 18 | `694b8639b6b9a171babd90a9f09efc20` |
| v2.3.3 | 17 | `1558f76f0e054f9339166b574e0abd53` |
| v2.3.2 | 16 | `6e5b65c7546b16db5421594ebed7dd5e` |
| v2.3.1 | 15 | `658b931601b5ccf9bb5a413c34f4a77a` |

> v2.4.1（versionCode 31）为报告页横轴乱码的过渡修复版，未单独归档 tag。

### 更新日志

| 版本 | 说明 |
|---|---|
| **v2.9.0** | 新增「休息结束后回到原应用」（含四次路径证伪与一次状态机返工）；主按钮降权、轮播圆点窗口化、卡片描边对比度 0.18→0.30、未授权空态重做；v2.7.2–v2.8.9 的改动全部累积在本版 |
| v2.7.1 | 统计改用**系统亮屏时长**，不再自己记账（原先越用越虚高，息屏时间会被算进用眼）；修复息屏后亮屏不重置计时（含跨天）、次日首次亮屏误弹超时；分钟设置改滚轮（停稳高亮中间格，确定才写入），每日目标改双栏；报告页图表拆分为亮屏/休息两张、休息改按时长统计、日视图改数字展示；长数字不再折行 |
| v2.7.0 | 统计改用系统亮屏时长（同 v2.7.1，本版未单独发布安装包） |
| v2.6.11 | 修复用眼时长记不实（忽多忽少）；修复切换页面掉帧卡顿，尤其刚打开应用后第一次切换；页面与卡片切换更跟手、落位更干脆 |
| v2.4.3 | 修复息屏后再亮屏误弹超时提醒：息屏 ≥1 分钟即结算并清零用眼计时，亮屏从 0 安静起算；超时提醒只在亮屏持续未休息时才弹 |
| v2.4.2 | 修复 Android 15/16 上到点自动全屏失效（改悬浮窗 + 前台服务）；修复报告页 30 天档横轴乱码；开关加二次确认；后台全屏休息页补上圆环进度条 |
| v2.4.1 | 报告页 30 天档横轴乱码的过渡修复版（versionCode 31，未单独归档 tag） |
| v2.4.0 | 新增超时二次提醒（超时继续计入统计，每 N 分钟重复提醒）；科普页改主页卡片轮播；补过渡动效 |
| v2.3.15 | 设置页即时生效 + 卡片流（取消「修改设置」弹窗）；底部导航与顶栏 emoji 换矢量图标；休息结束提示音音源改下拉选择 |
| v2.3.14 | 仓库地址随 GitHub 用户名迁移更新（`HuaHai-141225` → `HuaHai-114514`） |
| v2.3.13 | 新增免责声明、应用内标注仓库地址、首次启动引导（免责 + 可靠性设置两步） |
| v2.3.12 | 休息结束提示音改为可选音源（系统通知铃声 / 5 个内置音效），默认跟随系统 |
| v2.3.11 | 提示音改为内置单脉冲音，根治「响两声」；播放器换 `MediaPlayer`；设置页加「试听」 |
| v2.3.10 | 发声权收回应用（通知永久静默）；振动改用「通知」用途（此前被系统忽略） |
| v2.3.9 | 曾改由通知渠道发声，部分 ROM 无效，v2.3.10 已废弃 |
| v2.3.8 | 提示音换系统通知铃声 + 短振动，移除时长选项 |
| v2.3.7 | 提示音限时播放，防闹钟铃声过长 |
| v2.3.6 | 修复通知正文写死分钟数、Android 14+ 全屏提醒失效；新增可靠性自检页与提示音 |

<details>
<summary>v2.3.13 详情</summary>

- **免责声明**（`data/Disclaimer.kt`）：首次启动强制展示，外部点击 / 返回键无法绕过；设置页「关于」可随时回看。
- **仓库标注**（`data/AppInfo.kt`）：集中管理地址与版本号；设置页「关于」展示版本号、可点击仓库短地址、「打开仓库」「免责声明」按钮。
- **首次引导**（`ui/OnboardingDialog.kt`）：两步式。第 2 步复用自检面板的检查项（通知权限 / 精确闹钟 / 全屏通知 / 悬浮窗 / 电池优化 / 自启动），可「稍后设置」跳过；完成标记存于独立的 `eye_care_onboarding`。
- 为复用，`DiagnosticsPanel.kt` 的 `DiagItem` / `DiagRow` / `collectDiagnostics` 改为 `internal`。

</details>

> 更早版本的详细改动记录（含调试取证过程）见 [CHANGELOG.md](CHANGELOG.md)。

---

## 仓库历史

本仓库是 **2026-10-25 重建**的，源码由作者本机的完整快照重新提交，**不包含旧的提交历史**。

- **原仓库**：`HuaHai-114514/eyecare` —— 因作者设备重置后丢失 2FA 与密码，已无法访问，**不再维护**
- **现仓库**：`onlyKeoo/Eyecare` —— 当前唯一维护中的仓库

两者代码同源。原仓库的历史记录（commit、issue、star）无法迁移；如需追溯旧版本的改动细节，请查阅 [`CHANGELOG.md`](CHANGELOG.md)，其中完整保留了从 v2.3.1 起的每次变更。

---

## 设计决策

部分功能的取舍与踩坑记录单独成文，避免以后重复走弯路：

- [`docs/decisions-return-to-app.md`](docs/decisions-return-to-app.md) —— 「休息后返回原应用」的 12 条决策与四次路径证伪