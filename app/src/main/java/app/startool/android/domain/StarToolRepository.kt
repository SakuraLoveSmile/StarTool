package app.startool.android.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 数据层统一入口。UI 不直接访问 DAO；所有写操作经此接口串行协调，
 * 数据库操作必须在 IO 调度器执行，Room 事务保证原子性。
 *
 * 数据版本：每次本机数据发生变化的写操作（保存/删除/导入）都会推进内部版本号，
 * [ImportPlan.dataVersion] 用于检测"预览后数据已变化"。
 */
interface StarToolRepository {

    /** 观察某一天的记录，按小时升序。日期切换后不得被旧查询覆盖。 */
    fun observeDay(date: LocalDate): Flow<List<HourlyEntry>>

    /** 观察所有有记录的日期集合，用于日期选择。 */
    fun observeRecordedDates(): Flow<Set<LocalDate>>

    /**
     * 保存一条记录（新建或更新同日期同小时的现有记录）。
     * 分数必须完整且合法；备注可为空字符串。
     * 内容与已有记录完全相同时返回 [OpResult.NoChange] 且不改变 updatedAt。
     * 同一 (date, hour) 绝不能产生第二条记录。
     */
    suspend fun saveEntry(
        key: HourlyKey,
        mentalScore: Int,
        physicalScore: Int,
        note: String,
    ): OpResult

    suspend fun deleteEntry(key: HourlyKey): OpResult

    /**
     * 导出用的一致性快照。调用方负责在导出期间禁止其他写操作
     * （实现内部可通过维护锁保证快照一致）。
     */
    suspend fun createBackupSnapshot(): BackupSnapshot

    /**
     * 对已校验的备份做比较，生成导入计划。不修改任何数据。
     * 分类规则：
     *  - 本机不存在 → additions
     *  - 两项评分和备注完全一致 → identical
     *  - 同日期同小时内容不同 → conflicts
     *  - 仅存在于本机的记录 → 不在计划中（保留，不删除）
     */
    suspend fun previewImport(backup: ValidatedBackup): ImportPlan

    /**
     * 按用户选择应用导入计划。
     * 所有新增与替换在单个事务中执行；任一失败整体回滚。
     * 若 [plan] 的 dataVersion 与当前版本不一致，返回 [ImportApplyResult.PreviewStale]。
     * [choices] 仅覆盖 conflicts 中的键；缺省视为 KeepLocal。
     * 使用备份替换时整条记录作为整体替换，不拼接字段。
     */
    suspend fun applyImport(
        plan: ImportPlan,
        choices: Map<HourlyKey, ConflictChoice>,
    ): ImportApplyResult
}
