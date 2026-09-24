package app.startool.android.update

import app.startool.android.update.model.UpdateCheckResult
import app.startool.android.update.model.UpdateManifest
import app.startool.android.update.model.UpdateProxySource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class UpdateCheckerTest {

    private lateinit var preferenceStore: UpdatePreferenceStore
    private lateinit var fetcher: FakeHttpFetcher
    private var currentTime: Long = 1_000_000_000L

    private val currentAppId = "app.startool.android.gemini"
    private val currentVersionCode = 1
    private val currentVersionName = "0.1.0"
    private val currentSdk = 26

    private val sampleManifest = UpdateManifest(
        schemaVersion = 1,
        applicationId = currentAppId,
        versionCode = 2,
        versionName = "0.2.0",
        minSdk = 26,
        notes = "优化体验，修复已知问题",
        releaseUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.2.0",
    )

    @Before
    fun setup() {
        preferenceStore = UpdatePreferenceStore.inMemory()
        fetcher = FakeHttpFetcher()
        currentTime = 1_000_000_000L
    }

    private fun createChecker(
        sdkInt: Int = currentSdk,
        url: String = UpdateChecker.DEFAULT_UPDATE_URL,
    ): UpdateChecker {
        return UpdateChecker(
            preferenceStore = preferenceStore,
            currentApplicationId = currentAppId,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            httpFetcher = fetcher,
            currentSdkInt = sdkInt,
            clock = { currentTime },
            updateUrl = url,
        )
    }

    @Test
    fun testUpdateAvailableWhenVersionCodeIncreases() = runTest {
        fetcher.defaultResponse = HttpResponse(200, sampleManifest.toJson())
        val checker = createChecker()

        val result = checker.checkUpdate(isManual = true)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals(sampleManifest, available.manifest)
        assertEquals(currentVersionCode, available.currentVersionCode)
        assertEquals(currentVersionName, available.currentVersionName)
    }

    @Test
    fun testUpToDateWhenSameOrOlderVersion() = runTest {
        val checker = createChecker()

        // Same version
        val sameVersionManifest = sampleManifest.copy(versionCode = 1, versionName = "0.1.0")
        fetcher.defaultResponse = HttpResponse(200, sameVersionManifest.toJson())

        val resultSame = checker.checkUpdate(isManual = true)
        assertTrue(resultSame is UpdateCheckResult.UpToDate)
        val upToDateSame = resultSame as UpdateCheckResult.UpToDate
        assertEquals(1, upToDateSame.currentVersionCode)

        // Older version
        val olderVersionManifest = sampleManifest.copy(versionCode = 0, versionName = "0.0.9")
        fetcher.defaultResponse = HttpResponse(200, olderVersionManifest.toJson())

        val resultOlder = checker.checkUpdate(isManual = true)
        assertTrue(resultOlder is UpdateCheckResult.UpToDate)
    }

    @Test
    fun testNoReleaseFoundOnHttp404() = runTest {
        fetcher.defaultResponse = HttpResponse(404, """{"message":"Not Found"}""")
        val checker = createChecker()

        val result = checker.checkUpdate(isManual = true)
        assertEquals(UpdateCheckResult.NoReleaseFound, result)
    }

    @Test
    fun testNoReleaseFoundWhenAssetsEmptyOrMissingManifest() = runTest {
        val checker = createChecker()

        // Empty assets list
        fetcher.defaultResponse = HttpResponse(200, """{"tag_name":"v0.1.0","assets":[]}""")
        val resultEmptyAssets = checker.checkUpdate(isManual = true)
        assertEquals(UpdateCheckResult.NoReleaseFound, resultEmptyAssets)

        // Empty release array
        fetcher.defaultResponse = HttpResponse(200, "[]")
        val resultEmptyArray = checker.checkUpdate(isManual = true)
        assertEquals(UpdateCheckResult.NoReleaseFound, resultEmptyArray)

        // Assets without startool-update.json
        fetcher.defaultResponse = HttpResponse(
            200,
            """
            {
              "tag_name": "v0.1.0",
              "assets": [
                {
                  "name": "other-file.apk",
                  "browser_download_url": "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.1.0/other.apk"
                }
              ]
            }
            """.trimIndent(),
        )
        val resultNoTargetAsset = checker.checkUpdate(isManual = true)
        assertEquals(UpdateCheckResult.NoReleaseFound, resultNoTargetAsset)
    }

    @Test
    fun testInvalidManifestWhenJsonCorruptedOrFieldsMissing() = runTest {
        val checker = createChecker()

        // Malformed json
        fetcher.defaultResponse = HttpResponse(200, "{ invalid json content ...")
        val resultCorrupted = checker.checkUpdate(isManual = true)
        assertTrue(resultCorrupted is UpdateCheckResult.InvalidManifest)

        // Missing required fields (e.g. missing releaseUrl)
        val missingFieldsJson = """
            {
              "schemaVersion": 1,
              "applicationId": "app.startool.android.gemini",
              "versionCode": 2,
              "versionName": "0.2.0",
              "minSdk": 26,
              "notes": "notes"
            }
        """.trimIndent()
        fetcher.defaultResponse = HttpResponse(200, missingFieldsJson)
        val resultMissing = checker.checkUpdate(isManual = true)
        assertTrue(resultMissing is UpdateCheckResult.InvalidManifest)

        // Empty body
        fetcher.defaultResponse = HttpResponse(200, "")
        val resultEmpty = checker.checkUpdate(isManual = true)
        assertTrue(resultEmpty is UpdateCheckResult.InvalidManifest)
    }

    @Test
    fun testInvalidManifestWhenApplicationIdMismatch() = runTest {
        val spoofedManifest = sampleManifest.copy(applicationId = "app.fake.impostor")
        fetcher.defaultResponse = HttpResponse(200, spoofedManifest.toJson())
        val checker = createChecker()

        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.InvalidManifest)
        assertEquals("应用标识不符", (result as UpdateCheckResult.InvalidManifest).message)
    }

    @Test
    fun testInvalidManifestWhenReleaseUrlNotOfficial() = runTest {
        val checker = createChecker()

        // Third-party domain
        val maliciousUrlManifest = sampleManifest.copy(releaseUrl = "https://malicious.example.com/download.apk")
        fetcher.defaultResponse = HttpResponse(200, maliciousUrlManifest.toJson())
        val resultMalicious = checker.checkUpdate(isManual = true)
        assertTrue(resultMalicious is UpdateCheckResult.InvalidManifest)
        assertEquals("非官方下载链接", (resultMalicious as UpdateCheckResult.InvalidManifest).message)

        // Other GitHub repo
        val otherRepoManifest = sampleManifest.copy(releaseUrl = "https://github.com/OtherOrg/OtherRepo/releases")
        fetcher.defaultResponse = HttpResponse(200, otherRepoManifest.toJson())
        val resultOtherRepo = checker.checkUpdate(isManual = true)
        assertTrue(resultOtherRepo is UpdateCheckResult.InvalidManifest)
        assertEquals("非官方下载链接", (resultOtherRepo as UpdateCheckResult.InvalidManifest).message)
    }

    @Test
    fun testIncompatibleSystemWhenMinSdkHigherThanCurrentSdk() = runTest {
        val higherSdkManifest = sampleManifest.copy(minSdk = 33)
        fetcher.defaultResponse = HttpResponse(200, higherSdkManifest.toJson())
        val checker = createChecker(sdkInt = 26)

        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.IncompatibleSystem)
        val incompatible = result as UpdateCheckResult.IncompatibleSystem
        assertEquals(26, incompatible.currentSdk)
        assertEquals(33, incompatible.minSdk)
        assertEquals(higherSdkManifest, incompatible.manifest)
    }

    @Test
    fun testAutoCheckThrottling24Hours() = runTest {
        fetcher.defaultResponse = HttpResponse(200, sampleManifest.toJson())
        val checker = createChecker()

        // 1. Initial auto check: lastAutoCheckTimestamp is 0, so it should run
        val result1 = checker.checkUpdate(isManual = false)
        assertTrue(result1 is UpdateCheckResult.UpdateAvailable)
        assertEquals(1, fetcher.fetchCount)
        assertEquals(currentTime, preferenceStore.lastAutoCheckTimestamp)

        // 2. 23 hours later: within 24 hours, should skip network check
        currentTime += 23 * 3600 * 1000L
        val result2 = checker.checkUpdate(isManual = false)
        assertTrue(result2 is UpdateCheckResult.UpToDate)
        assertEquals(1, fetcher.fetchCount) // no additional network call

        // 3. 25 hours later (2 hours after previous): total 25 hours since last check -> should trigger
        currentTime += 2 * 3600 * 1000L
        val result3 = checker.checkUpdate(isManual = false)
        assertTrue(result3 is UpdateCheckResult.UpdateAvailable)
        assertEquals(2, fetcher.fetchCount) // network called again
        assertEquals(currentTime, preferenceStore.lastAutoCheckTimestamp)
    }

    @Test
    fun testRemindLaterBehavior() = runTest {
        fetcher.defaultResponse = HttpResponse(200, sampleManifest.toJson())
        val checker = createChecker()

        // User marks remind later
        checker.markRemindLater()
        assertEquals(currentTime, preferenceStore.remindLaterTimestamp)

        // Advance 2 hours (< 24 hours)
        currentTime += 2 * 3600 * 1000L

        // Automatic check should be skipped
        val autoResult = checker.checkUpdate(isManual = false)
        assertTrue(autoResult is UpdateCheckResult.UpToDate)
        assertEquals(0, fetcher.fetchCount)

        // Manual check should ignore remindLater and show update
        val manualResult = checker.checkUpdate(isManual = true)
        assertTrue(manualResult is UpdateCheckResult.UpdateAvailable)
        assertEquals(1, fetcher.fetchCount)
    }

    @Test
    fun testIgnoredVersionBehavior() = runTest {
        fetcher.defaultResponse = HttpResponse(200, sampleManifest.toJson())
        val checker = createChecker()

        // Ignore version 2
        checker.ignoreVersion(2)
        assertEquals(2, preferenceStore.ignoredVersionCode)

        // Automatic check should return UpToDate
        val autoResult = checker.checkUpdate(isManual = false)
        assertTrue(autoResult is UpdateCheckResult.UpToDate)

        // Manual check should still return UpdateAvailable
        val manualResult = checker.checkUpdate(isManual = true)
        assertTrue(manualResult is UpdateCheckResult.UpdateAvailable)
    }

    @Test
    fun testConcurrentRequestsCoalescedIntoSingleFetch() = runTest {
        fetcher.handler = { url ->
            delay(50) // simulate network latency
            HttpResponse(200, sampleManifest.toJson())
        }
        val checker = createChecker()

        // Launch 5 concurrent manual checks
        val deferreds = (1..5).map {
            async { checker.checkUpdate(isManual = true) }
        }
        val results = deferreds.awaitAll()

        // All 5 calls receive the expected UpdateAvailable result
        assertEquals(5, results.size)
        results.forEach { result ->
            assertTrue(result is UpdateCheckResult.UpdateAvailable)
            assertEquals(2, (result as UpdateCheckResult.UpdateAvailable).manifest.versionCode)
        }

        // Only 1 network fetch was made!
        assertEquals(1, fetcher.fetchCount)

        // Subsequent call after completion initiates a new fetch
        val resultAfter = checker.checkUpdate(isManual = true)
        assertTrue(resultAfter is UpdateCheckResult.UpdateAvailable)
        assertEquals(2, fetcher.fetchCount)
    }

    @Test
    fun testGitHubReleaseWithAssetResolution() = runTest {
        val assetUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.2.0/startool-update.json"
        val releaseJson = """
            {
              "tag_name": "v0.2.0",
              "assets": [
                {
                  "name": "startool-update.json",
                  "browser_download_url": "$assetUrl"
                }
              ]
            }
        """.trimIndent()

        fetcher.handler = { requestedUrl ->
            if (requestedUrl == UpdateChecker.DEFAULT_UPDATE_URL) {
                HttpResponse(200, releaseJson)
            } else if (requestedUrl == assetUrl) {
                HttpResponse(200, sampleManifest.toJson())
            } else {
                HttpResponse(404, null)
            }
        }

        val checker = createChecker()
        val result = checker.checkUpdate(isManual = true)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals(2, fetcher.fetchCount)
        assertEquals(listOf(UpdateChecker.DEFAULT_UPDATE_URL, assetUrl), fetcher.requestedUrls)
    }

    @Test
    fun testAutoCheckDisabled() = runTest {
        fetcher.defaultResponse = HttpResponse(200, sampleManifest.toJson())
        val checker = createChecker()

        checker.setAutoCheckEnabled(false)
        assertFalse(preferenceStore.autoCheckEnabled)

        // Auto check returns UpToDate without network call
        val autoResult = checker.checkUpdate(isManual = false)
        assertTrue(autoResult is UpdateCheckResult.UpToDate)
        assertEquals(0, fetcher.fetchCount)

        // Manual check still works
        val manualResult = checker.checkUpdate(isManual = true)
        assertTrue(manualResult is UpdateCheckResult.UpdateAvailable)
        assertEquals(1, fetcher.fetchCount)
    }

    @Test
    fun testNetworkExceptionReturnsNetworkError() = runTest {
        fetcher.handler = {
            throw IOException("Socket timeout connecting to server")
        }
        val checker = createChecker()

        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.NetworkError)
        val error = result as UpdateCheckResult.NetworkError
        assertEquals("Socket timeout connecting to server", error.message)
        assertTrue(error.cause is IOException)

        // Network error should not update lastAutoCheckTimestamp
        assertEquals(0L, preferenceStore.lastAutoCheckTimestamp)
    }

    @Test
    fun testHttpErrorStatusCodeReturnsNetworkError() = runTest {
        fetcher.defaultResponse = HttpResponse(503, "Service Unavailable")
        val checker = createChecker()

        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.NetworkError)
        assertEquals("HTTP 503", (result as UpdateCheckResult.NetworkError).message)
    }

    @Test
    fun testClearIgnoredVersion() {
        val checker = createChecker()
        checker.ignoreVersion(5)
        assertEquals(5, preferenceStore.ignoredVersionCode)

        checker.clearIgnoredVersion()
        assertNull(preferenceStore.ignoredVersionCode)
    }

    @Test
    fun testProxySourceWrapsDownloadUrl() {
        val checker = createChecker()
        preferenceStore.proxySource = UpdateProxySource.GhProxyNet
        val proxied = checker.getEffectiveReleaseUrl(sampleManifest)
        assertEquals("https://ghproxy.net/https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.2.0", proxied)

        preferenceStore.proxySource = UpdateProxySource.Custom
        preferenceStore.customProxyPrefix = "https://custom.cf-proxy.com"
        val customProxied = checker.getEffectiveReleaseUrl(sampleManifest)
        assertEquals("https://custom.cf-proxy.com/https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.2.0", customProxied)

        preferenceStore.proxySource = UpdateProxySource.Direct
        val direct = checker.getEffectiveReleaseUrl(sampleManifest)
        assertEquals(sampleManifest.releaseUrl, direct)
    }

    @Test
    fun testProxySourcePullsManifestDirectly() = runTest {
        preferenceStore.proxySource = UpdateProxySource.GhProxyNet
        fetcher.handler = { url ->
            if (url == "https://ghproxy.net/https://github.com/SakuraLoveSmile/StarTool/releases/latest/download/startool-update.json") {
                HttpResponse(200, sampleManifest.toJson())
            } else {
                HttpResponse(500, "Error")
            }
        }
        val checker = createChecker()
        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals(sampleManifest, (result as UpdateCheckResult.UpdateAvailable).manifest)
    }

    @Test
    fun testDirectSourceFallbacksToGhProxyOnApiFailure() = runTest {
        preferenceStore.proxySource = UpdateProxySource.Direct
        fetcher.handler = { url ->
            if (url == UpdateChecker.DEFAULT_UPDATE_URL) {
                HttpResponse(503, "Service Unavailable")
            } else if (url == "https://ghproxy.net/https://github.com/SakuraLoveSmile/StarTool/releases/latest/download/startool-update.json") {
                HttpResponse(200, sampleManifest.toJson())
            } else {
                HttpResponse(404, "Not Found")
            }
        }
        val checker = createChecker()
        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals(sampleManifest, (result as UpdateCheckResult.UpdateAvailable).manifest)
    }

    @Test
    fun testProxySourceReturnsNoReleaseFoundWhen404() = runTest {
        preferenceStore.proxySource = UpdateProxySource.GhProxyNet
        fetcher.handler = { url ->
            if (url == "https://ghproxy.net/https://github.com/SakuraLoveSmile/StarTool/releases/latest/download/startool-update.json") {
                HttpResponse(404, "Not Found")
            } else {
                HttpResponse(500, "Error")
            }
        }
        val checker = createChecker()
        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.NoReleaseFound)
    }

    @Test
    fun testDirectSourceReturnsNoReleaseFoundWhenApiBlockedAndFallback404() = runTest {
        preferenceStore.proxySource = UpdateProxySource.Direct
        fetcher.handler = { url ->
            if (url == UpdateChecker.DEFAULT_UPDATE_URL) {
                HttpResponse(403, "rate limit exceeded")
            } else if (url == "https://ghproxy.net/https://github.com/SakuraLoveSmile/StarTool/releases/latest/download/startool-update.json") {
                HttpResponse(404, "Not Found")
            } else {
                HttpResponse(500, "Error")
            }
        }
        val checker = createChecker()
        val result = checker.checkUpdate(isManual = true)
        assertTrue(result is UpdateCheckResult.NoReleaseFound)
    }
}

class FakeHttpFetcher : HttpFetcher {
    var defaultResponse: HttpResponse = HttpResponse(200, "{}")
    var handler: (suspend (url: String) -> HttpResponse)? = null
    val requestedUrls = mutableListOf<String>()
    var fetchCount = 0

    override suspend fun fetch(url: String): HttpResponse {
        fetchCount++
        requestedUrls.add(url)
        return handler?.invoke(url) ?: defaultResponse
    }
}
