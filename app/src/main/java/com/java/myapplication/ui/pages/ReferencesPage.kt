package com.java.myapplication.ui.pages

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.java.myapplication.ui.design.HyperSpace
import com.java.myapplication.ui.design.SubPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTitleDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「参考与致谢」页（v3.8）。
 *
 * ## 版式来源
 *
 * 结构照 HuaweiPods 的 `AboutReferencesPage`：一段说明 → 若干分组（小标题 + 卡片），
 * 每个条目是「名称 + 一句它做了什么 + 右侧链接图标」，点条目跳转上游。
 * 这种写法的好处是把「用到的依赖」「参考过的项目」「素材来源」分成三组，
 * 不会让人误以为所有列出的项目都是代码依赖。
 *
 * ## 为什么还保留应用内全文
 *
 * GPL-3.0 要求分发时随附许可证，而「只给一个 GitHub 链接」在离线或链接失效时等于没有。
 * 所以正文之外单列「许可证全文」一组，文字从 `assets/licenses/` 读取——
 * 与打进 APK 的那份完全同一来源，不在代码里另抄一遍。
 */
@Composable
fun ReferencesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<LicenseDoc?>(null) }

    SubPage("参考与致谢", onBack = onBack) {
        Spacer(Modifier.height(HyperSpace.Tight))

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.CornerRadius,
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = "护眼时光以 GPL-3.0 发布。下面列出它用到的开源组件、参考过的项目，" +
                    "以及随应用分发的许可证全文。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        Spacer(Modifier.height(HyperSpace.Section))

        ReferenceGroup(
            title = "组件依赖",
            items = dependencyReferences,
            onOpen = { openUrl(context, it) },
        )

        Spacer(Modifier.height(HyperSpace.Section))

        ReferenceGroup(
            title = "设计参考",
            items = designReferences,
            onOpen = { openUrl(context, it) },
        )

        Spacer(Modifier.height(HyperSpace.Section))

        // 许可证全文：点击在应用内阅读，不依赖网络
        Column(modifier = Modifier.fillMaxWidth()) {
            SmallTitle(
                text = "许可证全文",
                insideMargin = SmallTitleDefaults.InsideMargin,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                licenseDocs.forEach { doc ->
                    BasicComponent(
                        title = doc.title,
                        summary = doc.summary,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        onClick = { reading = doc },
                    )
                }
            }
        }

        Spacer(Modifier.height(HyperSpace.Section))

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.CornerRadius,
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = "完整清单（上游提交、修改范围、核查方法）见仓库根目录 THIRD_PARTY_NOTICES.md。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }

    reading?.let { doc ->
        LicenseTextDialog(doc = doc, onDismiss = { reading = null })
    }
}

private data class Reference(
    val name: String,
    val summary: String,
    val url: String,
)

private data class LicenseDoc(
    val title: String,
    val summary: String,
    val asset: String,
)

private val dependencyReferences = listOf(
    Reference(
        name = "Miuix",
        summary = "组件、主题、图标、圆角与背景模糊 · 0.9.4 / 0.9.3 · Apache-2.0",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
    Reference(
        name = "AndroidX · Kotlin",
        summary = "基础框架与语言运行时 · Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    Reference(
        name = "MaterialKolor",
        summary = "动态取色的调色板实现（由 Miuix 传递引入）· MIT",
        url = "https://github.com/jordond/materialkolor",
    ),
    Reference(
        name = "Google Material Icons",
        summary = "通知栏小图标 visibility · Apache-2.0",
        url = "https://github.com/google/material-design-icons",
    ),
)

private val designReferences = listOf(
    Reference(
        name = "HuaweiPods",
        summary = "界面与动效参考；切页时长算式改写自其 MainTabsPagerState · GPL-3.0",
        url = "https://github.com/Nshpiter/HuaweiPods",
    ),
    Reference(
        name = "HyperIsland",
        summary = "关于页版式参考，未包含其代码 · MIT",
        url = "https://github.com/1812z/HyperIsland",
    ),
)

private val licenseDocs = listOf(
    LicenseDoc(
        title = "GNU GPL v3.0",
        summary = "本项目与 HuaweiPods 使用同一份许可证",
        asset = "gpl-3.0.txt",
    ),
    LicenseDoc(
        title = "Apache License 2.0",
        summary = "Miuix · AndroidX · Material Icons",
        asset = "apache-2.0.txt",
    ),
    LicenseDoc(
        title = "MIT · MaterialKolor",
        summary = "Copyright © 2026 Jordon de Hoog",
        asset = "mit-materialkolor.txt",
    ),
    LicenseDoc(
        title = "MIT · HyperIsland（参考）",
        summary = "Copyright © 2026 1812z",
        asset = "mit-hyperisland.txt",
    ),
)

@Composable
private fun ReferenceGroup(
    title: String,
    items: List<Reference>,
    onOpen: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = title,
            insideMargin = SmallTitleDefaults.InsideMargin,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                BasicComponent(
                    title = item.name,
                    summary = item.summary,
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.Link,
                            contentDescription = "打开上游仓库",
                            modifier = Modifier.padding(end = 6.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    onClick = { onOpen(item.url) },
                )
            }
        }
    }
}

/** 许可证全文弹窗：文本来自 assets，与打进 APK 的那份同源。 */
@Composable
private fun LicenseTextDialog(doc: LicenseDoc, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState(initialValue = "正在读取…", doc.asset) {
        value = readAsset(context, doc.asset)
    }
    OverlayDialog(
        show = true,
        title = doc.title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 弹窗内容高度不受限，正文会把「关闭」按钮顶出屏幕。
                // 给滚动区一个上限，按钮就始终在可视范围内。
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("关闭", style = MiuixTheme.textStyles.button)
        }
    }
}

private fun readAsset(context: Context, name: String): String = runCatching {
    context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
}.getOrElse { "无法读取 $name：${it.message}" }

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
