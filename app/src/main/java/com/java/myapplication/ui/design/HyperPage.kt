package com.java.myapplication.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一级页面外壳（v3.6）。
 *
 * ## 对齐 HuaweiPods：顶栏在布局内，不模糊
 *
 * 之前把顶栏做成 overlay + 渐进模糊，滚动时能看见内容从标题下穿过。
 * 但 `layerBackdrop` 录制的层被折叠补偿平移过，采样坐标对不上，
 * 滚动时会出现"糊着上一帧"的横带。
 *
 * HuaweiPods 的做法更简单也更稳：`Scaffold` 的 topBar 是布局的一部分，
 * 内容永远在它下面，顶栏用默认 surface 底色——没有采样、没有残影，
 * 也不存在"折叠吃掉滚动"的死区（折叠时内容区高度变化，天然跟手）。
 * 这里照它做。
 */
@Composable
fun RootPage(
    title: String,
    subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val behavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()

    // 场景必须自己是不透明的：NavDisplay 转场时新旧两个场景同时在屏上，
    // 页面根节点一旦透明，滑入中的新页面会把旧页面的行"透"出来
    // （症状就是"动效里没有背景、结束后背景才出现"）。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = title,
            largeTitle = title,
            subtitle = subtitle,
            scrollBehavior = behavior,
            actions = actions,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .nestedScroll(behavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = HyperSpace.Page),
        ) {
            content()
            // 悬浮底栏浮在内容之上；留出栏高，最后一张卡才不会被压住
            Spacer(Modifier.height(HyperSpace.BottomWithNav))
        }
    }
}

/**
 * 二级页面外壳：返回 + 标题 + 滚动内容。
 *
 * 二级页不做内容穿插：它本来就是"从上一个页面推进来的一个任务"，
 * 顶栏必须一直清晰可读，模糊反而增加层级噪音。
 */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        SmallTopAppBar(
            title = title,
            color = Color.Transparent,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = HyperSpace.Page),
        ) {
            content()
            Spacer(Modifier.height(HyperSpace.Bottom))
        }
    }
}
