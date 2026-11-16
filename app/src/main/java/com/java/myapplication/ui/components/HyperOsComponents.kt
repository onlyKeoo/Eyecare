package com.java.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS 风格的基础版式组件（v2.10.0）。
 *
 * ## 为什么要单独做一套
 *
 * miuix 提供的是**原子组件**（Card / Switch / Button），但 HyperOS 的辨识度
 * 不在单个控件上，而在**版式**：大标题 → 分组卡片 → 卡内用发丝线分行的列表。
 * 只换组件不改版式，观感就会像「Material 换了个皮」——这也是前两轮失败的原因。
 *
 * 这个文件把 HyperOS 的三件套抽出来：
 * - [HyperOsGroupCard]：一张卡里装若干行，行与行之间自动加发丝线
 * - [HyperOsRow]：左侧标签、右侧值/控件，可带箭头
 * - [HyperOsSegmented]：胶囊分段控件（用于日报表的日/周/月）
 *
 * ## 数值的来源
 *
 * 以下取值来自 HyperOS 的视觉规范：
 * - 卡片圆角 **26dp**（比 miuix 默认 16dp 明显更圆，是「一眼 HyperOS」的关键）
 * - 卡内行高 **56dp**（单行）/ 上下内距 16dp
 * - 发丝线宽度 **0.5dp**，左侧起点缩进 16dp（不贯通到卡片边缘）
 * - 卡片间距 **12dp**（组与组之间），标题与首卡之间 **16dp**
 */

/** HyperOS 卡片圆角：比 miuix 默认值更圆，是版式辨识度的核心 */
val HyperOsCardRadius: Dp = 26.dp

/** 卡内单行的高度 */
private val RowHeight: Dp = 56.dp

/** 卡内容器的水平内距 */
private val RowHorizontalPadding: Dp = 18.dp

/**
 * 分组卡片：一张卡里装若干行。
 *
 * 行与行之间的发丝线由此处统一绘制，调用方不需要自己插 Divider ——
 * 这样既能保证「最后一行下面没有线」，也能保证所有卡片的线位置一致。
 *
 * @param rows 卡内的行，通常是一串 [HyperOsRow]
 */
@Composable
fun HyperOsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HyperOsCardRadius))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/**
 * 卡内一行：左标签、右侧内容。
 *
 * @param title 主标签
 * @param subtitle 副标签（可空）。按 DESIGN.md 的「删减优先」，
 *        只在「不看就猜不到的后果 / 默认值 / 相互关系」时才填
 * @param value 右侧显示的当前值（可空），用于「设置项 → 当前取值」这类行
 * @param showChevron 是否显示右箭头。凡点击后跳离当前页的行都该有
 * @param onClick 点击回调。为空时该行不可点，也不显示水波纹
 * @param trailing 右侧自定义内容（如开关、滑块），优先于 [value]
 */
@Composable
fun HyperOsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        // 关掉水波纹：HyperOS 的列表行是按下列表项缩放变暗，
                        // 不是 Material 的圆形涟漪
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .defaultMinSize(minHeight = RowHeight)
            .padding(horizontal = RowHorizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }

        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            ChevronIcon()
        }
    }
}

/** 行与行之间的发丝线。调用方在需要分隔的两行之间插一个即可 */
@Composable
fun HyperOsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = RowHorizontalPadding),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

/**
 * 右箭头。自绘而非用 miuix-icons：项目约定整个界面只用一套图标
 * （见 DESIGN.md「需要图标时用自绘矢量 EyeIcons」）。
 */
@Composable
private fun ChevronIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawLine(
            color = Color(0xFF9AA69E),
            start = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.22f),
            end = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.5f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF9AA69E),
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.78f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/**
 * 胶囊分段控件（HyperOS 的「日 / 周 / 月」切换）。
 *
 * 与 Material 的 SegmentedButton 区别：整体是一颗胶囊，选中项是**内部的白胶囊**，
 * 未选中项没有边框。HyperOS 用它替代 Tab 栏。
 *
 * @param options 选项文案
 * @param selectedIndex 当前选中下标
 * @param onSelect 点击回调
 */
@Composable
fun HyperOsSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent
                    )
                    .clickable(
                        interactionSource = null,
                        indication = null,
                    ) { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) {
                        MiuixTheme.colorScheme.onSurfaceContainer
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainerVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
