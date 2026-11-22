package com.java.myapplication.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.java.myapplication.R
import com.java.myapplication.data.EyeTips
import com.java.myapplication.data.SettingsStore

/**
 * 到点后强制全屏的「后台覆盖」服务（v2.4.2 新增）。
 *
 * 为什么要自绘 Overlay，而不继续用 `setFullScreenIntent`：
 * Android 15（API 35）起，系统收紧了全屏通知——在**亮屏解锁**状态下，
 * 全屏通知不再自动拉起全屏界面，只会退化成普通横幅（锁屏时才可能全屏）。
 * 这意味着「亮屏时待在别的 App 里，到点强行盖一层全屏休息页」用通知已经做不到。
 * 唯一可靠的路径是自绘悬浮窗：
 *
 *   AlarmScheduler 到点 → 本服务 → [SYSTEM_ALERT_WINDOW] 权限直接叠一层全屏 View
 *
 * 设计约束（与产品约定一致）：
 * - **只在不息屏时弹**：屏幕没亮时谈不上护眼，弹了也看不见，反而打扰（Q4/Q5 结论）；
 * - **倒计时结束自动消失**（Q9=a）；
 * - **可返回键 / 点关闭按钮手动结束**，不强制锁死（Q8=b）；
 * - **前台时不用它**：若主界面在前台，直接走 App 内休息页，避免叠两层（Q10）。
 *
 * 这不是前台优先级的常驻服务：只在休息倒计时期间短暂存活，倒计时一结束立即 
 * stopSelf，不常驻、不耗电。
 */
class RestOverlayService : Service() {

    companion object {
        const val EXTRA_REST_SECONDS = "extra_rest_seconds"
        const val EXTRA_TIP_INDEX = "extra_tip_index"

        private const val CHANNEL_ID = "eye_care_overlay_v1"
        private const val NOTIFICATION_ID = 1010

        /** 遮罩淡入时长：入口要「立刻盖上来」，所以短且走 ease-out */
        private const val FADE_IN_MS = 200L
        /** 自动到点收尾的淡出时长（用户主动收起走 dismissAndStop(fast = true)，140ms） */
        private const val FADE_OUT_MS = 220L

        /** 是否拥有悬浮窗权限（Android 6+ 需用户单独授权） */
        fun canDrawOverlay(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            return Settings.canDrawOverlays(context)
        }

        /** 请求悬浮窗权限的系统页 Intent；个别 ROM 无此页时回退到应用详情 */
        fun overlayPermissionIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )

        /** 亮屏时才启动覆盖服务（熄屏/锁屏直接跳过，符合「屏没亮不用休息」的约定） */
        fun startIfScreenOn(context: Context, restSeconds: Int, tipIndex: Int) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return
            if (!canDrawOverlay(context)) return
            val intent = Intent(context, RestOverlayService::class.java).apply {
                putExtra(EXTRA_REST_SECONDS, restSeconds)
                putExtra(EXTRA_TIP_INDEX, tipIndex)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止覆盖（开始休息 / 稍后再说 / 手动结束时由状态机调用） */
        fun stop(context: Context) {
            context.stopService(Intent(context, RestOverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var remainingSeconds = 0
    private var totalSeconds = 0
    private var ringView: ProgressRingView? = null
    private var countdownText: TextView? = null
    private var dismissed = false

    /** 正在做淡出动画、等待移除的覆盖层（淡出结束由 [forceCleanup] 兜底移除） */
    private var fadingView: View? = null

    /** 淡出兜底：动画因任何原因没跑完也要把覆盖层摘掉并停服务，避免留下无主悬浮窗 */
    private val forceCleanup: Runnable = Runnable {
        handler.removeCallbacks(forceCleanup)
        val view = fadingView
        fadingView = null
        if (view != null) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        stopForegroundCompat()
        stopSelf()
    }

    /** 是否按深色渲染覆盖层：与 App 内使用同一个 ThemeMode 判定，不再各算一套 */
    private fun isDarkUi(): Boolean {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return SettingsStore.load(this).themeMode.isDark(night)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (dismissed) return
            remainingSeconds--
            countdownText?.text = remainingSeconds.coerceAtLeast(0).toString()
            ringView?.let { it.progress = if (totalSeconds <= 0) 1f else 1f - remainingSeconds.toFloat() / totalSeconds; it.invalidate() }
            if (remainingSeconds <= 0) {
                // 倒计时结束自动收尾（Q9=a）：结束休息阶段并撤掉覆盖
                com.java.myapplication.timer.EyeTimer.finishRest(this@RestOverlayService)
                dismissAndStop()
            } else {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务：Android 12+ 后台启动 Service 必须尽快挂前台通知，否则会被系统杀
        startForegroundCompat()
        if (overlayView != null) return START_NOT_STICKY
        if (!canDrawOverlay(this)) { dismissAndStop(); return START_NOT_STICKY }

        val restSeconds = intent?.getIntExtra(EXTRA_REST_SECONDS, 0) ?: 0
        if (restSeconds <= 0) { dismissAndStop(); return START_NOT_STICKY }
        val tipIndex = intent?.getIntExtra(EXTRA_TIP_INDEX, 0) ?: 0

        remainingSeconds = restSeconds
        totalSeconds = restSeconds
        showOverlay(restSeconds, tipIndex)
        handler.postDelayed(tick, 1000L)
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "休息全屏覆盖", NotificationManager.IMPORTANCE_MIN)
                    .apply {
                        setShowBadge(false)
                        setSound(null, null)
                    }
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_eye_care)
                .setContentTitle("正在休息")
                .setContentText("远眺倒计时中…")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    else 0
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun showOverlay(restSeconds: Int, tipIndex: Int) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val ctx = this
        val root = FrameLayout(ctx)

        // 配色跟随深色模式：这块全屏遮罩以前硬编码浅色，深色模式下会「啪」地糊一脸白光，
        // 与护眼初衷正好相反。现在与 Compose 休息页（ui/RestScreen.kt）用同一套成对色值。
        // 配色以 Compose 侧发布的调色板为准（v3.2）。
        //
        // 这样「跟随系统取色」开启时，后台悬浮窗和 App 内休息页是同一套色；
        // 没发布过（服务抢在首帧前启动）才回落到固定的浅/深两套。
        val dark = isDarkUi()
        val palette = com.java.myapplication.data.ThemeBridge.read(this)
            ?: if (dark) com.java.myapplication.data.ThemePalette.FallbackDark
            else com.java.myapplication.data.ThemePalette.FallbackLight
        val titleColor = palette.onBackground
        val subtitleColor = palette.onSurfaceVariant
        val bodyColor = palette.onSurfaceVariant
        val accentColor = palette.primary
        val trackColor = withAlpha(palette.onBackground, if (dark) 0x33 else 0x1A)

        // 背景：纵向渐变。由调色板的 background / surfaceContainer 生成，
        // 不再写死绿色渐变 —— 否则系统取色开启后，这里会是整屏唯一的绿色。
        val bgColors = intArrayOf(
            palette.background,
            mixColor(palette.background, palette.surfaceContainer, 0.55f),
            palette.surface,
        )
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors)
        root.background = bg

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(40), dp(32), dp(40))
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 顶部 emoji + 标题
        column.addView(textView(ctx, "🌄", 48f, titleColor, bold = false))
        column.addView(spacer(ctx, 16))
        column.addView(textView(ctx, "请抬头，眺望远方", 28f, titleColor, bold = true))
        column.addView(spacer(ctx, 6))
        column.addView(textView(ctx, "让眼睛离开屏幕，看向 6 米外的远方", 15f, subtitleColor))
        column.addView(spacer(ctx, 32))

        // 倒计时环 + 中心大数字（与 App 内休息页的 EyeProgressRing 保持一致）
        val ringSize = dp(200)
        val ringBox = FrameLayout(ctx)
        val ring = ProgressRingView(ctx).apply {
            total = restSeconds
            progress = 0f
            strokeDp = 18f
            this.trackColor = trackColor
            this.darkUi = dark
        }
        ringView = ring
        ringBox.addView(
            ring,
            FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER)
        )
        val countdown = textView(ctx, restSeconds.toString(), 64f, titleColor, bold = true)
        countdownText = countdown
        val unit = textView(ctx, "秒", 14f, subtitleColor)
        val countdownColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(countdown)
            addView(unit)
        }
        ringBox.addView(
            countdownColumn,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        column.addView(ringBox)
        column.addView(spacer(ctx, 28))

        // 护眼知识卡
        val tip = EyeTips.byIndex(tipIndex)
        column.addView(textView(ctx, "${tip.emoji} ${tip.title}", 17f, titleColor, bold = true))
        column.addView(spacer(ctx, 6))
        column.addView(textView(ctx, tip.content, 15f, bodyColor))
        column.addView(spacer(ctx, 32))

        // 「我已休息好」按钮（Q8=b：允许提前结束）
        val closeBtn = textView(ctx, "我已休息好，继续工作", 16f, accentColor, bold = true).apply {
            setPadding(dp(24), dp(12), dp(24), dp(12))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(2), accentColor)
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener { onUserDismiss() }
        }
        column.addView(closeBtn)

        // 返回键 / 物理返回也视为「主动结束」（Q8=b，不锁死）
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_UP &&
                keyCode == android.view.KeyEvent.KEYCODE_BACK
            ) {
                onUserDismiss(); true
            } else false
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        params.gravity = Gravity.CENTER

        try {
            wm.addView(root, params)
            overlayView = root
            // 淡入：以前是「啪」地一下整屏盖上来，现在 200ms 渐显。
            // 必须显式用 DecelerateInterpolator：ViewPropertyAnimator 默认是
            // AccelerateDecelerate，起手慢，遮罩这种「立刻要盖上来」的入场会显得迟钝。
            root.alpha = 0f
            root.animate()
                .alpha(1f)
                .setDuration(FADE_IN_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
            root.requestFocus()
        } catch (_: Exception) {
            // 权限被回收 / ROM 拦截：安静退出，退回通知路径
            dismissAndStop()
        }
    }

    /** 用户主动结束：记录实际休息时长并撤掉覆盖 */
    private fun onUserDismiss() {
        if (dismissed) return
        com.java.myapplication.timer.EyeTimer.skipRest(this)
        dismissAndStop(fast = true)
    }

    /**
     * 收起覆盖层。
     *
     * [fast] = true 用于**用户主动**点「我已休息好」/ 返回键：用户已经做完决定了，
     * 系统要立刻回应，所以退场取 140ms（非对称节奏：慢在用户决策，快在系统回应）。
     * 自动倒计时结束仍走 220ms —— 那是系统自己发起的收尾，稍慢一点不显得突兀。
     */
    private fun dismissAndStop(fast: Boolean = false) {
        if (dismissed) return
        dismissed = true
        handler.removeCallbacks(tick)
        val view = overlayView
        overlayView = null
        if (view == null) {
            stopForegroundCompat()
            stopSelf()
            return
        }
        val fadeMs = if (fast) 140L else FADE_OUT_MS
        // 淡出后再移除；forceCleanup 兜底，动画被打断也不会留下无主悬浮窗
        fadingView = view
        handler.removeCallbacks(forceCleanup)
        handler.postDelayed(forceCleanup, fadeMs + 100L)
        view.animate()
            .alpha(0f)
            .setDuration(fadeMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction(forceCleanup)
            .start()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(forceCleanup)
        overlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        // 淡出途中被销毁（例如用户手动停服务）：兜底摘掉残留的那个 View，避免 WindowLeaked
        fadingView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        fadingView = null
        super.onDestroy()
    }

    // ---------- 小工具 ----------

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun textView(
        ctx: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun spacer(ctx: Context, heightDp: Int): View =
        View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }
}

/**
 * 后台全屏覆盖专用的圆环进度视图（v2.4.2）。
 *
 * 用原生 Canvas 复刻 App 内休息页的 [com.java.myapplication.ui.components.EyeProgressRing]：
 * 轨道 + 从 12 点顺时针推进的进度弧，弧色随进度 绿 → 橙 → 红。
 * 原生 View 不方便拿 Compose 的 Brush.sweepGradient，这里用单色近似同一视觉语义。
 */
class ProgressRingView(context: Context) : View(context) {

    /** 总秒数（用于把「剩余秒」换算成进度，仅作参考，进度由外部直接给） */
    var total: Int = 0

    /** 0f..1f：已休息进度 */
    var progress: Float = 0f

    /** 环粗（dp） */
    var strokeDp: Float = 18f

    /** 轨道颜色（ARGB int） */
    var trackColor: Int = 0x1A4CAF7D

    /**
     * 进度弧的扫掠渐变色标。
     *
     * v3.2 起由调色板生成：主色 → 警示色 → 主色。品牌绿模式下就是原来的
     * 绿→橙→红，Monet 模式下自动变成系统强调色→错误色，不再需要两套常量。
     */
    /** 服务侧算好的深浅，避免 View 自己去读 Configuration（两处判断会漂移） */
    var darkUi: Boolean = false

    private fun arcShadesFromPalette(): IntArray {
        val palette = com.java.myapplication.data.ThemeBridge.read(context)
            ?: if (darkUi) com.java.myapplication.data.ThemePalette.FallbackDark
            else com.java.myapplication.data.ThemePalette.FallbackLight
        val a = palette.primary
        val b = palette.error
        return intArrayOf(
            mixColor(a, b, 0.25f),
            b,
            mixColor(a, b, 0.50f),
            a,
            mixColor(a, b, 0.25f),
        )
    }

    private val density = resources.displayMetrics.density

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val stroke = strokeDp * density
        paint.strokeWidth = stroke
        val inset = stroke / 2f
        val rect = android.graphics.RectF(inset, inset, width - inset, height - inset)

        // 轨道
        paint.shader = null
        paint.color = trackColor
        canvas.drawArc(rect, -90f, 360f, false, paint)

        // 进度弧：单色硬分档（绿/橙/红三段）已改为与 Compose 端一致的连续扫掠渐变
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            paint.shader = android.graphics.SweepGradient(
                rect.centerX(),
                rect.centerY(),
                arcShadesFromPalette(),
                null,
            )
            canvas.drawArc(rect, -90f, 360f * p, false, paint)
            paint.shader = null
        }
    }
}

/** 两色线性混合；t 会被夹到 0..1。ARGB 各自插值，避免只混 RGB 把 alpha 弄丢。 */
private fun mixColor(from: Int, to: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    val a = ((from ushr 24) and 0xFF) + (((to ushr 24) and 0xFF) - ((from ushr 24) and 0xFF)) * f
    val r = ((from shr 16) and 0xFF) + (((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * f
    val g = ((from shr 8) and 0xFF) + (((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * f
    val b = (from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * f
    return ((a.toInt() and 0xFF) shl 24) or ((r.toInt() and 0xFF) shl 16) or
        ((g.toInt() and 0xFF) shl 8) or (b.toInt() and 0xFF)
}

/** 只替换 alpha，保留 RGB。 */
private fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
