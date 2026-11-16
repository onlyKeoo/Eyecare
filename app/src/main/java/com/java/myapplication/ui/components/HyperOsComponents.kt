package com.java.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS 版式组件（v2.11.0）。
 *
 * ## 与上一版的区别：不再自己造行
 *
 * 上一版我手写了 HyperOsRow（行高 56dp、内距 18dp、自绘箭头），
 * 那是凭观感猜的。查官方文档后发现 miuix 已经提供了完全对应的组件：
 *
 * - [BasicComponent] —— 官方文档称其为「基础标准组件」，
 *   提供标题、摘要、左右自定义区，明确用于「列表项、设置项」
 * - [ArrowPreference] —— 「基于 BasicComponent 的带箭头组件，指示可点击」
 *
 * 手写版最大的问题是**交互细节不对**：按下时的缩放、禁用态的透明度、
 * 无障碍语义角色、行高与字号的比例关系，miuix 都按 HyperOS 规范处理过，
 * 我自己写只能凑个大概。所以这里改成对官方组件的薄封装，
 * 保留项目需要的参数名（title / subtitle / value / showChevron），
 * 内部全部交给 miuix。
 *
 * 保留的唯一自绘是 [HyperOsSegmented] —— miuix 的 TabRow 是下划线样式，
 * 与 HyperOS 的胶囊分段不是一回事。
 */

/** HyperOS 卡片圆角：miuix Card 默认 16dp，HyperOS 实际更圆 */
val HyperOsCardRadius: Dp = 26.dp

/**
 * 分组卡片：一张卡里装若干行。
 *
 * 直接用 miuix 的 [Card]。`insideMargin` 给 0 —— 行自带内距，
 * 卡片再加一层会变成两倍留白。
 */
@Composable
fun HyperOsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = HyperOsCardRadius,
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(content = content)
    }
}

/**
 * 卡内一行。
 *
 * 两种情况：
 * - 需要右箭头（点进去跳到别的页面）→ 用官方 [ArrowPreference]
 * - 其余（右侧是开关、数值、按钮）→ 用官方 [BasicComponent]，右侧内容走 endActions
 *
 * @param title 主标签
 * @param subtitle 摘要。按 DESIGN.md「删减优先」，只在「不看就猜不到的后果 /
 *        默认值 / 相互关系」时才填
 * @param value 右对齐的当前值
 * @param showChevron 是否显示右箭头
 * @param onClick 点击回调
 * @param trailing 右侧自定义内容（开关等），优先于 [value]
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
    if (showChevron) {
        ArrowPreference(
            title = title,
            modifier = modifier,
            summary = subtitle,
            endActions = {
                if (value != null) {
                    Text(
                        text = value,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
                if (trailing != null) trailing()
            },
            onClick = onClick,
        )
    } else {
        BasicComponent(
            title = title,
            modifier = modifier,
            summary = subtitle,
            endActions = {
                if (trailing != null) {
                    trailing()
                } else if (value != null) {
                    Text(
                        text = value,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            },
            onClick = onClick,
        )
    }
}

/**
 * 行与行之间的发丝线。
 *
 * 用 Material3 的 HorizontalDivider 而非 miuix 的 Divider：
 * 前者能精确控制 0.5dp 与左侧缩进，后者不支持 inset。
 * 这条线是视觉细节，不涉及交互，所以不算「混用两套组件」。
 */
@Composable
fun HyperOsRowDivider(startInset: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startInset),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

/**
 * 胶囊分段控件（HyperOS 的「日 / 周 / 月」切换）。
 *
 * miuix 的 TabRow 是下划线样式，与 HyperOS 的「胶囊内嵌白色滑块」不是一回事，
 * 官方也没有对应组件，所以这个保留自绘。
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

/**
 * 对话框（v2.11.0）。
 *
 * ## 为什么做成兼容层
 *
 * 项目里原有 9 处 `AlertDialog`（Material3），签名是
 * `onDismissRequest / title / text / confirmButton / dismissButton`。
 * 如果逐处改写成 miuix 的 `OverlayDialog`，每处都要重排按钮布局，改动面大、易错。
 *
 * 这里做一个同签名的封装，内部换成 miuix 的 [OverlayDialog]，
 * 于是调用处只需要把函数名换掉 —— 样式立刻变成 HyperOS，
 * 但业务代码一行不动。
 *
 * miuix 的 OverlayDialog 要求被 miuix 的 Scaffold 包裹（它靠 MiuixPopupHost
 * 承载浮层），本项目已在 EyeCareApp 里换过 Scaffold。
 *
 * @param title 标题内容（原 AlertDialog 的 title lambda）
 * @param text 正文内容（原 text lambda）
 * @param confirmButton 确认按钮（原样传入，通常是 TextButton）
 * @param dismissButton 取消按钮，可空
 */
@Composable
fun HyperOsAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    // 下面两个参数只为兼容原 Material3 调用处的具名传参，不生效：
    // miuix 的 OverlayDialog 自己决定背景与圆角（走主题），
    // 外部再传颜色/形状会破坏 HyperOS 的一致性。
    @Suppress("UNUSED_PARAMETER")
    containerColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER")
    shape: Shape = RectangleShape,
) {
    OverlayDialog(
        show = true,
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null) {
                title()
                Spacer(Modifier.height(10.dp))
            }
            if (text != null) {
                text()
                Spacer(Modifier.height(18.dp))
            }
            // v2.11.1：按钮必须自己画底色。
            //
            // 上一版把调用方传来的 Material3 TextButton 直接摆进 Row，
            // 而 TextButton 的背景是透明的 —— 结果对话框底部只剩两串绿字，
            // 既没有 HyperOS 的浅灰按钮底，也没有明确的点击区域。
            //
            // 修法：在外面套一层带底色与圆角的容器。容器负责"长什么样"，
            // 里面的 Button 只负责"点了干什么"，这样 9 处调用都不用改。
            //
            // 尺寸取 46dp 高、23dp 圆角 —— 与 HyperOS 对话框按钮一致（胶囊形，
            // 不是直角矩形，也不是 Material 的 8dp 小圆角）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissButton != null) {
                    DialogButtonSlot(
                        modifier = Modifier.weight(1f),
                        content = dismissButton,
                    )
                }
                DialogButtonSlot(
                    modifier = Modifier.weight(1f),
                    content = confirmButton,
                )
            }
        }
    }
}

/**
 * 对话框里的一个按钮位（v2.11.1）。
 *
 * 只负责外观：46dp 高、胶囊圆角、浅灰底；点击逻辑由传入的 content 决定。
 * 用 justify 居中的 Box 包裹，使内部的 Material TextButton 自然居中，
 * 不必关心它是 TextButton 还是别的什么按钮。
 */
@Composable
private fun DialogButtonSlot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
