package app.startool.android.update

import app.startool.android.update.model.UpdateManifest
import app.startool.android.update.model.UpdateProxySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApkSourceResolverTest {

    private val officialApkUrl =
        "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.1.2/StarTool-v0.1.2-release.apk"

    private fun manifest(
        apkUrl: String? = officialApkUrl,
        apkSha256: String? = "b".repeat(64),
        apkSizeBytes: Long? = 8_575_734L,
    ) = UpdateManifest(
        schemaVersion = 2,
        applicationId = "app.startool.android.gemini",
        versionCode = 3,
        versionName = "0.1.2",
        minSdk = 26,
        notes = "更新说明",
        releaseUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.1.2",
        apkUrl = apkUrl,
        apkSha256 = apkSha256,
        apkSizeBytes = apkSizeBytes,
    )

    private fun resolver(
        proxy: UpdateProxySource = UpdateProxySource.Direct,
        customPrefix: String = "",
    ) = ApkSourceResolver(
        UpdatePreferenceStore.inMemory(proxySource = proxy, customProxyPrefix = customPrefix),
    )

    @Test
    fun missingApkUrlReturnsNull_fallsBackToBrowser() {
        assertNull(resolver().resolve(manifest(apkUrl = null)))
        assertNull(resolver().resolve(manifest(apkUrl = "")))
        assertNull(resolver().resolve(manifest(apkUrl = "   ")))
    }

    @Test
    fun foreignHostIsRejected() {
        assertNull(resolver().resolve(manifest(apkUrl = "https://evil.example.com/x.apk")))
        assertNull(
            resolver().resolve(
                manifest(apkUrl = "http://github.com/SakuraLoveSmile/StarTool/releases/download/v0.1.2/a.apk"),
            ),
        )
    }

    @Test
    fun nonApkExtensionIsRejected() {
        assertNull(
            resolver().resolve(
                manifest(apkUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/latest/download/startool-update.json"),
            ),
        )
    }

    @Test
    fun urlWhitespaceHandling() {
        // 前后空白会先被 trim（清单手写笔误场景），因此仍然可用
        assertEquals(
            officialApkUrl,
            resolver().resolve(manifest(apkUrl = "$officialApkUrl "))!!.url,
        )
        // URL 中间出现空白一定是非法地址，必须拒绝
        assertNull(resolver().resolve(manifest(apkUrl = "https://github.com/SakuraLoveSmile/StarTool/a b.apk")))
    }

    @Test
    fun directProxyKeepsUrlUntouched() {
        val source = resolver().resolve(manifest())
        assertNotNull(source)
        assertEquals(officialApkUrl, source!!.url)
    }

    @Test
    fun ghProxyWrapsUrl() {
        val source = resolver(proxy = UpdateProxySource.GhProxyNet).resolve(manifest())
        assertEquals("https://ghproxy.net/$officialApkUrl", source!!.url)
    }

    @Test
    fun customProxyWrapsUrlAndNormalisesTrailingSlash() {
        val source = resolver(
            proxy = UpdateProxySource.Custom,
            customPrefix = "https://my-proxy.example",
        ).resolve(manifest())
        assertEquals("https://my-proxy.example/$officialApkUrl", source!!.url)
    }

    @Test
    fun sha256IsLowercasedAndTrimmed() {
        val source = resolver().resolve(manifest(apkSha256 = "  ${"A".repeat(64)}  "))
        assertEquals("a".repeat(64), source!!.expectedSha256)
    }

    @Test
    fun malformedSha256IsDropped_soVerifierCanSkipHashCheck() {
        assertNull(resolver().resolve(manifest(apkSha256 = "zz"))!!.expectedSha256)
        assertNull(resolver().resolve(manifest(apkSha256 = "g".repeat(64)))!!.expectedSha256)
        assertNull(resolver().resolve(manifest(apkSha256 = null))!!.expectedSha256)
    }

    @Test
    fun nonPositiveSizeIsDropped() {
        assertNull(resolver().resolve(manifest(apkSizeBytes = 0L))!!.expectedSizeBytes)
        assertNull(resolver().resolve(manifest(apkSizeBytes = -1L))!!.expectedSizeBytes)
        assertEquals(
            8_575_734L,
            resolver().resolve(manifest(apkSizeBytes = 8_575_734L))!!.expectedSizeBytes,
        )
    }
}
