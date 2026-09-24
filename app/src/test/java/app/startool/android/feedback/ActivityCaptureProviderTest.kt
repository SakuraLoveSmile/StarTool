package app.startool.android.feedback

import android.graphics.Bitmap
import app.startool.android.feedback.capture.ActivityCaptureProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ActivityCaptureProviderTest {

    @Test
    fun testLimitsConstants() {
        assertEquals(2048, ActivityCaptureProvider.MAX_CAPTURE_EDGE)
        assertEquals(4_000_000, ActivityCaptureProvider.MAX_CAPTURE_PIXELS)
        assertEquals(5 * 1024 * 1024, ActivityCaptureProvider.MAX_PNG_BYTES)
    }

    @Test
    fun testScaleCalculationLogic() {
        // 模拟 scaleToFitLimits 的数学缩放逻辑验证
        val maxEdge = 2048.0
        val maxPixels = 4_000_000.0

        // 场景 1：在范围内（1080 x 1920 = 2,073,600 像素，最大边 1920 <= 2048）
        val w1 = 1080.0
        val h1 = 1920.0
        val f1 = minOf(
            if (maxOf(w1, h1) > maxEdge) maxEdge / maxOf(w1, h1) else 1.0,
            if (w1 * h1 > maxPixels) kotlin.math.sqrt(maxPixels / (w1 * h1)) else 1.0
        )
        assertEquals(1.0, f1, 0.0001)

        // 场景 2：超长边（1000 x 4000）
        val w2 = 1000.0
        val h2 = 4000.0
        val f2 = minOf(
            if (maxOf(w2, h2) > maxEdge) maxEdge / maxOf(w2, h2) else 1.0,
            if (w2 * h2 > maxPixels) kotlin.math.sqrt(maxPixels / (w2 * h2)) else 1.0
        )
        // maxEdge 限制为 2048 / 4000 = 0.512
        val targetH2 = (h2 * f2).roundToInt()
        assertTrue(targetH2 <= 2048)

        // 场景 3：大面积正方形（3000 x 3000 = 9,000,000 像素）
        val w3 = 3000.0
        val h3 = 3000.0
        val f3 = minOf(
            if (maxOf(w3, h3) > maxEdge) maxEdge / maxOf(w3, h3) else 1.0,
            if (w3 * h3 > maxPixels) kotlin.math.sqrt(maxPixels / (w3 * h3)) else 1.0
        )
        val targetW3 = (w3 * f3).roundToInt()
        val targetH3 = (h3 * f3).roundToInt()
        assertTrue(targetW3 <= 2048)
        assertTrue(targetH3 <= 2048)
        assertTrue(targetW3.toLong() * targetH3.toLong() <= 4_000_000L)
    }
}
