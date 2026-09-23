package app.startool.android.backup

import app.startool.android.domain.BackupSnapshot
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.HourlyKey
import app.startool.android.domain.ValidatedBackup
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject

class BackupValidationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object BackupCodec {

    const val FORMAT_NAME = "startool-backup"
    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_BACKUP_BYTES = 10 * 1024 * 1024 // 10 MiB
    const val MAX_ENTRIES = 100_000

    private val DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * 将快照编码为格式合同规定的未加密 JSON。
     */
    fun encode(snapshot: BackupSnapshot, exportedAt: Instant): String {
        val root = JSONObject()
        root.put("format", FORMAT_NAME)
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportedAt", exportedAt.toString())

        val entriesArray = JSONArray()
        for (entry in snapshot.entries) {
            val item = JSONObject()
            item.put("date", entry.key.date.toString())
            item.put("hour", entry.key.hour)
            item.put("mentalScore", entry.mentalScore)
            item.put("physicalScore", entry.physicalScore)
            item.put("note", entry.note)
            item.put("createdAt", entry.createdAt.toString())
            item.put("updatedAt", entry.updatedAt.toString())
            entriesArray.put(item)
        }
        root.put("entries", entriesArray)
        return root.toString(2)
    }

    /**
     * 解析并严格校验备份 JSON。
     * 所有记录必须全部校验通过，否则整体抛出 [BackupValidationException]。
     */
    fun decodeAndValidate(
        bytes: ByteArray,
        maxBytes: Int = MAX_BACKUP_BYTES,
        maxEntries: Int = MAX_ENTRIES,
    ): ValidatedBackup {
        if (bytes.size > maxBytes) {
            throw BackupValidationException("备份文件大小超过上限 (${bytes.size} > $maxBytes 字节)")
        }

        val jsonString = try {
            bytes.toString(Charsets.UTF_8)
        } catch (t: Throwable) {
            throw BackupValidationException("文件非合法 UTF-8 编码", t)
        }

        val root = try {
            JSONObject(jsonString)
        } catch (t: Throwable) {
            throw BackupValidationException("JSON 语法解析失败: ${t.message}", t)
        }

        // 1. format 校验
        if (!root.has("format") || root.optString("format") != FORMAT_NAME) {
            throw BackupValidationException("无效的 format 字段，期望 '$FORMAT_NAME'")
        }

        // 2. schemaVersion 校验
        if (!root.has("schemaVersion")) {
            throw BackupValidationException("缺少 schemaVersion 字段")
        }
        val versionVal = root.opt("schemaVersion")
        if (versionVal !is Int || versionVal != CURRENT_SCHEMA_VERSION) {
            throw BackupValidationException("不支持的 schemaVersion: $versionVal")
        }

        // 3. exportedAt
        val exportedAt = if (root.has("exportedAt") && !root.isNull("exportedAt")) {
            try {
                Instant.parse(root.getString("exportedAt"))
            } catch (t: DateTimeParseException) {
                throw BackupValidationException("无效的 exportedAt 时间戳: ${root.opt("exportedAt")}")
            }
        } else {
            null
        }

        // 4. entries 校验
        if (!root.has("entries")) {
            throw BackupValidationException("缺少 entries 数组")
        }
        val entriesArray = root.optJSONArray("entries")
            ?: throw BackupValidationException("entries 必须为 JSON 数组")

        if (entriesArray.length() > maxEntries) {
            throw BackupValidationException("记录数超出上限 (${entriesArray.length()} > $maxEntries)")
        }

        val seenKeys = mutableSetOf<HourlyKey>()
        val resultEntries = ArrayList<HourlyEntry>(entriesArray.length())

        for (i in 0 until entriesArray.length()) {
            val item = entriesArray.optJSONObject(i)
                ?: throw BackupValidationException("第 $i 条记录不是合法的 JSON 对象")

            val entry = parseEntry(item, i)
            if (!seenKeys.add(entry.key)) {
                throw BackupValidationException("备份包含重复时间记录: ${entry.key}")
            }
            resultEntries.add(entry)
        }

        return ValidatedBackup(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = exportedAt,
            entries = resultEntries,
        )
    }

    private fun parseEntry(obj: JSONObject, index: Int): HourlyEntry {
        // 必填字段检查
        val requiredFields = listOf("date", "hour", "mentalScore", "physicalScore", "note", "createdAt", "updatedAt")
        for (field in requiredFields) {
            if (!obj.has(field) || obj.isNull(field)) {
                throw BackupValidationException("第 $index 条记录缺少必填字段: $field")
            }
        }

        // date
        val dateRaw = obj.optString("date")
        if (!DATE_PATTERN.matcher(dateRaw).matches()) {
            throw BackupValidationException("第 $index 条记录日期格式错误，必须为 YYYY-MM-DD: $dateRaw")
        }
        val date = try {
            LocalDate.parse(dateRaw)
        } catch (t: DateTimeParseException) {
            throw BackupValidationException("第 $index 条记录日期非有效日历日期: $dateRaw")
        }

        // hour: 必须为 0..23 整数，拒绝小数与字符串
        val hourVal = obj.opt("hour")
        if (hourVal !is Int || hourVal !in 0..23) {
            throw BackupValidationException("第 $index 条记录 hour 必须是 0..23 整数: $hourVal")
        }

        // mentalScore: 必须为 0..10 整数
        val mentalVal = obj.opt("mentalScore")
        if (mentalVal !is Int || mentalVal !in HourlyEntry.SCORE_MIN..HourlyEntry.SCORE_MAX) {
            throw BackupValidationException("第 $index 条记录 mentalScore 必须是 0..10 整数: $mentalVal")
        }

        // physicalScore: 必须为 0..10 整数
        val physicalVal = obj.opt("physicalScore")
        if (physicalVal !is Int || physicalVal !in HourlyEntry.SCORE_MIN..HourlyEntry.SCORE_MAX) {
            throw BackupValidationException("第 $index 条记录 physicalScore 必须是 0..10 整数: $physicalVal")
        }

        // note: 字符串，最多 200 个 Unicode 码点
        val noteVal = obj.opt("note")
        if (noteVal !is String) {
            throw BackupValidationException("第 $index 条记录 note 必须为字符串: $noteVal")
        }
        if (noteVal.codePointCount(0, noteVal.length) > HourlyEntry.NOTE_MAX_CODEPOINTS) {
            throw BackupValidationException("第 $index 条记录 note 超过 200 码点上限 (长 ${noteVal.length})")
        }

        // createdAt / updatedAt
        val createdAt = try {
            Instant.parse(obj.getString("createdAt"))
        } catch (t: DateTimeParseException) {
            throw BackupValidationException("第 $index 条记录 createdAt 非有效 UTC 时间戳")
        }

        val updatedAt = try {
            Instant.parse(obj.getString("updatedAt"))
        } catch (t: DateTimeParseException) {
            throw BackupValidationException("第 $index 条记录 updatedAt 非有效 UTC 时间戳")
        }

        return HourlyEntry(
            key = HourlyKey(date, hourVal),
            mentalScore = mentalVal,
            physicalScore = physicalVal,
            note = noteVal,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * 生成导出文件名：StarTool-backup-YYYYMMDD-HHmmss.json
     */
    fun createFileName(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(java.time.ZoneId.of("UTC"))
        return "StarTool-backup-${formatter.format(instant)}.json"
    }
}
