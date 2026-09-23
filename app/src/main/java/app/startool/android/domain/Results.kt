package app.startool.android.domain

/** 通用写操作结果。 */
sealed interface OpResult {
    data object Success : OpResult
    /** 保存内容与已有记录完全相同，视为无操作（不改变修改时间）。 */
    data object NoChange : OpResult
    /** 操作期间有导出/导入任务在运行，暂时拒绝写入以保证快照一致。 */
    data object BlockedByMaintenance : OpResult
    data class Failure(val cause: Throwable) : OpResult
}

/** 应用导入计划的结果。 */
sealed interface ImportApplyResult {
    data class Completed(val report: ImportReport) : ImportApplyResult
    /** 预览后本机数据已变化，预览失效，需重新生成计划。 */
    data object PreviewStale : ImportApplyResult
    data class Failure(val cause: Throwable) : ImportApplyResult
}
