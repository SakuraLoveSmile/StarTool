package app.startool.android.update

import app.startool.android.update.model.DownloadError
import java.io.File
import java.security.MessageDigest

/** 下载完成后的校验结论。 */
sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Failed(val error: DownloadError) : VerifyResult
}

/**
 * P2：安装包完整性校验。
 *
 * 顺序刻意设计为「先体积后哈希」：
 *  - 体积不符能在毫秒级内发现，且能抓住服务端提前断流的截断下载；
 *  - 哈希需要全量读盘，放在最后；清单没给哈希时降级为只校验体积。
 *
 * 本类不依赖 Android API，可在 JVM 单元测试中直接使用。
 */
class ApkVerifier {

    fun verify(file: File, source: ApkSource): VerifyResult {
        if (!file.exists() || !file.isFile) {
            return VerifyResult.Failed(DownloadError.Storage("安装包不存在或不可读"))
        }
        val actualSize = file.length()
        val expectedSize = source.expectedSizeBytes
        if (expectedSize != null && actualSize != expectedSize) {
            return VerifyResult.Failed(DownloadError.SizeMismatch(expectedSize, actualSize))
        }
        val expectedSha = source.expectedSha256
        if (expectedSha != null) {
            val actualSha = sha256(file)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                return VerifyResult.Failed(DownloadError.HashMismatch(expectedSha, actualSha))
            }
        }
        return VerifyResult.Ok
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return toHex(digest.digest())
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val HEX = "0123456789abcdef"

        /**
         * 手写十六进制而非 String.format("%02x") / HexFormat：
         *  - HexFormat 需要 Android API 33，本项目 minSdk 26；
         *  - %02x 受默认 Locale 影响，部分语言环境下数字位会本地化。
         */
        fun toHex(bytes: ByteArray): String {
            val out = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                out.append(HEX[v ushr 4])
                out.append(HEX[v and 0x0F])
            }
            return out.toString()
        }
    }
}
