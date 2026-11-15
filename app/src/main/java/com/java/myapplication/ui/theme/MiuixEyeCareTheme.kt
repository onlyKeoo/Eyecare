package com.java.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * miuix 配色（v2.10.0 / ui-miuix 分支）。
 *
 * ## 为什么不用 miuix 默认的蓝色
 *
 * miuix 默认主色是 `#3482FF`（MIUI 蓝）。本项目是护眼工具，
 * 主色一直是原野绿 `#4CAF7D` —— 换成蓝色会丢掉「护眼」这个语义，
 * 而且绿→蓝是冷暖反转，用户第一眼会觉得「这不是同一个应用」。
 *
 * 所以这里做的是「**保留品牌色，套用 miuix 的形态**」：
 * 圆角、间距、控件尺寸、动效全部交给 miuix，颜色仍是我们自己的绿。
 *
 * ## 字段对应关系
 *
 * miuix 的 Colors 有 50 多个字段，但真正影响观感的是这几组：
 * - `primary` / `onPrimary` —— 主按钮、开关打开态、滑块
 * - `background` / `surface` / `surfaceContainer` —— 页面底 / 卡片 / 悬浮层
 * - `onBackground` / `onSurfaceVariantSummary` —— 主文字 / 次要说明
 * - `outline` / `dividerLine` —— 描边 / 分割线
 *
 * 其余字段（disabled*、slider*、tertiary*）用 miuix 默认值推导，
 * 因为它们的取值逻辑是「主色的淡化版」，手写反而容易配错。
 */
private fun eyeCareLightColors() = lightColorScheme(
    // 主色：原野绿。开关打开、主按钮、进度环都用它
    primary = GrassGreen,
    onPrimary = Color.White,
    primaryVariant = GrassGreenDark,
    onPrimaryVariant = Color(0xFFC9E6D6),
    primaryContainer = GrassGreenLight,
    onPrimaryContainer = Color.White,
    disabledPrimary = Color(0xFFC5DDD0),
    disabledOnPrimary = Color(0xFFF2F8F4),
    disabledPrimaryButton = Color(0xFFC5DDD0),
    disabledOnPrimaryButton = Color.White,

    // 次要色：晨雾蓝。用于次级按钮、辅助强调
    secondary = Color(0xFFF0F0F0),
    onSecondary = Color(0xFF303030),
    secondaryVariant = Color(0xFFF5F5F5),
    onSecondaryVariant = Color(0xFF303030),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF9AA69E),

    // 第三级：极淡的绿，用于「已选中」的背景块
    tertiaryContainer = Color(0xFFE8F3EC),
    onTertiaryContainer = GrassGreenDark,

    // 页面底与卡片
    background = SoftIvory,
    onBackground = InkGray,
    onBackgroundVariant = InkGraySecondary,
    surface = SoftIvory,
    onSurface = InkGray,
    surfaceVariant = SoftIvoryCard,
    surfaceContainer = SoftIvoryCard,
    onSurfaceContainer = InkGray,
    onSurfaceContainerVariant = InkGrayHint,

    // 文字层级：主 / 次要 / 弱提示
    onSurfaceSecondary = Color(0xCC2C3E33),
    onSurfaceVariantSummary = Color(0x992C3E33),
    onSurfaceVariantActions = Color(0x662C3E33),
    disabledOnSurface = InkGrayHint,

    // 描边与分割线：DESIGN.md 要求「卡片边界能看出来」，
    // 这里给 outline 比 Material 版（alpha 0.30）稍实一点，
    // 因为 miuix 卡片本身没有阴影，全靠这圈描边浮起来。
    outline = Color(0xFFDCD6CD),
    dividerLine = Color(0xFFE8E3DB),

    // 滑块与遮罩
    sliderKeyPoint = Color(0x4D4CAF7D),
    sliderKeyPointForeground = GrassGreen,
    windowDimming = Color.Black.copy(alpha = 0.3f),
)

private fun eyeCareDarkColors() = darkColorScheme(
    primary = NightGreen,
    onPrimary = NightBackground,
    primaryVariant = Color(0xFF4E8A69),
    onPrimaryVariant = Color(0xFF2A3D34),
    primaryContainer = Color(0xFF3A5247),
    onPrimaryContainer = NightText,
    disabledPrimary = Color(0xFF3A4A42),
    disabledOnPrimary = Color(0xFF6F7C74),
    disabledPrimaryButton = Color(0xFF3A4A42),
    disabledOnPrimaryButton = Color(0xFF8A968E),

    secondary = Color(0xFF2C3A33),
    onSecondary = NightText,
    secondaryVariant = Color(0xFF324038),
    onSecondaryVariant = NightText,
    secondaryContainer = Color(0xFF2C3A33),
    onSecondaryContainer = NightTextSecondary,

    tertiaryContainer = Color(0xFF2A3B32),
    onTertiaryContainer = NightGreen,

    background = NightBackground,
    onBackground = NightText,
    onBackgroundVariant = NightTextSecondary,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightCard,
    surfaceContainer = NightCard,
    onSurfaceContainer = NightText,
    onSurfaceContainerVariant = NightTextHint,

    onSurfaceSecondary = Color(0xCCD9E3DC),
    onSurfaceVariantSummary = Color(0x99D9E3DC),
    onSurfaceVariantActions = Color(0x66D9E3DC),
    disabledOnSurface = NightTextHint,

    // 夜间描边：比日间更淡，否则深色底上会显得生硬
    outline = Color(0xFF3A4A42),
    dividerLine = Color(0xFF33423A),

    sliderKeyPoint = Color(0x4D5E9E7B),
    sliderKeyPointForeground = NightGreen,
    windowDimming = Color.Black.copy(alpha = 0.5f),
)

/**
 * 护眼主题（miuix 版）。
 *
 * 与 Material3 版（[MyApplicationTheme]）并存：miuix 的组件读
 * `MiuixTheme.colorScheme`，Material3 的组件读 `MaterialTheme.colorScheme`，
 * 两套 CompositionLocal 互不干扰。改造期间页面里会同时出现两种组件，
 * 所以**两个主题都要包**，否则未改造的部分会掉回默认配色。
 *
 * 等全部页面改完、Material3 依赖彻底移除后，这里可以只留 MiuixTheme。
 *
 * @param darkTheme 是否使用深色配色
 * @param autoNight 是否允许随系统深色状态自动切换。
 *        为 false 时强制使用浅色配色（设置页「自动夜间模式」关闭时的行为）。
 */
@Composable
fun EyeCareMiuixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    autoNight: Boolean = true,
    content: @Composable () -> Unit
) {
    val useDark = autoNight && darkTheme
    val miuixColors = if (useDark) eyeCareDarkColors() else eyeCareLightColors()

    MiuixTheme(colors = miuixColors) {
        // Material3 那一套继续保留：改造未完成的页面还在用它。
        MyApplicationTheme(darkTheme = darkTheme, autoNight = autoNight) {
            content()
        }
    }
}
