package app.startool.android.acceptance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndToEndAcceptanceTest : AcceptanceTestBase() {

    @Test
    fun testEmptyRecordScreenDisplayed() {
        // 初始记录页：存在 hour_list 与各小时卡片
        composeRule.onNodeWithTag("hour_list").assertIsDisplayed()
        composeRule.onNodeWithTag("hour_0").assertIsDisplayed()
        composeRule.onNodeWithTag("recorded_count_text").assertIsDisplayed()
    }

    @Test
    fun testRecordSaveAndPersistAcrossRelaunch() {
        // 1. 点击 9 点小时卡片打开评分编辑面板
        composeRule.onNodeWithTag("hour_9").performClick()
        composeRule.onNodeWithTag("edit_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_date_hour").assertIsDisplayed()

        // 初始保存按钮应禁用（未选择）
        composeRule.onNodeWithTag("save_entry").assertIsNotEnabled()

        // 2. 精神状态打分：点击设为 0 分
        composeRule.onNodeWithTag("mental_set_zero").performClick()
        composeRule.onNodeWithTag("mental_score_text").assertTextContains("0分")

        // 身体状态打分：点击设为 0 分后加到 2 分
        composeRule.onNodeWithTag("physical_set_zero").performClick()
        composeRule.onNodeWithTag("physical_plus").performClick()
        composeRule.onNodeWithTag("physical_plus").performClick()
        composeRule.onNodeWithTag("physical_score_text").assertTextContains("2分")

        // 填写备注
        composeRule.onNodeWithTag("note_field").performTextInput("验收测试备注")

        // 此时保存按钮可用
        composeRule.onNodeWithTag("save_entry").assertIsEnabled()
        composeRule.onNodeWithTag("save_entry").performClick()

        // 面板关闭，卡片显示已记录的分数和备注
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hour_9").assertTextContains("0分")
        composeRule.onNodeWithTag("hour_9").assertTextContains("2分")
        composeRule.onNodeWithTag("hour_9").assertTextContains("验收测试备注")

        // 3. 进程重启/重开验证持久化
        relaunch()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hour_9").assertTextContains("0分")
        composeRule.onNodeWithTag("hour_9").assertTextContains("2分")
        composeRule.onNodeWithTag("hour_9").assertTextContains("验收测试备注")
    }

    @Test
    fun testNavigateToHistoryAndBack() {
        // 1. 在记录页先记录一条
        composeRule.onNodeWithTag("hour_10").performClick()
        composeRule.onNodeWithTag("mental_set_zero").performClick()
        composeRule.onNodeWithTag("physical_set_zero").performClick()
        composeRule.onNodeWithTag("save_entry").performClick()
        composeRule.waitForIdle()

        // 2. 切换到底部导航“回看”
        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitForIdle()

        // 3. 回看页展示图表与明细
        composeRule.onNodeWithTag("history_chart").assertIsDisplayed()
        composeRule.onNodeWithTag("history_item_10").assertIsDisplayed()
        composeRule.onNodeWithTag("chart_legend_mental").assertIsDisplayed()
        composeRule.onNodeWithTag("chart_legend_physical").assertIsDisplayed()

        // 4. 切回“记录”页
        composeRule.onNodeWithTag("nav_record").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hour_10").assertIsDisplayed()
    }

    @Test
    fun testSettingsNavigation() {
        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_export").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_import").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_about").assertIsDisplayed()
    }
}
