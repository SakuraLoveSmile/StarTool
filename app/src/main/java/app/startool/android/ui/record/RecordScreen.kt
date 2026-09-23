package app.startool.android.ui.record

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.startool.android.AppContainer
import app.startool.android.ui.components.EmptyView

/**
 * 记录页（子 agent B 独占）。签名冻结，不要修改参数列表。
 *
 * 实现要求见实施计划 §3：24 个小时卡片、底部评分面板、
 * 0 分与未选择区分、未保存改动确认、SavedStateHandle 草稿。
 * 使用 container.repository / container.selectedDateStore / container.clock /
 * container.maintenance.busy；共享组件在 ui/components 中（DateBar、StarRating、
 * LoadingView、ErrorView、EmptyView）。
 */
@Composable
fun RecordScreen(
    container: AppContainer,
    showSnackbar: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // T0 占位：子 agent B 将完整实现此页面。
    EmptyView(message = "记录页开发中", modifier = modifier)
}
