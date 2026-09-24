package app.startool.android.update

import app.startool.android.update.model.DownloadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 一次下载的最终结果。协程被取消时不返回本类型，而是抛出 CancellationException。 */
sealed interface DownloadOutcome {
    data class Completed(val file: File, val totalBytes: Long?) : DownloadOutcome
    data class Failed(val error: DownloadError) : DownloadOutcome
}

/**
 * P2：APK 流式下载抽象。
 *
 * 与 [HttpFetcher] 分开的原因：后者把响应体整个读成 String（HttpFetcher.kt:50），
 * 适合几 KB 的 JSON，但 APK 有 8MB+ 且是二进制，必须流式写盘并回报进度。
 */
interface ApkDownloader {
    /**
     * @param resumeFromBytes 从该偏移续传；服务端不支持 Range 时自动从头重下。
     * @param onProgress 每读一块回调一次（实现内部已节流），参数为已下载字节与总字节（未知为 null）。
     */
    suspend fun download(
        url: String,
        targetFile: File,
        resumeFromBytes: Long,
        onProgress: suspend (loaded: Long, total: Long?) -> Unit,
    ): DownloadOutcome
}

class DefaultApkDownloader(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxRedirects: Int = 5,
    private val bufferSize: Int = 64 * 1024,
    private val progressIntervalMs: Long = 120L,
) : ApkDownloader {

    override suspend fun download(
        url: String,
        targetFile: File,
        resumeFromBytes: Long,
        onProgress: suspend (loaded: Long, total: Long?) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        var currentUrl = url
        var redirects = 0
        var loaded = 0L

        try {
            var connection: HttpURLConnection? = null
            var finalCode = 0

            // 手动跟随重定向：GitHub asset 会 302 到 release-assets，
            // 自动跟随会让 Range 头的行为难以预测。
            while (true) {
                val conn = open(currentUrl, resumeFromBytes)
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank() || redirects >= maxRedirects) {
                        return@withContext DownloadOutcome.Failed(DownloadError.Http(code))
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    return@withContext DownloadOutcome.Failed(DownloadError.Http(code))
                }
                connection = conn
                finalCode = code
                break
            }

            val conn = connection
                ?: return@withContext DownloadOutcome.Failed(DownloadError.Network("无法建立下载连接"))

            val isPartial = finalCode == HttpURLConnection.HTTP_PARTIAL
            val startOffset = if (isPartial && resumeFromBytes > 0L) resumeFromBytes else 0L
            val contentLength = conn.contentLengthLong.takeIf { it > 0L }
            val totalBytes = when {
                contentLength == null -> null
                isPartial -> contentLength + startOffset
                else -> contentLength
            }

            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            if (startOffset == 0L && targetFile.exists()) targetFile.delete()

            loaded = startOffset
            var lastEmit = 0L
            val buffer = ByteArray(bufferSize)

            FileOutputStream(targetFile, startOffset > 0L).use { out ->
                conn.inputStream.use { input ->
                    // 先发一次，让进度条立刻有值而不是卡在 0%
                    onProgress(loaded, totalBytes)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        loaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= progressIntervalMs) {
                            lastEmit = now
                            onProgress(loaded, totalBytes)
                        }
                    }
                }
                out.flush()
            }
            conn.disconnect()

            // 服务端提前断流：read 返回 -1 而字节数不够，必须当作中断而不是成功，
            // 否则截断的 APK 会被当成完整文件送进安装器。
            if (totalBytes != null && loaded < totalBytes) {
                return@withContext DownloadOutcome.Failed(
                    DownloadError.Interrupted(loaded, "连接提前结束"),
                )
            }

            onProgress(loaded, totalBytes ?: loaded)
            DownloadOutcome.Completed(targetFile, totalBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            DownloadOutcome.Failed(
                if (loaded > 0L) {
                    DownloadError.Interrupted(loaded, e.message ?: "连接中断")
                } else {
                    DownloadError.Network(e.message ?: "网络请求失败", e)
                },
            )
        } catch (e: Throwable) {
            DownloadOutcome.Failed(DownloadError.Network(e.message ?: "下载失败", e))
        }
    }

    private fun open(url: String, resumeFromBytes: Long): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "StarTool-Android")
        conn.setRequestProperty("Accept", "application/vnd.android.package-archive, */*")
        if (resumeFromBytes > 0L) {
            conn.setRequestProperty("Range", "bytes=$resumeFromBytes-")
        }
        return conn
    }
}
