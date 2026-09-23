package app.startool.android.data

import androidx.room.withTransaction
import app.startool.android.MaintenanceLock
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
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomStarToolRepository(
    private val database: StarToolDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val maintenance: MaintenanceLock? = null,
) : StarToolRepository {

    private val dao = database.hourlyEntryDao()
    private val writeMutex = Mutex()
    private val versionCounter = AtomicLong(1L)

    override fun observeDay(date: LocalDate): Flow<List<HourlyEntry>> =
        dao.observeDay(date.toString())
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeRecordedDates(): Flow<Set<LocalDate>> =
        dao.observeRecordedDates()
            .map { list -> list.map { LocalDate.parse(it) }.toSet() }
            .distinctUntilChanged()

    override suspend fun saveEntry(
        key: HourlyKey,
        mentalScore: Int,
        physicalScore: Int,
        note: String,
    ): OpResult {
        if (maintenance?.busy?.value == true) {
            return OpResult.BlockedByMaintenance
        }
        return writeMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val existing = dao.getEntry(key.date.toString(), key.hour)
                    if (existing != null &&
                        existing.mentalScore == mentalScore &&
                        existing.physicalScore == physicalScore &&
                        existing.note == note
                    ) {
                        return@withContext OpResult.NoChange
                    }

                    val now = clock.instant()
                    val createdAtEpoch = existing?.createdAtEpochMilli ?: now.toEpochMilli()
                    val updatedAtEpoch = now.toEpochMilli()

                    val entity = HourlyEntryEntity(
                        date = key.date.toString(),
                        hour = key.hour,
                        mentalScore = mentalScore,
                        physicalScore = physicalScore,
                        note = note,
                        createdAtEpochMilli = createdAtEpoch,
                        updatedAtEpochMilli = updatedAtEpoch,
                    )
                    dao.insertOrUpdate(entity)
                    versionCounter.incrementAndGet()
                    OpResult.Success
                }.getOrElse { OpResult.Failure(it) }
            }
        }
    }

    override suspend fun deleteEntry(key: HourlyKey): OpResult {
        if (maintenance?.busy?.value == true) {
            return OpResult.BlockedByMaintenance
        }
        return writeMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val deleted = dao.delete(key.date.toString(), key.hour)
                    if (deleted > 0) {
                        versionCounter.incrementAndGet()
                        OpResult.Success
                    } else {
                        OpResult.NoChange
                    }
                }.getOrElse { OpResult.Failure(it) }
            }
        }
    }

    override suspend fun createBackupSnapshot(): BackupSnapshot =
        withContext(Dispatchers.IO) {
            val entries = dao.getAllEntries().map { it.toDomain() }
            BackupSnapshot(entries)
        }

    override suspend fun previewImport(backup: ValidatedBackup): ImportPlan =
        withContext(Dispatchers.IO) {
            val locals = dao.getAllEntries().map { it.toDomain() }
            val localMap = locals.associateBy { it.key }

            val additions = mutableListOf<HourlyEntry>()
            val identical = mutableListOf<HourlyEntry>()
            val conflicts = mutableListOf<ImportConflict>()

            for (backupEntry in backup.entries) {
                val local = localMap[backupEntry.key]
                when {
                    local == null -> additions.add(backupEntry)
                    backupEntry.contentEquals(local) -> identical.add(backupEntry)
                    else -> conflicts.add(ImportConflict(backupEntry.key, local, backupEntry))
                }
            }

            ImportPlan(
                backup = backup,
                additions = additions,
                identical = identical,
                conflicts = conflicts,
                dataVersion = versionCounter.get(),
            )
        }

    override suspend fun applyImport(
        plan: ImportPlan,
        choices: Map<HourlyKey, ConflictChoice>,
    ): ImportApplyResult = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (plan.dataVersion != versionCounter.get()) {
                    return@withContext ImportApplyResult.PreviewStale
                }

                val toInsert = mutableListOf<HourlyEntryEntity>()

                for (addition in plan.additions) {
                    toInsert.add(HourlyEntryEntity.fromDomain(addition))
                }

                var replacedCount = 0
                var keptLocalCount = 0
                for (conflict in plan.conflicts) {
                    val choice = choices[conflict.key] ?: ConflictChoice.KeepLocal
                    if (choice == ConflictChoice.UseBackup) {
                        toInsert.add(HourlyEntryEntity.fromDomain(conflict.backup))
                        replacedCount++
                    } else {
                        keptLocalCount++
                    }
                }

                database.withTransaction {
                    if (toInsert.isNotEmpty()) {
                        dao.insertAll(toInsert)
                    }
                }

                versionCounter.incrementAndGet()

                val report = ImportReport(
                    added = plan.additions.size,
                    replacedWithBackup = replacedCount,
                    keptLocal = keptLocalCount,
                    skippedIdentical = plan.identical.size,
                )
                ImportApplyResult.Completed(report)
            }.getOrElse { ImportApplyResult.Failure(it) }
        }
    }
}
