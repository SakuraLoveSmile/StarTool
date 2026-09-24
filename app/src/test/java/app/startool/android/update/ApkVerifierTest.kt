package app.startool.android.update

import app.startool.android.update.model.DownloadError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ApkVerifierTest {

    private lateinit var dir: File
    private lateinit var file: File

    private val payload = "StarTool fake apk payload for verifier test".toByteArray()
    private val realSha = sha256Of(payload)

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "startool-verifier-${System.nanoTime()}")
        dir.mkdirs()
        file = File(dir, "test.apk")
        file.writeBytes(payload)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun source(
        size: Long? = payload.size.toLong(),
        sha: String? = realSha,
    ) = ApkSource(
        url = "https://github.com/SakuraLoveSmile/StarTool/releases/download/v1.0.0/a.apk",
        expectedSizeBytes = size,
        expectedSha256 = sha,
    )

    private fun failed(result: VerifyResult): DownloadError {
        assertTrue("expected Failed but was $result", result is VerifyResult.Failed)
        return (result as VerifyResult.Failed).error
    }

    @Test
    fun okWhenSizeAndHashMatch() {
        assertEquals(VerifyResult.Ok, ApkVerifier().verify(file, source()))
    }

    @Test
    fun missingFileIsStorageError() {
        file.delete()
        val error = failed(ApkVerifier().verify(file, source()))
        assertTrue(error is DownloadError.Storage)
    }

    @Test
    fun sizeMismatchCarriesExpectedAndActual() {
        val error = failed(ApkVerifier().verify(file, source(size = payload.size + 10L)))
        assertTrue(error is DownloadError.SizeMismatch)
        error as DownloadError.SizeMismatch
        assertEquals(payload.size + 10L, error.expected)
        assertEquals(payload.size.toLong(), error.actual)
    }

    @Test
    fun hashMismatchIsReported() {
        val error = failed(ApkVerifier().verify(file, source(sha = "0".repeat(64))))
        assertTrue(error is DownloadError.HashMismatch)
        val mismatch = error as DownloadError.HashMismatch
        // expected 是清单声明的哈希，actual 是从文件实算出来的
        assertEquals("0".repeat(64), mismatch.expected)
        assertEquals(realSha, mismatch.actual)
    }
    @Test
    fun sizeIsCheckedBeforeHash_soTruncatedFileFailsFast() {
        // 体积和哈希都不对时，必须先报体积：这正是「服务端提前断流」的典型形态
        val error = failed(
            ApkVerifier().verify(file, source(size = 1L, sha = "0".repeat(64))),
        )
        assertTrue(error is DownloadError.SizeMismatch)
    }

    @Test
    fun nullExpectedShaSkipsHashCheck() {
        assertEquals(VerifyResult.Ok, ApkVerifier().verify(file, source(sha = null)))
    }

    @Test
    fun nullExpectedSizeSkipsSizeCheck() {
        assertEquals(VerifyResult.Ok, ApkVerifier().verify(file, source(size = null)))
    }

    @Test
    fun sha256IsLowercase64Hex() {
        val hex = ApkVerifier().sha256(file)
        assertEquals(64, hex.length)
        assertEquals(realSha, hex)
        assertTrue(hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun toHexMatchesJdkDigestOutput() {
        val bytes = byteArrayOf(0x00, 0x0f, 0x10, 0x7f, 0x80.toByte(), 0xff.toByte())
        assertEquals("000f107f80ff", ApkVerifier.toHex(bytes))
    }

    private fun sha256Of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
