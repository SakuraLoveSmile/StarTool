package app.startool.android.feedback.model

import org.json.JSONObject

data class SessionData(
    val accessToken: String,
    val expiresAt: Long,
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("accessToken", accessToken)
            put("expiresAt", expiresAt)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SessionData? {
            return try {
                val obj = JSONObject(jsonStr)
                val token = obj.optString("accessToken")
                val expiresAt = obj.optLong("expiresAt", 0L)
                if (token.isNotEmpty()) {
                    SessionData(token, expiresAt)
                } else {
                    null
                }
            } catch (t: Throwable) {
                null
            }
        }
    }
}
