package app.startool.android.update

import android.content.SharedPreferences
import app.startool.android.update.model.UpdateProxySource

interface UpdatePreferenceStore {
    var autoCheckEnabled: Boolean
    var lastAutoCheckTimestamp: Long
    var ignoredVersionCode: Int?
    var remindLaterTimestamp: Long
    var proxySource: UpdateProxySource
    var customProxyPrefix: String

    // ---------------- P3：安装回执的跨进程恢复 ----------------
    // 安装成功时系统往往会立刻杀掉本进程，UI 来不及显示结果；
    // 这三个字段让冷启动（AppContainer 初始化 -> UpdateDownloadManager.init）
    // 能补上「已更新到 x.y.z」或「上次更新未完成」的提示。

    /** commit 时记录的目标版本；冷启动时与实际 versionCode 比对得出结局。 */
    var pendingInstallTargetVersionCode: Int?
    var pendingInstallTargetVersionName: String?
    /** 安装失败的面向用户文案，冷启动时回放一次后清空。 */
    var lastInstallError: String?

    companion object {
        const val PREF_NAME = "startool_update_preferences"
        const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        const val KEY_LAST_AUTO_CHECK_TIMESTAMP = "last_auto_check_timestamp"
        const val KEY_IGNORED_VERSION_CODE = "ignored_version_code"
        const val KEY_REMIND_LATER_TIMESTAMP = "remind_later_timestamp"
        const val KEY_PROXY_SOURCE = "proxy_source"
        const val KEY_CUSTOM_PROXY_PREFIX = "custom_proxy_prefix"
        const val KEY_PENDING_INSTALL_TARGET_VERSION_CODE = "pending_install_target_version_code"
        const val KEY_PENDING_INSTALL_TARGET_VERSION_NAME = "pending_install_target_version_name"
        const val KEY_LAST_INSTALL_ERROR = "last_install_error"

        operator fun invoke(sharedPreferences: SharedPreferences): UpdatePreferenceStore =
            SharedPreferencesUpdatePreferenceStore(sharedPreferences)

        operator fun invoke(): UpdatePreferenceStore =
            InMemoryUpdatePreferenceStore()

        fun inMemory(
            autoCheckEnabled: Boolean = true,
            lastAutoCheckTimestamp: Long = 0L,
            ignoredVersionCode: Int? = null,
            remindLaterTimestamp: Long = 0L,
            proxySource: UpdateProxySource = UpdateProxySource.Direct,
            customProxyPrefix: String = "",
            pendingInstallTargetVersionCode: Int? = null,
            pendingInstallTargetVersionName: String? = null,
            lastInstallError: String? = null,
        ): UpdatePreferenceStore = InMemoryUpdatePreferenceStore(
            autoCheckEnabled = autoCheckEnabled,
            lastAutoCheckTimestamp = lastAutoCheckTimestamp,
            ignoredVersionCode = ignoredVersionCode,
            remindLaterTimestamp = remindLaterTimestamp,
            proxySource = proxySource,
            customProxyPrefix = customProxyPrefix,
            pendingInstallTargetVersionCode = pendingInstallTargetVersionCode,
            pendingInstallTargetVersionName = pendingInstallTargetVersionName,
            lastInstallError = lastInstallError,
        )
    }
}

class SharedPreferencesUpdatePreferenceStore(
    private val sharedPreferences: SharedPreferences,
) : UpdatePreferenceStore {
    override var autoCheckEnabled: Boolean
        get() = sharedPreferences.getBoolean(UpdatePreferenceStore.KEY_AUTO_CHECK_ENABLED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(UpdatePreferenceStore.KEY_AUTO_CHECK_ENABLED, value).apply()
        }

    override var lastAutoCheckTimestamp: Long
        get() = sharedPreferences.getLong(UpdatePreferenceStore.KEY_LAST_AUTO_CHECK_TIMESTAMP, 0L)
        set(value) {
            sharedPreferences.edit().putLong(UpdatePreferenceStore.KEY_LAST_AUTO_CHECK_TIMESTAMP, value).apply()
        }

    override var ignoredVersionCode: Int?
        get() = if (sharedPreferences.contains(UpdatePreferenceStore.KEY_IGNORED_VERSION_CODE)) {
            sharedPreferences.getInt(UpdatePreferenceStore.KEY_IGNORED_VERSION_CODE, 0)
        } else {
            null
        }
        set(value) {
            sharedPreferences.edit().apply {
                if (value != null) {
                    putInt(UpdatePreferenceStore.KEY_IGNORED_VERSION_CODE, value)
                } else {
                    remove(UpdatePreferenceStore.KEY_IGNORED_VERSION_CODE)
                }
                apply()
            }
        }

    override var remindLaterTimestamp: Long
        get() = sharedPreferences.getLong(UpdatePreferenceStore.KEY_REMIND_LATER_TIMESTAMP, 0L)
        set(value) {
            sharedPreferences.edit().putLong(UpdatePreferenceStore.KEY_REMIND_LATER_TIMESTAMP, value).apply()
        }

    override var proxySource: UpdateProxySource
        get() = UpdateProxySource.fromId(
            sharedPreferences.getString(UpdatePreferenceStore.KEY_PROXY_SOURCE, UpdateProxySource.Direct.id),
        )
        set(value) {
            sharedPreferences.edit().putString(UpdatePreferenceStore.KEY_PROXY_SOURCE, value.id).apply()
        }

    override var customProxyPrefix: String
        get() = sharedPreferences.getString(UpdatePreferenceStore.KEY_CUSTOM_PROXY_PREFIX, "").orEmpty()
        set(value) {
            sharedPreferences.edit().putString(UpdatePreferenceStore.KEY_CUSTOM_PROXY_PREFIX, value).apply()
        }

    override var pendingInstallTargetVersionCode: Int?
        get() = nullableInt(UpdatePreferenceStore.KEY_PENDING_INSTALL_TARGET_VERSION_CODE)
        set(value) = putNullableInt(UpdatePreferenceStore.KEY_PENDING_INSTALL_TARGET_VERSION_CODE, value)

    override var pendingInstallTargetVersionName: String?
        get() = nullableString(UpdatePreferenceStore.KEY_PENDING_INSTALL_TARGET_VERSION_NAME)
        set(value) = putNullableString(UpdatePreferenceStore.KEY_PENDING_INSTALL_TARGET_VERSION_NAME, value)

    override var lastInstallError: String?
        get() = nullableString(UpdatePreferenceStore.KEY_LAST_INSTALL_ERROR)
        set(value) = putNullableString(UpdatePreferenceStore.KEY_LAST_INSTALL_ERROR, value)

    private fun nullableInt(key: String): Int? =
        if (sharedPreferences.contains(key)) sharedPreferences.getInt(key, 0) else null

    private fun putNullableInt(key: String, value: Int?) {
        sharedPreferences.edit().apply {
            if (value != null) putInt(key, value) else remove(key)
            apply()
        }
    }

    private fun nullableString(key: String): String? =
        if (sharedPreferences.contains(key)) sharedPreferences.getString(key, null) else null

    private fun putNullableString(key: String, value: String?) {
        sharedPreferences.edit().apply {
            if (value != null) putString(key, value) else remove(key)
            apply()
        }
    }
}

class InMemoryUpdatePreferenceStore(
    override var autoCheckEnabled: Boolean = true,
    override var lastAutoCheckTimestamp: Long = 0L,
    override var ignoredVersionCode: Int? = null,
    override var remindLaterTimestamp: Long = 0L,
    override var proxySource: UpdateProxySource = UpdateProxySource.Direct,
    override var customProxyPrefix: String = "",
    override var pendingInstallTargetVersionCode: Int? = null,
    override var pendingInstallTargetVersionName: String? = null,
    override var lastInstallError: String? = null,
) : UpdatePreferenceStore
