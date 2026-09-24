package app.startool.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * P3：用 PackageInstaller 把已校验的 APK 交给系统安装。
 *
 * 为什么不用 `ACTION_VIEW` + `startActivityForResult`：
 * Android 11 起调用方拿不到真实安装结果，onActivityResult 恒为 RESULT_CANCELED，
 * 做不出「安装结果提示」。PackageInstaller 的状态回执是唯一可靠来源，
 * 也才能区分「用户取消」和「签名不匹配」这两类完全不同的结局。
 *
 * API 形状（compileSdk 36 实测）：`PackageInstaller` 自身**没有** openWrite/fsync/commit，
 * 必须 `openSession(id)` 拿到 [PackageInstaller.Session] 再操作。
 */
class ApkInstaller(
    private val context: Context,
) {

    /** 是否已授予「安装未知应用」权限（API 26+，本项目 minSdk 正好 26）。 */
    fun canRequestInstallPermission(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 跳系统设置页授权；返回 null 表示设备不支持该入口。 */
    fun unknownSourcesSettingsIntent(): Intent? = try {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * 提交会话并 commit。
     *
     * @return sessionId，供状态回执比对
     * @throws Throwable 提交失败时抛出，调用方负责展示错误并回收状态
     */
    fun install(apkFile: File, targetVersionCode: Int, targetVersionName: String): Int {
        require(apkFile.exists() && apkFile.length() > 0L) {
            "安装包不存在或为空：${apkFile.absolutePath}"
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            // 就是本应用自己的覆盖安装，包名保持一致
            setAppPackageName(context.packageName)
        }

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0L, apkFile.length()).use { out ->
                    apkFile.inputStream().use { input -> input.copyTo(out) }
                    // 必须在 close 之前 fsync，否则数据可能还没落盘就 commit 了
                    session.fsync(out)
                }

                val callback = Intent(context, InstallStatusReceiver::class.java).apply {
                    action = InstallStatusReceiver.ACTION_INSTALL_STATUS
                    setPackage(context.packageName)
                    putExtra(InstallStatusReceiver.EXTRA_SESSION_ID, sessionId)
                    putExtra(InstallStatusReceiver.EXTRA_TARGET_VERSION_CODE, targetVersionCode)
                    putExtra(InstallStatusReceiver.EXTRA_TARGET_VERSION_NAME, targetVersionName)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    // FLAG_MUTABLE 是硬要求：系统要往里回填 EXTRA_STATUS / EXTRA_STATUS_MESSAGE。
                    // Android 12+ 上写成 IMMUTABLE 会直接抛 IllegalArgumentException。
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pendingIntent.intentSender)
            }
            return sessionId
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw t
        }
    }

    /**
     * PackageInstaller 提交失败时的兜底：用系统安装器打开 APK。
     * 拿不到结果，但至少保证用户仍能手动装上。
     */
    fun fallbackViewIntent(apkFile: File): Intent? = try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.$FILE_PROVIDER_SUFFIX",
            apkFile,
        )
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * 重启应用。
     *
     * 先把启动 Intent 交给 system_server，再结束本进程 —— 这是 ProcessPhoenix 的做法：
     * Intent 已被系统接收，杀掉旧进程后系统会为新任务拉起新进程，不会出现
     * 「先 exit 再 startActivity 导致没启动」的竞态。
     */
    fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            runCatching { context.startActivity(launch) }
        }
        Runtime.getRuntime().exit(0)
    }

    companion object {
        /** 与 AndroidManifest 中 provider 的 authorities 后缀保持一致。 */
        const val FILE_PROVIDER_SUFFIX = "fileprovider"
    }
}
