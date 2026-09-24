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
 * P2：应用内下载的编排器。
 *
 * 设计要点：
 *  - 下载跑在注入的 [scope]（生产环境用 AppContainer.appScope），因此旋转屏幕、
 *    Composable 重建都不会丢失进度；进程被杀则靠 .part 断点文件在下次启动恢复语义。
 *  - 所有状态只经由 [state] 单向流出，UI 不回写。
 *  - [start] 返回 false 表示「没有可用的内下载源」，调用方应降级为跳浏览器。
 */
class UpdateDownloadManager(
    private val downloadDirProvider: () -> File,
    private val scope: CoroutineScope,
    private val preferenceStore: UpdatePreferenceStore,
    private val currentVersionCode: Int,
    private val downloader: ApkDownloader = DefaultApkDownloader(),
    private val verifier: ApkVerifier = ApkVerifier(),
    private val resolver: ApkSourceResolver = ApkSourceResolver(preferenceStore),
) {

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastManifest: UpdateManifest? = null

    /** 最近一次检测到的更新清单，用于重试与安装阶段取用。 */
    val currentManifest: UpdateManifest? get() = lastManifest

    /** 校验通过、待安装的 APK；非 Ready 状态返回 null。 */
    val readyFile: File?
        get() = (_state.value as? UpdateDownloadState.Ready)?.file

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
                    // Interrupted 保留断点供续传；其余错误保留同样无害，重试时会按长度决定是否续传
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

    private fun fileNameFor(manifest: UpdateManifest): String =
        "StarTool-v${manifest.versionName}-r${manifest.versionCode}.apk"

    companion object {
        const val PART_SUFFIX = ".part"
    }
}
