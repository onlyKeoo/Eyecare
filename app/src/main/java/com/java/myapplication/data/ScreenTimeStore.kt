package com.java.myapplication.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * 系统亮屏时长（v2.6.12）。
 *
 * 应用自己统计的「用眼时长」依赖亮/灭屏广播与本进程存活，息屏边界、进程被回收、
 * 悬浮窗是否算在内这些环节都会带进一点偏差。这里直接向系统要
 * SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE 事件，算出来的就是
 * **设备真正亮屏了多久**，作为对照值展示在统计页。
 *
 * 注意语义区别：亮屏时长 >= 用眼时长。用户亮着屏没在用眼、看别的应用、
 * 甚至把手机撂在桌面上，都会被算进亮屏时长。
 *
 * v2.6.16：App 自统计的用眼时长因「息屏广播丢失后 screenOnAt 陈旧」屡修不净
 * （实测系统亮屏 3 分钟 / 自统计 33 分钟），已**整体废止**。统计页展示的
 * 「今日用眼」与报表的日/周/月全部改用本模块的系统亮屏时长。
 * 界面上明确标注口径为「屏幕亮着的时间」，不再暗示它是精确的用眼时长。
 *
 * 需要用户在系统设置里授予「使用情况访问权限」，未授权时相关函数返回 null，
 * 调用方展示占位并提供一键跳转授权。
 */
object ScreenTimeStore {

    /** 「使用情况访问」的系统设置页 */
    fun permissionIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * 是否已授予「使用情况访问权限」。
     *
     * 这是 AppOps 而非运行时权限，没有 checkSelfPermission 可用，只能查 AppOps 的模式位。
     */
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 今日亮屏总时长（秒）。未授权、或系统没给任何事件时返回 null。
     *
     * 事件成对出现（SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE），逐对累加；
     * 结尾若只有「亮」没有「灭」，说明此刻正亮着，补到当前时刻。
     */
    fun todayScreenOnSeconds(
        context: Context,
        now: Long = System.currentTimeMillis()
    ): Long? = screenOnSecondsBetween(context, dayStartMillis(now), now)

    /**
     * 任意区间内的亮屏总时长（秒），用于报表的日/周/月。未授权或无事件返回 null。
     *
     * v2.6.16 新增。区间语义为 [from, to)。事件在区间外开始（跨区间）时，
     * 会把起点夹到 from，避免把区间外的时间算进来。
     */
    fun screenOnSecondsBetween(context: Context, from: Long, to: Long): Long? {
        if (to <= from) return 0L
        if (!hasPermission(context)) return null
        val segments = screenOnSegments(context, from, to) ?: return null
        // 无事件 = 系统这段没记录到亮/灭变化。返回 null 让上层按"无数据"处理，
        // 不要用 0 冒充"确认没亮屏"。
        if (segments.isEmpty()) return null
        var totalMs = 0L
        for (seg in segments) totalMs += (seg[1] - seg[0])
        return totalMs / 1000L
    }

    /**
     * 把 [from, to) 内的亮屏区间解析成若干 `[startMs, endMs]` 片段。
     *
     * v2.7.5：这是本次修复的核心。原实现逐区间独立扫描事件，
     * **只要区间内没有事件就返回 null**，于是「连续亮屏跨过整点」时，
     * 后一个小时查不到任何事件，被上层 `?: 0L` 记成 0 ——
     * 实测 12:06 亮屏到 14:00，12 点档拿到 54 分钟，13 点档整格丢失。
     *
     * 正确做法是在**一个连续区间**内维护亮/灭状态机，并处理两种边界：
     * - 首个事件是「灭」：说明区间开始时屏幕**已经亮着**（系统不会补发"亮"事件），
     *   起点回填为 from；
     * - 首个事件是「亮」：说明区间开始前是灭的，正常起算。
     *
     * 拿到片段后，逐小时累加就只是纯算术，不再受事件稀疏影响。
     *
     * @return 片段列表；无权限返回 null；区间内无任何事件返回空列表
     */
    private fun screenOnSegments(context: Context, from: Long, to: Long): List<LongArray>? {
        if (to <= from) return emptyList()
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null

        val events = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        val segments = ArrayList<LongArray>()

        var screenOnAt = -1L
        var sawFirst = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    // 首个事件是「亮」→ 区间开始前是灭的
                    sawFirst = true
                    if (screenOnAt < 0L) {
                        screenOnAt = event.timeStamp.coerceIn(from, to)
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // 首个事件是「灭」→ 区间开始时屏幕已经亮着，起点回填 from
                    if (!sawFirst) {
                        sawFirst = true
                        if (screenOnAt < 0L) screenOnAt = from
                    }
                    if (screenOnAt >= 0L) {
                        val end = event.timeStamp.coerceIn(from, to)
                        if (end > screenOnAt) segments.add(longArrayOf(screenOnAt, end))
                        screenOnAt = -1L
                    }
                }
            }
        }

        // 结尾仍亮着（或查询时正亮）：补到区间终点
        if (screenOnAt >= 0L && to > screenOnAt) {
            segments.add(longArrayOf(screenOnAt, to))
        }
        return segments
    }

    /**
     * 逐日亮屏时长（秒），供报表折线/柱状图使用（v2.6.16 新增）。
     *
     * @param days 需要统计的天数（含今天），从「今天 - (days-1) 天」的 0 点开始
     * @return 与 [days] 等长的列表，索引 0 是最早的一天；某天无数据或未授权时为 null
     */
    fun dailyScreenOnSeconds(
        context: Context,
        days: Int,
        now: Long = System.currentTimeMillis()
    ): List<Long?> {
        if (days <= 0) return emptyList()
        if (!hasPermission(context)) return List(days) { null }
        val result = ArrayList<Long?>(days)
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStartMillis(now)
        // 回退到最早那天的 0 点
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        for (i in 0 until days) {
            val from = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val to = if (i == days - 1) now else cal.timeInMillis
            result.add(screenOnSecondsBetween(context, from, to))
        }
        return result
    }

    /**
     * 今日**分时**亮屏时长（秒），长度固定 24，索引 = 小时（0..23）。
     *
     * v2.7.3 新增，用于报告页「日」档位的分时柱状图。
     *
     * 为什么需要它：日档位原先直接隐藏了柱状图，理由是「只有一天数据，一根柱子
     * 没有横向比较意义」——这个理由本身成立，但结论下错了：单日缺的不是柱图，
     * 而是**可比较的横轴**。把横轴从"天"换成"小时"，24 根柱子天然可比，
     * 还能顺便回答「一天里什么时候最费眼」，这是日档位原本完全没说的事。
     *
     * 未授权时返回 null（由调用方显示空态，不拿全 0 的假数据充数）。
     */
    fun todayHourlyScreenOnSeconds(
        context: Context,
        now: Long = System.currentTimeMillis()
    ): List<Long>? {
        if (!hasPermission(context)) return null
        val dayStart = dayStartMillis(now)
        val result = LongArray(24)

        // v2.7.5：全天**只查一次**事件流，再按小时切分。
        //
        // 原实现逐小时各查一次（24 次 queryEvents），每次都是独立的事件扫描。
        // 连续亮屏跨过整点时，后一个小时区间内没有任何事件 → 返回 null → 记 0，
        // 于是「一次连续亮屏只在包含唤醒事件的那个小时被计入」。
        // 实测症状：10–14 点几乎全程亮屏，柱状图却只有 10 点档 53 分、12 点档 54 分，
        // 且这两格之和（107 分）远小于实际亮屏时长。
        //
        // 改为一次性拿到 [dayStart, now) 的全部亮屏片段，再逐片段按小时累加。
        // 切分是纯算术，与事件稀疏程度无关，跨整点不再丢时长。
        val segments = screenOnSegments(context, dayStart, now) ?: return null
        splitSegmentsIntoHours(segments, dayStart, result)
        return result.map { it / 1000L }
    }

    /**
     * 把亮屏片段按小时累加进 [result]（毫秒）。
     *
     * 抽成独立函数是为了可单测：这段「按小时切分」是 v2.7.5 修复的核心，
     * 之前 24 次独立查询的写法正是在这里把跨整点的连续亮屏丢掉了。
     * 纯算术，不依赖 Context / 系统 API。
     *
     * @param segments 每项为 `[startMs, endMs]`，须已按时间升序
     * @param dayStart 当日 0 点时间戳
     * @param result 长度 24 的累加容器，索引 = 小时
     */
    internal fun splitSegmentsIntoHours(
        segments: List<LongArray>,
        dayStart: Long,
        result: LongArray
    ) {
        val dayEnd = dayStart + 24 * 3600_000L
        for (seg in segments) {
            // v2.7.5：先把片段夹进当天范围再切分。
            // 若起点早于 dayStart，`(cursor - dayStart) / hour` 的整数除法会向零取整
            // 得到 0（而不是 -1），于是 0 点这一格会把**前一天**的时长也算进去。
            // 实际调用中事件查询有边界、不会越界，但这是纯算术函数的固有陷阱，必须夹住。
            val segStart = seg[0].coerceIn(dayStart, dayEnd)
            val segEnd = seg[1].coerceIn(dayStart, dayEnd)
            if (segEnd <= segStart) continue
            var cursor = segStart
            while (cursor < segEnd) {
                val hourIndex = (((cursor - dayStart) / 3600_000L).toInt()).coerceIn(0, 23)
                val hourEnd = dayStart + (hourIndex + 1) * 3600_000L
                val sliceEnd = minOf(segEnd, hourEnd)
                if (sliceEnd > cursor) {
                    result[hourIndex] += (sliceEnd - cursor)
                    cursor = sliceEnd
                } else {
                    // 防御：理论不可达，避免极端时区/时钟跳变造成死循环
                    break
                }
            }
        }
    }

    /** 当日 0 点的时间戳 */
    private fun dayStartMillis(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
