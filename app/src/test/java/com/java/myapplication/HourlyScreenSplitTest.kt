package com.java.myapplication

import com.java.myapplication.data.ScreenTimeStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分时亮屏统计测试（v2.7.5 新增）。
 *
 * 背景：报告页「日」档位的分时柱状图按**两小时**分组，但每根柱子只统计到了
 * 一个小时的数据。实测 10–14 点几乎全程亮屏（中间吃饭 30 分钟），
 * 柱状图却只有 10 点档 53 分、12 点档 54 分，13 点整格丢失。
 *
 * 根因：原实现逐小时各查一次 `queryEvents`（24 次独立扫描）。
 * 连续亮屏跨过整点时，后一个小时区间内**没有任何事件**，函数返回 null，
 * 上层 `?: 0L` 记成 0。于是「一次连续亮屏只在包含唤醒事件的那个小时被计入」。
 *
 * 修法：全天只查一次事件流，解析出亮屏片段后按小时切分累加。
 * 本测试针对切分逻辑（纯算术），不依赖 Context / 系统 API。
 */
class HourlyScreenSplitTest {

    private val hour = 3600_000L
    // 用任意固定基准当「当日 0 点」，切分只关心相对偏移
    private val dayStart = 1_700_000_000_000L

    private fun split(vararg segs: Pair<Long, Long>): LongArray {
        val list = segs.map { longArrayOf(it.first, it.second) }
        val result = LongArray(24)
        ScreenTimeStore.splitSegmentsIntoHours(list, dayStart, result)
        return result
    }

    private fun h(hourIndex: Int) = dayStart + hourIndex * hour

    @Test
    fun `连续亮屏跨整点时后续小时不再丢失`() {
        // 10:06 亮屏 -> 13:00 息屏，中间跨了 11 点整和 12 点整
        val r = split(h(10) + 6 * 60_000L to h(13))
        // 10 点档：10:06 -> 11:00 = 54 分钟
        assertEquals(54 * 60_000L, r[10])
        // 11 点档：整小时
        assertEquals(hour, r[11])
        // 12 点档：整小时
        assertEquals(hour, r[12])
        // 13 点档没有
        assertEquals(0L, r[13])
    }

    @Test
    fun `跨多小时的超长亮屏每一格都满`() {
        // 09:00 -> 17:00 连续亮屏 8 小时
        val r = split(h(9) to h(17))
        for (i in 9 until 17) assertEquals("hour $i 应为满格", hour, r[i])
        assertEquals(0L, r[8])
        assertEquals(0L, r[17])
    }

    @Test
    fun `单小时内不足一小时的片段按实际长度累加`() {
        val r = split(h(12) + 10 * 60_000L to h(12) + 40 * 60_000L)
        assertEquals(30 * 60_000L, r[12])
    }

    @Test
    fun `同一小时内的多个片段累加而非覆盖`() {
        // 12 点亮 10 分钟、灭、再亮 20 分钟
        val r = split(
            h(12) to h(12) + 10 * 60_000L,
            h(12) + 30 * 60_000L to h(12) + 50 * 60_000L
        )
        assertEquals(30 * 60_000L, r[12])
    }

    @Test
    fun `跨零点的片段只落在当天 0 点这一格`() {
        // 片段起点早于 dayStart（前一天），应按 0 点格截断
        val r = split(dayStart - 30 * 60_000L to dayStart + 30 * 60_000L)
        // 负偏移被 coerceIn 夹到 0 格；终点落在 0 点后 30 分钟
        assertEquals(30 * 60_000L, r[0])
        assertEquals(0L, r[1])
    }

    @Test
    fun `空片段列表不产生任何时长`() {
        val result = LongArray(24)
        ScreenTimeStore.splitSegmentsIntoHours(emptyList(), dayStart, result)
        assertEquals(0L, result.sum())
    }

    @Test
    fun `两小时分组合并后应等于真实亮屏总时长`() {
        // 还原用户实测场景：10–14 点连续亮屏，中间 30 分钟吃饭（12:00–12:30 息屏）
        val r = split(
            h(10) to h(12),                 // 10:00-12:00
            h(12) + 30 * 60_000L to h(14)   // 12:30-14:00
        )
        // 分组后：10-12 点 = 2 小时；12-14 点 = 1.5 小时
        val bucket1 = r[10] + r[11]
        val bucket2 = r[12] + r[13]
        assertEquals(2 * hour, bucket1)
        assertEquals(90 * 60_000L, bucket2)
        // 总量必须等于片段总和，不能有丢失
        assertEquals(3 * hour + 30 * 60_000L, r.sum())
    }
}
