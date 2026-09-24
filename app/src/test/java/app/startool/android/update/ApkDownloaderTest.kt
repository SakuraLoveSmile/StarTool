package app.startool.android.update

import app.startool.android.update.model.DownloadError
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * 对 [DefaultApkDownloader] 做真实协议层验证。
 *
 * 用纯 java.net.ServerSocket 起一个极小的 HTTP/1.1 服务，而不是
 * com.sun.net.httpserver —— 后者在 Android 单元测试的编译类路径里不可见。
 * 不 mock HttpURLConnection：重定向、Range、Content-Length 这些行为正是要测的东西。
 */
class ApkDownloaderTest {

    private lateinit var server: MiniHttpServer
    private lateinit var dir: File
    private val payload = ByteArray(200_000) { (it % 251).toByte() }

    private val downloader = DefaultApkDownloader(
        connectTimeoutMs = 3_000,
        readTimeoutMs = 3_000,
        progressIntervalMs = 0L, // 测试里每次都发进度，方便断言
    )

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "startool-dl-${System.nanoTime()}")
        dir.mkdirs()
        server = MiniHttpServer { _, _ -> MiniHttpServer.Response(404, ByteArray(0)) }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    private fun url(path: String) = "http://127.0.0.1:${server.port}$path"
    private fun target(name: String) = File(dir, name)

    @Test
    fun downloadsWholeFileAndReportsProgress() {
        server.handler = { path, _ ->
            if (path == "/ok") {
                MiniHttpServer.Response(200, payload)
            } else {
                MiniHttpServer.Response(404, ByteArray(0))
            }
        }
        val file = target("ok.apk")
        val reports = mutableListOf<Pair<Long, Long?>>()

        val outcome = runBlocking {
            downloader.download(url("/ok"), file, 0L) { loaded, total -> reports.add(loaded to total) }
        }

        assertTrue("expected Completed, got $outcome", outcome is DownloadOutcome.Completed)
        assertEquals(payload.size.toLong(), file.length())
        assertTrue("progress should be reported at least once", reports.isNotEmpty())
        assertEquals(payload.size.toLong(), reports.last().first)
        assertEquals(payload.size.toLong(), reports.last().second)
        // 进度必须单调不减
        assertTrue(reports.zipWithNext().all { (a, b) -> b.first >= a.first })
    }

    @Test
    fun httpErrorIsSurfacedWithStatusCode() {
        server.handler = { _, _ -> MiniHttpServer.Response(404, ByteArray(0)) }

        val outcome = runBlocking {
            downloader.download(url("/missing"), target("404.apk"), 0L) { _, _ -> }
        }

        assertTrue("expected Failed, got $outcome", outcome is DownloadOutcome.Failed)
        assertEquals(DownloadError.Http(404), (outcome as DownloadOutcome.Failed).error)
    }

    @Test
    fun truncatedBodyIsReportedAsInterruptedNotCompleted() {
        // 声明 500000 字节却只发 1000 字节 —— 模拟服务端提前断流
        server.handler = { _, _ ->
            MiniHttpServer.Response(200, payload.copyOfRange(0, 1_000), declaredLength = 500_000L)
        }
        val file = target("truncated.apk")

        val outcome = runBlocking {
            downloader.download(url("/truncated"), file, 0L) { _, _ -> }
        }

        // 关键：提前断流绝不能被当成 Completed，否则截断的 APK 会被送去安装
        assertTrue("expected Failed, got $outcome", outcome is DownloadOutcome.Failed)
        val error = (outcome as DownloadOutcome.Failed).error
        assertTrue("expected Interrupted, got $error", error is DownloadError.Interrupted)
        val loaded = (error as DownloadError.Interrupted).loadedBytes
        assertTrue("loadedBytes should be partial, was $loaded", loaded in 1 until 500_000L)
    }

    @Test
    fun resumeFromRangeServerCompletesWithCorrectTotal() {
        server.handler = { _, headers ->
            val start = parseRangeStart(headers["Range"])
            if (start in 1 until payload.size) {
                MiniHttpServer.Response(206, payload.copyOfRange(start, payload.size))
            } else {
                MiniHttpServer.Response(200, payload)
            }
        }
        val file = target("resume.apk")
        val half = payload.size / 2
        file.writeBytes(payload.copyOfRange(0, half))

        val outcome = runBlocking {
            downloader.download(url("/range"), file, half.toLong()) { _, _ -> }
        }

        assertTrue("expected Completed, got $outcome", outcome is DownloadOutcome.Completed)
        assertTrue("resumed file content must match exactly", file.readBytes().contentEquals(payload))
        // 206 的 Content-Length 是剩余量，总长必须还原成完整体积
        val total = (outcome as DownloadOutcome.Completed).totalBytes
        assertEquals(payload.size.toLong(), total)
    }

    @Test
    fun serverIgnoringRangeRestartsFromZeroInsteadOfAppending() {
        // 故意忽略 Range，永远回 200 全量
        server.handler = { _, _ -> MiniHttpServer.Response(200, payload) }
        val file = target("no-range.apk")
        // 预置一段垃圾断点：如果实现盲目 append，最终文件会是「垃圾 + payload」
        file.writeBytes(ByteArray(10_000) { 0xAA.toByte() })

        val outcome = runBlocking {
            downloader.download(url("/no-range"), file, 10_000L) { _, _ -> }
        }

        assertTrue("expected Completed, got $outcome", outcome is DownloadOutcome.Completed)
        assertEquals(payload.size.toLong(), file.length())
        assertTrue(file.readBytes().contentEquals(payload))
    }

    @Test
    fun redirectIsFollowed() {
        server.handler = { path, _ ->
            when (path) {
                "/redirect" -> MiniHttpServer.Response(
                    code = 302,
                    body = ByteArray(0),
                    headers = mapOf("Location" to url("/ok")),
                )
                "/ok" -> MiniHttpServer.Response(200, payload)
                else -> MiniHttpServer.Response(404, ByteArray(0))
            }
        }
        val file = target("redirect.apk")

        val outcome = runBlocking {
            downloader.download(url("/redirect"), file, 0L) { _, _ -> }
        }

        assertTrue("expected Completed, got $outcome", outcome is DownloadOutcome.Completed)
        assertTrue(file.readBytes().contentEquals(payload))
    }

    @Test
    fun unknownContentLengthFallsBackToIndeterminateProgress() {
        // declaredLength = -1 表示不写 Content-Length，客户端拿不到总长
        server.handler = { _, _ -> MiniHttpServer.Response(200, payload, declaredLength = -1L) }
        val file = target("no-length.apk")
        val reports = mutableListOf<Pair<Long, Long?>>()

        val outcome = runBlocking {
            downloader.download(url("/nolength"), file, 0L) { l, t -> reports.add(l to t) }
        }

        assertTrue("expected Completed, got $outcome", outcome is DownloadOutcome.Completed)
        assertTrue(file.readBytes().contentEquals(payload))
        assertTrue(reports.isNotEmpty())
        // 总长未知时必须回 null，UI 才知道走不确定进度条
        assertEquals(null, reports.dropLast(1).firstOrNull()?.second)
    }

    private fun parseRangeStart(header: String?): Int {
        if (header == null || !header.startsWith("bytes=")) return 0
        return header.removePrefix("bytes=").substringBefore("-").toIntOrNull() ?: 0
    }

    /** 极简 HTTP/1.1 服务器：够用即可，只为驱动真实的 HttpURLConnection。 */
    private class MiniHttpServer(var handler: (path: String, headers: Map<String, String>) -> Response) {

        data class Response(
            val code: Int,
            val body: ByteArray,
            val declaredLength: Long = body.size.toLong(),
            val headers: Map<String, String> = emptyMap(),
        )

        private val serverSocket = ServerSocket(0)
        @Volatile private var running = true

        val port: Int get() = serverSocket.localPort

        fun start() {
            val acceptor = Thread {
                while (running) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: IOException) {
                        break
                    }
                    Thread { serve(socket) }.apply { isDaemon = true }.start()
                }
            }
            acceptor.isDaemon = true
            acceptor.start()
        }

        fun stop() {
            running = false
            runCatching { serverSocket.close() }
        }

        private fun serve(socket: Socket) {
            try {
                socket.use { s ->
                    s.soTimeout = 5_000
                    val request = readRequest(s.getInputStream()) ?: return
                    val response = handler(request.first, request.second)
                    val out = s.getOutputStream()
                    out.write(render(response).toByteArray(Charsets.US_ASCII))
                    out.write(response.body)
                    out.flush()
                }
            } catch (_: Exception) {
                // 测试用服务端，客户端提前断开属于预期
            }
        }

        private fun render(response: Response): String = buildString {
            append("HTTP/1.1 ${response.code} ${reason(response.code)}\r\n")
            if (response.declaredLength >= 0) {
                append("Content-Length: ${response.declaredLength}\r\n")
            }
            append("Connection: close\r\n")
            response.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }

        private fun reason(code: Int): String = when (code) {
            200 -> "OK"
            206 -> "Partial Content"
            302 -> "Found"
            404 -> "Not Found"
            else -> "Status"
        }

        private fun readRequest(input: InputStream): Pair<String, Map<String, String>>? {
            val sb = StringBuilder()
            while (!sb.endsWith("\r\n\r\n")) {
                val c = input.read()
                if (c < 0) return null
                sb.append(c.toChar())
                if (sb.length > 16_384) return null
            }
            val lines = sb.toString().split("\r\n")
            val path = lines.firstOrNull()?.split(" ")?.getOrNull(1) ?: "/"
            val headers = lines.drop(1).mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
            }.toMap()
            return path to headers
        }
    }
}
