package app.startool.android.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.startool.android.AppContainer
import app.startool.android.ui.components.EmptyView

/**
 * 设置 / 数据管理页（子 agent D 独占）。签名冻结，不要修改参数列表。
 *
 * 实现要求见实施计划 §5：导出备份、选择文件恢复、预览与冲突比较、
 * 结果页、应用说明。文件操作走 container.fileGateway（SAF 桥接）；
 * 维护期间通过 container.maintenance 置忙。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // T0 占位：子 agent D 将完整实现此页面。
    EmptyView(message = "设置页开发中", modifier = modifier)
}
