package app.startool.android.update
import app.startool.android.update.model.UpdateProxySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePreferenceStoreTest {

    @Test
    fun testDefaultValues() {
        val store = UpdatePreferenceStore.inMemory()
        assertTrue(store.autoCheckEnabled)
        assertEquals(0L, store.lastAutoCheckTimestamp)
        assertNull(store.ignoredVersionCode)
        assertEquals(0L, store.remindLaterTimestamp)
        assertEquals(UpdateProxySource.Direct, store.proxySource)
        assertEquals("", store.customProxyPrefix)
    }

    @Test
    fun testUpdateValues() {
        val store = UpdatePreferenceStore.inMemory()

        store.autoCheckEnabled = false
        assertFalse(store.autoCheckEnabled)

        store.lastAutoCheckTimestamp = 123456789L
        assertEquals(123456789L, store.lastAutoCheckTimestamp)

        store.ignoredVersionCode = 42
        assertEquals(42, store.ignoredVersionCode)

        store.ignoredVersionCode = null
        assertNull(store.ignoredVersionCode)

        store.remindLaterTimestamp = 987654321L
        assertEquals(987654321L, store.remindLaterTimestamp)

        store.proxySource = UpdateProxySource.GhProxyNet
        assertEquals(UpdateProxySource.GhProxyNet, store.proxySource)

        store.customProxyPrefix = "https://myproxy.example.com"
        assertEquals("https://myproxy.example.com", store.customProxyPrefix)
    }

    @Test
    fun testCustomInitialValues() {
        val store = UpdatePreferenceStore.inMemory(
            autoCheckEnabled = false,
            lastAutoCheckTimestamp = 100L,
            ignoredVersionCode = 3,
            remindLaterTimestamp = 200L,
            proxySource = UpdateProxySource.Custom,
            customProxyPrefix = "https://custom.proxy.org",
        )
        assertFalse(store.autoCheckEnabled)
        assertEquals(100L, store.lastAutoCheckTimestamp)
        assertEquals(3, store.ignoredVersionCode)
        assertEquals(200L, store.remindLaterTimestamp)
        assertEquals(UpdateProxySource.Custom, store.proxySource)
        assertEquals("https://custom.proxy.org", store.customProxyPrefix)
    }

    @Test
    fun testInstallReceiptDefaultsAreNull() {
        val store = UpdatePreferenceStore.inMemory()
        assertNull(store.pendingInstallTargetVersionCode)
        assertNull(store.pendingInstallTargetVersionName)
        assertNull(store.lastInstallError)
    }

    @Test
    fun testInstallReceiptRoundTrip() {
        val store = UpdatePreferenceStore.inMemory(
            pendingInstallTargetVersionCode = 7,
            pendingInstallTargetVersionName = "0.1.7",
            lastInstallError = "安装包签名与本机已装应用不一致",
        )
        assertEquals(7, store.pendingInstallTargetVersionCode)
        assertEquals("0.1.7", store.pendingInstallTargetVersionName)
        assertEquals("安装包签名与本机已装应用不一致", store.lastInstallError)

        // 置空后必须真的读出 null，而不是 0 / 空串（冷启动回执依赖这个语义）
        store.pendingInstallTargetVersionCode = null
        store.pendingInstallTargetVersionName = null
        store.lastInstallError = null
        assertNull(store.pendingInstallTargetVersionCode)
        assertNull(store.pendingInstallTargetVersionName)
        assertNull(store.lastInstallError)
    }
}
