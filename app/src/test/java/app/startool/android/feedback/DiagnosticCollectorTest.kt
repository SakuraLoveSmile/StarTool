package app.startool.android.feedback

import app.startool.android.feedback.diagnostics.DiagnosticCollector
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticCollectorTest {

    @Test
    fun collect_containsOnlyWhitelistedFields() {
        val info = DiagnosticCollector.collect(
            packageName = "app.startool.android.gemini",
            errorSummary = "Test error detail",
            versionName = "0.1.0",
            versionCode = 1,
        )

        val jsonStr = info.toJsonString()
        val json = JSONObject(jsonStr)

        assertEquals("StarTool (Gemini)", json.getString("appName"))
        assertEquals("app.startool.android.gemini", json.getString("packageName"))
        assertEquals("0.1.0", json.getString("versionName"))
        assertEquals(1, json.getInt("versionCode"))
        assertEquals("Test error detail", json.getString("errorSummary"))

        // 验证白名单：严禁收集个人数据
        assertFalse("严禁收集评分", json.has("score"))
        assertFalse("严禁收集小时备注", json.has("note"))
        assertFalse("严禁收集数据库内容", json.has("entries"))
        assertFalse("严禁收集备份内容", json.has("backup"))
        assertFalse("严禁收集全量日志", json.has("logcat"))
    }
}
