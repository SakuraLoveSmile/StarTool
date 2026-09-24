package app.startool.android.update

import app.startool.android.update.model.DownloadError
import app.startool.android.update.model.UpdateDownloadState
import app.startool.android.update.model.UpdateManifest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateDownloadManagerTest {

    private lateinit var dir: File
    private val payload = "StarTool in-app update payload".toByteArray()
    private val payloadSha = sha256Of(payload)

    private lateinit var fake: FakeDownloader

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "startool-mgr-${System.nanoTime()}")
        dir.mkdirs()
        fake = FakeDownloader()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun manifest(
        versionCode: Int = 3,
        apkUrl: String? = "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.1.2/a.apk",
        sha: String? = payloadSha,
        size: Long? = payload.size.toLong(),
    ) = UpdateManifest(
        schemaVersion = 2,
        applicationId = "app.startool.android.gemini",
        versionCode = versionCode,
        versionName = "0.1.2",
        minSdk = 26,
        notes = "n",
        releaseUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.1.2",
        apkUrl = apkUrl,
        apkSha256 = sha,
        apkSizeBytes = size,
    )

    /** 用扩展接收者拿到 runTest 的 TestScope 作为下载用的 CoroutineScope。 */
    private fun CoroutineScope.manager(currentVersionCode: Int = 2) = UpdateDownloadManager(
        downloadDirProvider = { dir },
        scope = this,
        preferenceStore = UpdatePreferenceStore.inMemory(),
        currentVersionCode = currentVersionCode,
        downloader = fake,
    )

    // ---------------- start 的准入判定 ----------------

    @Test
    fun startReturnsFalseWhenManifestHasNoApkUrl_soCallerFallsBackToBrowser() = runTest {
        val mgr = manager()
        assertFalse(mgr.start(manifest(apkUrl = null)))
        assertEquals(UpdateDownloadState.Idle, mgr.state.value)
    }

    @Test
    fun startReturnsFalseWhenTargetIsNotNewer() = runTest {
        val mgr = manager(currentVersionCode = 5)
        assertFalse(mgr.start(manifest(versionCode = 5)))
        assertFalse(mgr.start(manifest(versionCode = 4)))
        assertEquals(UpdateDownloadState.Idle, mgr.state.value)
    }

    // ---------------- 正常链路 ----------------

    @Test
    fun happyPathGoesThroughVerifyingToReady() = runTest {
        fake.outcome = { target ->
            target.writeBytes(payload)
            DownloadOutcome.Completed(target, payload.size.toLong())
        }
        val mgr = manager()
        assertTrue(mgr.start(manifest()))
        advanceUntilIdle()

        val state = mgr.state.value
        assertTrue("expected Ready, got $state", state is UpdateDownloadState.Ready)
        val ready = state as UpdateDownloadState.Ready
        assertEquals("0.1.2", ready.targetVersionName)
        assertEquals(3, ready.targetVersionCode)
        assertTrue(ready.file.exists())
        assertTrue(ready.file.name.endsWith(".apk"))
        assertFalse(ready.file.name.endsWith(".part"))
        assertEquals(ready.file, mgr.readyFile)
        // 校验通过后断点文件不应残留
        assertNull(dir.list()?.firstOrNull { it.endsWith(".part") })
    }

    @Test
    fun progressIsPublishedWhileDownloading() = runTest {
        fake.blockUntil = CompletableDeferred()
        fake.outcome = { target ->
            target.writeBytes(payload)
            DownloadOutcome.Completed(target, payload.size.toLong())
        }
        val mgr = manager()
        mgr.start(manifest())
        advanceUntilIdle() // 让协程跑到挂起点
        assertTrue(
            "expected Downloading, got ${mgr.state.value}",
            mgr.state.value is UpdateDownloadState.Downloading,
        )

        fake.blockUntil?.complete(Unit)
        advanceUntilIdle()
        assertTrue(mgr.state.value is UpdateDownloadState.Ready)
    }

    // ---------------- 校验失败 ----------------

    @Test
    fun sizeMismatchDeletesFileAndNeverReachesReady() = runTest {
        fake.outcome = { target ->
            target.writeBytes("tampered".toByteArray())
            DownloadOutcome.Completed(target, target.length())
        }
        val mgr = manager()
        mgr.start(manifest())
        advanceUntilIdle()

        val state = mgr.state.value
        assertTrue("expected DownloadFailed, got $state", state is UpdateDownloadState.DownloadFailed)
        val failed = state as UpdateDownloadState.DownloadFailed
        assertTrue("got ${failed.error}", failed.error is DownloadError.SizeMismatch)
        assertNull("损坏的文件必须被删除", mgr.readyFile)
        assertNull("目录必须被清空", dir.list()?.firstOrNull())
    }

    @Test
    fun hashMismatchWithMatchingSizeIsStillRejected() = runTest {
        // 体积一致、内容被篡改 —— 只有哈希校验能拦住这种情况
        val tampered = payload.copyOf().also { it[0] = (it[0] + 1).toByte() }
        fake.outcome = { target ->
            target.writeBytes(tampered)
            DownloadOutcome.Completed(target, tampered.size.toLong())
        }
        val mgr = manager()
        mgr.start(manifest())
        advanceUntilIdle()

        val state = mgr.state.value
        assertTrue("expected DownloadFailed, got $state", state is UpdateDownloadState.DownloadFailed)
        val failed = state as UpdateDownloadState.DownloadFailed
        assertTrue("got ${failed.error}", failed.error is DownloadError.HashMismatch)
        assertNull(mgr.readyFile)
        assertNull(dir.list()?.firstOrNull())
    }

    // ---------------- 下载失败与重试 ----------------

    @Test
    fun downloadFailureReportsResumeableBytes() = runTest {
        File(dir, "StarTool-v0.1.2-r3.apk.part").writeBytes(ByteArray(1_000) { 1 })

        fake.outcome = { DownloadOutcome.Failed(DownloadError.Interrupted(1_000L)) }
        val mgr = manager()
        mgr.start(manifest())
        advanceUntilIdle()

        val state = mgr.state.value
        assertTrue(state is UpdateDownloadState.DownloadFailed)
        state as UpdateDownloadState.DownloadFailed
        assertTrue(state.canResume)
        assertEquals(1_000L, state.loadedBytes)
    }

    @Test
    fun retryResumesFromExistingPartBytes() = runTest {
        File(dir, "StarTool-v0.1.2-r3.apk.part").writeBytes(ByteArray(2_048) { 2 })

        fake.outcome = { DownloadOutcome.Failed(DownloadError.Interrupted(2_048L)) }
        val mgr = manager()
        mgr.start(manifest())
        advanceUntilIdle()
        assertEquals(1, fake.requests.size)

        mgr.retry()
        advanceUntilIdle()
        assertEquals(2, fake.requests.size)
        // 续传：第二次请求必须带上第一次留下的断点偏移
        assertEquals(2_048L, fake.requests[1].third)
    }

    @Test
    fun retryWithoutManifestDoesNothing() = runTest {
        val mgr = manager()
        assertFalse(mgr.retry())
        assertEquals(UpdateDownloadState.Idle, mgr.state.value)
    }

    // ---------------- 取消与重复点击 ----------------

    @Test
    fun cancelWhileDownloadingSetsCancelledState() = runTest {
        fake.blockUntil = CompletableDeferred()
        fake.outcome = { DownloadOutcome.Failed(DownloadError.Network("unused")) }
        val mgr = manager()
        mgr.start(manifest())
        advanceUntilIdle()
        assertTrue(mgr.state.value is UpdateDownloadState.Downloading)

        mgr.cancel()
        advanceUntilIdle()

        assertEquals(UpdateDownloadState.Cancelled, mgr.state.value)
        assertNull(mgr.readyFile)
    }

    @Test
    fun secondStartWhileActiveIsIgnored() = runTest {
        fake.blockUntil = CompletableDeferred()
        fake.outcome = { target ->
            target.writeBytes(payload)
            DownloadOutcome.Completed(target, payload.size.toLong())
        }
        val mgr = manager()
        assertTrue(mgr.start(manifest()))
        // 返回 true 表示「已在下载」，但不应发起第二次请求
        assertTrue(mgr.start(manifest()))
        advanceUntilIdle()

        assertEquals(1, fake.requests.size)
        fake.blockUntil?.complete(Unit)
        advanceUntilIdle()
        assertTrue(mgr.state.value is UpdateDownloadState.Ready)
    }

    @Test
    fun resetReturnsToIdleAndStopsBackgroundJob() = runTest {
        fake.blockUntil = CompletableDeferred()
        fake.outcome = { DownloadOutcome.Failed(DownloadError.Network("x")) }
        val mgr = manager()
        mgr.start(manifest())
        mgr.reset()
        advanceUntilIdle()

        assertEquals(UpdateDownloadState.Idle, mgr.state.value)
        assertNull(mgr.readyFile)
    }

    // ---------------- 已下载过的包不重复下载 ----------------

    @Test
    fun existingVerifiedApkSkipsDownload() = runTest {
        File(dir, "StarTool-v0.1.2-r3.apk").writeBytes(payload)

        val mgr = manager()
        assertTrue(mgr.start(manifest()))
        advanceUntilIdle()

        assertTrue(mgr.state.value is UpdateDownloadState.Ready)
        assertEquals(0, fake.requests.size)
        assertNotNull(mgr.readyFile)
    }

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeDownloader : ApkDownloader {
        var outcome: ((File) -> DownloadOutcome)? = null
        var blockUntil: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<Triple<String, File, Long>>()

        override suspend fun download(
            url: String,
            targetFile: File,
            resumeFromBytes: Long,
            onProgress: suspend (loaded: Long, total: Long?) -> Unit,
        ): DownloadOutcome {
            requests.add(Triple(url, targetFile, resumeFromBytes))
            onProgress(resumeFromBytes, null)
            blockUntil?.await()
            onProgress(1L, 2L)
            return outcome?.invoke(targetFile) ?: DownloadOutcome.Failed(DownloadError.Network("未配置"))
        }
    }
}
