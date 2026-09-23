package app.startool.android.data

import androidx.room.Entity
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.HourlyKey
import java.time.Instant
import java.time.LocalDate

/**
 * 本地 Room 实体：
 * 联合主键 (date, hour)
 * 日期以 YYYY-MM-DD 字符串存储；小时以 0–23 整数存储；
 * 时间戳以 UTC 毫秒值存储。
 */
@Entity(
    tableName = "hourly_entries",
    primaryKeys = ["date", "hour"],
)
data class HourlyEntryEntity(
    val date: String,
    val hour: Int,
    val mentalScore: Int,
    val physicalScore: Int,
    val note: String,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
) {
    fun toDomain(): HourlyEntry = HourlyEntry(
        key = HourlyKey(LocalDate.parse(date), hour),
        mentalScore = mentalScore,
        physicalScore = physicalScore,
        note = note,
        createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMilli),
    )

    companion object {
        fun fromDomain(entry: HourlyEntry): HourlyEntryEntity = HourlyEntryEntity(
            date = entry.key.date.toString(),
            hour = entry.key.hour,
            mentalScore = entry.mentalScore,
            physicalScore = entry.physicalScore,
            note = entry.note,
            createdAtEpochMilli = entry.createdAt.toEpochMilli(),
            updatedAtEpochMilli = entry.updatedAt.toEpochMilli(),
        )
    }
}
