package com.java.myapplication.ui.pages

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.java.myapplication.data.AppInfo
import com.java.myapplication.data.AppSettings
import com.java.myapplication.data.ThemeMode
import com.java.myapplication.notify.RestSoundPlayer
import com.java.myapplication.notify.RestSoundSource
import com.java.myapplication.ui.DiagnosticsPanel
import com.java.myapplication.ui.EyeCareViewModel
import com.java.myapplication.ui.design.HyperDivider
import com.java.myapplication.ui.design.HyperSection
import com.java.myapplication.ui.design.HyperSpace
import com.java.myapplication.ui.design.RootPage
import com.java.myapplication.ui.design.SubPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页（v3.0 重做）。
 *
 * ## 结构
 *
 * 一级：四组入口（护眼 / 提醒 / 通用 / 其他），只负责"去哪"，
 * 不放任何开关 —— 开关放在一级页会让整页都是控件，反而找不到入口。
 *
 * 二级：每一类参数一页，页面内仍然分组（同组参数放一张卡）。
 * 返回由 [BackHandler] 接管：在二级页按返回回到一级，在一级页交还系统。
 *
 * ## 为什么用 miuix 的 Preference 组件
 *
 * [MiuixArrowPreference]、[RadioButtonPreference]、[Switch] 这些的手感
 * （按下缩放、行高、内距、与描边的对齐）都是 HyperOS 的规范值，
 * 自己用 Box + clickable 拼出来的行，按下去只有 ripple，观感立刻掉档。
 */
@Composable
internal fun SettingsRouteContent(
    route: SettingsRoute,
    viewModel: EyeCareViewModel,
    context: Context,
    snackbarHostState: SnackbarHostState,
    onOpen: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
) {
    var showDiagnostics by remember { mutableStateOf(false) }
    var showFullScreenConfirm by remember { mutableStateOf(false) }
    var pendingAnnounce by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()


    LaunchedEffect(pendingAnnounce) {
        val msg = pendingAnnounce ?: return@LaunchedEffect
        delay(600L)
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        pendingAnnounce = null
    }

    // 统一提交：永远基于**当前最新**设置做变换，避免闭包捕获旧值互相覆盖
    fun update(
        transform: (AppSettings) -> AppSettings,
        announce: String = "设置已保存",
        settled: Boolean = false,
    ) {
        viewModel.updateSettings(context, transform(viewModel.settings))
        if (settled) {
            pendingAnnounce = announce
        } else {
            pendingAnnounce = null
            scope.launch {
                        snackbarHostState.showSnackbar(announce, duration = SnackbarDuration.Short)
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsPanel(onDismiss = { showDiagnostics = false })
        return
    }

    val s = viewModel.settings

    when (route) {
        SettingsRoute.Home -> RootPage(title = "设置") {
            Spacer(Modifier.height(HyperSpace.Tight))
            HyperSection {
                SettingsEntry("计时参数", "提醒间隔、休息时长、每日目标") {
                    onOpen(SettingsRoute.Timing)
                }
                HyperDivider()
                SettingsEntry("护眼模式", "全屏提醒、超时提醒、免打扰、提示音、返回原应用") {
                    onOpen(SettingsRoute.Reminder)
                }
                HyperDivider()
                SettingsEntry("久坐提醒", "按间隔提醒起身活动") {
                    onOpen(SettingsRoute.Sit)
                }
            }
            Spacer(Modifier.height(HyperSpace.Section))
            HyperSection {
                SettingsEntry(
                    "外观",
                    s.themeMode.label,
                ) {
                    onOpen(SettingsRoute.Appearance)
                }
            }
            Spacer(Modifier.height(HyperSpace.Section))
            HyperSection {
                SettingsEntry("提醒可靠性自检", "提醒不准时，逐项检查系统权限与电池设置") {
                    showDiagnostics = true
                }
                HyperDivider()
                SettingsEntry(
                    "关于",
                    AppInfo.REPO_URL_SHORT,
                    trailing = { VersionBadge() },
                ) {
                    onOpen(SettingsRoute.About)
                }
            }
        }

        SettingsRoute.Timing -> SubPage("计时参数", onBack = onBack) {
            Spacer(Modifier.height(HyperSpace.Tight))
            HyperSection(title = "节奏") {
                IntSliderRow(
                    title = "用眼提醒间隔",
                    value = s.workMinutes,
                    range = 1..120,
                    unit = "分钟",
                    onChange = { v -> update({ it.copy(workMinutes = v) }, "用眼提醒间隔：$v 分钟", settled = true) },
                )
                HyperDivider(startInset = 16.dp)
                IntSliderRow(
                    title = "休息时长",
                    value = s.restSeconds,
                    range = 5..300,
                    unit = "秒",
                    onChange = { v -> update({ it.copy(restSeconds = v) }, "休息时长：$v 秒", settled = true) },
                )
            }
            Spacer(Modifier.height(HyperSpace.Section))
            HyperSection(title = "目标") {
                IntSliderRow(
                    title = "每日用眼目标",
                    value = s.dailyGoalMinutes,
                    range = 10..480,
                    unit = "分钟",
                    onChange = { v -> update({ it.copy(dailyGoalMinutes = v) }, "每日用眼目标：$v 分钟", settled = true) },
                )
            }
        }

        SettingsRoute.Reminder -> SubPage("护眼模式", onBack = onBack) {
            Spacer(Modifier.height(HyperSpace.Tight))
            HyperSection(title = "到点提醒") {
                SwitchRow(
                    title = "到点自动全屏",
                    subtitle = "关闭后只发通知横幅，不弹全屏",
                    checked = s.autoFullScreen,
                    onChecked = { v ->
                        if (v) showFullScreenConfirm = true
                        else update({ it.copy(autoFullScreen = false) }, "已关闭「到点自动全屏」")
                    },
                )
                HyperDivider(startInset = 16.dp)
                IntSliderRow(
                    title = "超时二次提醒",
                    subtitle = "到点后仍不休息，每隔这么久再提醒一次",
                    value = s.overdueRemindMinutes,
                    range = AppSettings.OVERDUE_REMIND_RANGE,
                    unit = "分钟",
                    onChange = { v ->
                        update({ it.copy(overdueRemindMinutes = v) }, "超时二次提醒：$v 分钟", settled = true)
                    },
                )
            }
            Spacer(Modifier.height(HyperSpace.Section))
            HyperSection(title = "免打扰") {
                SwitchRow(
                    title = "启用免打扰时段",
                    subtitle = "时段内只计时不打扰，出时段自动恢复",
                    checked = s.dndEnabled,
                    onChecked = { v ->
                        update({ it.copy(dndEnabled = v) }, if (v) "免打扰时段已开启" else "免打扰时段已关闭")
                    },
                )
                AnimatedVisibility(
                    visible = s.dndEnabled,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                ) {
                    Column {
                        HyperDivider(startInset = 16.dp)
                        TimeRow(
                            title = "开始",
                            minuteOfDay = s.dndStartMinute,
                            onPick = { v ->
                                update({ it.copy(dndStartMinute = v) }, "免打扰开始：${AppSettings.formatMinuteOfDay(v)}")
                            },
                        )
                        HyperDivider(startInset = 16.dp)
                        TimeRow(
                            title = "结束",
                            minuteOfDay = s.dndEndMinute,
                            onPick = { v ->
                                update({ it.copy(dndEndMinute = v) }, "免打扰结束：${AppSettings.formatMinuteOfDay(v)}")
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(HyperSpace.Section))
            HyperSection(title = "休息结束") {
                SwitchRow(
                    title = "提示音",
                    checked = s.restEndSoundEnabled,
                    onChecked = { v ->
                        update({ it.copy(restEndSoundEnabled = v) }, if (v) "提示音已开启" else "提示音已关闭")
                    },
                )
                AnimatedVisibility(
                    visible = s.restEndSoundEnabled,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                ) {
                    Column {
                        HyperDivider(startInset = 16.dp)
                        SwitchRow(
                            title = "同时振动",
                            checked = s.restEndSoundVibrate,
                            onChecked = { v ->
                                update({ it.copy(restEndSoundVibrate = v) }, if (v) "已开启同时振动" else "已关闭同时振动")
                            },
                        )
                        HyperDivider(startInset = 16.dp)
                        SoundSourceRow(
                            current = s.restEndSoundSource,
                            onSelect = { v ->
                                update({ it.copy(restEndSoundSource = v) }, "提示音音源：${v.label}")
                            },
                        )
                    }
                }
                HyperDivider(startInset = 16.dp)
                SwitchRow(
                    title = "休息后回到原应用",
                    subtitle = "需「使用情况访问」权限",
                    checked = s.returnToAppAfterRest,
                    onChecked = { v ->
                        update(
                            { it.copy(returnToAppAfterRest = v) },
                            if (v) "已开启休息后回到原应用" else "已关闭休息后回到原应用",
                        )
                    },
                )
            }
        }

        SettingsRoute.Sit -> SubPage("久坐提醒", onBack = onBack) {
            Spacer(Modifier.height(HyperSpace.Tight))
            HyperSection {
                SwitchRow(
                    title = "久坐提醒",
                    subtitle = "与用眼提醒独立计时",
                    checked = s.sitReminderEnabled,
                    onChecked = { v ->
                        update({ it.copy(sitReminderEnabled = v) }, if (v) "久坐提醒已开启" else "久坐提醒已关闭")
                    },
                )
                AnimatedVisibility(
                    visible = s.sitReminderEnabled,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                ) {
                    Column {
                        HyperDivider(startInset = 16.dp)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = "提醒间隔",
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            TabRow(
                                tabs = AppSettings.SIT_INTERVAL_CHOICES.map { "$it 分钟" },
                                selectedTabIndex = AppSettings.SIT_INTERVAL_CHOICES
                                    .indexOf(s.sitIntervalMinutes).coerceAtLeast(0),
                                onTabSelected = { idx ->
                                    val minutes = AppSettings.SIT_INTERVAL_CHOICES[idx]
                                    update(
                                        { it.copy(sitIntervalMinutes = minutes) },
                                        "久坐提醒间隔：$minutes 分钟",
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        SettingsRoute.Appearance -> SubPage("外观", onBack = onBack) {
            Spacer(Modifier.height(HyperSpace.Tight))
            HyperSection {
                OverlayDropdownPreference(
                    title = "主题",
                    items = ThemeMode.entries.map { it.label },
                    selectedIndex = s.themeMode.ordinal,
                    onSelectedIndexChange = { idx ->
                        val mode = ThemeMode.entries[idx]
                        update({ it.copy(themeMode = mode) }, "主题：${mode.label}")
                    },
                )
                HyperDivider(startInset = 16.dp)
                SwitchRow(
                    title = "悬浮底栏",
                    subtitle = "让底部导航栏浮在内容上方",
                    checked = s.floatingBottomBar,
                    onChecked = { v ->
                        update(
                            { it.copy(floatingBottomBar = v) },
                            if (v) "底栏已改为悬浮" else "底栏已改为贴底",
                        )
                    },
                )
                HyperDivider(startInset = 16.dp)
                SwitchRow(
                    title = "底栏模糊",
                    subtitle = "关闭后底栏改用不透明底色",
                    checked = s.blurBottomBar,
                    onChecked = { v ->
                        update(
                            { it.copy(blurBottomBar = v) },
                            if (v) "底栏模糊已开启" else "底栏模糊已关闭",
                        )
                    },
                )
            }
        }

        SettingsRoute.About -> SubPage("关于", onBack = onBack) {
            AboutPage(context = context, onOpenLicense = { onOpen(SettingsRoute.References) })
        }

        SettingsRoute.References -> ReferencesPage(onBack = onBack)
    }

    if (showFullScreenConfirm) {
        OverlayDialog(
            show = true,
            title = "开启「到点自动全屏」？",
            summary = "开启后，用眼到点时会在你正在使用的应用之上强制弹出全屏休息页，" +
                "可能打断正在进行的操作（如看视频、开会、导航）。\n\n" +
                "手机息屏或锁屏时不会弹出。",
            onDismissRequest = { showFullScreenConfirm = false },
        ) {
            Button(
                onClick = {
                    showFullScreenConfirm = false
                    update({ it.copy(autoFullScreen = true) }, "已开启「到点自动全屏」")
                },
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                minHeight = 44.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("确认开启", style = MiuixTheme.textStyles.button) }
            Box(Modifier.padding(top = 8.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showFullScreenConfirm = false },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    minHeight = 44.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** 设置页路由。[depth] 决定转场方向：变大往里推，变小往回退。 */
internal sealed interface SettingsRoute {
    val key: String

    data object Home : SettingsRoute { override val key = "home" }
    data object Timing : SettingsRoute { override val key = "timing" }
    data object Reminder : SettingsRoute { override val key = "reminder" }
    data object Sit : SettingsRoute { override val key = "sit" }
    data object Appearance : SettingsRoute { override val key = "appearance" }
    data object About : SettingsRoute { override val key = "about" }
    data object References : SettingsRoute { override val key = "references" }

    companion object {
        fun fromKey(key: String): SettingsRoute = when (key) {
            Home.key -> Home
            Timing.key -> Timing
            Reminder.key -> Reminder
            Sit.key -> Sit
            Appearance.key -> Appearance
            About.key -> About
            References.key -> References
            else -> Home
        }
    }
}

/** 一级页的一行入口：标题 + 一句当前值 + 箭头。 */
@Composable
private fun SettingsEntry(
    title: String,
    summary: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    MiuixArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
        endActions = if (trailing == null) {
            {}
        } else {
            { trailing() }
        },
    )
}

@Composable
private fun VersionBadge() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val name = remember(context) { AppInfo.versionName(context) }
    Text(
        text = "v$name",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 开关行：用 miuix 的 Switch，按下有缩放反馈。 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    // 直接交给 miuix：行高、内距、开关动效、按下反馈、无障碍角色都由库统一，
    // 之前手写的 Row + Switch 在副标题灰度和行高上都和 Preference 行对不齐。
    SwitchPreference(
        title = title,
        summary = subtitle,
        checked = checked,
        onCheckedChange = onChecked,
    )
}

@Composable
private fun IntSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    onChange: (Int) -> Unit,
    subtitle: String? = null,
) {
    // 拖动只改本地值，松手才落盘：onChange 会重排闹钟，不能每帧提交。
    // valueText 由本地值渲染，所以拖动时数字跟手，但配置只在松手时保存一次。
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    SliderPreference(
        value = draft,
        onValueChange = { draft = it.coerceIn(range.first.toFloat(), range.last.toFloat()) },
        onValueChangeFinished = {
            val v = draft.toInt().coerceIn(range.first, range.last)
            if (v != value) onChange(v)
        },
        title = title,
        summary = subtitle,
        valueText = "${draft.toInt()} $unit",
        valueRange = range.first.toFloat()..range.last.toFloat(),
        showKeyPoints = false,
    )
}

/** 免打扰时间行：点击弹底部滚轮。 */
@Composable
private fun TimeRow(title: String, minuteOfDay: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    MiuixArrowPreference(
        title = title,
        summary = AppSettings.formatMinuteOfDay(minuteOfDay),
        onClick = { open = true },
    )
    if (open) {
        TimePickerSheet(
            initial = minuteOfDay,
            onDismiss = { open = false },
            onConfirm = {
                open = false
                onPick(it)
            },
        )
    }
}

/**
 * 时间选择底部弹层：两列滚轮（小时 / 分钟）。
 *
 * 为什么不用系统 TimePickerDialog：它是 Material 的对话框，方角、系统蓝、
 * 还会在浅色主题上盖一层灰 —— 和整套界面不是一回事。
 */
@Composable
private fun TimePickerSheet(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var hour by remember { mutableStateOf(initial / 60) }
    var minute by remember { mutableStateOf(initial % 60) }
    OverlayBottomSheet(
        show = true,
        title = "选择时间",
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberPicker(
                value = hour,
                onValueChange = { hour = it },
                range = 0..23,
                label = { "%02d".format(it) },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ":",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurface,
            )
            NumberPicker(
                value = minute,
                onValueChange = { minute = it },
                range = 0..59,
                label = { "%02d".format(it) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                cornerRadius = 20.dp,
                minHeight = 44.dp,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
            Button(
                onClick = { onConfirm(hour * 60 + minute) },
                modifier = Modifier.weight(1f),
                cornerRadius = 20.dp,
                minHeight = 44.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("确定", style = MiuixTheme.textStyles.button) }
        }
    }
}

/** 提示音音源：一行入口 + 底部弹层单选 + 试听。 */
@Composable
private fun SoundSourceRow(current: RestSoundSource, onSelect: (RestSoundSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    MiuixArrowPreference(
        title = "提示音音源",
        summary = current.label,
        onClick = { open = true },
    )
    if (open) {
        OverlayBottomSheet(
            show = true,
            title = "提示音音源",
            onDismissRequest = {
                RestSoundPlayer.stop()
                open = false
            },
        ) {
            RestSoundSource.entries.forEach { source ->
                RadioButtonPreference(
                    title = source.label,
                    selected = source == current,
                    onClick = {
                        onSelect(source)
                        // 选完立刻试听：比写「单声/清亮」这种文字描述准得多
                        RestSoundPlayer.play(context, source)
                        open = false
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = "试听当前",
                onClick = { RestSoundPlayer.play(context, current) },
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                minHeight = 44.dp,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/**
 * 设置 tab 的导航壳（v3.5）。
 *
 * ## 为什么用 miuix 的 NavDisplay
 *
 * 之前的二级页转场是自写 `AnimatedContent`（1/4 屏、200ms、自定曲线），
 * 和 miuix 的导航规范差一截。现在交给 `miuix-navigation3-ui` 的 `NavDisplay`：
 * 默认推入是 **500ms 整屏位移 + NavTransitionEasing(0.8, 0.95)**，
 * 预测性返回是库自己实现的纯位移 —— 不需要我们再手写 progress。
 *
 * ## 为什么离开 tab 要清栈
 *
 * 三个 tab 在 Pager 里常驻组合，NavDisplay 的返回处理也会一直注册。
 * 如果带着二级页切到计时 tab，按返回会把"看不见的设置页"弹掉一层。
 * 所以离开设置 tab 时把栈收回首页；代价是切回来不再停在原二级页，
 * 但这比"吞掉一次返回"可预期。
 */
@Composable
internal fun SettingsScreen(
    viewModel: EyeCareViewModel,
    context: Context,
    snackbarHostState: SnackbarHostState,
    isActive: Boolean,
    openTimingSignal: Int = 0,
) {
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<SettingsRoute>, String>(
            save = { list -> list.map { it.key } },
            restore = { keys ->
                mutableStateListOf(*keys.map(SettingsRoute::fromKey).toTypedArray())
            },
        ),
    ) { mutableStateListOf<SettingsRoute>(SettingsRoute.Home) }

    LaunchedEffect(isActive) {
        if (!isActive) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
    }

    // 计时页的参数瓦片 → 打开「计时参数」。
    // 只认变化量：signal 为 0 是初始值，不该在首次进入设置时硬跳二级页。
    LaunchedEffect(openTimingSignal, isActive) {
        if (isActive && openTimingSignal > 0 && stack.last() != SettingsRoute.Timing) {
            stack.add(SettingsRoute.Timing)
        }
    }

    val provider = entryProvider<SettingsRoute> {
        entry<SettingsRoute.Home> {
            SettingsRouteContent(
                route = SettingsRoute.Home,
                viewModel = viewModel,
                context = context,
                snackbarHostState = snackbarHostState,
                onOpen = { stack.add(it) },
                onBack = {},
            )
        }
        entry<SettingsRoute.Timing> {
            SettingsRouteContent(
                route = SettingsRoute.Timing,
                viewModel = viewModel,
                context = context,
                snackbarHostState = snackbarHostState,
                onOpen = {},
                onBack = { popSettings(stack) },
            )
        }
        entry<SettingsRoute.Reminder> {
            SettingsRouteContent(
                route = SettingsRoute.Reminder,
                viewModel = viewModel,
                context = context,
                snackbarHostState = snackbarHostState,
                onOpen = {},
                onBack = { popSettings(stack) },
            )
        }
        entry<SettingsRoute.Sit> {
            SettingsRouteContent(
                route = SettingsRoute.Sit,
                viewModel = viewModel,
                context = context,
                snackbarHostState = snackbarHostState,
                onOpen = {},
                onBack = { popSettings(stack) },
            )
        }
        entry<SettingsRoute.Appearance> {
            SettingsRouteContent(
                route = SettingsRoute.Appearance,
                viewModel = viewModel,
                context = context,
                snackbarHostState = snackbarHostState,
                onOpen = {},
                onBack = { popSettings(stack) },
            )
        }
        entry<SettingsRoute.References> {
            SettingsRouteContent(
                route = SettingsRoute.References,
                viewModel = viewModel,
                context = context,
                snackbarHostState = snackbarHostState,
                onOpen = {},
                onBack = { popSettings(stack) },
            )
        }
        entry<SettingsRoute.About> {
            SettingsRouteContent(
                route = SettingsRoute.About,
                viewModel = viewModel,
                context = context,
                snackbarHostState = snackbarHostState,
                // 关于页要能继续往里推「参考与致谢」；之前这里是空 lambda，
                // 所以点那一行没有任何反应 —— 这就是"打不开"的原因。
                onOpen = { stack.add(it) },
                onBack = { popSettings(stack) },
            )
        }
    }

    val entries = rememberDecoratedNavEntries(stack, entryProvider = provider)
    NavDisplay(
        entries = entries,
        // 与 HuaweiPods 对齐：dimAmount 用 miuix 的默认值 0.5。
        //
        // 这个值做的事，是在转场时给底下那页盖一层黑色 Box，alpha 随进度升到 0.5。
        // 明暗交界正好落在滑入页面的左边缘，观感就是"新页面在旧页面上投了阴影"，
        // 而不是平面地滑过去 —— 这是层级感的来源。
        //
        // 注意：0.5 曾经被反馈成"黑闪"，那是因为当时页面根节点还是透明的，
        // 遮罩连整屏 app 底色一起压暗。现在页面自带不透明底板（见 HyperPage），
        // 被压暗的只有滑出的旧页面，与参考项目的表现一致。
        // 如果以后又觉得闪，只调这一个数字，别动别的。
        transitionEffects = NavDisplayTransitionEffects(
            enableCornerClip = true,
            dimAmount = 0.5f,
            blockInputDuringTransition = true,
        ),
        onBack = { popSettings(stack) },
    )
}

private fun popSettings(stack: MutableList<SettingsRoute>) {
    if (stack.size > 1) stack.removeAt(stack.lastIndex)
}
