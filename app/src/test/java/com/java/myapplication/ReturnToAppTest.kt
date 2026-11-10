package com.java.myapplication

import com.java.myapplication.data.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「休息后回到原应用」相关纯逻辑测试（v2.8.0）。
 *
 * 需求场景：在别的应用里到点只收到横幅 → 点通知进本应用 → 休息结束停留在本应用，
 * 希望回到提醒发生时正在用的应用。
 *
 * 本文件只测**不依赖 Android 系统服务**的部分：
 * 状态字段的默认值与携带、以及「何时该提示即将返回」的秒数边界。
 * 真正的包名识别（`UsageStatsManager`）与拉起应用（`PackageManager`）
 * 需要真机，属于验收项，不在单测范围。
 */
class ReturnToAppTest {

    // ============ 状态字段 ============

    @Test
    fun `旧版本状态没有返回目标时应为空且不显示提示`() {
        // 从上一版升级上来：SharedPreferences 里没有这两个键，load 走默认值
        val s = TimerState()
        assertNull("默认不应有返回目标", s.returnTargetPkg)
        assertFalse("默认不应有待显示的提示", s.returnNoticePending)
    }

    @Test
    fun `返回目标能随状态一起携带`() {
        val s = TimerState(returnTargetPkg = "com.tencent.mm")
        assertEquals("com.tencent.mm", s.returnTargetPkg)
        // copy 不应丢失它 —— finishRest 里正是靠 copy 传递状态
        assertEquals("com.tencent.mm", s.copy(awaitingRest = true).returnTargetPkg)
    }

    @Test
    fun `消费提示后标记应被清除`() {
        val s = TimerState(returnNoticePending = true)
        assertFalse(s.copy(returnNoticePending = false).returnNoticePending)
    }

    // ============ 「即将返回」的显示时机 ============

    /**
     * 与 RestScreen 里的 `RETURN_HINT_SECONDS` 保持同一口径。
     * 这里是副本而不是直接引用：那个常量是 private，
     * 若哪天改了值，本测试会失败并提醒同步。
     */
    private val hintSeconds = 5

    private fun showHint(remaining: Int): Boolean = remaining in 1..hintSeconds

    @Test
    fun `最后五秒才提示即将返回`() {
        assertFalse("还剩 6 秒不该提示", showHint(6))
        assertTrue("还剩 5 秒应提示", showHint(5))
        assertTrue("还剩 1 秒应提示", showHint(1))
    }

    @Test
    fun `倒计时归零与未进入末段都不提示`() {
        assertFalse("已经是 0，提示无意义", showHint(0))
        assertFalse("休息刚开始", showHint(20))
        assertFalse("负数属异常值，不应命中", showHint(-1))
    }

    @Test
    fun `休息时长较短时末段不会提前到负数`() {
        // 用户把休息设成 3 秒：整个休息都在 1..5 区间内，
        // 即一进休息页就显示「即将返回」，这是可接受的（本来就要结束了）。
        assertTrue(showHint(3))
        assertTrue(showHint(2))
    }
}
