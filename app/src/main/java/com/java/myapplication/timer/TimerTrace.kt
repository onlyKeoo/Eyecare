package com.java.myapplication.timer

import android.content.Context
import android.util.Log
import com.java.myapplication.data.TimerState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首次亮屏误触发诊断日志。
 *
 * 两路输出：
 * 1. Android logcat（标签 EyeCareDiag），供开发者用 adb 抓取；
 * 2. 应用私有目录下的文本文件，供**不接电脑的用户**在应用内直接分享。
 *
 * 只追加记录，不读写任何计时状态，因此不会影响提醒行为。
 * 文件超过 512KB 自动轮换，避免长期运行无限增长。
 */
internal object TimerTrace {
    const val TAG = "EyeCareDiag"
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun event(message: String) {
        val line = "${format.format(Date())} $message"
        Log.d(TAG, line)
        appendToFile(line)
    }

    private fun appendToFile(line: String) {
        // 诊断文件仅保存在应用私有目录，不申请存储权限，也不改变业务数据。
        runCatching {
            val file = FileHolder.file ?: return
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > 512 * 1024L) {
                file.writeText("# EyeCare diagnostic log rotated\n")
            }
            file.appendText(line + "\n")
        }
    }

    fun exportText(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText()
        else "暂无诊断日志。请先经历一次亮屏/息屏，再导出。"
    }

    /** 日志文件当前行数（无文件时为 0），供界面显示「有没有记录到东西」 */
    fun lineCount(context: Context): Int {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return 0
        return runCatching { file.readLines().size }.getOrDefault(0)
    }

    /**
     * 清空诊断日志。
     *
     * 只删这个诊断文件本身 —— 它由本应用独立创建，与计时状态、设置、
     * 统计数据分属不同文件，删除不影响任何业务行为。
     * 返回是否确实删掉了内容（无文件也算成功，避免界面报错）。
     */
    fun clear(context: Context): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    private object FileHolder {
        var file: File? = null
    }

    fun init(context: Context) {
        FileHolder.file = File(context.filesDir, FILE_NAME)
    }

    private const val FILE_NAME = "eyecare-diagnostic.log"

    fun state(prefix: String, state: TimerState) {
        event(
            "$prefix state=" +
                "enabled=${state.reminderEnabled},resting=${state.phaseResting}," +
                "awaiting=${state.awaitingRest},accum=${state.workAccumMs}," +
                "onAt=${state.screenOnAt},offAt=${state.offAt}," +
                "overdue=${state.overdueCounting},overdueRecorded=${state.overdueRecordedMs}," +
                "dayStart=${state.accumStartWallMs},gen=${state.accumGeneration}/" +
                "${state.recordedGeneration}"
        )
    }

    fun alarm(action: String, triggerAt: Long? = null, detail: String = "") {
        event("alarm action=$action triggerAt=$triggerAt now=${System.currentTimeMillis()} $detail")
    }
}
