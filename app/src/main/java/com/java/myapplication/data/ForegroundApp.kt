package com.java.myapplication.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.java.myapplication.timer.TimerTrace

/**
 * 「休息结束后回到原应用」的来源识别（v2.8.0）。
 *
 * 需求场景：在别的应用里到点时只收到横幅通知 → 点通知进入护眼时光 → 休息结束
 * 后停留在护眼时光，用户希望回到提醒发生时正在用的那个应用。
 *
 * 为什么必须自己记：通知的 contentIntent 指向 MainActivity 自身
 * （`RestNotifier` 用 `PendingIntent.getActivity` + NEW_TASK|CLEAR_TOP），
 * 系统不会替我们保存「进来之前是哪个应用」。Activity 的 `referrer` 只在
 * 由别的应用显式启动时才有值，从通知点击进入时为空。
 *
 * 为什么能查：本项目已声明 `PACKAGE_USAGE_STATS` 并在 `ScreenTimeStore` 中
 * 用于读亮屏时长。查「最近切到前台的包名」走同一份数据，**不新增敏感权限**。
 * 未授权时返回 null，调用方按「未识别」处理。
 */
object ForegroundApp {

    /**
     * 查询 [at] 时刻附近最后一个切到前台的应用包名。
     *
     * 为什么往回看这么久：`MOVE_TO_FOREGROUND` / `ACTIVITY_RESUMED` 只在
     * **发生切换**时才有事件。如果用户在某个应用里连续用了半小时没切换过，
     * 提醒发生时最近的切前台事件可能已经是半小时前。窗口取 6 小时是为了覆盖
     * 「一整个下午都在同一个应用里」这种常见情况；再往前就没有参考意义了。
     *
     * @return 包名；未授权、无记录、或只查到应被排除的对象时返回 null
     */
    fun lastForegroundPackageAt(
        context: Context,
        at: Long = System.currentTimeMillis()
    ): String? {
        if (!ScreenTimeStore.hasPermission(context)) {
            TimerTrace.event("fg.lookup abort=noUsagePermission")
            return null
        }
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null

        val from = at - WINDOW_MS
        val events = manager.queryEvents(from, at)
        val event = UsageEvents.Event()

        // 取时间戳最大的那一条，而不是「最后一个读到的」——
        // queryEvents 按时间升序返回，两者通常一致；显式比较时间戳是为了
        // 在个别 ROM 返回乱序时不会取错。
        var bestPkg: String? = null
        var bestClass: String? = null
        var bestAt = Long.MIN_VALUE

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (!isForeground) continue
            if (event.timeStamp > at) continue
            val pkg = event.packageName ?: continue
            if (shouldIgnore(context, pkg)) continue
            if (event.timeStamp > bestAt) {
                bestAt = event.timeStamp
                bestPkg = pkg
                bestClass = event.className
            }
        }
        // v2.8.3：把查询结果写进诊断日志。跳转失败的原因分两种（查不到 / 查到但没跳），
        // 只看界面无法区分，这条日志能直接回答是哪一种。
        TimerTrace.event("fg.lookup result=$bestPkg cls=$bestClass at=$bestAt")
        // 顺手把组件名缓存起来：launch 时用它可以绕开包可见性限制
        if (bestPkg != null && bestClass != null) {
            saveComponentClass(context, bestPkg, bestClass)
        }
        return bestPkg
    }

    /**
     * 包名 → 最近一次前台 Activity 的类名（v2.8.6）。
     *
     * 为什么需要它：HyperOS 上 `<queries>` 的 intent 声明**没有**转换为包可见性授权
     * （`dumpsys package` 显示 `queries via package name:` 为空），
     * 因此 `resolveActivity` / `getLaunchIntentForPackage` 对第三方应用都返回 null，
     * 表现为 `ActivityNotFoundException`。
     *
     * `UsageEvents` 的 `ACTIVITY_RESUMED` 事件自带 `className`，用它 +
     * `setClassName()` 显式指定组件，**不经过「查询可见性」这一步**，可正常拉起。
     */
    fun cachedComponentClass(context: Context, pkg: String): String? =
        prefs(context).getString("cls_$pkg", null)

    private fun prefs(context: Context) =
        context.getSharedPreferences("fg_return_cache", Context.MODE_PRIVATE)

    private fun saveComponentClass(context: Context, pkg: String, cls: String) {
        // 持久化而非只放内存：休息常由闹钟在后台触发，那时进程可能已被回收，
        // 内存缓存会丢，又退回「解析不到」的老问题。
        prefs(context).edit().putString("cls_$pkg", cls).apply()
    }

    /**
     * 这些包名不能当作「返回目标」：
     * - 本应用自己：回到自己等于没返回，还会让「即将返回护眼时光」这种提示很荒唐；
     * - 桌面：用户从桌面点开应用，桌面确实是「上一个前台」，但把用户送回桌面
     *   不是他想要的（他想要的是继续刚才那个应用）；
     * - SystemUI / 输入法：它们是浮层来源，不算用户真正在用的应用。
     */
    private fun shouldIgnore(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return true
        if (pkg in ALWAYS_IGNORE) return true
        return pkg in launcherPackages(context)
    }

    /** 当前默认桌面的包名集合（个别 ROM 有多个，全部排除） */
    private fun launcherPackages(context: Context): Set<String> {
        if (cachedLaunchers != null) return cachedLaunchers!!
        val result = mutableSetOf<String>()
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pm = context.packageManager
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            resolved.forEach { info -> info.activityInfo?.packageName?.let { result.add(it) } }
        }
        cachedLaunchers = result
        return result
    }

    /** PackageManager 查询有开销，桌面集合基本不变，进程内缓存一次即可 */
    private var cachedLaunchers: Set<String>? = null

    /** 把包名解析成界面上显示的应用名；解析不到时回退显示包名 */
    fun appLabel(context: Context, pkg: String): String {
        return runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    /**
     * 把 [pkg] 拉到前台。
     *
     * 用 `getLaunchIntentForPackage` 而不是自己拼 Intent：它返回的是该应用的
     * 启动入口（ACTION_MAIN + CATEGORY_LAUNCHER + FLAG_ACTIVITY_NEW_TASK）。
     * 系统对「任务已存在」的应用会直接把**原任务原样带到前台**（保留用户
     * 原来的界面与滚动位置），而不是重建 —— 这正是需求要的「继续刚才的事」。
     *
     * 已知退化：若该应用已被系统回收，只能重新启动，落点可能回到首页。
     * 这一点在决策记录 D9 中已明确接受。
     *
     * @return 是否成功发出启动请求（不代表目标应用一定显示了界面）
     */
    fun launch(context: Context, pkg: String): Boolean {
        // 四次失败的完整教训链（务必保留，避免以后又"简化"回去）：
        //
        // 1. `getLaunchIntentForPackage` → 全部 null（包可见性）；
        // 2. 加 `<queries><intent>` + 隐式 Intent → ActivityNotFoundException，
        //    HyperOS 未把 intent 声明转成可见性（`queries via package name:` 为空）；
        // 3. 用 UsageEvents 的 className 显式启动 → 抖音成功，B站 SecurityException，
        //    因为记录到的是**子页面**（detail.UnitedBizDetailsActivity），子页面不导出；
        // 4. 猜测常见入口类名 → 不可靠，`MainActivityV2` 这类命名无法穷举。
        //
        // 最终方案（v2.8.8）：声明 `QUERY_ALL_PACKAGES` 后，`queryIntentActivities`
        // 能拿到目标应用**真正声明的 LAUNCHER 入口组件**，精确、无需猜测。
        // 注：曾经尝试用「任务栈顶 Activity」启动来恢复用户原来那个页面（v2.8.9），
        // 已删除。原因：B站的视频详情页（UnitedBizDetailsActivity）是**动态组件**，
        // 不在静态声明的 Activity 列表里（`dumpsys package` 查不到），
        // 外部 setClassName 必然失败，只会白跑一次并可能触发系统安全提示。
        //
        // 结论：一个应用无法把另一个应用恢复到它内部的任意页面 —— 除非那个页面
        // 是公开导出的静态组件。本功能的能力边界止于「把应用拉回前台」，
        // 落点由目标应用自己决定（抖音恢复原界面，B站回首页）。决策记录 D12。
        val entry = resolveLauncherComponent(context, pkg)
        if (entry != null) {
            val explicit = Intent().apply {
                component = entry
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryLaunch(context, explicit, pkg, "resolved:${entry.className}")) {
                // 记录成功的那一个，下次直接命中（也让下次不必再查 PackageManager）
                saveComponentClass(context, pkg, entry.className)
                return true
            }
        } else {
            TimerTrace.event("fg.launch pkg=$pkg resolve=null（无 LAUNCHER 入口或无可见性）")
        }

        // 兜底：历史里最早出现的 Activity（入口最可能是它）
        val fallbackCls = entryClassFromHistory(context, pkg) ?: cachedComponentClass(context, pkg)
        if (fallbackCls != null) {
            val explicit = Intent().apply {
                component = ComponentName(pkg, fallbackCls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryLaunch(context, explicit, pkg, "history:$fallbackCls")) return true
        }
        return false
    }

    /**
     * 精确解析目标应用的 LAUNCHER 入口组件（v2.8.8）。
     *
     * 依赖 Manifest 里声明的 `QUERY_ALL_PACKAGES`；没有它，Android 11+ 的
     * 包可见性会让这里返回 null。优先取 `MAIN/LAUNCHER`，其次取
     * `MAIN`（部分应用只声明 MAIN 而无 LAUNCHER）。
     */
    private fun resolveLauncherComponent(context: Context, pkg: String): ComponentName? {
        val pm = context.packageManager
        val queries = listOf(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            },
            Intent(Intent.ACTION_MAIN).apply { setPackage(pkg) }
        )
        for (q in queries) {
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(q, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(q, 0)
            }
            val hit = resolved.firstOrNull { it.activityInfo?.packageName == pkg }
            if (hit != null) {
                val ai = hit.activityInfo
                return ComponentName(ai.packageName, ai.name)
            }
        }
        return null
    }

    /**
     * 从 UsageEvents 历史里找 [pkg] **最早**出现的 Activity 类名（v2.8.7）。
     *
     * 为什么「最早」比「最近」可靠：用户第一次使用某个应用，必然是从它的
     * 入口 Activity 进入；之后才可能进各种子页面。而「最近一次」往往正是
     * 用户被提醒打断时停留的那个**子页面**（实测 B 站记录到视频详情页，
     * 该页面不导出，外部启动抛 SecurityException）。
     *
     * 往回看 24 小时：入口页通常在一天内至少出现过一次。
     */
    private fun entryClassFromHistory(context: Context, pkg: String): String? {
        if (!ScreenTimeStore.hasPermission(context)) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        // UsageStats 的历史窗口有限，取最近 24 小时
        val events = manager.queryEvents(now - 24 * 60 * 60 * 1000L, now)
        val event = UsageEvents.Event()
        var earliestAt = Long.MAX_VALUE
        var earliestCls: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val fg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (!fg) continue
            if (event.packageName != pkg) continue
            val cls = event.className ?: continue
            if (event.timeStamp < earliestAt) {
                earliestAt = event.timeStamp
                earliestCls = cls
            }
        }
        return earliestCls
    }

    private fun tryLaunch(context: Context, intent: Intent, pkg: String, via: String): Boolean =
        runCatching {
            context.startActivity(intent)
            TimerTrace.event("fg.launch pkg=$pkg ok=true via=$via")
            true
        }.getOrElse { e ->
            TimerTrace.event("fg.launch pkg=$pkg ok=false via=$via err=${e.javaClass.simpleName}")
            false
        }

    /** 往回看的时间窗口：6 小时，覆盖「整个下午都在同一个应用里」 */
    private const val WINDOW_MS = 6 * 60 * 60 * 1000L

    /** 无论是否当前桌面，都不该作为返回目标的系统组件 */
    private val ALWAYS_IGNORE = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin"
    )
}
