package app.startool.android.backup

import app.startool.android.MaintenanceLock
import app.startool.android.domain.BackupFileGateway
import app.startool.android.domain.ConflictChoice
import app.startool.android.domain.HourlyKey
import app.startool.android.domain.ImportApplyResult
import app.startool.android.domain.ImportPlan
import app.startool.android.domain.StarToolRepository
import java.time.Clock

sealed interface ExportResult {
    data class Success(val count: Int) : ExportResult
    data object Cancelled : ExportResult
    data class Failure(val message: String) : ExportResult
}

sealed interface PreviewResult {
    data class Success(val plan: ImportPlan) : PreviewResult
    data object Cancelled : PreviewResult
    data class Failure(val message: String) : PreviewResult
}

class BackupCoordinator(
    private val repository: StarToolRepository,
    private val maintenance: MaintenanceLock,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun exportBackup(gateway: BackupFileGateway): ExportResult {
        val fileName = BackupCodec.createFileName(clock.instant())
        val uri = gateway.pickSaveLocation(fileName) ?: return ExportResult.Cancelled

        return maintenance.runExclusive {
            try {
                val snapshot = repository.createBackupSnapshot()
                val json = BackupCodec.encode(snapshot, clock.instant())
                val bytes = json.toByteArray(Charsets.UTF_8)
                val written = gateway.writeAll(uri, bytes)
                if (written) {
                    ExportResult.Success(snapshot.entries.size)
                } else {
                    gateway.delete(uri)
                    ExportResult.Failure("写入备份文件失败")
                }
            } catch (t: Throwable) {
                gateway.delete(uri)
                ExportResult.Failure(t.message ?: "导出异常")
            }
        }
    }

    suspend fun pickAndPreview(gateway: BackupFileGateway): PreviewResult {
        val uri = gateway.pickOpenFile() ?: return PreviewResult.Cancelled

        return maintenance.runExclusive {
            try {
                val bytes = gateway.readAll(uri)
                    ?: return@runExclusive PreviewResult.Failure("读取文件失败")
                val validated = BackupCodec.decodeAndValidate(bytes)
                val plan = repository.previewImport(validated)
                PreviewResult.Success(plan)
            } catch (t: Throwable) {
                PreviewResult.Failure(t.message ?: "校验失败")
            }
        }
    }

    suspend fun applyImport(
        plan: ImportPlan,
        choices: Map<HourlyKey, ConflictChoice>,
    ): ImportApplyResult = maintenance.runExclusive {
        repository.applyImport(plan, choices)
    }
}
