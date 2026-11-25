package com.java.myapplication.ui.pages

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.java.myapplication.data.ScreenTimeStore
import com.java.myapplication.data.StatsStore
import com.java.myapplication.ui.EyeCareViewModel
import com.java.myapplication.ui.design.HyperEmptyState
import com.java.myapplication.ui.design.HyperSection
import com.java.myapplication.ui.design.HyperSpace
import com.java.myapplication.ui.design.RootPage
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 记录页（v3.0 重做）。
 *
 * ## 与上一版的差别
 *
 * 上一版是「三个 FilterChip + 一堆卡片」。这一版改用 miuix 的 [TabRow]：
 * 日 / 周 / 月 是**同一份数据的不同时间范围**，属于切换视角而不是筛选条件，
 * 用分段 Tab 比用筛选片更准确（筛选片的语义是"叠加条件"）。
 *
 * ## 数据口径一个字都没动
 *
 * - 亮屏时长：系统「使用情况访问」（[ScreenTimeStore]），未授权时为 null；
 * - 休息次数 / 休息时长：App 自统计（[StatsStore]），这条链路没有虚高问题；
 * - 日档位画**分时**（两小时一根柱），周/月档位画**逐日**。
 *
 * 为什么日档不画逐日：只有一天数据时，一根柱子无论多宽都不成立，
 * 而且它和上面的今日小结完全重复。分时柱状图能回答"今天什么时候最费眼"，
 * 这是逐日图在日档下答不出来的。
 */
@Composable
fun RecordsPage(viewModel: EyeCareViewModel, context: Context) {
    var rangeIndex by remember { mutableIntStateOf(1) }
    val ranges = listOf(1, 7, 30)
    val days = ranges[rangeIndex]

    var minuteTick by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    val statsKey = minuteTick to viewModel.todayCount
    var hasUsageAccess by remember { mutableStateOf(ScreenTimeStore.hasPermission(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            minuteTick = System.currentTimeMillis() / 60_000L
        }
    }

    val screenOnSeconds = remember(statsKey, hasUsageAccess) {
        if (hasUsageAccess) ScreenTimeStore.todayScreenOnSeconds(context) else null
    }
    val stats = remember(days, statsKey) { StatsStore.dailySeries(context, days) }
    val screenOnDaily = remember(days, statsKey, hasUsageAccess) {
        if (hasUsageAccess) ScreenTimeStore.dailyScreenOnSeconds(context, days)
        else List(days) { null }
    }
    val hourly = remember(statsKey, hasUsageAccess, minuteTick) {
        if (hasUsageAccess) ScreenTimeStore.todayHourlyScreenOnSeconds(context) else null
    }

    // 分时：24 小时两两合并成 12 根，柱宽才够点中（24 根时单柱约 5dp）
    val hourlyData = remember(hourly) {
        val src = hourly ?: List(24) { 0L }
        src.chunked(2).mapIndexed { bucket, pair ->
            val startHour = bucket * 2
            val endHour = minOf(startHour + 2, 24)
            "${startHour}–${endHour}" to (pair.sum() / 60).toInt()
        }
    }

    val labels = remember(stats) { stats.map { shortDayLabel(it.dayKey) } }
    val workData = remember(labels, screenOnDaily) {
        labels.mapIndexed { i, l -> l to ((screenOnDaily.getOrNull(i)?.toInt() ?: 0) / 60) }
    }
    val restData = remember(stats, labels) {
        stats.mapIndexed { i, s -> labels[i] to (s.restSeconds / 60) }
    }

    RootPage(title = "用眼记录") {
        Spacer(Modifier.height(HyperSpace.Tight))

        TabRow(
            tabs = listOf("日", "周", "月"),
            selectedTabIndex = rangeIndex,
            onTabSelected = { rangeIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(HyperSpace.Section))

        if (screenOnSeconds == null) {
            HyperEmptyState(
                title = "还没有亮屏数据",
                description = "授权「使用情况访问」后，这里显示系统统计的亮屏时长。",
            ) {
                TextButton(
                    text = "去授权",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                ScreenTimeStore.permissionIntent()
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        hasUsageAccess = ScreenTimeStore.hasPermission(context)
                    },
                    cornerRadius = 20.dp,
                    minHeight = 44.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        } else {
            HyperSection(title = "今天") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    SummaryTile(
                        value = formatHoursMinutes(screenOnSeconds.toInt()),
                        label = "亮屏",
                        modifier = Modifier.weight(1.4f),
                    )
                    SummaryTile(
                        value = "${viewModel.todayCount}",
                        label = "休息次数",
                        modifier = Modifier.weight(1f),
                    )
                    SummaryTile(
                        value = "${viewModel.todaySeconds / 60}",
                        label = "休息分钟",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(HyperSpace.Section))

        if (days == 1) {
            HyperSection(title = "今日分时亮屏") {
                BarChart(
                    data = hourlyData,
                    barColor = MiuixTheme.colorScheme.primary,
                    unit = "分钟",
                    axisMode = AxisMode.HOURS,
                    highlightLast = false,
                )
            }
        } else {
            HyperSection(title = "每日亮屏") {
                BarChart(
                    data = workData,
                    barColor = MiuixTheme.colorScheme.primary,
                    unit = "分钟",
                    axisMode = AxisMode.DAYS,
                    highlightLast = true,
                )
            }
            Spacer(Modifier.height(HyperSpace.Section))
            HyperSection(title = "每日休息") {
                BarChart(
                    data = restData,
                    // secondary 在 miuix 里是 #E6E6E6（浅灰底），画柱子几乎看不见。
                    // 用主色的浅版：两张图同色系、靠深浅区分，白底/黑底都读得出。
                    barColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.45f),
                    unit = "分钟",
                    axisMode = AxisMode.DAYS,
                    highlightLast = true,
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private enum class AxisMode { HOURS, DAYS }

/**
 * 单序列柱状图：读数 + 柱体 + 稀疏横轴。
 *
 * 交互只有一件事：点柱子看那一格是多少。没有长按、没有图例、没有提示气泡 ——
 * 图例（"亮屏(分钟)"）在标题里已经说过一次，再画一遍是同一句话说两遍。
 *
 * 横轴标签做**抽稀**：30 天档位如果 30 个标签全画，字会挤成一条灰带，
 * 所以只保留首、中、末三个刻度，其余留空 —— 柱子的位置本身已经表达了顺序。
 */
@Composable
private fun BarChart(
    data: List<Pair<String, Int>>,
    barColor: Color,
    unit: String,
    axisMode: AxisMode,
    highlightLast: Boolean,
) {
    var selected by remember(data) { mutableIntStateOf(-1) }
    val maxValue = remember(data) { maxOf(1, data.maxOfOrNull { it.second } ?: 0) }
    val selectedValue = data.getOrNull(selected)?.second
    val selectedLabel = data.getOrNull(selected)?.first

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        // 读数行：默认显示峰值那一格，点柱子后跟着选中的走。
        // 用 AnimatedContent 交叉淡入，避免数字瞬间跳变成"闪"。
        val peakIndex = remember(data) {
            data.indices.maxByOrNull { data[it].second } ?: -1
        }
        val readIndex = if (selected >= 0) selected else peakIndex
        val readValue = data.getOrNull(readIndex)?.second ?: 0
        val readLabel = data.getOrNull(readIndex)?.first ?: ""
        AnimatedContent(
            targetState = readIndex to readValue,
            transitionSpec = {
                fadeIn(tween(140)) togetherWith fadeOut(tween(90))
            },
            label = "chart-readout",
        ) { (_, value) ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (data.isEmpty()) "0" else "$value",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (data.isEmpty()) "" else readLabel + if (axisMode == AxisMode.HOURS) "点" else "",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        // 选中态文案：告诉用户点柱子能读数（否则没人会去点）
        if (selectedValue != null && selectedLabel != null) {
            Text(
                text = "已选中 $selectedLabel",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else if (data.isNotEmpty()) {
            Text(
                text = "点柱子查看每格读数",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        Spacer(Modifier.height(10.dp))

        val trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        val lastIndex = data.lastIndex
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        if (data.isNotEmpty()) {
                            val slot = size.width.toFloat() / data.size
                            val idx = (offset.x / slot).toInt().coerceIn(0, lastIndex)
                            selected = if (selected == idx) -1 else idx
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                if (data.isEmpty()) return@Canvas
                val slot = size.width / data.size
                val barWidth = (slot * 0.52f).coerceAtLeast(3f)
                val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
                data.forEachIndexed { i, (_, v) ->
                    val cx = slot * i + slot / 2f
                    // 轨道：始终画满高度的淡底，柱子才有"容器"可参考
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(cx - barWidth / 2f, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = radius,
                    )
                    val h = size.height * (v.toFloat() / maxValue).coerceIn(0f, 1f)
                    if (h > 0f) {
                        val isActive = i == selected || (selected < 0 && highlightLast && i == lastIndex)
                        val color = if (selected < 0 && !isActive) barColor.copy(alpha = 0.55f) else barColor
                        drawRoundRect(
                            color = if (isActive) color else color.copy(alpha = 0.75f),
                            topLeft = Offset(cx - barWidth / 2f, size.height - h),
                            size = Size(barWidth, h),
                            cornerRadius = radius,
                        )
                    } else if (i == selected) {
                        drawRoundRect(
                            color = dim,
                            topLeft = Offset(cx - barWidth / 2f, size.height - 2f),
                            size = Size(barWidth, 2f),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 稀疏横轴
        Row(modifier = Modifier.fillMaxWidth()) {
            val keep = axisKeeps(data.size, axisMode)
            data.indices.forEach { i ->
                Text(
                    text = if (i in keep) data[i].first else "",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 抽稀规则：12 槽每 3 格一个标签；7 天每格都标；30 天只标 4 个刻度。 */
private fun axisKeeps(count: Int, mode: AxisMode): Set<Int> = when {
    count == 0 -> emptySet()
    mode == AxisMode.HOURS -> ((0 until count step 3).toSet() + (count - 1))
    count <= 7 -> (0 until count).toSet()
    else -> setOf(0, count / 3, count * 2 / 3, count - 1)
}

/** 日期标签：只留「M/d」，30 天档位下也不会被压坏。 */
private fun shortDayLabel(dayKey: String): String {
    val parts = dayKey.split("-")
    if (parts.size != 3) return dayKey
    val month = parts[1].toIntOrNull() ?: return dayKey
    val day = parts[2].toIntOrNull() ?: return dayKey
    return "$month/$day"
}
