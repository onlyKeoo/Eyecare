package com.java.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.java.myapplication.notify.RestNotifier
import com.java.myapplication.ui.EyeCareApp
import com.java.myapplication.ui.EyeCareViewModel
import com.java.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // 与 Compose viewModel() 共享同一 Activity 作用域实例
    private val viewModel: EyeCareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 通知「开始休息」按钮：由系统代发 Activity 启动（getActivity PendingIntent），
        // 携带 ACTION_REST_NOW 进入即表示用户已确认开始休息。
        handleRestNow(intent)
        // v2.8.1：点通知本体进来时也记一次 —— 用户常先点横幅打开应用，
        // 再在确认弹窗里点「开始休息」。此时系统前台已切到本应用，
        // 但 UsageStats 里「切进来之前那个应用」仍在，记录依然准确。
        //
        // 用显式 extra 判断而不是 `action == null`：从桌面图标正常打开时
        // action 同样是 null，那样会把无关的旧应用记成返回目标。
        if (intent?.getBooleanExtra(RestNotifier.EXTRA_FROM_REMINDER, false) == true) {
            com.java.myapplication.timer.EyeTimer.recordReturnTarget(this)
        }
        setContent {
            // 自动夜间模式开关由 ViewModel 实时推送，拨动后无需重启应用即可生效
            val autoNight by viewModel.autoNightModeState
            // App 实际是否走深色：系统深色 **且** 开了自动夜间模式。
            // 状态栏/导航栏图标的明暗必须跟这个值走 —— 只跟系统的话，
            // 用户在系统深色下关掉自动夜间模式会得到「浅底白图标」看不见。
            val dark = autoNight && isSystemInDarkTheme()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT
                        )
                    }
                )
            }
            MyApplicationTheme(autoNight = autoNight) {
                EyeCareApp()
            }
        }
    }

    // Activity 已存在时从通知按钮再次进入（CLEAR_TOP + singleTop）走这里
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRestNow(intent)
        // v2.8.1：与 onCreate 同源，从通知横幅再次进入时也记录一次
        if (intent.getBooleanExtra(RestNotifier.EXTRA_FROM_REMINDER, false)) {
            com.java.myapplication.timer.EyeTimer.recordReturnTarget(this)
        }
    }

    // 点击通知「开始休息」按钮：切换计时状态到休息。
    // 界面侧由 onResume → onForeground → syncFromStore 读到 phaseResting=true 后渲染休息页。
    private fun handleRestNow(intent: Intent?) {
        if (intent?.action != RestNotifier.ACTION_REST_NOW) return
        // v2.8.1：先记录「点通知时在用的应用」，再进入休息 —— 顺序不能反：
        // beginRest 会把当前状态落盘，若之后再写目标，那次落盘就带不上它了。
        com.java.myapplication.timer.EyeTimer.recordReturnTarget(this)
        viewModel.startRest(this)
    }

    // 回到前台：自愈补齐后台期间流逝的时间 + 立即刷新读数 + 恢复界面刷新协程
    override fun onResume() {
        super.onResume()
        // 标记前台：到点时据此判断该走「App 内休息页」而非后台悬浮窗（v2.4.2）
        AppForeground.isForeground = true
        viewModel.onForeground(this)
    }

    // 退到后台：停掉界面刷新协程；计时由亮/灭屏广播与系统闹钟接管
    override fun onPause() {
        super.onPause()
        // 标记后台：到点将改用悬浮窗强制全屏（v2.4.2）
        AppForeground.isForeground = false
        viewModel.onBackground()
    }
}