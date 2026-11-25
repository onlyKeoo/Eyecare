package com.java.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme as materialDark
import androidx.compose.material3.lightColorScheme as materialLight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.java.myapplication.data.ThemeBridge
import com.java.myapplication.data.ThemeMode
import com.java.myapplication.data.ThemePalette
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 当前是否深色（v3.0）。
 *
 * 材质层（柔光底、玻璃卡面）需要知道这件事，但它不该自己去读
 * `isSystemInDarkTheme()`：App 的深浅色是「主题模式 + 系统深浅」共同决定的，
 * 直接读系统会在「强制浅色」时让材质和组件配色错位。
 */
internal val LocalEyeCareDark = staticCompositionLocalOf { false }

/**
 * 护眼主题（HyperOS / miuix，v3.4）。
 *
 * ## 为什么不再自定义配色
 *
 * 上一版维护了一套护眼绿 + 一套 Monet。用户确认要的是**截图里那种系统蓝**，
 * 也就是 miuix 的默认调色板（浅色 #3482FF / 深色 #277AF7），
 * 而不是"跟随主题种子色"——后者取到的是手机主题色，可能完全不是蓝。
 *
 * 所以现在直接用 `ThemeController` 的默认 `lightColorScheme()` / `darkColorScheme()`：
 * 颜色由库统一维护，系统设置页的观感、开关色、选中色自然一致。
 * 少维护两套色板，也少一个"取错源"的机会。
 *
 * ## 三档主题
 *
 * [ThemeMode] 直接映射到 miuix 的 System / Light / Dark。
 * 关掉自动夜间模式只能浅色、无法强制深色的老问题，随之解决。
 *
 * ## 主题桥
 *
 * 解析完成后再把调色板写进 [ThemeBridge]，给后台悬浮窗（非 Compose）用。
 * 用 `SideEffect` 而不是 `LaunchedEffect`：必须在本帧绘制前写入，
 * 否则悬浮窗极短的生命周期里可能读到上一套色。
 */
@Composable
fun EyeCareMiuixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = themeMode.isDark(isSystemInDarkTheme())

    val controller = remember(themeMode) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                ThemeMode.SYSTEM -> ColorSchemeMode.System
                ThemeMode.LIGHT -> ColorSchemeMode.Light
                ThemeMode.DARK -> ColorSchemeMode.Dark
            },
        )
    }

    CompositionLocalProvider(LocalEyeCareDark provides useDark) {
        MiuixTheme(controller = controller) {
            val colors = MiuixTheme.colorScheme
            val context = LocalContext.current
            SideEffect {
                ThemeBridge.publish(
                    context,
                    ThemePalette(
                        dark = useDark,
                        primary = colors.primary.toArgb(),
                        onPrimary = colors.onPrimary.toArgb(),
                        background = colors.background.toArgb(),
                        surface = colors.surface.toArgb(),
                        surfaceContainer = colors.surfaceContainer.toArgb(),
                        onBackground = colors.onBackground.toArgb(),
                        onSurfaceVariant = colors.onSurfaceVariantSummary.toArgb(),
                        error = colors.error.toArgb(),
                    ),
                )
            }
            MaterialTheme(
                colorScheme = if (useDark) colors.toMaterialDark() else colors.toMaterialLight(),
                content = content,
            )
        }
    }
}

/**
 * 把 miuix 的 Colors 映射成 Material3 的 ColorScheme。
 *
 * 只映射实际会被读到的字段。这里不写死任何品牌色：miuix 换默认色板时，
 * Material 这一层自动跟着换。
 */
private fun Colors.toMaterialLight() = materialLight(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiaryContainer,
    onTertiary = onTertiaryContainer,
    error = error,
    onError = onError,
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
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiaryContainer,
    onTertiary = onTertiaryContainer,
    error = error,
    onError = onError,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onBackground,
    surfaceVariant = surfaceContainer,
    onSurfaceVariant = onBackgroundVariant,
    outline = outline,
)
