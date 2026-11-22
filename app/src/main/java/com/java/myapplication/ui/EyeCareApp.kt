package com.java.myapplication.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.java.myapplication.data.OnboardingStore
import com.java.myapplication.notify.RestNotifier
import com.java.myapplication.ui.design.hyperNavGlass
import com.java.myapplication.ui.pages.RecordsPage
import com.java.myapplication.ui.pages.RestPage
import com.java.myapplication.ui.pages.SettingsScreen
import com.java.myapplication.ui.pages.TimerPage
import com.java.myapplication.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/** 一级页只有三个：计时、记录、设置。 */
internal enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TIMER("计时", MiuixIcons.Stopwatch),
    RECORDS("记录", MiuixIcons.Report),
    SETTINGS("设置", MiuixIcons.Settings),
}

/**
 * App 主框架（v3.0 重做）。
 *
 * ## 结构
 *
 * ```
 * MiuixScaffold
 *   ├─ bottomBar:  NavigationBar（计时 / 记录 / 设置）
 *   ├─ snackbarHost: 保存回执、返回原应用失败提示
 *   └─ Box(background = surface)      ← 页面底色，三页共用
 *        └─ HorizontalPager           ← 三页横滑，跟手
 * ```
 *
 * ## 为什么仍然是 Pager
 *
 * 底部导航切页有两种做法：Pager（横向连续画布）和 AnimatedContent（新旧两页同时挂载）。
 * 后者在上一版实测会掉帧：切换时两页各自跑完整的 measure/layout/draw，
 * 位移只是让这件事每帧都可见。Pager 每帧只做一次布局，位移全部走
 * graphicsLayer.translationX（合成层），不触发重排，所以「有位移」和「顺」能同时成立。
 *
 * ## 为什么休息页直接替换整棵树
 *
 * 休息是**模态**：它要把用户从当前任务里拽出来。用弹层会让人想关掉，
 * 用整页才符合「离开屏幕一会儿」的目的。它替换的是整个 Scaffold，不只是内容区，
 * 因此底部导航在休息时不可达 —— 这是刻意的。
 */
@Composable
fun EyeCareApp(viewModel: EyeCareViewModel = viewModel()) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.init(context)
        RestNotifier.ensureChannel(context)
    }

    LaunchedEffect(Unit) {
        viewModel.startTicking(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission(context)
        }
    }

    var currentTab by remember { mutableStateOf(Tab.TIMER) }
    // 从计时页点参数瓦片时，切到设置并直接落在「计时参数」页
    var openTimingSignal by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showOnboarding by remember { mutableStateOf(!OnboardingStore.isDone(context)) }

    // 休息中：整页接管
    if (viewModel.phase == RestPhase.RESTING) {
        RestPage(viewModel, context)
        return
    }

    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { Tab.entries.size },
    )
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReducedMotion()

    val goTo: (Tab) -> Unit = { tab ->
        // 先落高亮再起动画：点击是明确意图，应该立即反馈；
        // settledPage 监听继续负责手指滑动后的同步。
        currentTab = tab
        if (pagerState.currentPage != tab.ordinal) {
            scope.launch {
                if (reduceMotion) {
                    pagerState.scrollToPage(tab.ordinal)
                } else {
                    // 距离比例时长：跨 2 页不是把跨 1 页的动画播两遍，而是给更长的时长，
                    // 避免长距离"急停"。下限 2 页，让相邻切换也有过程感。
                    //
                    // 出处：算式改写自 HuaweiPods（GPL-3.0，提交 e2cf07eda485）的
                    // app/src/main/java/moe/chenxy/huaweipods/ui/MainTabsPagerState.kt；
                    // 上游用 animateScrollBy 自己算像素，这里改用 animateScrollToPage，
                    // 只保留时长算式与 EaseInOut，并补了「减少动态效果」时直接落页的分支。
                    // 修改日期：2026-11-21。详见 THIRD_PARTY_NOTICES.md。
                    val pages = kotlin.math.abs(tab.ordinal - pagerState.currentPage).coerceAtLeast(2)
                    pagerState.animateScrollToPage(
                        tab.ordinal,
                        animationSpec = tween(
                            durationMillis = 100 * pages + 100,
                            easing = androidx.compose.animation.core.EaseInOut,
                        ),
                    )
                }
            }
        }
    }

    // 底栏玻璃的采样源：整块页面内容。它在 content 槽里，与 bottomBar 是兄弟节点，
    // 不会形成采样环。只录一层就够，顶栏用的是各页自己的 backdrop（更近、更准）。
    val navBackdrop = rememberLayerBackdrop()
    val shellSettings = viewModel.settings

    MiuixScaffold(
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        bottomBar = {
            // 底栏形态来自设置（v3.4）：悬浮 / 贴底、模糊 / 不透明。
            //
            // 悬浮时内容从栏下穿过，模糊才有东西可采样；贴底时内容在栏上方结束，
            // 不再需要底栏采样层。两种形态共用同一组 item，不复制导航逻辑。
            if (shellSettings.floatingBottomBar) {
                FloatingNavigationBar(
                    modifier = Modifier
                        .then(
                            if (shellSettings.blurBottomBar) {
                                Modifier.hyperNavGlass(backdrop = navBackdrop)
                            } else {
                                Modifier.background(MiuixTheme.colorScheme.surfaceContainer)
                            },
                        )
                        .zIndex(2f),
                    color = if (shellSettings.blurBottomBar) {
                        Color.Transparent
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer
                    },
                    cornerRadius = 26.dp,
                    showDivider = false,
                    defaultWindowInsetsPadding = true,
                ) {
                    Tab.entries.forEach { tab ->
                        FloatingNavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { goTo(tab) },
                            icon = tab.icon,
                            label = tab.label,
                        )
                    }
                }
            } else {
                NavigationBar(
                    color = MiuixTheme.colorScheme.surface,
                    showDivider = true,
                    mode = NavigationBarDisplayMode.IconAndText,
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { goTo(tab) },
                            icon = tab.icon,
                            label = tab.label,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { settled ->
                currentTab = Tab.entries[settled]
            }
        }

        // 只取顶部 inset：底部让内容一直延伸到悬浮底栏下面，
        // 这样滚动时才有内容从玻璃底下经过（模糊才有意义）。
        // 录制整块页面（柔光底 + 内容），底栏才能连背景一起模糊；
        // 只录 pager 的话，页面之外的区域在采样里是透明的，玻璃会发黑。
        // 页面底色与 HuaweiPods 一致：直接用主题 surface（深色即纯黑），
        // 不再叠自绘的绿色光斑。彩色留给控件，背景保持中性。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    // 悬浮底栏：内容延伸到底栏之下；贴底底栏：内容在栏上方结束
                    bottom = if (shellSettings.floatingBottomBar) {
                        0.dp
                    } else {
                        innerPadding.calculateBottomPadding()
                    },
                )
                .then(
                    if (shellSettings.floatingBottomBar && shellSettings.blurBottomBar) {
                        Modifier.layerBackdrop(navBackdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                // 三页全部预组合：否则第一次切到最后一页时，
                // 「组合 + 首次 measure + 首次 draw」会整段砸在弹簧动画里。
                beyondViewportPageCount = 2,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
                key = { it },
            ) { page ->
                when (Tab.entries[page]) {
                    Tab.TIMER -> TimerPage(
                        viewModel = viewModel,
                        context = context,
                        onOpenTimingSettings = {
                            openTimingSignal++
                            goTo(Tab.SETTINGS)
                        },
                    )
                    Tab.RECORDS -> RecordsPage(viewModel, context)
                    Tab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        context = context,
                        snackbarHostState = snackbarHostState,
                        isActive = currentTab == Tab.SETTINGS,
                        openTimingSignal = openTimingSignal,
                    )
                }
            }
        }
    }

    // 休息刚结束：给一条轻量回执。
    //
    // 休息页是整页替换，结束时它直接消失，用户只看到"回到了计时页"——
    // 没有任何"刚才那次休息完成了"的确认。这里补一条 Snackbar，
    // 不做闪烁、不做全屏动画（完成反馈必须克制，否则会变成干扰）。
    var previousPhase by remember { mutableStateOf(viewModel.phase) }
    LaunchedEffect(viewModel.phase) {
        val was = previousPhase
        previousPhase = viewModel.phase
        if (was == RestPhase.RESTING && viewModel.phase == RestPhase.IDLE) {
            snackbarHostState.showSnackbar("休息完成，继续加油")
        }
    }

    // 休息结束但没能返回原应用：进入前台时补一条说明（标记持久化，不会丢）
    LaunchedEffect(viewModel.phase) {
        if (viewModel.phase == RestPhase.IDLE) {
            val msg = viewModel.takeReturnNotice(context)
            if (msg != null) snackbarHostState.showSnackbar(msg)
        }
    }

    // 到点提醒：用弹层确认「现在休息 / 稍后再说」。
    // 用 OverlayDialog 而不是 Material AlertDialog —— 后者是方角+系统蓝，
    // 一出现就会把整套 HyperOS 观感打断。
    if (viewModel.phase == RestPhase.AWAITING) {
        val haptic = LocalHapticFeedback.current
        LaunchedEffect(Unit) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        OverlayDialog(
            show = true,
            title = "该让眼睛休息啦",
            summary = "已连续用眼 ${viewModel.settings.workMinutes} 分钟。\n" +
                "点「现在休息」后开始 ${viewModel.settings.restSeconds} 秒远眺倒计时。",
            onDismissRequest = { viewModel.postponeRest(context) },
        ) {
            Button(
                onClick = { viewModel.startRest(context) },
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 20.dp,
                minHeight = 44.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("现在休息", style = MiuixTheme.textStyles.button)
            }
            Box(Modifier.padding(top = 8.dp)) {
                TextButton(
                    text = "稍后再说",
                    onClick = { viewModel.postponeRest(context) },
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 20.dp,
                    minHeight = 44.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    if (showOnboarding) {
        OnboardingDialog(
            onFinish = {
                OnboardingStore.markDone(context)
                showOnboarding = false
            },
        )
    }
}

/** Android 13+ 通知权限：拒绝不影响使用，只是到点没有横幅。 */
private fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    runCatching {
        (context as? android.app.Activity)?.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1001,
        )
    }
    // 非 Activity context（如预览）直接跳过：权限会在 Activity 真正前台时再问
}
