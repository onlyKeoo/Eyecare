package com.java.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme as materialDark
import androidx.compose.material3.lightColorScheme as materialLight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 护眼主题（HyperOS / miuix）。
 *
 * ## 为什么用 ThemeController 而不是直接传 colors
 *
 * miuix 官方文档推荐用 `ThemeController(ColorSchemeMode.System)`：它会自己跟随
 * 系统的深浅色切换，并把「当前是深色还是浅色」这件事统一管理。
 * 直接传 `colors =` 的那个重载不会跟随系统，需要我们手工判断 `isSystemInDarkTheme()`，
 * 还得自己处理系统切换时的重组 —— 那是重复实现库已经做好的事。
 *
 * 本项目的「自动夜间模式」开关有两种状态：
 * - 开启 → 跟随系统（`isDark = null`）
 * - 关闭 → 强制浅色（`isDark = false`）
 * 正好对应 controller 的 `isDark` 参数，不需要额外逻辑。
 *
 * ## 为什么保留 Material3 主题
 *
 * 改造是分阶段的：设置页的数字滚轮、对话框等仍是 Material3 组件。
 * 如果只提供 miuix 主题，那些组件会掉回 Material 默认的紫色系，整页配色会裂开。
 *
 * 所以这里把 miuix 的配色**镜像**成一套 Material3 colorScheme，
 * 让两种组件读到的 primary / surface / onBackground 是同一批颜色。
 * 等全部组件换成 miuix 后，这段可以整体删掉。
 */
private fun eyeCareLightColors(): Colors = lightColorScheme(
    // 主色：原野绿。开关、主按钮、进度环都用它
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

    secondary = Color(0xFFF0F0F0),
    onSecondary = Color(0xFF303030),
    secondaryVariant = Color(0xFFF5F5F5),
    onSecondaryVariant = Color(0xFF303030),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF9AA69E),

    tertiaryContainer = Color(0xFFE8F3EC),
    onTertiaryContainer = GrassGreenDark,

    background = SoftIvory,
    onBackground = InkGray,
    onBackgroundVariant = InkGraySecondary,
    surface = SoftIvory,
    onSurface = InkGray,
    surfaceVariant = SoftIvoryCard,
    surfaceContainer = SoftIvoryCard,
    onSurfaceContainer = InkGray,
    onSurfaceContainerVariant = InkGrayHint,

    onSurfaceSecondary = Color(0xCC2C3E33),
    onSurfaceVariantSummary = Color(0x992C3E33),
    onSurfaceVariantActions = Color(0x662C3E33),
    disabledOnSurface = InkGrayHint,

    // 描边与分割线：miuix 的卡片没有阴影，层次全靠底色差 + 一圈极淡的线
    outline = Color(0xFFDCD6CD),
    dividerLine = Color(0xFFE8E3DB),

    sliderKeyPoint = Color(0x4D4CAF7D),
    sliderKeyPointForeground = GrassGreen,
    windowDimming = Color.Black.copy(alpha = 0.3f),
)

private fun eyeCareDarkColors(): Colors = darkColorScheme(
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

    outline = Color(0xFF3A4A42),
    dividerLine = Color(0xFF33423A),

    sliderKeyPoint = Color(0x4D5E9E7B),
    sliderKeyPointForeground = NightGreen,
    windowDimming = Color.Black.copy(alpha = 0.5f),
)

/**
 * 把 miuix 的 Colors 映射成 Material3 的 ColorScheme。
 *
 * 只映射实际会被读到的字段：primary / surface / background / outline 这几组。
 * 其余留 Material 默认值 —— 它们只在 Material 组件的次要部位出现
 * （如 ripple），不影响整体观感。
 */
private fun Colors.toMaterialLight() = materialLight(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = MistBlue,
    onSecondary = InkGray,
    secondaryContainer = MistBlueLight,
    onSecondaryContainer = InkGray,
    tertiary = SunOrange,
    onTertiary = SoftIvoryCard,
    error = CoralRed,
    onError = Color.White,
    background = background,
    onBackground = onBackground,
    surface = background,
    onSurface = onBackground,
    surfaceVariant = surfaceContainer,
    onSurfaceVariant = onBackgroundVariant,
    outline = outline,
)

private fun Colors.toMaterialDark() = materialDark(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = NightBlue,
    onSecondary = NightBackground,
    secondaryContainer = NightCard,
    onSecondaryContainer = NightText,
    tertiary = NightOrange,
    onTertiary = NightBackground,
    error = NightRed,
    onError = NightBackground,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onBackground,
    surfaceVariant = surfaceContainer,
    onSurfaceVariant = onBackgroundVariant,
    outline = outline,
)

/**
 * 护眼主题。
 *
 * @param autoNight 设置页的「自动夜间模式」。
 *        开启时跟随系统；关闭时强制浅色（这正是 [ThemeController.isDark] 的语义）。
 *        注意：不需要再传 `darkTheme`，是否深色由 controller 自己算。
 */
@Composable
fun EyeCareMiuixTheme(
    autoNight: Boolean = true,
    content: @Composable () -> Unit
) {
    val light = remember { eyeCareLightColors() }
    val dark = remember { eyeCareDarkColors() }

    val controller = remember(autoNight) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.System,
            lightColors = light,
            darkColors = dark,
            // null = 跟随系统；false = 强制浅色
            isDark = if (autoNight) null else false,
        )
    }

    // 当前生效的是深色还是浅色 —— Material3 那一层要跟着同一个判断，
    // 否则会出现「miuix 是深色、Material 组件还是浅色」的错位。
    val useDark = if (autoNight) isSystemInDarkTheme() else false

    MiuixTheme(controller = controller) {
        MaterialTheme(
            colorScheme = if (useDark) dark.toMaterialDark() else light.toMaterialLight(),
            content = content,
        )
    }
}
