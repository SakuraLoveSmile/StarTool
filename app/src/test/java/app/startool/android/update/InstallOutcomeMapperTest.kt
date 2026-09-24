package app.startool.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 状态码取值以 compileSdk 36 的 android.jar 实测为准（javap 核对），
 * 本测试同时充当「常量值漂移」的回归护栏：如果哪天猜错了序号，这里会立刻红。
 */
class InstallOutcomeMapperTest {

    private fun feedback(status: Int, message: String? = null) =
        InstallOutcomeMapper.describe(status, message)

    @Test
    fun statusConstantsMatchPlatformValues() {
        assertEquals(0, InstallOutcomeMapper.STATUS_SUCCESS)
        assertEquals(-1, InstallOutcomeMapper.STATUS_PENDING_USER_ACTION)
        assertEquals(1, InstallOutcomeMapper.STATUS_FAILURE)
        assertEquals(2, InstallOutcomeMapper.STATUS_FAILURE_BLOCKED)
        assertEquals(3, InstallOutcomeMapper.STATUS_FAILURE_ABORTED)
        assertEquals(4, InstallOutcomeMapper.STATUS_FAILURE_INVALID)
        assertEquals(5, InstallOutcomeMapper.STATUS_FAILURE_CONFLICT)
        assertEquals(6, InstallOutcomeMapper.STATUS_FAILURE_STORAGE)
        assertEquals(7, InstallOutcomeMapper.STATUS_FAILURE_INCOMPATIBLE)
        assertEquals(8, InstallOutcomeMapper.STATUS_FAILURE_TIMEOUT)
    }

    @Test
    fun successMapsToSuccess() {
        assertTrue(feedback(InstallOutcomeMapper.STATUS_SUCCESS) is InstallFeedback.Success)
    }

    @Test
    fun pendingUserActionIsNotAnError() {
        assertTrue(
            feedback(InstallOutcomeMapper.STATUS_PENDING_USER_ACTION) is InstallFeedback.PendingUserAction,
        )
    }

    @Test
    fun signatureConflictGivesActionableChineseGuidance() {
        // 这正是本项目真机实测遇到的失败
        val realWorld = "[INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package " +
            "app.startool.android.gemini signatures do not match newer version; ignoring!]"
        val result = feedback(InstallOutcomeMapper.STATUS_FAILURE, realWorld)

        assertTrue("got $result", result is InstallFeedback.Failed)
        result as InstallFeedback.Failed
        assertTrue("应包含导出备份提示: ${result.userMessage}", result.userMessage.contains("导出备份"))
        assertTrue("应包含卸载指引: ${result.userMessage}", result.userMessage.contains("卸载"))
        assertTrue(result.userMessage.contains("签名"))
        // 原始技术信息保留在 detail 里，便于排查
        assertTrue(result.technicalDetail!!.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
    }

    @Test
    fun conflictStatusCodeWithSignatureMessageAlsoMapped() {
        val result = feedback(
            InstallOutcomeMapper.STATUS_FAILURE_CONFLICT,
            " signatures do not match ",
        )
        assertTrue(result is InstallFeedback.Failed)
        assertTrue((result as InstallFeedback.Failed).userMessage.contains("签名"))
    }

    @Test
    fun userCancellationIsNotReportedAsError() {
        for (msg in listOf(
            "User canceled the install",
            "User cancelled",
            "INSTALL_FAILED_ABORTED by user",
        )) {
            val result = feedback(InstallOutcomeMapper.STATUS_FAILURE, msg)
            assertTrue("msg=$msg got=$result", result is InstallFeedback.Cancelled)
        }
    }

    @Test
    fun abortedStatusCodeIsCancelledRegardlessOfMessage() {
        assertTrue(
            feedback(InstallOutcomeMapper.STATUS_FAILURE_ABORTED, null) is InstallFeedback.Cancelled,
        )
        assertTrue(
            feedback(InstallOutcomeMapper.STATUS_FAILURE_ABORTED, "session abandoned")
                is InstallFeedback.Cancelled,
        )
    }

    @Test
    fun eachFailureCodeHasDistinctActionableCopy() {
        val cases = mapOf(
            InstallOutcomeMapper.STATUS_FAILURE_BLOCKED to "拦截",
            InstallOutcomeMapper.STATUS_FAILURE_INCOMPATIBLE to "不兼容",
            InstallOutcomeMapper.STATUS_FAILURE_INVALID to "无效或已损坏",
            InstallOutcomeMapper.STATUS_FAILURE_STORAGE to "存储空间不足",
            InstallOutcomeMapper.STATUS_FAILURE_TIMEOUT to "超时",
        )
        val seen = mutableSetOf<String>()
        for ((code, keyword) in cases) {
            val result = feedback(code, "some detail")
            assertTrue("code=$code got=$result", result is InstallFeedback.Failed)
            val message = (result as InstallFeedback.Failed).userMessage
            assertTrue("code=$code 文案应含「$keyword」，实际: $message", message.contains(keyword))
            assertTrue("不同状态码文案不应重复: $message", seen.add(message))
            assertEquals("some detail", result.technicalDetail)
        }
    }

    @Test
    fun blankMessageStillProducesReadableCopy() {
        val result = feedback(InstallOutcomeMapper.STATUS_FAILURE, "   ")
        assertTrue(result is InstallFeedback.Failed)
        assertTrue((result as InstallFeedback.Failed).userMessage.contains("安装失败"))
        assertEquals(null, result.technicalDetail)
    }

    @Test
    fun unknownStatusCodeFallsBackToGenericFailure() {
        val result = feedback(99, null)
        assertTrue("got $result", result is InstallFeedback.Failed)
        assertTrue((result as InstallFeedback.Failed).userMessage.contains("99"))
    }
}
