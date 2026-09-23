package app.startool.android.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.startool.android.AppContainer
import app.startool.android.ui.components.EmptyView

/**
 * 回看页（子 agent C 独占）。签名冻结，不要修改参数列表。
 *
 * 实现要求见实施计划 §4：双曲线（紫实线圆点 = 精神，青绿虚线菱形 = 身体）、
 * 24 小时横轴 0–10 纵轴、漏记不连线、记录明细、空态与读屏等价内容。
 */
@Composable
fun HistoryScreen(
    container: AppContainer,
    showSnackbar: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // T0 占位：子 agent C 将完整实现此页面。
    EmptyView(message = "回看页开发中", modifier = modifier)
}
