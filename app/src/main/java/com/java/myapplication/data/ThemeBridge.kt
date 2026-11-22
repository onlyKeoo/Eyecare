package com.java.myapplication.data

import android.content.Context

/**
 * 主题桥（v3.2）。
 *
 * ## 为什么需要它
 *
 * 后台悬浮窗（[com.java.myapplication.notify.RestOverlayService]）不是 Compose，
 * 拿不到 `MiuixTheme.colorScheme`；但它和 App 内休息页必须长得一模一样，
 * 否则用户会在"App 里是绿色、切到别的应用弹出来是另一套色"之间反复横跳。
 *
 * 做法：Compose 侧在主题解析完成后，把当前生效的调色板写进一份极小的
 * SharedPreferences；Service 读同一份值。**只写不读**在 Compose 侧，
 * **只读不写**在 Service 侧，避免两处各自判断系统主题、结果不一致。
 *
 * ## 为什么不直接存"是否 Monet"
 *
 * Service 不需要知道颜色从哪来，它只需要"当前该用什么色"。
 * 把策略留在 Compose 侧，Service 只消费结果，将来加主题（比如纯黑 OLED）
 * 也不用再改 Service。
 */
data class ThemePalette(
    val dark: Boolean,
    val primary: Int,
    val onPrimary: Int,
    val background: Int,
    val surface: Int,
    val surfaceContainer: Int,
    val onBackground: Int,
    val onSurfaceVariant: Int,
    val error: Int,
) {
    companion object {
        /** 主题还没发布过时的兜底：与 miuix 默认（蓝）调色板一致 */
        val FallbackLight = ThemePalette(
            dark = false,
            primary = 0xFF3482FF.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            background = 0xFFFFFFFF.toInt(),
            surface = 0xFFF7F7F7.toInt(),
            surfaceContainer = 0xFFFFFFFF.toInt(),
            onBackground = 0xFF000000.toInt(),
            onSurfaceVariant = 0x99000000.toInt(),
            error = 0xFFE94634.toInt(),
        )

        val FallbackDark = FallbackLight.copy(
            dark = true,
            primary = 0xFF277AF7.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            background = 0xFF242424.toInt(),
            surface = 0xFF000000.toInt(),
            surfaceContainer = 0xFF2B2B2B.toInt(),
            onBackground = 0xE6FFFFFF.toInt(),
            onSurfaceVariant = 0x80FFFFFF.toInt(),
            error = 0xFFF12522.toInt(),
        )
    }
}

object ThemeBridge {
    private const val PREFS = "eye_care_theme_bridge"
    private const val KEY_SIG = "signature"

    /** 上次写入的签名，避免每次重组都落一次盘（apply 也要走一遍 QueuedWork） */
    @Volatile
    private var lastSignature: Int = 0

    fun publish(context: Context, palette: ThemePalette) {
        val signature = palette.hashCode()
        if (signature == lastSignature) return
        lastSignature = signature
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SIG, signature)
            .putBoolean("dark", palette.dark)
            .putInt("primary", palette.primary)
            .putInt("on_primary", palette.onPrimary)
            .putInt("background", palette.background)
            .putInt("surface", palette.surface)
            .putInt("surface_container", palette.surfaceContainer)
            .putInt("on_background", palette.onBackground)
            .putInt("on_surface_variant", palette.onSurfaceVariant)
            .putInt("error", palette.error)
            .apply()
    }

    /**
     * 读取当前调色板；从未发布过（首帧、服务抢先启动）时返回 null，
     * 调用方按深浅选择 [ThemePalette.FallbackLight] / [ThemePalette.FallbackDark]。
     */
    fun read(context: Context): ThemePalette? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_SIG)) return null
        return ThemePalette(
            dark = p.getBoolean("dark", false),
            primary = p.getInt("primary", ThemePalette.FallbackLight.primary),
            onPrimary = p.getInt("on_primary", ThemePalette.FallbackLight.onPrimary),
            background = p.getInt("background", ThemePalette.FallbackLight.background),
            surface = p.getInt("surface", ThemePalette.FallbackLight.surface),
            surfaceContainer = p.getInt("surface_container", ThemePalette.FallbackLight.surfaceContainer),
            onBackground = p.getInt("on_background", ThemePalette.FallbackLight.onBackground),
            onSurfaceVariant = p.getInt("on_surface_variant", ThemePalette.FallbackLight.onSurfaceVariant),
            error = p.getInt("error", ThemePalette.FallbackLight.error),
        )
    }
}
