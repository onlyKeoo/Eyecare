package com.java.myapplication.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * HyperOS 4 / HyperLight 版式的取值表（v3.0 整体重做）。
 *
 * ## 为什么要有这个文件
 *
 * 上一版把 34sp 标题、26dp 圆角、24dp 页边距直接写在每个页面里，结果是
 * 「设置页 24dp、计时页 24dp、二级页 20dp」这种到处差 4dp 的状态 ——
 * 单看每页都还行，连起来翻页时左右基准线会轻微跳动。
 *
 * 这次改成：**页面结构只允许从这张表取值**。数值不多，但每一项都对应
 * HyperOS 的一个层级，改一处就整页一致。
 *
 * ## 为什么只有三档间距
 *
 * 间距的职责是表达「谁和谁是一伙的」。档位一多，就变成凭手感微调，
 * 最后没有任何两处相同。这里只保留：
 * - [Row]：同一张卡片里两行的间距；
 * - [Section]：两张卡片之间（一个分组结束）；
 * - [Page]：页面左右留白，也是所有卡片的左右基准线。
 */
object HyperSpace {
    /**
     * 页面左右留白；所有卡片、标题、按钮都对齐这条线。
     *
     * v3.2：16 → 14。参考项目是 12dp，但我们的行里有长句说明，
     * 12dp 会让换行更碎；14dp 是"更紧凑但不挤"的折中。
     */
    val Page = 14.dp

    /** 分组之间（两张卡片之间） */
    val Section = 22.dp

    /** 卡片内一行的上下内距 */
    val Row = 13.dp

    /** 紧凑间隔：标题与其说明、数值与其单位 */
    val Tight = 5.dp

    /** 页面底部留白（二级页） */
    val Bottom = 28.dp

    /** 一级页底部留白：内容要能从悬浮底栏底下滚过，因此留一条栏高 */
    val BottomWithNav = 112.dp
}

/**
 * 尺寸。
 *
 * [Touch] 是**触控区**下限，不是视觉尺寸：HyperOS 的图标按钮看着 40dp，
 * 实际可点区是 48dp。视觉小的控件靠 `minimumInteractiveComponentSize`
 * 或外层 Box 撑开，不能直接把触控区做成 40dp。
 */

/**
 * 动效词汇表（v3.0）。
 *
 * 全 App 只用两条曲线 + 两条弹簧，时长由各组件自带的规范值决定（如 NavDisplay 的 500ms）。上一版的问题是「每处动画各调一个 spring」，同一个 App 里
 * 出现了 0.82 / 0.9 / StiffnessMediumLow 三种阻尼，翻页和弹层的手感不一致。
 *
 * 规则：
 * - **系统发起的落位**（导航切换、页面推入）用 [Page]，临界阻尼、不回弹；
 * - **跟手的动量**（手势松手后的回弹）用 [Momentum]，允许轻微回弹；
 * - 时间文字、进度数字这类**高频读数**不做位移动画，只做颜色过渡。
 */
object HyperMotion {
    /** 页面推入 / 底部导航落位：不回弹 */
    val Page = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 手势松手后的归位：带一点回弹，因为动量是用户给的 */
    val Momentum = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 按压反馈：快进快出，不能有惯性 */
    val Press = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** 进度条推进：略慢于交互，避免每秒都在"跳" */
    val Progress = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
}
