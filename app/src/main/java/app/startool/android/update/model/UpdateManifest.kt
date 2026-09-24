package app.startool.android.update.model

import org.json.JSONObject

data class UpdateManifest(
    val schemaVersion: Int,
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val notes: String,
    val releaseUrl: String,
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
        return json.toString()
    }

    companion object {
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
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
