package app.startool.android.domain

import android.net.Uri

/**
 * 平台文件桥接：把 SAF（Storage Access Framework）操作抽象给 backup 模块使用。
 * 由 MainActivity 实现；单元测试可用内存实现替代。
 *
 * 所有方法都可能因用户取消返回 null（选择器类），或因 IO 抛异常。
 */
interface BackupFileGateway {

    /**
     * 弹出系统"保存到"选择器。
     * @param suggestedFileName 建议文件名，如 StarTool-backup-20260923-141530.json
     * @return 用户选择的 Uri；取消返回 null。
     */
    suspend fun pickSaveLocation(suggestedFileName: String): Uri?

    /**
     * 弹出系统"打开文件"选择器（application/json 及通用类型）。
     * @return 用户选择的 Uri；取消返回 null。
     */
    suspend fun pickOpenFile(): Uri?

    /**
     * 读出文件全部字节。调用方负责大小上限校验。
     * @return 文件内容；失败返回 null 或抛 IOException。
     */
    suspend fun readAll(uri: Uri): ByteArray?

    /**
     * 向 Uri 写入全部字节（截断模式）。
     * @return true 成功。
     */
    suspend fun writeAll(uri: Uri, bytes: ByteArray): Boolean

    /**
     * 尝试删除文件（仅用于清理本次导出失败产生的不完整文件）。
     * 失败不抛异常，返回 false。
     */
    suspend fun delete(uri: Uri): Boolean
}
