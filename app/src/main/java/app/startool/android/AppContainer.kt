package app.startool.android

import android.content.Context
import app.startool.android.domain.BackupFileGateway
import app.startool.android.data.RoomStarToolRepository
import app.startool.android.domain.StarToolRepository
import app.startool.android.data.StarToolDatabase
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 应用级手动依赖容器（不使用 Hilt）。
 * Application onCreate 中创建；MainActivity 通过 Application 取用。
 *
 * 集成阶段：T1 数据层完成后，把 [repository] 的默认实现从
 * FakeStarToolRepository 换成 RoomStarToolRepository。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** 应用作用域：用于生命周期与数据库一致的后台任务。 */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 可注入的时钟；测试用固定 Clock 覆盖跨小时/跨日/时区场景。 */
    var clock: Clock = Clock.systemDefaultZone()

    /** 记录页与回看页共享的所选日期。 */
    val selectedDateStore: SelectedDateStore by lazy { SelectedDateStore(clock) }

    /**
     * 数据仓库。T0 用假实现让 UI 并行开发；集成时替换为 Room 实现。
     * 集成后改为 RoomStarToolRepository，并把 maintenance 传入构造函数。
     */
    val repository: StarToolRepository by lazy {
        val db = StarToolDatabase.getInstance(appContext)
        RoomStarToolRepository(
            database = db,
            clock = clock,
            maintenance = maintenance,
        )
    }

    /**
     * SAF 文件桥接，由 MainActivity 在 onCreate 中赋值（需要 ActivityResult）。
     * backup 模块调用前检查非空；为空表示选择器不可用。
     */
    @Volatile
    var fileGateway: BackupFileGateway? = null

    /**
     * 主页面切换请求总线：子页面写入，StarToolRoot 消费。
     * 用于空态"去记录"等跨页跳转，不引入导航框架。
     */
    val mainTabRequests: MutableStateFlow<MainTab?> = MutableStateFlow(null)

    fun requestTab(tab: MainTab) {
        mainTabRequests.value = tab
    }

    /**
     * 维护锁：导出 / 导入进行中时置忙，写操作（保存/删除）在此期间
     * 返回 OpResult.BlockedByMaintenance，UI 同时禁用编辑入口。
     * 由 backup 模块（导出/恢复流程）调用 runExclusive。
     */
    val maintenance: MaintenanceLock = MaintenanceLock()

    val updatePreferenceStore: app.startool.android.update.UpdatePreferenceStore by lazy {
        val sp = appContext.getSharedPreferences("startool_update_prefs", Context.MODE_PRIVATE)
        app.startool.android.update.UpdatePreferenceStore(sp)
    }

    val currentVersionCode: Int by lazy {
        try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (t: Throwable) {
            1
        }
    }

    val currentVersionName: String by lazy {
        try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            pInfo.versionName ?: "0.1.0"
        } catch (t: Throwable) {
            "0.1.0"
        }
    }

    val updateChecker: app.startool.android.update.UpdateChecker by lazy {
        app.startool.android.update.UpdateChecker(
            preferenceStore = updatePreferenceStore,
            currentApplicationId = appContext.packageName,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
        )
    }

    val feedbackManager: app.startool.android.feedback.FeedbackManager by lazy {
        app.startool.android.feedback.FeedbackManager(appContext)
    }

    /** 当前活动的 Activity，弱引用避免内存泄漏 */
    @Volatile
    private var _currentActivityRef: java.lang.ref.WeakReference<android.app.Activity>? = null

    var currentActivity: android.app.Activity?
        get() = _currentActivityRef?.get()
        set(value) {
            _currentActivityRef = if (value != null) java.lang.ref.WeakReference(value) else null
        }
}

class MaintenanceLock {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val mutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        _busy.value = true
        return try {
            mutex.withLock { block() }
        } finally {
            _busy.value = false
        }
    }
}
