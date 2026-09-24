package app.startool.android.feedback.diagnostics

import android.content.Context
import android.os.Build
import org.json.JSONObject

data class DiagnosticInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val errorSummary: String? = null,
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("appName", appName)
            put("packageName", packageName)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("androidRelease", androidRelease)
            put("sdkInt", sdkInt)
            put("manufacturer", manufacturer)
            put("model", model)
            if (!errorSummary.isNullOrEmpty()) {
                put("errorSummary", errorSummary)
            }
        }.toString()
    }
}

/**
 * 诊断信息收集器（计划 §T2）。
 * 仅包含应用版本、系统版本与设备基础信息（白名单），
 * 严格禁止收集任何用户小时评分、备注、数据库快照、备份或全量系统日志。
 */
object DiagnosticCollector {

    fun collect(
        packageName: String,
        errorSummary: String? = null,
        versionName: String = "0.1.0",
        versionCode: Int = 1,
    ): DiagnosticInfo {
        return DiagnosticInfo(
            appName = "StarTool (Gemini)",
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            androidRelease = try { Build.VERSION.RELEASE ?: "Unknown" } catch (t: Throwable) { "Unknown" },
            sdkInt = try { Build.VERSION.SDK_INT } catch (t: Throwable) { 0 },
            manufacturer = try { Build.MANUFACTURER ?: "Unknown" } catch (t: Throwable) { "Unknown" },
            model = try { Build.MODEL ?: "Unknown" } catch (t: Throwable) { "Unknown" },
            errorSummary = errorSummary?.take(500),
        )
    }

    fun collect(
        context: Context,
        errorSummary: String? = null,
        versionName: String? = null,
        versionCode: Int? = null,
    ): DiagnosticInfo {
        var vName = versionName
        var vCode = versionCode
        if (vName == null || vCode == null) {
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (vName == null) {
                    vName = pInfo.versionName ?: "0.1.0"
                }
                if (vCode == null) {
                    vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pInfo.versionCode
                    }
                }
            } catch (t: Throwable) {
                if (vName == null) vName = "0.1.0"
                if (vCode == null) vCode = 1
            }
        }
        return collect(
            packageName = context.packageName,
            errorSummary = errorSummary,
            versionName = vName,
            versionCode = vCode,
        )
    }
}
