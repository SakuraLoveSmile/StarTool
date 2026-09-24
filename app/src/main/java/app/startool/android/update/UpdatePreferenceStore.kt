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

    companion object {
        const val PREF_NAME = "startool_update_preferences"
        const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        const val KEY_LAST_AUTO_CHECK_TIMESTAMP = "last_auto_check_timestamp"
        const val KEY_IGNORED_VERSION_CODE = "ignored_version_code"
        const val KEY_REMIND_LATER_TIMESTAMP = "remind_later_timestamp"
        const val KEY_PROXY_SOURCE = "proxy_source"
        const val KEY_CUSTOM_PROXY_PREFIX = "custom_proxy_prefix"

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
        ): UpdatePreferenceStore = InMemoryUpdatePreferenceStore(
            autoCheckEnabled = autoCheckEnabled,
            lastAutoCheckTimestamp = lastAutoCheckTimestamp,
            ignoredVersionCode = ignoredVersionCode,
            remindLaterTimestamp = remindLaterTimestamp,
            proxySource = proxySource,
            customProxyPrefix = customProxyPrefix,
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
        get() = UpdateProxySource.fromId(sharedPreferences.getString(UpdatePreferenceStore.KEY_PROXY_SOURCE, UpdateProxySource.Direct.id))
        set(value) {
            sharedPreferences.edit().putString(UpdatePreferenceStore.KEY_PROXY_SOURCE, value.id).apply()
        }

    override var customProxyPrefix: String
        get() = sharedPreferences.getString(UpdatePreferenceStore.KEY_CUSTOM_PROXY_PREFIX, "").orEmpty()
        set(value) {
            sharedPreferences.edit().putString(UpdatePreferenceStore.KEY_CUSTOM_PROXY_PREFIX, value).apply()
        }
}

class InMemoryUpdatePreferenceStore(
    override var autoCheckEnabled: Boolean = true,
    override var lastAutoCheckTimestamp: Long = 0L,
    override var ignoredVersionCode: Int? = null,
    override var remindLaterTimestamp: Long = 0L,
    override var proxySource: UpdateProxySource = UpdateProxySource.Direct,
    override var customProxyPrefix: String = "",
) : UpdatePreferenceStore
