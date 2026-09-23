package app.startool.android.domain

import java.time.Instant
import java.time.LocalDate

/**
 * 记录键：本地日历日期 + 小时（0–23）。联合唯一。
 * 13:00 表示 13:00–13:59 这个自然小时。
 * 日期和小时以本地日历值保存，不从 UTC 时间戳反推。
 */
data class HourlyKey(
    val date: LocalDate,
    val hour: Int,
) {
    init {
        require(hour in 0..23) { "hour must be in 0..23, was $hour" }
    }

    /** 例如 "13:00–13:59" */
    val hourLabel: String get() = "%02d:00–%02d:59".format(hour, hour)
}

/**
 * 一条已保存的小时记录。两项分数不允许为空（未填写只存在于草稿中）。
 */
data class HourlyEntry(
    val key: HourlyKey,
    val mentalScore: Int,
    val physicalScore: Int,
    /** 备注，非空字符串（无备注时为 ""），最多 [NOTE_MAX_CODEPOINTS] 个 Unicode 码点。 */
    val note: String,
    /** UTC 时间戳，仅用于备份比较，不决定归属日期。 */
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(mentalScore in SCORE_MIN..SCORE_MAX) { "mentalScore out of range" }
        require(physicalScore in SCORE_MIN..SCORE_MAX) { "physicalScore out of range" }
        require(note.codePointCount(0, note.length) <= NOTE_MAX_CODEPOINTS) { "note too long" }
    }

    /** 两项评分和备注是否一致（备份比较中的"相同记录"判定）。 */
    fun contentEquals(other: HourlyEntry): Boolean =
        mentalScore == other.mentalScore &&
            physicalScore == other.physicalScore &&
            note == other.note

    companion object {
        const val SCORE_MIN = 0
        const val SCORE_MAX = 10
        const val NOTE_MAX_CODEPOINTS = 200
    }
}

/**
 * 未保存的编辑草稿。分数可为空 = 未选择。
 * 只有草稿允许未填写；保存时两项都必须有值。
 */
data class EntryDraft(
    val key: HourlyKey,
    val mentalScore: Int?,
    val physicalScore: Int?,
    val note: String,
) {
    val isComplete: Boolean get() = mentalScore != null && physicalScore != null
}

/**
 * 已通过完整校验的备份内容。所有记录已验证合法，可直接导入。
 */
data class ValidatedBackup(
    val schemaVersion: Int,
    val exportedAt: Instant?,
    val entries: List<HourlyEntry>,
)

/** 冲突项：同日期同小时，但本机与备份内容不同。 */
data class ImportConflict(
    val key: HourlyKey,
    val local: HourlyEntry,
    val backup: HourlyEntry,
)

/**
 * 导入预览计划。
 * [dataVersion] 是预览生成时的本机数据版本；应用导入时若版本变化，计划作废。
 */
data class ImportPlan(
    val backup: ValidatedBackup,
    /** 本机不存在 → 新增 */
    val additions: List<HourlyEntry>,
    /** 内容完全一致 → 跳过 */
    val identical: List<HourlyEntry>,
    /** 内容不同 → 冲突，等待逐项选择 */
    val conflicts: List<ImportConflict>,
    val dataVersion: Long,
)

enum class ConflictChoice { KeepLocal, UseBackup }

/** 导入结果报告。 */
data class ImportReport(
    val added: Int,
    val replacedWithBackup: Int,
    val keptLocal: Int,
    val skippedIdentical: Int,
) {
    val summary: String
        get() = "新增 $added 条，使用备份替换 $replacedWithBackup 条，" +
            "保留本机冲突 $keptLocal 条，跳过相同记录 $skippedIdentical 条。"
}

/** 备份一致性快照。 */
data class BackupSnapshot(
    val entries: List<HourlyEntry>,
)
