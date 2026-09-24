package app.startool.android.update

/**
 * 安装会话状态 -> 面向用户的反馈。
 */
sealed interface InstallFeedback {
    /** 安装成功。 */
    data object Success : InstallFeedback

    /** 系统要求用户在安装确认弹窗里点「安装」，不是错误。 */
    data object PendingUserAction : InstallFeedback

    /** 用户在系统弹窗点了取消 / 会话被中止，不算失败。 */
    data object Cancelled : InstallFeedback

    /** 真正的安装失败。 */
    data class Failed(
        val userMessage: String,
        val technicalDetail: String?,
    ) : InstallFeedback
}

/**
 * PackageInstaller 状态码 -> [InstallFeedback] 的映射。
 *
 * 状态值以 compileSdk 36 的 `android.content.pm.PackageInstaller` 实测为准
 * （并非想当然的连续序号，已用 javap 核对过）：
 * {{{
 * STATUS_SUCCESS=0  STATUS_PENDING_USER_ACTION=-1
 * STATUS_FAILURE=1  STATUS_FAILURE_BLOCKED=2   STATUS_FAILURE_ABORTED=3
 * STATUS_FAILURE_INVALID=4  STATUS_FAILURE_CONFLICT=5
 * STATUS_FAILURE_STORAGE=6  STATUS_FAILURE_INCOMPATIBLE=7  STATUS_FAILURE_TIMEOUT=8
 * }}}
 * 刻意写字面量而非常量引用：本类因此不依赖 android.jar，可以在 JVM 单元测试里直接验证。
 *
 * 重点处理两类高频场景：
 *  - **签名不一致**（本项目实测遇到的 INSTALL_FAILED_UPDATE_INCOMPATIBLE）：
 *    给出「先导出备份再卸载旧版」的可执行指引，而不是抛一串英文错误码；
 *  - **用户取消 / 会话中止**：从失败里摘出来，UI 不该弹红色错误。
 */
object InstallOutcomeMapper {

    const val STATUS_SUCCESS = 0
    const val STATUS_PENDING_USER_ACTION = -1
    const val STATUS_FAILURE = 1
    const val STATUS_FAILURE_BLOCKED = 2
    const val STATUS_FAILURE_ABORTED = 3
    const val STATUS_FAILURE_INVALID = 4
    const val STATUS_FAILURE_CONFLICT = 5
    const val STATUS_FAILURE_STORAGE = 6
    const val STATUS_FAILURE_INCOMPATIBLE = 7
    const val STATUS_FAILURE_TIMEOUT = 8

    fun describe(statusCode: Int, statusMessage: String?): InstallFeedback {
        val message = statusMessage.orEmpty().trim()
        return when (statusCode) {
            STATUS_SUCCESS -> InstallFeedback.Success
            STATUS_PENDING_USER_ACTION -> InstallFeedback.PendingUserAction

            // 会话被中止（含用户在弹窗取消、我们自己 abandonSession）
            STATUS_FAILURE_ABORTED -> InstallFeedback.Cancelled

            STATUS_FAILURE -> failure(
                message = message,
                userMessage = "安装失败：${message.ifBlank { "未知错误" }}",
            )

            STATUS_FAILURE_BLOCKED -> failure(
                message = message,
                userMessage = "安装被系统或设备管理策略拦截，无法完成安装。",
            )

            STATUS_FAILURE_CONFLICT -> failure(
                message = message,
                userMessage = "安装包与本机已装应用冲突，无法覆盖安装。",
            )

            STATUS_FAILURE_INCOMPATIBLE -> failure(
                message = message,
                userMessage = "该安装包与当前 Android 版本不兼容。",
            )

            STATUS_FAILURE_INVALID -> failure(
                message = message,
                userMessage = "安装包无效或已损坏，请删除后重新下载。",
            )

            STATUS_FAILURE_STORAGE -> failure(
                message = message,
                userMessage = "设备存储空间不足，无法安装。请清理空间后重试。",
            )

            STATUS_FAILURE_TIMEOUT -> failure(
                message = message,
                userMessage = "安装超时，可能设备处于繁忙或省电状态，请重试。",
            )

            else -> failure(
                message = message,
                userMessage = "安装失败：${message.ifBlank { "未知错误（状态码 $statusCode）" }}",
            )
        }
    }

    private fun failure(message: String, userMessage: String): InstallFeedback {
        if (looksLikeCancellation(message)) return InstallFeedback.Cancelled
        return InstallFeedback.Failed(
            userMessage = signatureConflictMessage(message) ?: userMessage,
            technicalDetail = message.ifBlank { null },
        )
    }

    /**
     * 识别「签名不一致」这一实测出现过的失败。
     * 无论服务端给的是 CONFLICT 还是笼统的 FAILURE，都统一成可执行的中文指引。
     */
    private fun signatureConflictMessage(message: String): String? {
        val lower = message.lowercase()
        val hit = lower.contains("install_failed_update_incompatible") ||
            lower.contains("update_incompatible") ||
            lower.contains("signatures do not match") ||
            (lower.contains("signature") && lower.contains("not match"))
        return if (hit) {
            "安装包签名与本机已装应用不一致，无法覆盖安装。请先在设置页导出备份，" +
                "卸载旧版本后再安装新版本（卸载会清除应用数据）。"
        } else {
            null
        }
    }

    private fun looksLikeCancellation(message: String): Boolean {
        if (message.isBlank()) return false
        val lower = message.lowercase()
        return lower.contains("cancel") ||
            lower.contains("user declined") ||
            lower.contains("aborted")
    }
}
