package app.startool.android.acceptance

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.startool.android.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * 端到端验收测试基类。
 * 运行在 e2e 构建类型（applicationId `app.startool.android.e2e`），
 * 与试用包 `app.startool.android` 的数据完全隔离。
 *
 * 使用 createEmptyComposeRule + 手动 ActivityScenario.launch，
 * 以便在 Activity 启动（即 Room 打开数据库）之前先清空数据。
 */
abstract class AcceptanceTestBase {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    protected lateinit var scenario: ActivityScenario<MainActivity>

    protected val targetContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetAndLaunch() {
        clearDatabases()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    /** 删掉 e2e 包私有数据库目录下的所有库文件。 */
    private fun clearDatabases() {
        val ctx = targetContext
        ctx.databaseList()?.forEach { name -> ctx.deleteDatabase(name) }
    }

    /** 重建 Activity（验证持久化/进程重启场景时调用）。 */
    fun relaunch() {
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
