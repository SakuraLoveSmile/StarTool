package app.startool.android.fake

import app.startool.android.domain.BackupSnapshot
import app.startool.android.domain.ConflictChoice
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.HourlyKey
import app.startool.android.domain.ImportApplyResult
import app.startool.android.domain.ImportConflict
import app.startool.android.domain.ImportPlan
import app.startool.android.domain.ImportReport
import app.startool.android.domain.OpResult
import app.startool.android.domain.StarToolRepository
import app.startool.android.domain.ValidatedBackup
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 供 UI 并行开发的内存假实现（T0 共享假实现）。
 * 语义与真实实现一致：唯一键、版本号、快照、预览失效。
 * 集成阶段由 RoomStarToolRepository 替代。
 */
class FakeStarToolRepository(private val clock: Clock = Clock.systemDefaultZone()) :
    StarToolRepository {

    private val entries = MutableStateFlow<Map<HourlyKey, HourlyEntry>>(emptyMap())
    private val version = AtomicLong(0)
    private val writeMutex = Mutex()

    /** 预填一些演示数据便于 UI 开发；集成时移除或置空。 */
    constructor(clock: Clock, seedDemo: Boolean) : this(clock) {
        if (seedDemo) seed()
    }

    private fun seed() {
        val today = LocalDate.now(clock)
        val now = Instant.now(clock)
        listOf(
            HourlyEntry(HourlyKey(today, 9), 0, 10, "刚睡醒", now, now),
            HourlyEntry(HourlyKey(today, 10), 5, 5, "", now, now),
            HourlyEntry(HourlyKey(today, 12), 10, 0, "", now, now),
            HourlyEntry(HourlyKey(today, 13), 6, 3, "午饭后有点困", now, now),
            HourlyEntry(HourlyKey(today.minusDays(1), 20), 4, 6, "", now, now),
        ).forEach { e -> entries.update { it + (e.key to e) } }
        version.incrementAndGet()
    }

    override fun observeDay(date: LocalDate): Flow<List<HourlyEntry>> =
        entries.map { map -> map.values.filter { it.key.date == date }.sortedBy { it.key.hour } }

    override fun observeRecordedDates(): Flow<Set<LocalDate>> =
        entries.map { map -> map.keys.mapTo(linkedSetOf()) { it.date } }

    override suspend fun saveEntry(
        key: HourlyKey,
        mentalScore: Int,
        physicalScore: Int,
        note: String,
    ): OpResult {
        delay(120) // 模拟写库延迟，便于测试"保存中"状态
        return writeMutex.withLock {
            val existing = entries.value[key]
            if (existing != null &&
                existing.mentalScore == mentalScore &&
                existing.physicalScore == physicalScore &&
                existing.note == note
            ) {
                OpResult.NoChange
            } else {
                val now = Instant.now(clock)
                val entry = HourlyEntry(
                    key = key,
                    mentalScore = mentalScore,
                    physicalScore = physicalScore,
                    note = note,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                entries.update { it + (key to entry) }
                version.incrementAndGet()
                OpResult.Success
            }
        }
    }

    override suspend fun deleteEntry(key: HourlyKey): OpResult {
        delay(80)
        return writeMutex.withLock {
            if (entries.value.containsKey(key)) {
                entries.update { it - key }
                version.incrementAndGet()
            }
            OpResult.Success
        }
    }

    override suspend fun createBackupSnapshot(): BackupSnapshot =
        BackupSnapshot(entries.value.values.sortedWith(compareBy({ it.key.date }, { it.key.hour })))

    override suspend fun previewImport(backup: ValidatedBackup): ImportPlan {
        val local = entries.value
        val additions = mutableListOf<HourlyEntry>()
        val identical = mutableListOf<HourlyEntry>()
        val conflicts = mutableListOf<ImportConflict>()
        for (b in backup.entries) {
            val l = local[b.key]
            when {
                l == null -> additions += b
                l.contentEquals(b) -> identical += b
                else -> conflicts += ImportConflict(b.key, l, b)
            }
        }
        return ImportPlan(backup, additions, identical, conflicts, version.get())
    }

    override suspend fun applyImport(
        plan: ImportPlan,
        choices: Map<HourlyKey, ConflictChoice>,
    ): ImportApplyResult = writeMutex.withLock {
        if (plan.dataVersion != version.get()) return ImportApplyResult.PreviewStale

        var replaced = 0
        var kept = 0
        val newMap = ConcurrentHashMap(entries.value)
        plan.additions.forEach { newMap[it.key] = it }
        plan.conflicts.forEach { c ->
            when (choices[c.key] ?: ConflictChoice.KeepLocal) {
                ConflictChoice.UseBackup -> {
                    newMap[c.key] = c.backup
                    replaced++
                }
                ConflictChoice.KeepLocal -> kept++
            }
        }
        entries.value = newMap.toMap()
        version.incrementAndGet()
        ImportApplyResult.Completed(
            ImportReport(
                added = plan.additions.size,
                replacedWithBackup = replaced,
                keptLocal = kept,
                skippedIdentical = plan.identical.size,
            ),
        )
    }
}
