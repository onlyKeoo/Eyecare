package com.java.myapplication.ui.pages

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.data.ScreenTimeStore
import com.java.myapplication.ui.EyeCareViewModel
import com.java.myapplication.ui.RestPhase
import com.java.myapplication.ui.design.HyperDivider
import com.java.myapplication.ui.design.HyperEmptyState
import com.java.myapplication.ui.design.HyperMotion
import com.java.myapplication.ui.design.HyperSection
import com.java.myapplication.ui.design.HyperSpace
import com.java.myapplication.ui.design.RootPage
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 计时页（v3.0 重做）。
 *
 * ## 为什么不再用进度环
 *
 * 上一版把 236dp 的圆环当整页主体。问题不在「环好不好看」，而在**读什么**：
 * 环表达的是"占比"，可用户真正要的答案是"还剩多久"——那是一个**时间量**，
 * 不是比例。当剩余时间只剩 20 秒时，环才走了 1/60，几乎看不出变化；
 * 反过来数字每秒都在变，环却像没动。
 *
 * 这一版把主读数换成**大号剩余时间**（等宽数字，64sp），进度条退到它下面做
 * 辅助（6dp 细横条）。视线顺序变成：阶段 → 剩余时间 → 进度 → 操作，
 * 每一层只回答一个问题。
 *
 * ## 阶段是怎么表达的
 *
 * 只有三种，且都来自真实状态，没有为了好看造中间态：
 * - 用眼中：正在累计亮屏用眼时长；
 * - 已暂停：息屏了，计时跟着停（[EyeCareViewModel.pausedByScreenOff]）；
 * - 该休息了：到点，等用户确认（[RestPhase.AWAITING]）。
 *
 * 「暂停 / 继续」对应的是**提醒开关**，不是计时器的手动启停 ——
 * 这个 App 的用眼时长由系统亮屏事件驱动，没有"手动开始计时"这件事，
 * 所以界面上也不能给一个假的开始按钮。
 */
@Composable
fun TimerPage(
    viewModel: EyeCareViewModel,
    context: Context,
    onOpenTimingSettings: () -> Unit,
) {
    var hasUsageAccess by remember { mutableStateOf(ScreenTimeStore.hasPermission(context)) }
    var minuteTick by remember { mutableStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            minuteTick = System.currentTimeMillis() / 60_000L
        }
    }
    val screenOnSeconds = remember(hasUsageAccess, minuteTick) {
        if (hasUsageAccess) ScreenTimeStore.todayScreenOnSeconds(context) else null
    }

    RootPage(title = "护眼时光") {
        Spacer(Modifier.height(HyperSpace.Tight))
        TimerHeroCard(viewModel)
        Spacer(Modifier.height(HyperSpace.Tight))
        ModeTileRow(viewModel, onOpenTimingSettings)
        Spacer(Modifier.height(HyperSpace.Section))
        TimerActions(viewModel, context)
        Spacer(Modifier.height(HyperSpace.Section))
        TodaySection(
            viewModel = viewModel,
            screenOnSeconds = screenOnSeconds,
            onRequestScreenTime = {
                runCatching {
                    context.startActivity(
                        ScreenTimeStore.permissionIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                hasUsageAccess = ScreenTimeStore.hasPermission(context)
            },
        )
        Spacer(Modifier.height(HyperSpace.Section))
        HyperSection(title = "护眼知识") {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = viewModel.currentTip.title,
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(HyperSpace.Tight))
                Text(
                    text = viewModel.currentTip.content,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * 主读数卡：阶段 + 剩余时间 + 细进度条 + 本轮参数。
 *
 * 三层信息，从上到下逐级变轻：
 * 1. 阶段（小字 + 状态点）：回答"现在处于什么状态"；
 * 2. 剩余时间（64sp 等宽）：回答"还剩多久"；
 * 3. 进度条 + 参数：回答"走到哪了""这一轮多长"。
 */
@Composable
private fun TimerHeroCard(viewModel: EyeCareViewModel) {
    val awaiting = viewModel.phase == RestPhase.AWAITING
    val paused = viewModel.pausedByScreenOff
    val disabled = !viewModel.reminderEnabled
    val phaseText = when {
        disabled -> "提醒已关闭"
        awaiting -> "该休息了"
        paused -> "已暂停"
        else -> "用眼中"
    }
    val subtle = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val accent by animateColorAsState(
        targetValue = when {
            disabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            awaiting -> MiuixTheme.colorScheme.error
            paused -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            else -> MiuixTheme.colorScheme.primary
        },
        animationSpec = tween(220),
        label = "phase-accent",
    )
    val progress by animateFloatAsState(
        targetValue = viewModel.workProgress,
        animationSpec = HyperMotion.Progress,
        label = "work-progress",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(7.dp))
            // 阶段切换（用眼中 → 该休息了）用交叉淡入，避免一个字突然换掉
            androidx.compose.animation.AnimatedContent(
                targetState = phaseText,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(160),
                    ) togetherWith androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(100),
                    )
                },
                label = "phase-text",
            ) { text ->
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.footnote1,
                    color = accent,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 主读数：剩余时间。等宽数字，避免每秒跳字时整串左右抖。
        Text(
            text = if (awaiting) "00:00" else viewModel.formatTime(viewModel.remainingSeconds),
            style = MiuixTheme.textStyles.headline1.copy(
                fontSize = 64.sp,
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Medium,
            ),
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            // 大字号下 64sp 的 "120:00" 会超出卡片宽度，按宽度自动降到 28sp。
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(28.sp, 64.sp),
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = when {
                disabled -> "打开下方「护眼提醒」后开始累计"
                awaiting -> "点下方「现在休息」开始 ${viewModel.settings.restSeconds} 秒远眺"
                paused -> "息屏时不计入用眼时长，亮屏后继续"
                // 分钟向上取整：还剩 19:59 时说「20 分钟后」，而不是「19 分钟后」。
                // 之前这里少了前缀，界面上直接显示「后提醒你休息 40 秒」。
                else -> {
                    val minutes = (viewModel.remainingSeconds + 59) / 60
                    "$minutes 分钟后提醒你休息 ${viewModel.settings.restSeconds} 秒"
                }
            },
            style = MiuixTheme.textStyles.footnote1,
            color = subtle,
        )

        Spacer(Modifier.height(16.dp))

        // 细进度条交给 miuix 的 LinearProgressIndicator：
        // 轨道、圆角、动画状态都由库统一，别处（目标进度）也复用它。
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            height = 6.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = accent,
            ),
        )

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ParameterPair(
                label = "本轮",
                value = "${viewModel.settings.workMinutes}",
                unit = "分钟",
                modifier = Modifier.weight(1f),
            )
            ParameterPair(
                label = "每次休息",
                value = "${viewModel.settings.restSeconds}",
                unit = "秒",
                modifier = Modifier.weight(1f),
            )
        }
    }
}


/**
 * 参数瓦片：用眼间隔 / 休息时长 / 每日目标。
 *
 * 三块独立的小玻璃面，而不是塞进一张卡里排成三列 —— 参考 HuaweiPods 的
 * 状态网格：每块都有自己的边界，点是点、看是看，不用在一张卡里分辨"哪段属于哪项"。
 *
 * 点击进入「设置 → 计时参数」：参数在计时页可见可改，是"当前模式"最自然的落点。
 */
@Composable
private fun ModeTileRow(viewModel: EyeCareViewModel, onOpen: () -> Unit) {
    val items = listOf(
        Triple("间隔", "${viewModel.settings.workMinutes}", "分钟"),
        Triple("休息", "${viewModel.settings.restSeconds}", "秒"),
        Triple("目标", "${viewModel.settings.dailyGoalMinutes}", "分钟"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (label, value, unit) ->
            // 用 miuix 的 Card：按下时整体轻微下沉（PressFeedbackType.Sink），
            // 这是 HyperOS 的按压语言，比 Material 的水波纹更"实"。
            Card(
                modifier = Modifier.weight(1f),
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                colors = CardDefaults.defaultColors(),
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = false,
                onClick = onOpen,
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ParameterPair(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/**
 * 操作区。
 *
 * 只有两个真实动作：
 * - 「现在休息」：立即开始休息倒计时（[EyeCareViewModel.startRest]）；
 * - 「护眼提醒」开关：等价于暂停 / 继续整套提醒（[EyeCareViewModel.setReminderEnabled]）。
 *
 * 到点等待时额外给一个「稍后再说」，它会把本轮重新起算（[EyeCareViewModel.postponeRest]）。
 */
@Composable
private fun TimerActions(viewModel: EyeCareViewModel, context: Context) {
    HyperSection(title = "操作") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Button(
                onClick = { viewModel.startRest(context) },
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                minHeight = 46.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("现在休息", style = MiuixTheme.textStyles.button)
            }
            if (viewModel.phase == RestPhase.AWAITING) {
                Spacer(Modifier.height(10.dp))
                // 次要动作：浅灰底 + 主色文字。两个按钮都用实心蓝时，
                // 用户看不出哪个是「现在休息」。
                TextButton(
                    text = "稍后再说",
                    onClick = { viewModel.postponeRest(context) },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    minHeight = 46.dp,
                    colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.secondaryContainer,
                                textColor = MiuixTheme.colorScheme.primary,
                            ),
                )
            }
        }
        HyperDivider(startInset = 16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "护眼提醒",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            top.yukonga.miuix.kmp.basic.Switch(
                checked = viewModel.reminderEnabled,
                onCheckedChange = { viewModel.setReminderEnabled(context, it) },
            )
        }
    }
}

/**
 * 今日区：三个真实读数 + 目标进度 + 未授权引导。
 *
 * 这里的数字与「记录」页同源（系统亮屏时长 + 自统计休息），单位也是分钟/次，
 * 不再另算一套口径 —— 同一件事在两个页面显示不同的数，比不显示更糟。
 */
@Composable
private fun TodaySection(
    viewModel: EyeCareViewModel,
    screenOnSeconds: Long?,
    onRequestScreenTime: () -> Unit,
) {
    val goalMinutes = viewModel.settings.dailyGoalMinutes
    // 未授权时整块换成空态：不要"空态卡套在分组卡里"——
    // 两层卡片嵌套会让边框叠成一条双线，看起来像渲染错误。
    if (screenOnSeconds == null) {
        HyperEmptyState(
            title = "看不到亮屏时长",
            description = "授权「使用情况访问」后，这里显示系统统计的今日亮屏时长。",
        ) {
            TextButton(
                text = "去授权",
                onClick = onRequestScreenTime,
                cornerRadius = 20.dp,
                minHeight = 44.dp,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
        return
    }
    HyperSection(title = "今日") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            // 亮屏时长独占一行（v3.9）。
            //
            // 三列等宽时「3 小时 49 分」约占一半宽度，右侧的「7」就贴在它后面，
            // 读起来像「3 小时 49 分 7」；缩字号只能把字变小、挤的问题还在。
            // 而亮屏本来就是这张卡的主指标，给它整行也符合信息层级。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = formatHoursMinutes(screenOnSeconds.toInt()),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "亮屏",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = "${(screenOnSeconds / 60)} / $goalMinutes 分钟",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(10.dp))
            // 休息两项合成一行次级文字，避免它们与主指标抢注意力
            Text(
                text = "休息 ${viewModel.todayCount} 次 · 共 ${viewModel.todaySeconds / 60} 分钟",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(10.dp))
            GoalBar(
                usedMinutes = (screenOnSeconds / 60).toInt(),
                goalMinutes = goalMinutes,
            )
        }
    }
}

@Composable
private fun GoalBar(usedMinutes: Int, goalMinutes: Int) {
    val ratio = if (goalMinutes <= 0) 0f else (usedMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)
    val animated by animateFloatAsState(ratio, HyperMotion.Progress, label = "goal")
    val over = usedMinutes > goalMinutes
    val color by animateColorAsState(
        targetValue = if (over) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
        animationSpec = tween(220),
        label = "goal-color",
    )
    Column {
        Text(
            text = if (over) "已超出每日目标" else "每日目标",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = animated,
            modifier = Modifier.fillMaxWidth(),
            height = 6.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color,
            ),
        )
    }
}


/** 亮屏时长按「X 小时 Y 分」显示；不足一小时只显示分钟，避免出现「0 小时」。 */
internal fun formatHoursMinutes(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    return if (minutes >= 60) {
        "${minutes / 60} 小时 ${minutes % 60} 分"
    } else {
        "$minutes 分"
    }
}
