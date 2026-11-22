package com.java.myapplication.ui.pages

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.ui.EyeCareViewModel
import com.java.myapplication.ui.design.HyperMotion
import com.java.myapplication.ui.design.HyperSpace
import com.java.myapplication.ui.theme.EaseOutStrong
import com.java.myapplication.ui.theme.rememberReducedMotion
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import androidx.compose.foundation.layout.PaddingValues
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 从倒数第几秒开始预告「即将返回」。休息 20 秒时占四分之一，够读完又不喧宾夺主。 */
private const val RETURN_HINT_SECONDS = 5

/**
 * 休息全屏页（v3.0 重做）。
 *
 * ## 为什么是整页而不是弹层
 *
 * 休息的目的就是「让眼睛离开屏幕」。用弹层会让人想划掉、会遮一半屏幕继续看底下内容；
 * 整页 + 大字 + 留白才构成"停下来"的场景。
 *
 * ## 视觉
 *
 * 上一版是圆环 + 秒数 + 知识卡三块。这一版只留**一个巨大的秒数**：
 * 休息时用户只需要知道"还剩几秒"，圆环和卡片都在抢注意力。
 * 进度用一条极细的横线表示，不参与竞争。
 *
 * ## 动效
 *
 * 秒数**不做**每秒动画（一次休息 20–300 次，属于高频读数）；
 * 只有「进入最后 5 秒」这条提示做淡入，以及按钮的按压反馈。
 * 完成时不做闪光、不做缩放脉冲 —— 结束的反馈由整页淡出承担。
 */
@Composable
fun RestPage(viewModel: EyeCareViewModel, context: Context) {
    val remaining = viewModel.restRemaining
    // 「再休息 30 秒」会让剩余时间超过初始总时长。跨度必须跟着抬高，
    // 否则进度分子会变成负数、进度条直接归零（看起来像重来了）。
    var span by remember { mutableIntStateOf(viewModel.settings.restSeconds.coerceAtLeast(1)) }
    LaunchedEffect(remaining) {
        if (remaining > span) span = remaining
    }
    val ratio = ((span - remaining).toFloat() / span).coerceIn(0f, 1f)
    val progress by animateFloatAsState(ratio, HyperMotion.Progress, label = "rest-progress")

    val aboutToReturn = remaining in 1..RETURN_HINT_SECONDS
    val returnLabel = viewModel.returnTargetLabel

    // 休息页是"从当前任务被拽进另一个场景"，硬切会像闪了一下。
    // 入场只做 0.98 → 1 的缩放 + 淡入（260ms）；结束离开时不反向播放，
    // 因为那时用户已经准备好回到原来的事，拖一段退场反而是阻碍。
    val reduceMotion = rememberReducedMotion()
    val enter = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            enter.animateTo(1f, androidx.compose.animation.core.tween(260, easing = EaseOutStrong))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .graphicsLayer {
                alpha = enter.value
                val scale = 0.98f + 0.02f * enter.value
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Text(
                text = "让眼睛休息",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(HyperSpace.Section))

            // 主读数：剩余秒数。等宽数字，避免 10→9 时整块宽度跳变。
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$remaining",
                    style = MiuixTheme.textStyles.headline1.copy(
                        fontSize = 96.sp,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Light,
                    ),
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(40.sp, 96.sp),
                )
                Text(
                    text = "秒",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (aboutToReturn && returnLabel != null) "即将返回 $returnLabel" else "看向 6 米外，放松睫状肌",
                style = MiuixTheme.textStyles.footnote1,
                color = if (aboutToReturn && returnLabel != null) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(HyperSpace.Section))

            // 细进度线：4dp，走完即结束。用 miuix 的进度条，不手写轨道。
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp),
                height = 4.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(),
            )

            Spacer(Modifier.height(56.dp))

            if (viewModel.currentTip.title.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp),
                    cornerRadius = CardDefaults.CornerRadius,
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "护眼知识",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(HyperSpace.Tight))
                    Text(
                        text = viewModel.currentTip.title,
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = viewModel.currentTip.content,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(Modifier.height(HyperSpace.Section))

            // 窄屏或大字号时改为上下排列：并排时每个按钮只有约 150dp，
            // 字号一放大「再休息 30 秒」就会被压成两行甚至溢出。
            val stackButtons = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
            ) {
                val stacked = stackButtons || maxWidth < 320.dp
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            text = "再休息 30 秒",
                            onClick = { viewModel.extendRest(context, 30) },
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            minHeight = 46.dp,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        Button(
                            onClick = { viewModel.skipRest(context) },
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            minHeight = 46.dp,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("我已休息好", style = MiuixTheme.textStyles.button)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            text = "再休息 30 秒",
                            onClick = { viewModel.extendRest(context, 30) },
                            modifier = Modifier.weight(1f),
                            cornerRadius = 20.dp,
                            minHeight = 46.dp,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        Button(
                            onClick = { viewModel.skipRest(context) },
                            modifier = Modifier.weight(1f),
                            cornerRadius = 20.dp,
                            minHeight = 46.dp,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("我已休息好", style = MiuixTheme.textStyles.button)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
