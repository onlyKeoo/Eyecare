package com.java.myapplication.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** 主动播放的效果不受 Compose 动画时长控制，需要单独跟随系统设置。 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun isDisabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var disabled by remember(resolver) { mutableStateOf(isDisabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { disabled = isDisabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        disabled = isDisabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return disabled
}

/**
 * 全项目统一的动效词汇表。
 *
 * 之前各处直接写 `tween(...)`，用的是 Compose 默认的 `FastOutSlowInEasing`
 * —— 那是一条弱 ease-in-out，入场时起手偏慢，正是 Emil Kowalski 说的
 * 「ease-in delays the moment the user watches most」。这里集中定义两条强曲线，
 * 所有入场/退场都从这里取，避免每个文件各写一套。
 */

/** 强 ease-out：入场 / 退场用。起手快、落点稳，UI 动效的默认曲线。 */
val EaseOutStrong: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** 强 ease-in-out：同屏位移 / 形变用（元素已经在屏幕上，只是换位置）。 */
val EaseInOutStrong: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
