package app.startool.android.backup

import app.startool.android.domain.BackupSnapshot
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.HourlyKey
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val sampleTime = Instant.parse("2026-09-23T06:00:00Z")

    @Test
    fun testEncodeDecodeRoundtrip() {
        val entry1 = HourlyEntry(
            key = HourlyKey(LocalDate.of(2026, 9, 23), 13),
            mentalScore = 6,
            physicalScore = 3,
            note = "午饭后",
            createdAt = Instant.parse("2026-09-23T05:15:00Z"),
            updatedAt = Instant.parse("2026-09-23T05:15:00Z"),
        )
        val entry2 = HourlyEntry(
            key = HourlyKey(LocalDate.of(2026, 9, 23), 14),
            mentalScore = 0,
            physicalScore = 10,
            note = "",
            createdAt = Instant.parse("2026-09-23T06:00:00Z"),
            updatedAt = Instant.parse("2026-09-23T06:00:00Z"),
        )
        val snapshot = BackupSnapshot(listOf(entry1, entry2))

        val json = BackupCodec.encode(snapshot, sampleTime)
        val validated = BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8))

        assertEquals(1, validated.schemaVersion)
        assertEquals(sampleTime, validated.exportedAt)
        assertEquals(2, validated.entries.size)
        assertEquals(entry1, validated.entries[0])
        assertEquals(entry2, validated.entries[1])
    }

    @Test
    fun testEmptyEntriesAccepted() {
        val snapshot = BackupSnapshot(emptyList())
        val json = BackupCodec.encode(snapshot, sampleTime)
        val validated = BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8))
        assertEquals(0, validated.entries.size)
    }

    @Test
    fun testUnknownFieldsIgnoredInV1() {
        val json = """
        {
          "format": "startool-backup",
          "schemaVersion": 1,
          "unknownRootField": "ignore_me",
          "entries": [
            {
              "date": "2026-09-23",
              "hour": 9,
              "mentalScore": 5,
              "physicalScore": 5,
              "note": "test",
              "extraField": 123,
              "createdAt": "2026-09-23T01:00:00Z",
              "updatedAt": "2026-09-23T01:00:00Z"
            }
          ]
        }
        """.trimIndent()
        val validated = BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8))
        assertEquals(1, validated.entries.size)
    }

    @Test
    fun testRejectInvalidFormat() {
        val json = """{"format": "wrong", "schemaVersion": 1, "entries": []}"""
        assertThrowsValidation { BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun testRejectUnsupportedSchemaVersion() {
        val json = """{"format": "startool-backup", "schemaVersion": 2, "entries": []}"""
        assertThrowsValidation { BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun testRejectDuplicateKeys() {
        val json = """
        {
          "format": "startool-backup",
          "schemaVersion": 1,
          "entries": [
            {
              "date": "2026-09-23", "hour": 9, "mentalScore": 5, "physicalScore": 5, "note": "",
              "createdAt": "2026-09-23T01:00:00Z", "updatedAt": "2026-09-23T01:00:00Z"
            },
            {
              "date": "2026-09-23", "hour": 9, "mentalScore": 6, "physicalScore": 6, "note": "",
              "createdAt": "2026-09-23T01:00:00Z", "updatedAt": "2026-09-23T01:00:00Z"
            }
          ]
        }
        """.trimIndent()
        assertThrowsValidation { BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun testRejectOutOfRangeScore() {
        val json = """
        {
          "format": "startool-backup",
          "schemaVersion": 1,
          "entries": [
            {
              "date": "2026-09-23", "hour": 9, "mentalScore": 11, "physicalScore": 5, "note": "",
              "createdAt": "2026-09-23T01:00:00Z", "updatedAt": "2026-09-23T01:00:00Z"
            }
          ]
        }
        """.trimIndent()
        assertThrowsValidation { BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun testRejectFloatScore() {
        val json = """
        {
          "format": "startool-backup",
          "schemaVersion": 1,
          "entries": [
            {
              "date": "2026-09-23", "hour": 9, "mentalScore": 5.5, "physicalScore": 5, "note": "",
              "createdAt": "2026-09-23T01:00:00Z", "updatedAt": "2026-09-23T01:00:00Z"
            }
          ]
        }
        """.trimIndent()
        assertThrowsValidation { BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun testRejectNoteTooLong() {
        val longNote = "字".repeat(201)
        val json = """
        {
          "format": "startool-backup",
          "schemaVersion": 1,
          "entries": [
            {
              "date": "2026-09-23", "hour": 9, "mentalScore": 5, "physicalScore": 5, "note": "$longNote",
              "createdAt": "2026-09-23T01:00:00Z", "updatedAt": "2026-09-23T01:00:00Z"
            }
          ]
        }
        """.trimIndent()
        assertThrowsValidation { BackupCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun testFileNameFormat() {
        val fn = BackupCodec.createFileName(Instant.parse("2026-09-23T06:15:30Z"))
        assertEquals("StarTool-backup-20260923-061530.json", fn)
    }

    private fun assertThrowsValidation(block: () -> Unit) {
        try {
            block()
            fail("Expected BackupValidationException was not thrown")
        } catch (e: BackupValidationException) {
            // expected
            assertNotNull(e.message)
        }
    }
}
