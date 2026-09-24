package app.startool.android.update

import app.startool.android.update.model.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestTest {

    private val sampleJson = """
        {
          "schemaVersion": 1,
          "applicationId": "app.startool.android.gemini",
          "versionCode": 2,
          "versionName": "0.2.0",
          "minSdk": 26,
          "notes": "更新说明内容...",
          "releaseUrl": "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.2.0"
        }
    """.trimIndent()

    @Test
    fun testParseValidJson() {
        val manifest = UpdateManifest.fromJson(sampleJson)
        assertNotNull(manifest)
        assertEquals(1, manifest?.schemaVersion)
        assertEquals("app.startool.android.gemini", manifest?.applicationId)
        assertEquals(2, manifest?.versionCode)
        assertEquals("0.2.0", manifest?.versionName)
        assertEquals(26, manifest?.minSdk)
        assertEquals("更新说明内容...", manifest?.notes)
        assertEquals("https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.2.0", manifest?.releaseUrl)
    }

    @Test
    fun testSerializationRoundtrip() {
        val original = UpdateManifest(
            schemaVersion = 1,
            applicationId = "app.startool.android.gemini",
            versionCode = 5,
            versionName = "1.0.0",
            minSdk = 28,
            notes = "新功能上线\n- 支持导出\n- 性能优化",
            releaseUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v1.0.0",
        )

        val jsonStr = original.toJson()
        val parsed = UpdateManifest.fromJson(jsonStr)
        assertEquals(original, parsed)
    }

    @Test
    fun testMalformedJsonReturnsNull() {
        assertNull(UpdateManifest.fromJson(""))
        assertNull(UpdateManifest.fromJson("{ invalid"))
        assertNull(UpdateManifest.fromJson("null"))
        assertNull(UpdateManifest.fromJson("[]"))
    }

    @Test
    fun testMissingFieldsReturnsNull() {
        val fields = listOf("schemaVersion", "applicationId", "versionCode", "versionName", "minSdk", "notes", "releaseUrl")
        for (field in fields) {
            val lines = sampleJson.lines().filter { !it.contains("\"$field\"") }
            val modifiedJson = lines.joinToString("\n")
            assertNull("Expected null when missing field: $field", UpdateManifest.fromJson(modifiedJson))
        }
    }

    @Test
    fun testNullFieldValueReturnsNull() {
        val jsonWithNull = sampleJson.replace("\"versionCode\": 2", "\"versionCode\": null")
        assertNull(UpdateManifest.fromJson(jsonWithNull))
    }

    @Test
    fun testParseOptionalApkFields() {
        val json = """
            {
              "schemaVersion": 2,
              "applicationId": "app.startool.android.gemini",
              "versionCode": 3,
              "versionName": "0.1.2",
              "minSdk": 26,
              "notes": "n",
              "releaseUrl": "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.1.2",
              "apkUrl": "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.1.2/StarTool-v0.1.2-release.apk",
              "apkSha256": "ABCDEF0123456789abcdef0123456789ABCDEF0123456789abcdef0123456789",
              "apkSizeBytes": 8575734
            }
        """.trimIndent()
        val manifest = UpdateManifest.fromJson(json)
        assertNotNull(manifest)
        assertEquals(
            "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.1.2/StarTool-v0.1.2-release.apk",
            manifest?.apkUrl,
        )
        // 哈希统一归一成小写，便于下载后直接比对
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", manifest?.apkSha256)
        assertEquals(8575734L, manifest?.apkSizeBytes)
    }

    @Test
    fun testBackwardCompatibleWhenApkFieldsAbsent() {
        // schemaVersion 1 的旧清单必须照常解析，新字段为 null
        val manifest = UpdateManifest.fromJson(sampleJson)
        assertNotNull(manifest)
        assertNull(manifest?.apkUrl)
        assertNull(manifest?.apkSha256)
        assertNull(manifest?.apkSizeBytes)
    }

    @Test
    fun testNullApkFieldsDoNotBreakParsing() {
        val json = """
            {
              "schemaVersion": 1,
              "applicationId": "app.startool.android.gemini",
              "versionCode": 2,
              "versionName": "0.2.0",
              "minSdk": 26,
              "notes": "更新说明内容...",
              "releaseUrl": "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.2.0",
              "apkUrl": null,
              "apkSha256": null,
              "apkSizeBytes": null
            }
        """.trimIndent()
        val manifest = UpdateManifest.fromJson(json)
        assertNotNull(manifest)
        assertNull(manifest?.apkUrl)
        assertNull(manifest?.apkSizeBytes)
    }

    @Test
    fun testRoundtripWithApkFields() {
        val original = UpdateManifest(
            schemaVersion = 2,
            applicationId = "app.startool.android.gemini",
            versionCode = 7,
            versionName = "0.3.0",
            minSdk = 26,
            notes = "n",
            releaseUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.3.0",
            apkUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.3.0/StarTool-v0.3.0-release.apk",
            apkSha256 = "c".repeat(64),
            apkSizeBytes = 12345L,
        )
        assertEquals(original, UpdateManifest.fromJson(original.toJson()))
    }

    @Test
    fun testRoundtripWithoutApkFields() {
        val original = UpdateManifest.fromJson(sampleJson)!!
        assertEquals(original, UpdateManifest.fromJson(original.toJson()))
    }
}
