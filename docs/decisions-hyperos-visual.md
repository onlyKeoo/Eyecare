# 决策记录 · HyperOS 视觉方向

本文件只记**已经由维护者拍板**的结论与理由；未决问题集中放在最后一节。
（拷打强度：4 深挖；记录方式：本文件逐条追加。）

---

## D1 · 参考对象分工

- **界面与动效**参考 `Nshpiter/HuaweiPods`：悬浮底栏、内容从栏下穿过、参数瓦片、按压下沉、
  距离比例切页。
- **关于页**参考 `1812z/HyperIsland`：居中头图 → 分组列表、头图随滚动淡出。
- **不照搬**：HuaweiPods 的全屏 AGSL 流动背景（低端机代价大）、HyperIsland 的玻璃组件
  （依赖高版本且我们不引入 `miuix.glass`）。

理由：两个项目都基于 miuix，结构可迁移；装饰性最强的部分反而最贵，收益最低。

## D2 · minSdk 抬到 33

- 结论：`minSdk 24 → 33`，引入官方 `miuix-blur`，使用真实背景模糊
  （顶栏渐进模糊 + 悬浮底栏玻璃）。
- 理由：库的清单声明 33，强行 `overrideLibrary` 会在 RenderEffect 类加载处崩溃；
  维护者 2026-11-21 明确同意用放弃 Android 7–12 换真实模糊。
- 连带：README 支持版本徽章改为 Android 13+；禁止任何 `overrideLibrary` 绕过的写法。

## D3 · 主题色策略：可选的系统取色（方案 C）

- 结论：设置 → 外观新增「跟随系统取色」，**默认关闭**；关闭时维持现有护眼绿，
  开启时整套界面跟随系统/Monet 取色。两套配色都保留并维护。
- 理由：默认关保证现有观感不被打断；需要"系统感"的用户可以自己打开。
  不选"默认跟随"是因为换壁纸会改变 App 观感，属于用户没预期的行为。
- 实现约束（待落地时验证）：
  - `ThemeController` 的 `remember` key 必须包含这个新开关，否则拨动后不重建配色；
  - 当前 Material3 配色是在 `MiuixTheme` **外层**算好的，Monet 下必须改成
    "先在 `MiuixTheme` 内取 `MiuixTheme.colorScheme`，再包 `MaterialTheme`"，
    否则 Material 组件（残余的 Text/对话框）会停留在旧配色；
  - `MonetSystem` 在 `isDark = false` 时仍会强制浅色（自动夜间模式关闭），行为与现在一致。
- 落地（2026-11-21）：
  - `AppSettings.followSystemColor`（默认 false）+ 外观页开关；
  - 主题层改为「MiuixTheme 内取 `colorScheme` → 再包 MaterialTheme」，Monet 色能渗到 Material 组件；
  - 新增 `ThemeBridge`：Compose 侧发布调色板，悬浮窗/通知读取；
  - 后台悬浮窗的标题色、强调色、渐变底、进度环、通知小图标全部改用同一套色。

## D4 · Monet 作用范围：整套跟随（A）

- 结论：开启「跟随系统取色」后，强调色、进度、开关、选中态、图表柱、卡片容器全部走系统色；
  **不再保留固定的护眼绿作为品牌色**。
- 理由：系统应用的识别度来自"整屏一套色"，一屏出现两套色相会立刻露馅；
  护眼语义靠文案、位置与进度条长度表达，不依赖绿色。

## D5 · 覆盖链路：全链路跟随（C）

- 结论：App 内、休息全屏页、后台悬浮窗、通知，四处使用同一套主题色与深浅判断。
- 理由：休息页是用户会盯着看 20 秒的界面，与 App 内不一致最刺眼；
  悬浮窗/通知若还是绿色，会像两个产品。
- 实现约束：`RestOverlayService` 不是 Compose，需要一条"主题桥"——
  在 Compose 侧把当前生效的强调色与是否取色写进 SharedPreferences，
  Service 读同一份值；不允许两处各自判断系统主题。

## D6 · 二级页导航：引入 Navigation3（A）

- 结论：使用 `androidx.navigation3` 管理二级页返回栈，配合 `miuix-navigation3-ui`
  的 NavDisplay 获得系统级转场与预测性返回。
- 已知阻碍（记录在案）：`miuix-navigation3-ui` 在 Maven 上**最高只到 0.9.3**，
  没有 0.9.4；0.9.3 依赖 navigation3-runtime 1.1.4。
  需要先验证「0.9.3 的 nav3 集成 + 0.9.4 的 miuix 核心」能否共存；
  不能共存时退回「plain navigation3 + 自写转场」，不硬凑版本。

## D7 · 页面留白：14dp（C）

- 结论：`HyperSpace.Page` 从 16dp 收到 14dp，作为参考项目 12dp 与现状 16dp 的折中。
- 理由：中文长句需要留白，但 16dp 在 6.7 寸屏上确实偏松；14dp 兼顾扫读与信息密度。
  只改页面基准线与卡片外缘，不压缩行内边距。

## D8 · 切页动效：距离比例 tween（B）

- 结论：底栏切页从弹簧改为 `tween(100ms × 跨页数 + 100ms, EaseInOut)`，
  与 HuaweiPods 的 `MainTabsPagerState` 一致；系统关闭动画时仍直接落页。
- 理由：跨 1 页与跨 2 页用同一段弹簧，长距离会显得"急停"；按距离给时长更连贯。
  代价是失去弹簧的轻微回弹，但系统发起的导航本来就不该回弹。

## D9 · 二级页返回：先用零依赖的预测性返回（A3）

- 结论：暂不引入 Navigation3；用 `androidx.activity.compose.PredictiveBackHandler`
  实现跟手的返回预览，保留现有二级页结构。
- 理由：`miuix-navigation3-ui` 是 androidx.navigation3 的 fork、最高 0.9.3，
  与 miuix 0.9.4 混用的二进制风险高于收益；而本项目只有 5 个二级页，
  预测性返回能拿到绝大部分"系统感"。
- 前置事实（已核对）：
  - `PredictiveBackHandler(enabled, onBack: suspend (Flow<BackEventCompat>) -> Unit)`
    在 activity-compose 1.12.0 中存在；
  - 项目当前**没有** `android:enableOnBackInvokedCallback`，需要补上，
    否则 Android 13+ 不会投递预测性返回事件；
  - 当前**没有**任何 miuix 的二级页转场：`SubPageTransition` 是自写的
    `AnimatedContent`（1/4 位移 + 淡入），miuix 核心不提供页面级导航动画，
    唯一提供的是 `miuix-navigation3-ui`（未采用）。

## D10 · 原则：miuix 有对应组件的，不允许手写

- 结论：凡 miuix 已提供的控件（Card / SwitchPreference / SliderPreference /
  LinearProgressIndicator / TabRow / OverlayDialog / OverlayBottomSheet /
  Snackbar / NumberPicker / NavigationBar），必须直接使用，不再"照着样子手写"。
  只有 miuix 确实没有的（页面导航转场、图表、空态、头图、背景）才允许自绘。
- 理由：手写版看着像，但按下反馈、行高、副标题颜色、无障碍语义都是自己凑的，
  与库内其它控件必然漂移；这正是"不像系统应用"的来源。
- 待迁移（已核对，均为"miuix 有、我们没用"）：
  1. `SettingsPage.SwitchRow` → `SwitchPreference`
  2. `SettingsPage.IntSliderRow` → `SliderPreference`
  3. `TimerPage.GoalBar` / 进度条 → `LinearProgressIndicator`
  4. `HyperSection` 的容器 → `Card`（内部就是 `squircleSurface`）
  5. 久坐间隔的三连按钮 → `TabRow`
- 保留自绘：`SubPageTransition`（miuix 核心无页面转场）、`BarChart`、`HyperEmptyState`、
  `AboutHero`、`SoftLightBackdrop`。
- 已迁移（2026-11-21）：五处全部换完；卡片按 Q2=A 使用 `Card` 默认值
  （16dp 圆角、不透明 surfaceContainer），不再叠加半透明与自绘描边。
  `hyperPane` 已删除，避免以后又有人照着手写卡面。

## D11 · 预测性返回：A / A / B（2026-11-21）

- **视觉（A）**：手势进度驱动整页 `scale 1.00→0.94`、`圆角 0→16dp`、
  右移 `0→18% 屏宽`；进度来自 `BackEventCompat.progress`，松手提交才出栈，
  中途取消用 180ms 弹回。
- **返回栈（A）**：`rememberSaveable` + `listSaver` 保存 `List<SettingsRoute>`，
  进程回收/旋转后回到原二级页。
- **覆盖范围（B）**：目标是连弹层一起接管，实际只能做到页面级。
  原因：miuix 的 `OverlayDialog` / `OverlayBottomSheet` 走
  `MiuixPopupUtils.DialogLayout`（窗口内自绘 + 自己的返回处理），
  没有暴露"按手势进度驱动 dismiss"的入口。外层再注册
  `PredictiveBackHandler` 会与它的 handler 抢事件，出现关两次或吞手势。
  所以：**二级页有跟手预览；弹层仍是普通返回关闭，没有预览**。
  这是库的能力边界，不是没做——除非 fork miuix 的 Overlay。
- 前置：清单补 `android:enableOnBackInvokedCallback="true"`；
  `PredictiveBackHandler` 只在设置 tab 可见时启用（三个 tab 常驻组合，
  否则在计时页按返回会悄悄弹掉看不见的设置栈）。

## 构建提示

- minSdk 33 后 debug APK 从 ~17.6MB 涨到 ~40MB：AGP 对 minSdk ≥ 28
  默认**不压缩 dex**（加快安装/启动）。不是依赖膨胀；release + minify 会小很多。

## D12 · 顶栏死区与主题颜色（2026-11-21 第二轮反馈）

- **顶栏死区**：用户选 B（大标题随内容滚走 + 小标题淡入）。落地时改成了
  **折叠补偿**：滚动被 `MiuixScrollBehavior` 吃掉多少（`heightOffset` 负值），
  内容就 `graphicsLayer.translationY` 同量上移，做到手指与内容 1:1。
  理由：B 需要自写顶栏并放弃 miuix `TopAppBar` 的折叠，违背 D10；
  而问题根因只是"折叠被消费但占位不变"，补偿三行代码即可，且仍是 miuix 顶栏。
  若目视后坚持 B，再重写。
- **主题颜色**：用户选 B —— 不要"跟随主题种子色"，要 **miuix 默认蓝**
  （浅 #3482FF / 深 #277AF7，即参考截图那种）。因此删掉自定义护眼绿与
  `followSystemColor`/Monet 分支，直接使用 `ThemeController` 默认色板。
- **主题模式**：三档（跟随系统 / 浅色 / 深色），替换原来的「自动夜间模式」布尔值；
  老数据按 `auto_night_mode=false → 浅色` 迁移，并继续回写旧键以兼容降级。
- **底栏**：新增「悬浮底栏」「底栏模糊」两个开关（用户选 C）；
  关闭模糊时底栏用不透明 surfaceContainer，关闭悬浮时改用贴底 `NavigationBar`。

## 待定问题

1. **导航方案受阻**：`miuix-navigation3-ui` 实测是 **androidx.navigation3 的 fork**
   （包名就是 `androidx.navigation3.*`，不是薄封装），Maven 最高 **0.9.3**，
   依赖 runtime 1.1.4；没有 0.9.4。与 0.9.4 核心混用存在二进制风险。
   三条路待选：A1 用 0.9.3 的 nav3 UI + runtime 1.1.4（接受版本偏差）；
   A2 用 androidx.navigation3 1.2.2 + 自写转场（干净但要重做转场）；
   A3 暂不引入，先做零依赖的预测性返回。
2. 关于页是否升为一级 tab（当前默认：不升）。
