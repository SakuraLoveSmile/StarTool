package app.startool.android.feedback.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import app.startool.android.feedback.model.SessionData
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface FeedbackSessionStorage {
    fun loadSession(apiBase: String, appId: String): SessionData?
    fun saveSession(apiBase: String, appId: String, session: SessionData)
    fun clearSession(apiBase: String, appId: String)
}

/**
 * 经 Android Keystore 保护的 Feedback 会话持久化实现（计划 §T2）。
 * 密钥受 AndroidKeyStore 硬件/系统级保护，密文与 IV 存入私有 SharedPreferences。
 * 按服务地址 [apiBase] 与应用标识 [appId] 严格隔离；绝不保存密码。
 */
class KeystoreSessionStore(
    context: Context? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    prefName: String = "startool_feedback_secure_sessions",
    prefs: SharedPreferences? = null,
) : FeedbackSessionStorage {

    private val prefs: SharedPreferences =
        prefs ?: (context?.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            ?: throw IllegalArgumentException("Either context or prefs must be provided"))
    private val keyAlias = "startool_feedback_session_key"

    @Synchronized
    override fun loadSession(apiBase: String, appId: String): SessionData? {
        val key = buildKey(apiBase, appId)
        val encryptedBase64 = prefs.getString(key, null) ?: return null
        return try {
            val decryptedJson = decrypt(encryptedBase64)
            if (decryptedJson == null) {
                clearSession(apiBase, appId)
                return null
            }
            val session = SessionData.fromJson(decryptedJson)
            if (session == null) {
                clearSession(apiBase, appId)
                return null
            }
            // 过期检查：如果已过期则自动清除并返回 null
            if (session.expiresAt > 0 && session.expiresAt <= clock()) {
                clearSession(apiBase, appId)
                null
            } else {
                session
            }
        } catch (t: Throwable) {
            clearSession(apiBase, appId)
            null
        }
    }

    @Synchronized
    override fun saveSession(apiBase: String, appId: String, session: SessionData) {
        val key = buildKey(apiBase, appId)
        try {
            val encryptedBase64 = encrypt(session.toJson())
            prefs.edit().putString(key, encryptedBase64).apply()
        } catch (t: Throwable) {
            // 加密失败不保存
        }
    }

    @Synchronized
    override fun clearSession(apiBase: String, appId: String) {
        val key = buildKey(apiBase, appId)
        prefs.edit().remove(key).apply()
    }

    private fun buildKey(apiBase: String, appId: String): String {
        val raw = apiBase.trim().trimEnd('/') + "#" + appId.trim()
        val safeKey = raw.replace('/', '_').replace(':', '_').replace('?', '_').replace('&', '_')
        return "sess_$safeKey"
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    @VisibleForTesting
    internal var cipherProvider: CipherProvider? = null

    private fun encrypt(plainText: String): String {
        cipherProvider?.let { return it.encrypt(plainText) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // 格式: base64(iv + cipherBytes)
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String? {
        cipherProvider?.let { return it.decrypt(encryptedBase64) }
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < 12) return null
        val iv = ByteArray(12)
        System.arraycopy(combined, 0, iv, 0, 12)
        val cipherBytes = ByteArray(combined.size - 12)
        System.arraycopy(combined, 12, cipherBytes, 0, cipherBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    interface CipherProvider {
        fun encrypt(plainText: String): String
        fun decrypt(encryptedText: String): String?
    }
}
