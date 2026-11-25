package com.java.myapplication.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.java.myapplication.ui.theme.LocalEyeCareDark
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 柔光玻璃（v3.6，只用于底栏）。
 *
 * ## 为什么只剩底栏
 *
 * 顶栏的模糊已经按 HuaweiPods 的做法去掉了：TopAppBar 在布局内、内容不在它
 * 下面经过，而采样层一旦被滚动位移就会与坐标错位，出现"糊着上一帧"的横带。
 * 底栏不同：它浮在内容之上，录制层没有任何 transform，采样是实时的。
 *
 * ## 结构约束
 *
 * 玻璃面不能位于它采样用的 backdrop 录制子树内部，否则形成采样环。
 * 底栏在 Scaffold 的 bottomBar 槽里，内容在 content 槽里，天然是兄弟节点。
 */
private const val NAV_BLUR_RADIUS = 28f

/** 底栏玻璃：胶囊形状，混色保持系统 surface 的色相。 */
@Composable
fun Modifier.hyperNavGlass(
    backdrop: Backdrop,
    cornerRadius: Dp = 26.dp,
): Modifier {
    val dark = LocalEyeCareDark.current
    val surface = MiuixTheme.colorScheme.surface
    return this.textureBlur(
        backdrop = backdrop,
        shape = RoundedCornerShape(cornerRadius),
        blurRadius = NAV_BLUR_RADIUS,
        noiseCoefficient = BlurDefaults.NoiseCoefficient,
        colors = BlurDefaults.blurColors(
            blendColors = listOf(
                BlendColorEntry(surface.copy(alpha = if (dark) 0.74f else 0.68f)),
                BlendColorEntry(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.02f)),
            ),
            saturation = 1.08f,
        ),
    )
}

