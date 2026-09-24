package app.startool.android.update.model

import org.json.JSONObject

/**
 * 更新清单。
 *
 * schemaVersion 1 的必填字段全部保留；P1 新增的 APK 元数据一律可选，
 * 保证老客户端读新清单、新客户端读老清单都不会解析失败。
 */
data class UpdateManifest(
    val schemaVersion: Int,
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val notes: String,
    val releaseUrl: String,
    /** P1：APK 直链。为空时客户端回退到 GitHub API 的 assets 解析。 */
    val apkUrl: String? = null,
    /** P1：APK 的 SHA-256（小写十六进制），下载完成后做完整性校验。 */
    val apkSha256: String? = null,
    /** P1：APK 字节数，用于进度条总长；为空则退回 HTTP Content-Length。 */
    val apkSizeBytes: Long? = null,
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("schemaVersion", schemaVersion)
        json.put("applicationId", applicationId)
        json.put("versionCode", versionCode)
        json.put("versionName", versionName)
        json.put("minSdk", minSdk)
        json.put("notes", notes)
        json.put("releaseUrl", releaseUrl)
        // 可选字段：null 时完全不写入，保证 toJson -> fromJson 往返相等。
        if (apkUrl != null) json.put("apkUrl", apkUrl)
        if (apkSha256 != null) json.put("apkSha256", apkSha256)
        if (apkSizeBytes != null) json.put("apkSizeBytes", apkSizeBytes)
        return json.toString()
    }

    companion object {
        /** 当前清单 schema 版本。新增字段必须可选，读取端向后兼容。 */
        const val SCHEMA_VERSION = 2

        private fun JSONObject.optionalString(key: String): String? =
            if (has(key) && !isNull(key)) getString(key) else null

        private fun JSONObject.optionalLong(key: String): Long? =
            if (has(key) && !isNull(key)) getLong(key) else null

        fun fromJson(jsonString: String): UpdateManifest? {
            return try {
                val json = JSONObject(jsonString)
                if (!json.has("schemaVersion") || json.isNull("schemaVersion") ||
                    !json.has("applicationId") || json.isNull("applicationId") ||
                    !json.has("versionCode") || json.isNull("versionCode") ||
                    !json.has("versionName") || json.isNull("versionName") ||
                    !json.has("minSdk") || json.isNull("minSdk") ||
                    !json.has("notes") || json.isNull("notes") ||
                    !json.has("releaseUrl") || json.isNull("releaseUrl")
                ) {
                    return null
                }
                UpdateManifest(
                    schemaVersion = json.getInt("schemaVersion"),
                    applicationId = json.getString("applicationId"),
                    versionCode = json.getInt("versionCode"),
                    versionName = json.getString("versionName"),
                    minSdk = json.getInt("minSdk"),
                    notes = json.getString("notes"),
                    releaseUrl = json.getString("releaseUrl"),
                    apkUrl = json.optionalString("apkUrl"),
                    apkSha256 = json.optionalString("apkSha256")?.lowercase(),
                    apkSizeBytes = json.optionalLong("apkSizeBytes"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
