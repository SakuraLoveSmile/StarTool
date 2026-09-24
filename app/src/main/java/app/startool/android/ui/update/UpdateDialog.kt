package app.startool.android.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.startool.android.AppContainer
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import app.startool.android.update.model.UpdateManifest
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.launch
/**
 * 「下载更新」的唯一实现（P4）。
 *
 * 优先走应用内下载；[app.startool.android.update.UpdateDownloadManager.start]
 * 返回 false（清单没有可用的官方 APK 直链）时，降级为调起浏览器打开 release 页 ——
 * 这是方案里明确要求保留的兜底路径。
 *
 * 之前 StarToolRoot 与 SettingsScreen 各自复制了一份跳浏览器逻辑，
 * P4 收敛到这一处，后续改动只需要改这里。
 */
class UpdateDownloadAction internal constructor(
    private val container: AppContainer,
    private val context: Context,
    private val scope: CoroutineScope,
    private val showSnackbar: suspend (String) -> Unit,
) {
    /** @return true 表示已进入应用内下载流程；false 表示降级跳浏览器（或跳浏览器也失败）。 */
    operator fun invoke(manifest: UpdateManifest): Boolean {
        if (container.updateDownloadManager.start(manifest)) return true
        return openInBrowser(container.updateChecker.getEffectiveReleaseUrl(manifest))
    }

    private fun openInBrowser(url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        scope.launch { showSnackbar("无法调起浏览器下载") }
        false
    }
}

@Composable
fun rememberUpdateDownloadAction(
    container: AppContainer,
    showSnackbar: suspend (String) -> Unit,
): UpdateDownloadAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(container, showSnackbar, context, scope) {
        UpdateDownloadAction(container, context, scope, showSnackbar)
    }
}

/**
 * 公共「发现新版本」弹窗。
 *
 * StarToolRoot 的更新横幅与设置页手动检查的结果共用这一份；
 * testTag 沿用原有的 update_dialog_* 命名，既有 UI 校验与截图流程不受影响。
 */
@Composable
fun UpdateDialog(
    manifest: UpdateManifest,
    onDownload: () -> Unit,
    onRemindLater: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update_dialog"),
        title = {
            Text(
                text = "发现新版本 ${manifest.versionName}",
                style = StarToolType.HourTitle,
                modifier = Modifier.testTag("update_dialog_title"),
            )
        },
        text = {
            Text(
                text = "更新说明：\n" + manifest.notes,
                style = StarToolType.Body,
                modifier = Modifier.testTag("update_dialog_notes"),
            )
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                modifier = Modifier.testTag("update_dialog_download"),
                colors = ButtonDefaults.buttonColors(containerColor = StarToolColors.Primary),
            ) {
                Text("下载更新", style = StarToolType.Body)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceXs)) {
                TextButton(
                    onClick = onRemindLater,
                    modifier = Modifier.testTag("update_dialog_remind_later"),
                ) {
                    Text("稍后提醒", style = StarToolType.Caption)
                }
                TextButton(
                    onClick = onIgnore,
                    modifier = Modifier.testTag("update_dialog_ignore"),
                ) {
                    Text("忽略此版本", style = StarToolType.Caption)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("update_dialog_close"),
                ) {
                    Text("关闭", style = StarToolType.Caption)
                }
            }
        },
    )
}
