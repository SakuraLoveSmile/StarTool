package app.startool.android.update

import app.startool.android.update.model.DownloadError
import app.startool.android.update.model.UpdateDownloadState
import app.startool.android.update.model.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 应用内更新的编排器（P2 下载 + P3 安装）。
 *
 * 设计要点：
 *  - 下载跑在注入的 [scope]（生产环境用 AppContainer.appScope），因此旋转屏幕、
 *    Composable 重建都不会丢失进度；进程被杀则靠 .part 断点文件在下次启动恢复语义。
 *  - 所有状态只经由 [state] 单向流出，UI 不回写。
 *  - [start] 返回 false 表示「没有可用的内下载源」，调用方应降级为跳浏览器。
 *  - 安装结局除实时 [state] 外还写入 [UpdatePreferenceStore]，
 *    因为安装成功后系统很可能直接杀掉本进程，UI 来不及显示。
 */
class UpdateDownloadManager(
    private val downloadDirProvider: () -> File,
    private val scope: CoroutineScope,
    private val preferenceStore: UpdatePreferenceStore,
    private val currentVersionCode: Int,
    private val downloader: ApkDownloader = DefaultApkDownloader(),
    private val verifier: ApkVerifier = ApkVerifier(),
    private val resolver: ApkSourceResolver = ApkSourceResolver(preferenceStore),
    private val installer: ApkInstaller? = null,
) {

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    /** 冷启动补发的一次性更新结果提示（成功或失败），UI 消费后调用 [consumePostUpdateNotice]。 */
    private val _postUpdateNotice = MutableStateFlow<String?>(null)
    val postUpdateNotice: StateFlow<String?> = _postUpdateNotice.asStateFlow()

    private var job: Job? = null
    private var lastManifest: UpdateManifest? = null
    private var installSessionId: Int = -1

    init {
        detectPostUpdate()
    }

    /** 最近一次检测到的更新清单，用于重试与安装阶段取用。 */
    val currentManifest: UpdateManifest? get() = lastManifest

    /** 校验通过、待安装的 APK；非 Ready 状态返回 null。 */
    val readyFile: File?
        get() = (_state.value as? UpdateDownloadState.Ready)?.file

    // ---------------------------------------------------------------- 下载

    /**
     * 开始下载。
     *
     * @return false 表示清单没有可用的官方 APK 直链（或版本并未更新），
     *         调用方应降级为「跳浏览器下载」，而不是提示失败。
     */
    fun start(manifest: UpdateManifest): Boolean {
        val source = resolver.resolve(manifest) ?: return false
        if (manifest.versionCode <= currentVersionCode) return false
        // 已在下载中：忽略重复点击，保持进度
        if (job?.isActive == true) return true

        lastManifest = manifest
        val finalFile = File(downloadDirProvider(), fileNameFor(manifest))
        val partFile = File(finalFile.parentFile, finalFile.name + PART_SUFFIX)

        job = scope.launch {
            // 同版本完整包已存在且校验通过：不重复下载，直接就绪
            if (finalFile.exists() && verifier.verify(finalFile, source) == VerifyResult.Ok) {
                _state.value = UpdateDownloadState.Ready(
                    file = finalFile,
                    targetVersionName = manifest.versionName,
                    targetVersionCode = manifest.versionCode,
                )
                return@launch
            }
            finalFile.delete()

            val resumeFrom = if (partFile.exists()) partFile.length() else 0L
            _state.value = UpdateDownloadState.Downloading(
                loadedBytes = resumeFrom,
                totalBytes = source.expectedSizeBytes,
            )

            val outcome = downloader.download(
                url = source.url,
                targetFile = partFile,
                resumeFromBytes = resumeFrom,
            ) { loaded, total ->
                _state.value = UpdateDownloadState.Downloading(
                    loadedBytes = loaded,
                    totalBytes = total ?: source.expectedSizeBytes,
                )
            }

            when (outcome) {
                is DownloadOutcome.Failed -> {
                    // Interrupted 保留断点供续传；其余错误保留同样无害，重试时按长度决定是否续传
                    _state.value = UpdateDownloadState.DownloadFailed(
                        error = outcome.error,
                        loadedBytes = if (partFile.exists()) partFile.length() else 0L,
                    )
                }

                is DownloadOutcome.Completed -> {
                    _state.value = UpdateDownloadState.Verifying(outcome.file.length())
                    when (val verdict = verifier.verify(outcome.file, source)) {
                        is VerifyResult.Failed -> {
                            // 校验不过的文件必须删掉，绝不能进入安装
                            outcome.file.delete()
                            _state.value = UpdateDownloadState.DownloadFailed(
                                error = verdict.error,
                                loadedBytes = 0L,
                            )
                        }

                        VerifyResult.Ok -> {
                            if (!outcome.file.renameTo(finalFile)) {
                                outcome.file.delete()
                                _state.value = UpdateDownloadState.DownloadFailed(
                                    error = DownloadError.Storage("无法写入安装包目录"),
                                    loadedBytes = 0L,
                                )
                            } else {
                                _state.value = UpdateDownloadState.Ready(
                                    file = finalFile,
                                    targetVersionName = manifest.versionName,
                                    targetVersionCode = manifest.versionCode,
                                )
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    /** 取消当前下载。断点文件保留，下次 [start]/[retry] 可续传。 */
    fun cancel() {
        if (job?.isActive != true) return
        job?.cancel()
        _state.value = UpdateDownloadState.Cancelled
    }

    /** 失败后重试。断点存在则续传，否则重新下载。 */
    fun retry(): Boolean {
        val manifest = lastManifest ?: return false
        if (job?.isActive == true) return true
        return start(manifest)
    }

    /** 回到 Idle 并终止后台任务（用户关闭更新提示时调用）。 */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = UpdateDownloadState.Idle
    }

    // ---------------------------------------------------------------- 安装

    /** 跳「安装未知应用」授权页；null 表示当前无需授权或设备不支持。 */
    fun unknownSourcesSettingsIntent() = installer?.unknownSourcesSettingsIntent()

    fun canInstallWithoutPermission(): Boolean =
        installer?.canRequestInstallPermission() == true

    /**
     * 把已就绪的 APK 提交给系统安装器。
     *
     * @return true 表示会话已 commit；false 表示需要先去授权（状态置为
     *         [UpdateDownloadState.AwaitingInstallPermission]），或提交本身失败。
     */
    fun startInstall(): Boolean {
        val ready = _state.value as? UpdateDownloadState.Ready ?: return false
        val target = installer ?: return false

        if (!target.canRequestInstallPermission()) {
            _state.value = UpdateDownloadState.AwaitingInstallPermission
            return false
        }
        return commitInstall(ready)
    }

    /** 用户从设置页返回且已授权后由 UI 调用，继续刚才中断的安装。 */
    fun onInstallPermissionGranted() {
        val ready = _state.value as? UpdateDownloadState.Ready ?: return
        if (installer?.canRequestInstallPermission() != true) return
        commitInstall(ready)
    }

    private fun commitInstall(ready: UpdateDownloadState.Ready): Boolean {
        val target = installer ?: return false
        return try {
            val sessionId = target.install(
                apkFile = ready.file,
                targetVersionCode = ready.targetVersionCode,
                targetVersionName = ready.targetVersionName,
            )
            installSessionId = sessionId
            // 先落盘：安装成功后进程随时可能被系统回收，UI 未必来得及显示
            preferenceStore.pendingInstallTargetVersionCode = ready.targetVersionCode
            preferenceStore.pendingInstallTargetVersionName = ready.targetVersionName
            preferenceStore.lastInstallError = null
            _state.value = UpdateDownloadState.Installing(
                sessionId = sessionId,
                targetVersionName = ready.targetVersionName,
            )
            true
        } catch (t: Throwable) {
            val message = "无法提交安装：${t.message ?: "未知错误"}"
            preferenceStore.lastInstallError = message
            _state.value = UpdateDownloadState.InstallFailed(
                message = message,
                technicalDetail = t.toString(),
            )
            false
        }
    }

    /** 系统要求用户在安装弹窗确认（STATUS_PENDING_USER_ACTION）。 */
    fun onInstallPendingUserAction() {
        if (_state.value is UpdateDownloadState.Installing) return
        // 进程若在 commit 之后被重启，状态可能已经是 Idle；
        // 此时仍要如实反映「正在等用户确认」，否则系统弹窗在前台而 UI 毫无提示。
        val versionName = (_state.value as? UpdateDownloadState.Ready)?.targetVersionName
            ?: lastManifest?.versionName
            ?: ""
        _state.value = UpdateDownloadState.Installing(
            sessionId = installSessionId,
            targetVersionName = versionName,
        )
    }

    /** 由 [InstallStatusReceiver] 回调，驱动安装结局。 */
    fun onInstallStatus(
        statusCode: Int,
        statusMessage: String?,
        sessionId: Int,
        targetVersionCode: Int,
        targetVersionName: String?,
    ) {
        if (sessionId >= 0 && installSessionId >= 0 && sessionId != installSessionId) return

        when (val feedback = InstallOutcomeMapper.describe(statusCode, statusMessage)) {
            InstallFeedback.PendingUserAction -> onInstallPendingUserAction()

            InstallFeedback.Success -> {
                preferenceStore.lastInstallError = null
                // 保留 pending：进程若随后被杀，冷启动据此补一条「已成功更新」
                if (targetVersionCode > 0) {
                    preferenceStore.pendingInstallTargetVersionCode = targetVersionCode
                    if (targetVersionName != null) {
                        preferenceStore.pendingInstallTargetVersionName = targetVersionName
                    }
                }
                _state.value = UpdateDownloadState.InstallSucceeded(
                    targetVersionCode = targetVersionCode,
                    targetVersionName = targetVersionName.orEmpty(),
                )
            }

            InstallFeedback.Cancelled -> {
                preferenceStore.pendingInstallTargetVersionCode = null
                preferenceStore.pendingInstallTargetVersionName = null
                preferenceStore.lastInstallError = null
                _state.value = UpdateDownloadState.InstallCancelled
            }

            is InstallFeedback.Failed -> {
                // 安装失败即终止这次更新：pending 清掉，避免冷启动误报「更新成功」
                preferenceStore.pendingInstallTargetVersionCode = null
                preferenceStore.pendingInstallTargetVersionName = null
                preferenceStore.lastInstallError = feedback.userMessage
                _state.value = UpdateDownloadState.InstallFailed(
                    message = feedback.userMessage,
                    technicalDetail = feedback.technicalDetail,
                )
            }
        }
    }

    fun consumePostUpdateNotice() {
        _postUpdateNotice.value = null
    }

    // ------------------------------------------------- 安装态的退出 / 重新安装

    /** 从当前状态或磁盘上已校验的包还原 Ready，供「重新安装 / 关闭」复用。 */
    private fun restoreReady(): UpdateDownloadState.Ready? {
        (_state.value as? UpdateDownloadState.Ready)?.let { return it }
        val manifest = lastManifest ?: return null
        val file = File(downloadDirProvider(), fileNameFor(manifest))
        if (!file.exists()) return null
        return UpdateDownloadState.Ready(
            file = file,
            targetVersionName = manifest.versionName,
            targetVersionCode = manifest.versionCode,
        )
    }

    /** 安装失败或被取消后再次安装 —— 安装包还在磁盘上，不重新下载。 */
    fun retryInstall(): Boolean {
        val ready = restoreReady() ?: return false
        _state.value = ready
        return startInstall()
    }

    /**
     * 关闭安装结果提示。
     * 成功后回 Idle（版本已更新，不该再显示安装按钮）；
     * 失败 / 取消 / 待授权则回到 Ready，用户仍可再次安装。
     */
    fun dismissInstallResult() {
        _state.value = when (_state.value) {
            is UpdateDownloadState.InstallSucceeded -> UpdateDownloadState.Idle
            is UpdateDownloadState.InstallFailed,
            is UpdateDownloadState.InstallCancelled,
            UpdateDownloadState.AwaitingInstallPermission,
            -> restoreReady() ?: UpdateDownloadState.Idle
            else -> UpdateDownloadState.Idle
        }
    }

    /** 重启应用，让新版本代码真正生效。 */
    fun restartApp() {
        installer?.restartApp()
    }

    /** 冷启动比对 pending 与实际版本，补发上次安装结局。 */
    private fun detectPostUpdate() {
        val pending = preferenceStore.pendingInstallTargetVersionCode ?: return
        val pendingName = preferenceStore.pendingInstallTargetVersionName
        preferenceStore.pendingInstallTargetVersionCode = null
        preferenceStore.pendingInstallTargetVersionName = null

        _postUpdateNotice.value = if (currentVersionCode >= pending) {
            "已成功更新到 ${pendingName ?: ("v$currentVersionCode")}"
        } else {
            preferenceStore.lastInstallError
                ?: "上次更新未完成（可能被取消或进程被中断），可重新下载后重试"
        }
        preferenceStore.lastInstallError = null
    }

    private fun fileNameFor(manifest: UpdateManifest): String =
        "StarTool-v${manifest.versionName}-r${manifest.versionCode}.apk"

    companion object {
        const val PART_SUFFIX = ".part"
    }
}
