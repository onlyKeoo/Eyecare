package com.java.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.java.myapplication.data.AppInfo
import com.java.myapplication.data.Disclaimer
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次启动引导（v3.0 重做视觉，流程不变）。
 *
 * 分两步：
 * 1. **免责声明** —— 必须点「我已知悉」才能继续，不能被点外部或返回键绕过；
 * 2. **可靠性设置** —— 逐项给出「去开启」入口，可「稍后设置」跳过。
 *
 * 只要走到 [onFinish]（无论走完还是跳过），就由调用方写入完成标记。
 * 想重看可靠性设置：设置 → 提醒可靠性自检；想重看免责声明：设置 → 关于 → 免责声明。
 */
@Composable
fun OnboardingDialog(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    OverlayDialog(
        show = true,
        title = if (step == 0) "欢迎使用护眼时光" else "让提醒准时到达",
        summary = if (step == 0) {
            "开始前，请花一分钟了解下面这些"
        } else {
            "Android 有几处开关会影响提醒，建议现在设置好"
        },
        // 第 0 步不允许绕过：点外部等于没同意免责声明
        onDismissRequest = { if (step == 1) onFinish() },
        onDismissFinished = { if (step == 1) Unit },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (step == 0) DisclaimerStep() else SetupGuideStep()
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { if (step == 0) step = 1 else onFinish() },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = if (step == 0) "我已知悉，继续" else "完成",
                style = MiuixTheme.textStyles.button,
            )
        }
        if (step == 1) {
            Box(Modifier.padding(top = 8.dp)) {
                TextButton(
                    text = "稍后设置",
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    minHeight = 44.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun DisclaimerStep() {
    Text(
        text = Disclaimer.TEXT,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "继续即表示你已阅读并同意以上说明。",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

@Composable
private fun SetupGuideStep() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val items = remember(refreshTick) { collectDiagnostics(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> DiagRow(item) }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "改完系统设置后回到这里，点「刷新状态」确认是否生效。",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        TextButton(
            text = "刷新状态",
            onClick = { refreshTick++ },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            minHeight = 42.dp,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

/** 只读的免责声明弹窗：文案与首次启动引导同源（[Disclaimer.TEXT]）。 */
@Composable
fun DisclaimerDialog(onDismiss: () -> Unit) {
    OverlayDialog(
        show = true,
        title = "免责声明",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = Disclaimer.TEXT,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "项目仓库：${AppInfo.REPO_URL}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("知道了", style = MiuixTheme.textStyles.button)
        }
    }
}
