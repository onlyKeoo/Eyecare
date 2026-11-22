package com.java.myapplication.ui.pages

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.java.myapplication.R
import com.java.myapplication.data.AppInfo
import com.java.myapplication.ui.DisclaimerDialog
import com.java.myapplication.ui.design.HyperDivider
import com.java.myapplication.ui.design.HyperSection
import com.java.myapplication.ui.design.HyperSpace
import com.java.myapplication.ui.theme.LocalEyeCareDark
import com.java.myapplication.ui.theme.rememberReducedMotion
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页（v3.1 重做，参考 HyperIsland 的 AboutPage 结构）。
 *
 * ## 版式
 *
 * 上一版是「一张卡里塞版本号 + 仓库地址 + 两个按钮」。参考项目给出的是
 * **居中头图 → 分组列表** 的结构：头图承担品牌与版本，下面全是可点的行。
 * 这比在卡里摆两个按钮更像系统设置页，也更好扫读。
 *
 * ## 动效
 *
 * 头图背后是一层缓慢旋转的柔光渐变（12 秒一圈）——它是**背景**，
 * 不承载信息，所以幅度很小（色相几乎不变，只移动高光位置）。
 * 系统关闭动画时直接取静止的一帧，不做任何循环。
 *
 * 注意：这里没有用 miuix 的 RuntimeShader 背景（HyperIsland 的做法），
 * 因为那是全屏 AGSL 着色器，在低端机上代价明显；一块 88dp 的图标底
 * 用普通 Brush 已经够看，没必要为装饰性头图引入每帧着色器。
 */
@Composable
fun AboutPage(context: Context, onOpenLicense: () -> Unit) {
    val versionName = remember(context) { AppInfo.versionName(context) }
    var showDisclaimer by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(HyperSpace.Tight))
        AboutHero(versionName = versionName)
        Spacer(Modifier.height(HyperSpace.Section))

        HyperSection(title = "项目") {
            ArrowPreference(
                title = "打开仓库",
                summary = AppInfo.REPO_URL_SHORT,
                onClick = { openUrl(context, AppInfo.REPO_URL) },
            )
            HyperDivider(startInset = 16.dp)
            ArrowPreference(
                title = "免责声明",
                summary = "使用前请阅读",
                onClick = { showDisclaimer = true },
            )
        }

        Spacer(Modifier.height(HyperSpace.Section))

        HyperSection(title = "版本") {
            BasicComponent(
                title = "当前版本",
                summary = "v$versionName",
            )
            HyperDivider(startInset = 16.dp)
            // 许可证与第三方声明：APK 里带着全文，这里给入口
            ArrowPreference(
                title = "参考与致谢",
                summary = "开源组件、设计参考与许可证全文",
                onClick = onOpenLicense,
            )
        }
    }

    if (showDisclaimer) {
        DisclaimerDialog(onDismiss = { showDisclaimer = false })
    }
}

/**
 * 头图：88dp 方形图标 + 名称 + 版本。
 *
 * 图标底用 squircle 玻璃面，前景用应用自己的眼睛矢量图 —— 不使用位图，
 * 深色模式下不会出现"白色图标的方块底"。
 */
@Composable
private fun AboutHero(versionName: String) {
    val reduceMotion = rememberReducedMotion()
    val dark = LocalEyeCareDark.current
    val base = MiuixTheme.colorScheme.primary
    val accent = MiuixTheme.colorScheme.secondary

    val phase by if (reduceMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.25f) }
    } else {
        rememberInfiniteTransition(label = "about-aurora").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "about-phase",
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.size(88.dp),
            cornerRadius = CardDefaults.CornerRadius,
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                // 高光沿对角线缓慢移动：色相不变，只挪位置。
                val t = phase
                val brush = Brush.linearGradient(
                    colors = listOf(
                        base.copy(alpha = if (dark) 0.30f else 0.22f),
                        accent.copy(alpha = if (dark) 0.18f else 0.14f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * (0.1f + 0.3f * t), 0f),
                    end = Offset(size.width, size.height * (0.9f - 0.2f * t)),
                )
                drawRect(brush)
            }
                Icon(
                    painter = painterResource(R.drawable.ic_eye_care),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "护眼时光",
            style = MiuixTheme.textStyles.title2.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "v$versionName",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
