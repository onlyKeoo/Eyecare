package com.java.myapplication.data

/**
 * 主题模式（v3.4）。
 *
 * 之前只有一个「自动夜间模式」开关：开 = 跟随系统，关 = 强制浅色。
 * 这样**永远无法强制深色** —— 系统白天、用户想夜里提前变深，没有入口。
 * 现在改成三档，和系统设置页的「主题」一致。
 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    /** 解析当前是否深色；[systemDark] 只在「跟随系统」时有意义 */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}
