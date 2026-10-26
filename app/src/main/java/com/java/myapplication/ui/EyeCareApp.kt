package com.java.myapplication.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.java.myapplication.data.AppSettings
import com.java.myapplication.data.OnboardingStore
import com.java.myapplication.data.ScreenTimeStore
import com.java.myapplication.data.StatsStore
import com.java.myapplication.notify.RestNotifier
import com.java.myapplication.ui.components.DailyBarChart
import com.java.myapplication.ui.components.EyeIcons
import com.java.myapplication.ui.components.EyeProgressRing
import com.java.myapplication.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Tab { TIMER, REPORT, SETTINGS }

/** 报告页时间档位 */
private enum class ReportRange(val days: Int, val label: String) {
    DAY(1, "日"),
    WEEK(7, "周"),
    MONTH(30, "月")
}


@Composable
fun EyeCareApp(viewModel: EyeCareViewModel = viewModel()) {
    val context = LocalContext.current

    // 首次加载初始化（与 ticker 分离，避免 init 内的 resync 让界面提前切休息页后 ticker 无人回收）
    LaunchedEffect(Unit) {
        viewModel.init(context)
        RestNotifier.ensureChannel(context)
    }

    // ticker 由 onForeground 单点驱动，onCleared / onBackground 负责取消
    LaunchedEffect(Unit) {
        viewModel.startTicking(context)
    }

    // 通知权限（Android 13+）
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission(context)
        }
    }

    var currentTab by remember { mutableStateOf(Tab.TIMER) }

    // 设置页保存反馈（改一下就存一下，给一条轻量 Snackbar 让人放心）
    val snackbarHostState = remember { SnackbarHostState() }

    // 首次启动引导：只在没看过时弹一次（走完或跳过都会写入完成标记）
    var showOnboarding by remember { mutableStateOf(!OnboardingStore.isDone(context)) }

    // 休息倒计时全屏页
    if (viewModel.phase == RestPhase.RESTING) {
        RestScreen(viewModel, context)
        return
    }

    // 页面切换：用 HorizontalPager 而不是 AnimatedContent。
    //
    // 之前用 AnimatedContent + 位移会掉帧，但病因不是「位移」——
    // 是 AnimatedContent 在切换时把新旧两页同时挂上，两页各自跑完整的
    // measure / layout / draw；位移只是让这件事每帧都可见（连静止的旧页也要重排）。
    // 所以上一版改成纯 alpha 是「把症状和病因一起删了」：不卡了，但也没动效了。
    //
    // Pager 的机制不一样：它是一块横贯三页的连续画布，每帧只做一次布局，
    // 三页的位移全部走各自的 graphicsLayer.translationX（合成层），
    // 不触发 measure / layout / draw —— 这就是「只动 transform」的落地方式。
    // 附带拿到 Apple 说的那几件事：跟手 1:1、可打断、松手时速度交接、边界阻尼。
    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { Tab.entries.size }
    )

    // 底部导航点击 → 弹簧滚到目标页。dampingRatio 1.0（临界阻尼）是 Apple 的默认值：
    // 不弹跳、干净落位；这是「系统发起」的动作，没有动量需要延续，
    // 所以不该有回弹 —— 回弹只留给用户甩出来的手势。
    val scope = rememberCoroutineScope()
    val goTo: (Tab) -> Unit = { tab ->
        // 先落高亮再起动画：settledPage 要等弹簧完全收敛才更新，
        // 只靠它驱动的话，点了导航得等整段滑动结束，图标和药丸才跟上。
        // 点击是明确的用户意图，应该立即反馈；settledPage 监听继续负责手势滑动。
        currentTab = tab
        if (pagerState.currentPage != tab.ordinal) {
            scope.launch {
                pagerState.animateScrollToPage(tab.ordinal, animationSpec = SettleSpring)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == Tab.TIMER,
                    onClick = { goTo(Tab.TIMER) },
                    icon = { Icon(EyeIcons.Timer, contentDescription = "护眼计时") },
                    label = { Text("护眼计时") },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = currentTab == Tab.REPORT,
                    onClick = { goTo(Tab.REPORT) },
                    icon = { Icon(EyeIcons.Report, contentDescription = "数据报告") },
                    label = { Text("数据报告") },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = currentTab == Tab.SETTINGS,
                    onClick = { goTo(Tab.SETTINGS) },
                    icon = { Icon(EyeIcons.Settings, contentDescription = "护眼设置") },
                    label = { Text("护眼设置") },
                    colors = navItemColors()
                )
            }
        }
    ) { innerPadding ->
        // 手势滑动时把当前页同步回导航栏，药丸 indicator 才会跟着手指走
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { settled ->
                currentTab = Tab.entries[settled]
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HorizontalPager(
                state = pagerState,
                // 两侧各预组合 2 页（总共只有 3 页，等于全部页面在首帧后就都在场）。
                //
                // 为什么是 2 而不是 1：这个参数决定「当前页两侧各有多少页会被提前测量」，
                // 而**首次测量一个页面 = 首次组合它整棵子树**。取 1 时，冷启动停在计时页，
                // 报告页会在启动时被组合，但**设置页（距离 2）不会** ——
                // 于是第一次切到设置页的那几帧要现做「组合 750 行 + 首次 measure + 首次 draw」，
                // 全砸在弹簧动画里，就是「第一次切明显掉帧、以后正常」。
                // 取 2 把这份一次性开销提前到启动首帧之后、用户还没点之前。
                //
                // 代价：三页常驻内存（都是轻量 Compose 树，无大图），
                // 换来「每次切换都一样顺」——这个交换对只有 3 页的底部导航是划算的。
                beyondViewportPageCount = 2,
                modifier = Modifier.fillMaxSize(),
                key = { it }
            ) { page ->
                when (Tab.entries[page]) {
                    Tab.TIMER -> TimerTab(viewModel, context)
                    Tab.REPORT -> ReportTab(viewModel, context)
                    Tab.SETTINGS -> SettingsTab(viewModel, context, snackbarHostState)
                }
            }
        }
    }

    // 到点提醒确认弹窗：点了「开始休息」才开始休息计时
    if (viewModel.phase == RestPhase.AWAITING) {
        // 弹窗出现时轻振一下：手上拿着手机、音量静音时也能察觉
        val haptic = LocalHapticFeedback.current
        LaunchedEffect(Unit) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        AlertDialog(
            onDismissRequest = { viewModel.postponeRest(context) },
            title = { Text("\uD83C\uDF3F 该让眼睛休息啦") },
            text = {
                Text(
                    "已连续用眼 ${viewModel.settings.workMinutes} 分钟。\n" +
                        "点「开始休息」后即刻起算 ${viewModel.settings.restSeconds} 秒远眺倒计时。"
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.startRest(context) },
                    shape = CircleShape
                ) {
                    Text("开始休息")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.postponeRest(context) }) {
                    Text("稍后再说")
                }
            }
        )
    }

    // 首次启动引导（v2.3.13）：免责声明 → 可靠性设置
    if (showOnboarding) {
        OnboardingDialog(
            onFinish = {
                OnboardingStore.markDone(context)
                showOnboarding = false
            }
        )
    }
}

// ============ 计时页 ============
@Composable
private fun TimerTab(
    viewModel: EyeCareViewModel,
    context: Context
) {
    // 系统亮屏时长（v2.6.16）：统计页唯一的数据源。
    // 亮屏时长会随时间自然增长，因此每分钟推进一次 tick 作为缓存键 ——
    // 这一页只以分钟展示，秒级变化看不出来，却会让 remember 每秒重查一次
    // 系统事件表（queryEvents 要遍历当天全部亮灭屏事件，代价不小）。
    var timerHasUsageAccess by remember { mutableStateOf(ScreenTimeStore.hasPermission(context)) }
    var timerMinuteTick by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            timerMinuteTick = System.currentTimeMillis() / 60_000L
        }
    }
    val timerScreenOnSeconds = remember(timerHasUsageAccess, timerMinuteTick) {
        if (timerHasUsageAccess) ScreenTimeStore.todayScreenOnSeconds(context) else null
    }
    val timerRequestUsageAccess: (() -> Unit)? = {
        runCatching {
            context.startActivity(
                ScreenTimeStore.permissionIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        // 回到前台时由下面的生命周期钩子重查授权状态
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部标题（设置入口统一放在底部导航，避免同一功能两处入口）
        // 这一列整体是居中的（进度环、按钮需要），但标题单独包一层占满宽度，
        // 走默认的 Start 对齐 —— 三个页面的标题就都在同一条左基准线上。
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "护眼时光",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "给眼睛一片呼吸的原野",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        // 免打扰进行中提示（有则淡入显示）
        AnimatedVisibility(
            visible = viewModel.inDndWindow,
            enter = fadeIn(tween(MotionDurations.ENTER_MS, easing = EaseOutStrong)),
            exit = fadeOut(tween(MotionDurations.EXIT_MS))
        ) {
            Column {
                SoftCard {
                    // v2.6.20：原标题写作「🌙 免打扰时段进行中」。emoji 走系统彩色字体，
                    // 与 titleSmall 的基线、字重都不一致，是自检页那类「不齐」的同源问题。
                    // 这里不另加图标，改用左侧一道竖向色条标记「静音中」——比再塞一个
                    // 图形更克制，也避免每张卡片都顶一个图标导致的重复。
                    Row {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "免打扰时段进行中",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "这段时间只为你计时，不会打扰你。出时段后自动恢复提醒。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // 提醒开关
        EyeCareSwitchCard(
            enabled = viewModel.reminderEnabled,
            onToggle = { viewModel.setReminderEnabled(context, it) }
        )

        Spacer(Modifier.height(24.dp))

        // 眼眸进度环
        Box(contentAlignment = Alignment.Center) {
            EyeProgressRing(
                progress = viewModel.workProgress,
                size = 220.dp,
                strokeWidth = 20.dp,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // v2.7.3：给环内文案一个硬宽度上限，防止以后再被写长。
                //
                // 环内径 = 220 − 描边 20×2 = 180dp，而文字块是居中的、
                // 上下两行分别落在圆心上下方，越靠外弦宽越短。
                // 取 152dp（≈ 内径 180 × 0.84）作为安全宽度：
                // 既给上下两行留出到环壁的视觉呼吸，也保证 12sp 下能放下
                // 约 12 个全角字符 —— 超过这个长度就该移到环外。
                modifier = Modifier.widthIn(max = 152.dp)
            ) {
                // v2.7.2：环内数字**仍是已用时长**（正计时），但副标题改成
                // 「距下次提醒」，并在下面直接给出倒计时。之前只有「本次亮屏已用眼」
                // 一行，用户分不清这个环是"本轮周期"还是"今日额度"。
                //
                // 数字加 tabular figures（tnum）：否则秒数从 1 跳到 9 时
                // 十位字宽变化会让整串左右抖一下，每秒一次很扎眼。
                Text(
                    text = viewModel.formatTime(viewModel.elapsedSeconds),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = LocalTextStyle.current.copy(
                        fontFeatureSettings = "tnum",
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    // v2.7.3：环内只留**一个短词**。
                    //
                    // 原先这里写「本轮已用 · 距下次提醒 17 分钟」，16 个全角字符
                    // 在 12sp 下理论宽约 192dp，而环内沿在文字高度上的可用弦宽
                    // 只有约 176dp（环外径 220 − 描边 20×2），必然顶到环壁。
                    //
                    // 环内空间本就窄（内径 180dp，且越靠上下弦宽越短），
                    // 这里只该放"这个数字是什么"，不该放完整句子。
                    // 倒计时信息移到环外说明行，那里没有宽度约束。
                    if (viewModel.pausedByScreenOff) "息屏已暂停"
                    else "本轮已用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            // v2.7.3：倒计时搬到环外。
            //
            // 环内只放"本轮已用"这四个字，真正的行动信息（还差多久到点）
            // 放在环下方说明行 —— 这里没有宽度约束，还能用颜色区分主次。
            // 倒计时比正计时更贴合"环要填满"的直觉：环走得越多，剩余越少。
            if (viewModel.pausedByScreenOff) {
                "息屏已暂停 · 亮屏后继续计时"
            } else {
                "距下次提醒还有 ${viewModel.remainingSeconds / 60} 分钟"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (viewModel.pausedByScreenOff) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(6.dp))

        Text(
            // 只描述"本轮周期"，不再出现「目标」二字 ——
            // 那个词在设置页专指每日用眼目标，两处混用会让用户误读环的含义。
            viewModel.cycleCaption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // 立即休息按钮（手动触发时才起算休息倒计时）
        //
        // v2.7.4：从「全宽 56dp 实心主色」降为「居中胶囊 + 描边」。
        // 主流程是到点自动弹全屏休息页，这个按钮只是**手动提前休息**的入口，
        // 却占了整行宽度 + 最高饱和度主色，视觉权重压过进度环本身，
        // 让人误以为它是本页的主行动。降级后环才是唯一视觉主体。
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OutlinedButton(
                onClick = { viewModel.startRest(context) },
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text(
                    "现在休息一下",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 护眼知识轮播（v2.4.0：取代原「护眼知识」导航页）
        TipCarousel()

        Spacer(Modifier.height(24.dp))

        // 今日用眼小结（实用向核心）
        // v2.6.16：主指标已改为系统亮屏时长，与报告页同源同口径。
        TodayWorkSummaryCard(
            screenOnSeconds = timerScreenOnSeconds,
            restCount = viewModel.todayCount,
            restSeconds = viewModel.todaySeconds,
            goalMinutes = viewModel.settings.dailyGoalMinutes,
            onRequestScreenTime = if (timerHasUsageAccess) null else timerRequestUsageAccess
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ============ 数据报告页（日 / 周 / 月） ============
@Composable
private fun ReportTab(viewModel: EyeCareViewModel, context: Context) {
    var range by remember { mutableStateOf(ReportRange.WEEK) }

    // 缓存键：App 自统计的今日累计已在 v2.6.16 废止（数字不可信、虚高），
    // 报告页的所有口径都改由系统亮屏时长驱动，故缓存键也换成「当前分钟」。
    //   1. 亮屏时长本身随时间增长，用当前分钟做键即可拿到新鲜读数；
    //   2. 休息次数仍是自统计的（这条链路没有虚高问题），一并参与做键。
    // 按**分钟**取整而不是用秒：这一页所有读数都以分钟展示，秒级变化看不出来，
    // 却会让 remember 每秒作废一次；而每次作废要重跑 dailySeries +
    // dailyScreenOnSeconds 两遍整表计算。取整到分钟后同一分钟内数字完全一致，
    // 计算量直接降到 1/60。
    var minuteTick by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    val statsKey = minuteTick to viewModel.todayCount
    val goalMinutes = viewModel.settings.dailyGoalMinutes

    // 系统亮屏时长：同样是「统计有没有变」的可观测键，跟着 statsKey 一起失效。
    // 没有「使用情况访问权限」时为 null，卡片会显示一个引导授权的入口。
    var hasUsageAccess by remember { mutableStateOf(ScreenTimeStore.hasPermission(context)) }
    val screenOnSeconds = remember(statsKey, hasUsageAccess) {
        if (hasUsageAccess) ScreenTimeStore.todayScreenOnSeconds(context) else null
    }
    // 每分钟推进一次 minuteTick，让上面的缓存键在亮屏时长增长后自然失效。
    // 只在页面可见时计时（这一页由 Pager 常驻，不依赖组合生命周期做可见性判断，
    // 故用最简单的 while 循环 + delay；成本是一次 60 秒的协程唤醒）。
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            minuteTick = System.currentTimeMillis() / 60_000L
        }
    }

    // v2.7.3：日档位改用「分时亮屏」柱状图。
    // 单日只有一根日柱时，柱状图确实没有横向比较意义（这正是 v2.7.1 隐藏它的理由），
    // 但结论下错了：日档缺的不是柱图，而是**可比较的横轴**。
    // 把横轴从「天」换成「小时」，24 根柱子天然可比，还能回答
    // 「今天什么时候最费眼」——这是日档位原本完全没说的事。
    val hourly = remember(statsKey, hasUsageAccess, minuteTick) {
        if (hasUsageAccess) ScreenTimeStore.todayHourlyScreenOnSeconds(context) else null
    }
    val hourlyData = remember(hourly) {
        // v2.7.3：按**两小时**合并成 12 根柱子，而不是 24 根。
        //
        // 24 槽位时柱宽只有 5.3dp（画布 305dp ÷ 24 ÷ 1.5 × 0.62），
        // 圆角占去一半，看上去就是一根根细线，既不好看也难点中。
        // 12 槽时柱宽约 10.5dp，既能分辨又保留了"几点到几点"的信息量。
        //
        // v2.7.5：标签改为**区间**（「12–14点」）而不是起点（「12点」）。
        // 柱子代表两小时的总和，只写起点会让用户以为是单小时数据，
        // 点开读数显示「12点 · 亮屏 108分钟」时更显矛盾（一小时不可能 108 分钟）。
        val src = hourly ?: List(24) { 0L }
        src.chunked(2).mapIndexed { bucket, pair ->
            val startHour = bucket * 2
            val endHour = minOf(startHour + 2, 24)
            "${startHour}–${endHour}点" to (pair.sum() / 60).toInt()
        }
    }
    // v2.7.5：峰值改为在**合并后的 12 根柱子**上找，与图表实际展示的口径一致。
    // 原先在 24 个小时里找峰值，再拿那个单小时下标去拼「13–14点」的文案，
    // 既与 2 小时分组对不上，也会出现「峰值 13 点」但 13 点那格其实属于 12–14 组的错位。
    val hourlyPeakIndex = remember(hourlyData) {
        var peak = -1
        var peakMin = 0
        hourlyData.forEachIndexed { i, entry ->
            if (entry.second > peakMin) { peakMin = entry.second; peak = i }
        }
        peak
    }

    // 休息次数仍走自统计（这条链路由休息完成事件直接触发，没有虚高问题）。
    val stats = remember(range, statsKey) {
        StatsStore.dailySeries(context, range.days)
    }

    // v2.6.16：用眼时长的逐日数据改由**系统亮屏时长**提供，与今日卡片同口径。
    // 未授权时整体为 null，图表显示空态并给出授权入口。
    val screenOnDaily = remember(range, statsKey, hasUsageAccess) {
        if (hasUsageAccess) {
            ScreenTimeStore.dailyScreenOnSeconds(context, range.days)
        } else {
            List(range.days) { null }
        }
    }

    // 横轴标签：用短格式「M/d」（如 9/1），比 MM-dd 更省宽度；
    // 30 天档位下 30 个标签会挤成一片、数字被压坏（图 1 的乱码就是这么来的），
    // 因此额外做「抽稀」：只保留少量关键刻度，其余留空，见下方 axisLabels。
    val labels = remember(stats) { stats.map { shortDayLabel(it.dayKey) } } // M/d
    val workData = remember(labels, screenOnDaily) {
        labels.mapIndexed { i, l -> l to ((screenOnDaily.getOrNull(i)?.toInt() ?: 0) / 60) }
    }
    // v2.7.1：休息侧由「次数」改为「时长（分钟）」。原来两张图共用一根 Y 轴，
    // 却拿「分钟」和「次」相比：数值差两个数量级，柱子按同一 maxValue 缩放，
    // 休息柱永远只有百分之几高，且两根柱子的高度本就不具可比性。
    // 改用 DailyStat.restSeconds（addRest 逐次累加的**真实**休息秒数），
    // 与亮屏同为时长口径，两张图上下排布时量纲一致。
    val restData = remember(stats, labels) {
        stats.mapIndexed { i, s -> labels[i] to (s.restSeconds / 60) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            "数据报告",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(18.dp))

        // 日 / 周 / 月 切换
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportRange.entries.forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { range = r },
                    label = { Text(r.label) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 今日小结（用眼侧）
        TodayWorkSummaryCard(
            screenOnSeconds = screenOnSeconds,
            restCount = viewModel.todayCount,
            restSeconds = viewModel.todaySeconds,
            goalMinutes = viewModel.settings.dailyGoalMinutes,
            onRequestScreenTime = if (hasUsageAccess) null else {
                {
                    // 跳到系统「使用情况访问」页；回到前台时重查一次授权状态
                    runCatching {
                        context.startActivity(
                            ScreenTimeStore.permissionIntent()
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        // v2.7.1：原「亮屏 / 休息对比」把两种**不同量纲**画进同一坐标系 ——
        // 左轴是分钟、右轴是次数，柱高却按同一个 maxValue 缩放，既不可比
        // （「亮屏柱比休息柱高」没有含义），休息柱也永远矮得看不见。
        // 现拆成两张独立图表，各自一根 Y 轴；休息侧同时由「次数」改为「时长」，
        // 使两张图量纲一致，上下排布时可横向参照。
        // v2.7.1：日档位不画柱状图。柱状图的价值在**多天横向比较**，只有一天
        // 数据时，一根柱子无论多宽都别扭（曾出现 145dp 宽、圆角 72dp 的胶囊），
        // 且这张图与上面「今日用眼小结」的数字完全重复。日档位的信息已由那张卡
        // 承载（亮屏时长 / 休息次数 / 休息时长三项 + 目标进度），故此处整体隐藏。
        if (range == ReportRange.DAY) {
            // v2.7.3：日档位的分时图，替代原来的整段空白。
            ChartCard(
                title = "今日分时亮屏",
                legendColor = GrassGreen,
                legendText = "亮屏(分钟)",
                data = hourlyData,
                stats = stats,
                valueLabel = "亮屏",
                highlightLastAsToday = false,
                hourAxis = true,
                subtitle = if (hourlyPeakIndex >= 0)
                    "用得最多的是 ${hourlyData[hourlyPeakIndex].first}"
                else "今天还没有亮屏记录"
            )
        }

        if (range != ReportRange.DAY) {
            ChartCard(
                title = "每日亮屏时长",
                legendColor = GrassGreen,
                legendText = "亮屏(分钟)",
                data = workData,
                stats = stats
            )

            Spacer(Modifier.height(20.dp))

            ChartCard(
                title = "每日休息时长",
                legendColor = MistBlue,
                legendText = "休息(分钟)",
                data = restData,
                stats = stats,
                valueLabel = "休息",
                barColor = MistBlue,
                emptyColor = MistBlue
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 单序列按日图表卡片：标题 + 图例 + 柱状图 + 横轴。
 *
 * v2.7.1 抽出。原先是「亮屏 / 休息」共用一张双序列卡，但两者量纲不同
 * （分钟 vs 次数），共用一根 Y 轴既不可比、柱子高度也失真。拆成两张单序列卡，
 * 每张各自按自己的最大值缩放，高度才真实。
 *
 * @param legendColor 图例圆点颜色，同时作为柱子颜色
 * @param data 逐日数据（标签 → 数值），单位由调用方决定，默认分钟
 */
@Composable
private fun ChartCard(
    title: String,
    legendColor: androidx.compose.ui.graphics.Color,
    legendText: String,
    data: List<Pair<String, Int>>,
    stats: List<com.java.myapplication.data.DailyStat>,
    barColor: androidx.compose.ui.graphics.Color = GrassGreen,
    emptyColor: androidx.compose.ui.graphics.Color = GrassGreen,
    valueLabel: String = "亮屏",
    highlightLastAsToday: Boolean = true,
    subtitle: String? = null,
    // v2.7.3：日档位横轴是「小时」而非「日期」。
    // DateAxis 依赖 DailyStat.dayKey 取标签，分时图没有 dayKey，
    // 且 24 根柱子若只显示一个日期刻度会完全看不出横轴含义。
    hourAxis: Boolean = false,
    tapHint: String = if (hourAxis) "点一下柱子，看某个小时的具体数值"
    else "点一下柱子，看某一天的具体数值"
) {
    SoftCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = legendColor, text = legendText)
            if (subtitle != null) {
                // 副标题与图例同行右对齐：它是对数据的一句话解读，
                // 不是标题的一部分，放标题下会抢占视觉层级。
                androidx.compose.foundation.layout.Spacer(
                    Modifier.weight(1f)
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        DailyBarChart(
            data = data,
            barColor = barColor,
            emptyColor = emptyColor,
            primaryLabel = valueLabel,
            highlightLastAsToday = highlightLastAsToday,
            tapHint = tapHint
        )
        Spacer(Modifier.height(6.dp))
        // 横轴：不再「每个日期一个等宽槽位」（30 天时每格仅 ~11dp，
        // "9/1" 会被截断成 "9"，就是之前看到的「只有月份没日期」）。
        // 改为独立刻度行：按档位固定少量刻度（最多 6 个），每个刻度用
        // 非等分排布，保证每个标签都有足够宽度完整显示。
        if (hourAxis) HourAxis() else DateAxis(stats = stats)
    }
}

/**
 * 分时图的横轴（v2.7.3）。
 *
 * 24 根柱子对应 0..23 点，这里只标 5 个刻度（0/6/12/18/23），
 * 用 SpaceBetween 均匀铺开 —— 标全部 24 个会让每格只剩 ~10dp，
 * 「18点」直接被截成「18」，和之前日期轴的老毛病一样。
 *
 * 刻度值与柱子槽位**不必严格对应**：这行只负责让人看出"从左到右是凌晨到深夜"，
 * 精确到某一根柱子的数值由点按读出（点柱子时上方会显示 "15点 · 亮屏 23分钟"）。
 */
@Composable
private fun HourAxis() {
    // 12 个槽位，每个 2 小时。刻度锚在**对应槽位的中心**上，
    // 而不是均匀铺开 —— 均匀铺开（SpaceBetween）会让标签与柱子错位：
    // 第 7 根柱子（12–14点）中心在 54.2% 处，而「12点」被放在 50% 处，
    // 越往右偏差越大，最后一根柱子的标签直接贴到最右边缘。
    val anchors = listOf(0, 3, 6, 9, 11)
    val labels = listOf("0点", "6点", "12点", "18点", "22点")

    Layout(
        content = {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    ) { measurables, constraints ->
        // 标签按内容自身宽度测量，不参与等分，否则会被压成 25dp 一格而截断
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            val step = width.toFloat() / 12f
            placeables.forEachIndexed { i, placeable ->
                // 槽位 i 的柱子中心 = (i + 0.5) * step（与 DailyBarChart 的绘制一致）
                val centerX = (anchors[i] + 0.5f) * step
                val rawX = (centerX - placeable.width / 2f).roundToInt()
                // 首尾标签会被推到边界外，夹回容器内（此时标签中心略偏，
                // 但保证文字完整可见 —— 宁可轻微偏心，也不要截断）
                val x = rawX.coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 报告页横轴刻度行。
 *
 * 设计要点：横轴刻度**不按数据点数等分**，而是先按档位算出「最多几个刻度」，
 * 再把整行等分成那么多个槽位。这样每个刻度都有约「屏宽 / 刻度数」的宽度，
 * （30 天档位只放 6 个刻度，每个宽约 60dp），"9/1" 之类的短标签必然能完整显示，
 * 彻底避开「N 个日期挤一行、每格十几 dp、数字被截断」的老毛病。
 *
 * 标签取「该刻度所代表日期」的短格式（M/d），刻度在时间轴上均匀分布，
 * 首尾分别落在最早/最新一天，方便一眼看出区间。
 */
@Composable
private fun DateAxis(stats: List<com.java.myapplication.data.DailyStat>) {
    if (stats.isEmpty()) return
    // 刻度数量：日/周每天一个，两周 4 个，月 6 个（足够看清区间又不拥挤）
    val tickCount = when {
        stats.size <= 7 -> stats.size
        stats.size <= 14 -> 4
        else -> 6
    }
    // 均匀取索引：在 [0, size-1] 上取 tickCount 个等距点（含首尾）
    val lastIndex = stats.lastIndex
    val ticks: List<Int> = if (tickCount <= 1) {
        listOf(0)
    } else {
        (0 until tickCount).map { i ->
            (lastIndex.toFloat() * i / (tickCount - 1)).roundToInt().coerceIn(0, lastIndex)
        }.distinct()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ticks.forEach { index ->
            Text(
                text = shortDayLabel(stats[index].dayKey),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * 底部导航的选中/未选中配色。
 *
 * v2.7.2：选中态统一为**绿色系**。
 *
 * 原先 `NavigationBarItem` 不传 colors，于是走 Material3 默认值：
 * indicator 用 `secondaryContainer`（本项目里是 MistBlue 淡蓝），
 * 而图标和文字继承 `primary`（绿色）。冷蓝底 + 绿图标在色相上直接冲突，
 * 观感像两个组件的叠色错误；蓝色本来又是「数据报告」这个页面的辅助色，
 * 拿来表示"当前选中"会让它的语义变混。
 *
 * 现在 indicator 改用 primary 的低透明度，和「开关开启态」「主按钮」
 * 共用同一套语言：绿色 = 可交互 / 当前选中。
 * 未选中用不透明的中灰（InkGraySecondary），保证压在米白底上仍够清晰。
 */
@Composable
private fun navItemColors(): NavigationBarItemColors = NavigationBarItemDefaults.colors(
    selectedIconColor = GrassGreenDark,
    selectedTextColor = GrassGreenDark,
    indicatorColor = GrassGreen.copy(alpha = 0.14f),
    unselectedIconColor = InkGraySecondary,
    unselectedTextColor = InkGraySecondary
)

/**
 * 把日期键 yyyy-MM-dd 转成短横轴标签「M/d」（如 2025-09-01 -> 9/1）。
 *
 * 为什么不再用 MM-dd：周/月档位下标签数量多、单标签宽度只有十几个 dp，
 * 两位月 + 连字符 + 两位日在窄槽里会被逐字压缩（真机上表现为一串 0/9 的乱码）。
 * M/d 少一个字符且去掉了补零，更容易在窄槽里完整显示。
 *
 * 解析失败时回退原始字符串，保证任何脏数据都不会导致崩溃。
 */
private fun shortDayLabel(dayKey: String): String {
    // 期望格式 yyyy-MM-dd
    if (dayKey.length < 10) return dayKey
    return try {
        val month = dayKey.substring(5, 7).trimStart('0').ifEmpty { "0" }
        val day = dayKey.substring(8, 10).trimStart('0').ifEmpty { "0" }
        "$month/$day"
    } catch (_: Exception) {
        dayKey
    }
}

private fun requestNotificationPermission(context: Context) {
    if (ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        // 通过 Activity 请求
        (context as? android.app.Activity)?.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
        )
    }
}