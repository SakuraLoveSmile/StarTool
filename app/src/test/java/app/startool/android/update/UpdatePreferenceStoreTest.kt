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
}
