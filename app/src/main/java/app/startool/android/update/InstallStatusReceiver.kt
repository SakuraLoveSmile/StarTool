package app.startool.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import app.startool.android.StarToolApplication

/**
 * P3：接收 PackageInstaller 会话的状态回执。
 *
 * 两个必须处理的分支：
 *  - STATUS_PENDING_USER_ACTION(-1)：系统要求用户确认。此时必须把回执里的
 *    确认 Intent 拉起来，否则安装界面永远不出现（应用在后台时尤其明显）；
 *  - 其余状态：转交给 [UpdateDownloadManager] 更新状态机并写入持久化字段，
 *    这样即使进程随后被系统回收，冷启动也能补一条结果提示。
 */
class InstallStatusReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION") // getParcelableExtra(Class) 需 API 33，minSdk 26
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val app = context.applicationContext as? StarToolApplication ?: return
        val manager = app.container.updateDownloadManager

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val targetVersionCode = intent.getIntExtra(EXTRA_TARGET_VERSION_CODE, -1)
        val targetVersionName = intent.getStringExtra(EXTRA_TARGET_VERSION_NAME)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // 把系统的安装确认弹窗拉到前台，否则用户看不到任何界面
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                try {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                } catch (_: Throwable) {
                    // 拉不起来也不算致命，状态机照常推进
                }
            }
            manager.onInstallPendingUserAction()
            return
        }

        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        manager.onInstallStatus(
            statusCode = status,
            statusMessage = message,
            sessionId = sessionId,
            targetVersionCode = targetVersionCode,
            targetVersionName = targetVersionName,
        )
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "app.startool.android.update.INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "startool.extra.SESSION_ID"
        const val EXTRA_TARGET_VERSION_CODE = "startool.extra.TARGET_VERSION_CODE"
        const val EXTRA_TARGET_VERSION_NAME = "startool.extra.TARGET_VERSION_NAME"
    }
}
