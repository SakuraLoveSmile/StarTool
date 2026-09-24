package app.startool.android.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.startool.android.update.model.DownloadError
import app.startool.android.update.model.UpdateDownloadState
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import java.util.Locale

/**
 * 更新卡片内的下载 / 安装进度区（P2 下载 + P3 安装）。
 *
 * 纯渲染组件 —— 持有状态的是 [app.startool.android.update.UpdateDownloadManager]，
 * 因此离开设置页再回来进度不丢，进程被杀也能靠持久化字段恢复结局提示。
 */
@Composable
fun UpdateProgressSection(
    state: UpdateDownloadState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onGoToInstallSettings: () -> Unit,
    onRetryInstall: () -> Unit,
    onRestartApp: () -> Unit,
    onDismissInstallResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        UpdateDownloadState.Idle -> Unit

        is UpdateDownloadState.Downloading -> {
            val fraction = state.fraction
            Column(modifier = modifier.fillMaxWidth()) {
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_update_progress"),
                        color = StarToolColors.Primary,
                        trackColor = StarToolColors.Surface,
                    )
                    Spacer(Modifier.height(StarToolDimens.SpaceXs))
                    Text(
                        text = "${(fraction * 100).toInt()}% · " +
                            "${formatBytes(state.loadedBytes)} / ${formatBytes(state.totalBytes ?: 0L)}",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                        modifier = Modifier.testTag("settings_update_progress_text"),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_update_progress"),
                        color = StarToolColors.Primary,
                    )
                    Spacer(Modifier.height(StarToolDimens.SpaceXs))
                    Text(
                        text = "正在下载 · 已完成 ${formatBytes(state.loadedBytes)}",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                        modifier = Modifier.testTag("settings_update_progress_text"),
                    )
                }
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("settings_update_cancel_button"),
                    ) {
                        Text("取消", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }

        is UpdateDownloadState.Verifying -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = "正在校验安装包完整性…（${formatBytes(state.loadedBytes)}）",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                    modifier = Modifier.testTag("settings_update_progress"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("settings_update_cancel_button"),
                    ) {
                        Text("取消", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }

        is UpdateDownloadState.Ready -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = "下载完成，已通过完整性校验（${formatBytes(state.file.length())}）",
                    style = StarToolType.Caption,
                    color = StarToolColors.Primary,
                    modifier = Modifier.testTag("settings_update_ready"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_update_install_button"),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = StarToolColors.Primary,
                    ),
                ) {
                    Text("安装 v${state.targetVersionName}", style = StarToolType.Body)
                }
            }
        }

        UpdateDownloadState.AwaitingInstallPermission -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = "系统尚未允许本应用安装应用，需要先在设置中授权。",
                    style = StarToolType.Caption,
                    color = StarToolColors.Error,
                    modifier = Modifier.testTag("settings_update_permission_hint"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm)) {
                    Button(
                        onClick = onGoToInstallSettings,
                        modifier = Modifier.testTag("settings_update_permission_button"),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = StarToolColors.Primary,
                        ),
                    ) {
                        Text("去授权", style = StarToolType.Body)
                    }
                    TextButton(
                        onClick = onDismissInstallResult,
                        modifier = Modifier.testTag("settings_update_permission_cancel"),
                    ) {
                        Text("稍后", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }

        is UpdateDownloadState.Installing -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = "正在安装 v${state.targetVersionName}，请在系统弹窗中确认…",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                    modifier = Modifier.testTag("settings_update_installing"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceXs))
                Text(
                    text = "若安装完成后应用没有自动重启，可手动重启应用。",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                )
            }
        }

        is UpdateDownloadState.InstallSucceeded -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = "已安装 v${state.targetVersionName}（versionCode ${state.targetVersionCode}）",
                    style = StarToolType.Caption,
                    color = StarToolColors.Primary,
                    modifier = Modifier.testTag("settings_update_install_success"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm)) {
                    Button(
                        onClick = onRestartApp,
                        modifier = Modifier.testTag("settings_update_restart_button"),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = StarToolColors.Primary,
                        ),
                    ) {
                        Text("立即重启", style = StarToolType.Body)
                    }
                    TextButton(
                        onClick = onDismissInstallResult,
                        modifier = Modifier.testTag("settings_update_install_success_close"),
                    ) {
                        Text("稍后", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }

        is UpdateDownloadState.InstallFailed -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = state.message,
                    style = StarToolType.Caption,
                    color = StarToolColors.Error,
                    modifier = Modifier.testTag("settings_update_install_error"),
                )
                state.technicalDetail?.let { detail ->
                    Spacer(Modifier.height(StarToolDimens.SpaceXs))
                    Text(
                        text = "技术细节：$detail",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                        modifier = Modifier.testTag("settings_update_install_error_detail"),
                    )
                }
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm)) {
                    OutlinedButton(
                        onClick = onRetryInstall,
                        modifier = Modifier.testTag("settings_update_install_retry_button"),
                    ) {
                        Text("重新安装")
                    }
                    TextButton(
                        onClick = onDismissInstallResult,
                        modifier = Modifier.testTag("settings_update_install_error_close"),
                    ) {
                        Text("关闭", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }

        UpdateDownloadState.InstallCancelled -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = "已取消安装，安装包仍保留，可随时重新安装。",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                    modifier = Modifier.testTag("settings_update_install_cancelled"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm)) {
                    OutlinedButton(
                        onClick = onRetryInstall,
                        modifier = Modifier.testTag("settings_update_install_retry_button"),
                    ) {
                        Text("重新安装")
                    }
                    TextButton(
                        onClick = onDismissInstallResult,
                        modifier = Modifier.testTag("settings_update_install_cancelled_close"),
                    ) {
                        Text("关闭", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }

        is UpdateDownloadState.DownloadFailed -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = describeDownloadError(state),
                    style = StarToolType.Caption,
                    color = StarToolColors.Error,
                    modifier = Modifier.testTag("settings_update_download_error"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm)) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("settings_update_download_retry_button"),
                    ) {
                        Text(if (state.canResume) "继续下载" else "重试")
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("settings_update_download_dismiss_button"),
                    ) {
                        Text("关闭", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }

        UpdateDownloadState.Cancelled -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = "已取消下载",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                    modifier = Modifier.testTag("settings_update_cancelled"),
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm)) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("settings_update_download_retry_button"),
                    ) {
                        Text("继续下载")
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("settings_update_cancelled_close"),
                    ) {
                        Text("关闭", color = StarToolColors.TextSecondary)
                    }
                }
            }
        }
    }
}

/** 下载错误 -> 面向用户的中文文案。 */
internal fun describeDownloadError(state: UpdateDownloadState.DownloadFailed): String {
    return when (val error = state.error) {
        is DownloadError.Network -> "下载失败：${error.message}"

        is DownloadError.Interrupted ->
            if (state.canResume) {
                "下载中断，已保留 ${formatBytes(state.loadedBytes)} 断点，可继续下载"
            } else {
                "下载中断：${error.message}"
            }

        is DownloadError.Http -> when (error.code) {
            403 -> "下载地址失效或被限流（HTTP 403），可在上方切换下载源后重试"
            404 -> "该版本的安装包不存在（HTTP 404）"
            in 500..599 -> "服务器异常（HTTP ${error.code}），请稍后重试"
            else -> "下载失败（HTTP ${error.code}）"
        }

        is DownloadError.SizeMismatch ->
            "安装包体积不符（期望 ${formatBytes(error.expected)}，实际 ${formatBytes(error.actual)}），已删除，请重试"

        is DownloadError.HashMismatch ->
            "安装包 SHA-256 校验不通过，文件可能已损坏或被篡改，已删除，请重试"

        is DownloadError.Storage -> "存储异常：${error.message}"

        DownloadError.CancelledByUser -> "已取消下载"
    }
}

/** 字节数人性化展示。显式用 Locale.ROOT，避免部分语言环境下小数点被本地化。 */
internal fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * kb
    return when {
        bytes >= mb -> String.format(Locale.ROOT, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.ROOT, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
