package app.startool.android.update.model

import java.io.File

/**
 * 应用内更新的完整状态机（P2 下载 + P3 安装）。
 *
 * UI 只负责渲染，不持有业务状态；所有转移都发生在 [app.startool.android.update.UpdateDownloadManager]。
 *
 * P2 阶段实现：Idle / Downloading / Verifying / Ready / DownloadFailed / Cancelled
 * P3 阶段实现：AwaitingInstallPermission / Installing / InstallSucceeded / InstallFailed / InstallCancelled
 */
sealed interface UpdateDownloadState {

    /** 尚未开始下载。 */
    data object Idle : UpdateDownloadState

    /**
     * 正在下载。
     * @param totalBytes 为 null 表示服务端未给出总长，UI 走不确定进度条。
     */
    data class Downloading(
        val loadedBytes: Long,
        val totalBytes: Long?,
    ) : UpdateDownloadState {
        /** 0f..1f；总长未知时为 null。 */
        val fraction: Float?
            get() = totalBytes
                ?.takeIf { it > 0L }
                ?.let { (loadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
    }

    /** 下载完成，正在做体积 / SHA-256 校验。 */
    data class Verifying(val loadedBytes: Long) : UpdateDownloadState

    /** 校验通过，等待用户确认安装。 */
    data class Ready(
        val file: File,
        val targetVersionName: String,
        val targetVersionCode: Int,
    ) : UpdateDownloadState

    /** 下载失败。部分错误（如 Interrupted）保留了断点文件，可续传重试。 */
    data class DownloadFailed(
        val error: DownloadError,
        val loadedBytes: Long,
    ) : UpdateDownloadState {
        /** 是否还有可复用的断点字节。 */
        val canResume: Boolean get() = loadedBytes > 0L
    }

    /** 用户主动取消下载。断点文件保留，下次可续传。 */
    data object Cancelled : UpdateDownloadState

    // ------------------------- P3：安装阶段 -------------------------

    /** 等待用户授予「安装未知应用」权限。 */
    data object AwaitingInstallPermission : UpdateDownloadState

    /** 已提交给系统安装器，等待用户在系统弹窗确认。 */
    data class Installing(
        val sessionId: Int,
        val targetVersionName: String,
    ) : UpdateDownloadState

    /** 安装成功（进程可能随即被系统回收，回执由冷启动比对兜底）。 */
    data class InstallSucceeded(
        val targetVersionCode: Int,
        val targetVersionName: String,
    ) : UpdateDownloadState

    /** 安装失败，携带面向用户的中文文案。 */
    data class InstallFailed(
        val message: String,
        val technicalDetail: String?,
    ) : UpdateDownloadState

    /** 用户在系统安装弹窗点了取消。 */
    data object InstallCancelled : UpdateDownloadState
}

/** 下载 / 校验阶段的错误分类，用于给出不同的用户文案与重试策略。 */
sealed interface DownloadError {

    /** 无法建立连接或读取失败（无断点）。 */
    data class Network(
        val message: String,
        val cause: Throwable? = null,
    ) : DownloadError

    /** 下载中途断开，已有断点，可 Range 续传。 */
    data class Interrupted(
        val loadedBytes: Long,
        val message: String = "连接中断",
    ) : DownloadError

    /** 服务端返回非 2xx。 */
    data class Http(val code: Int) : DownloadError

    /** 实际字节数与清单声明不符（含被截断的下载）。 */
    data class SizeMismatch(
        val expected: Long,
        val actual: Long,
    ) : DownloadError

    /** SHA-256 不匹配 —— 走第三方镜像时最重要的安全闸门。 */
    data class HashMismatch(
        val expected: String,
        val actual: String,
    ) : DownloadError

    /** 本地存储问题：目录不可写、文件缺失、重命名失败等。 */
    data class Storage(val message: String) : DownloadError

    /** 用户取消，不应作为错误提示。 */
    data object CancelledByUser : DownloadError
}
