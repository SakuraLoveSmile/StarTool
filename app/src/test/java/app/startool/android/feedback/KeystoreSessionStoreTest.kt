package app.startool.android.feedback

import android.content.SharedPreferences
import app.startool.android.feedback.model.SessionData
import app.startool.android.feedback.storage.KeystoreSessionStore
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeystoreSessionStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = data[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val backing: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val staging = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { staging[it] = value }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { toRemove.add(it) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clear) backing.clear()
                for (k in toRemove) backing.remove(k)
                backing.putAll(staging)
            }
        }
    }

    private class SimpleCipherProvider : KeystoreSessionStore.CipherProvider {
        override fun encrypt(plainText: String): String {
            return "enc_" + Base64.getEncoder().encodeToString(plainText.toByteArray(Charsets.UTF_8))
        }

        override fun decrypt(encryptedText: String): String? {
            if (!encryptedText.startsWith("enc_")) return null
            val b64 = encryptedText.removePrefix("enc_")
            return try {
                String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
            } catch (t: Throwable) {
                null
            }
        }
    }

    @Test
    fun sessionData_toJsonAndFromJson() {
        val session = SessionData(accessToken = "test_token_123", expiresAt = 1893456000000L)
        val json = session.toJson()
        val restored = SessionData.fromJson(json)

        assertNotNull(restored)
        assertEquals("test_token_123", restored?.accessToken)
        assertEquals(1893456000000L, restored?.expiresAt)
    }

    @Test
    fun keystoreSessionStore_isolationByApiBaseAndAppId() {
        var currentTime = 1000L
        val prefs = FakeSharedPreferences()
        val store = KeystoreSessionStore(
            prefs = prefs,
            clock = { currentTime },
        ).apply {
            cipherProvider = SimpleCipherProvider()
        }

        val sessionA = SessionData("tok_A", 5000L)
        val sessionB = SessionData("tok_B", 5000L)

        store.saveSession("https://api1.com", "app.one", sessionA)
        store.saveSession("https://api2.com", "app.two", sessionB)

        assertEquals("tok_A", store.loadSession("https://api1.com", "app.one")?.accessToken)
        assertEquals("tok_B", store.loadSession("https://api2.com", "app.two")?.accessToken)
        assertNull(store.loadSession("https://api1.com", "app.two"))
        assertNull(store.loadSession("https://api2.com", "app.one"))
    }

    @Test
    fun keystoreSessionStore_expiredSessionIsCleared() {
        var currentTime = 1000L
        val prefs = FakeSharedPreferences()
        val store = KeystoreSessionStore(
            prefs = prefs,
            clock = { currentTime },
        ).apply {
            cipherProvider = SimpleCipherProvider()
        }

        val session = SessionData("tok_exp", 2000L)
        store.saveSession("https://api.com", "app.id", session)

        assertEquals("tok_exp", store.loadSession("https://api.com", "app.id")?.accessToken)

        // 时间推进至过期
        currentTime = 2001L
        assertNull(store.loadSession("https://api.com", "app.id"))
        // 再次加载已被完全清除
        assertNull(store.loadSession("https://api.com", "app.id"))
    }

    @Test
    fun keystoreSessionStore_clearSessionExplicitly() {
        val prefs = FakeSharedPreferences()
        val store = KeystoreSessionStore(
            prefs = prefs,
            clock = { 1000L },
        ).apply {
            cipherProvider = SimpleCipherProvider()
        }

        val future = 5000L
        store.saveSession("https://api.com", "app.id", SessionData("tok", future))
        assertNotNull(store.loadSession("https://api.com", "app.id"))

        store.clearSession("https://api.com", "app.id")
        assertNull(store.loadSession("https://api.com", "app.id"))
    }

    @Test
    fun keystoreSessionStore_corruptedDataAutoCleared() {
        val prefs = FakeSharedPreferences()
        val store = KeystoreSessionStore(
            prefs = prefs,
            clock = { 1000L },
        ).apply {
            cipherProvider = SimpleCipherProvider()
        }

        val apiBase = "https://api.com"
        val appId = "app.id"
        store.saveSession(apiBase, appId, SessionData("token", 5000L))
        assertNotNull(store.loadSession(apiBase, appId))

        val key = prefs.all.keys.first()
        prefs.edit().putString(key, "corrupted_garbage").apply()

        assertNull(store.loadSession(apiBase, appId))
        assertNull(prefs.getString(key, null))
    }
}
