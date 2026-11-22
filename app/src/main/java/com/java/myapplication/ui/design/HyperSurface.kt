package com.java.myapplication.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTitleDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分组：小标题 + 一张玻璃卡。
 *
 * HyperOS 的设置页就是「一行小灰标题 + 一张大白卡」的节奏，
 * 小标题本身不占视觉权重，作用是把卡片分组切开。
 */
@Composable
fun HyperSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            SmallTitle(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                insideMargin = SmallTitleDefaults.InsideMargin,
            )
        }
        // 容器交给 miuix Card：它内部就是 squircleSurface + surfaceContainer，
        // 和设置行、弹层是同一套材质。我们只把内距/圆角当参数传进去。
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.CornerRadius,
            insideMargin = contentPadding,
            colors = CardDefaults.defaultColors(),
            content = content,
        )
    }
}

/**
 * 行与行之间的分割线。
 *
 * HyperOS 的分割线是**从文字起始处开始**的：它不是把卡片切开，
 * 而是标出"这两行是不同的项"，所以左侧要缩进到内容起点。
 */
@Composable
fun HyperDivider(startInset: Dp = HyperSpace.Page) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

/**
 * 空状态 / 错误状态共用的容器。
 *
 * 只在真的没有数据时出现：图标 + 一句说明 + 一个可执行的动作。
 * 不用插画，因为这套界面里没有任何位图资源，塞一张图反而会显得不像系统应用。
 */
@Composable
fun HyperEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HyperSpace.Tight),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Box(Modifier.padding(top = HyperSpace.Row)) { action() }
            }
        }
    }
}
