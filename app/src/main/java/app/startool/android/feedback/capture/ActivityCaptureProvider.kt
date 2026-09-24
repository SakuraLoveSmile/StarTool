package app.startool.android.feedback.capture

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import androidx.annotation.VisibleForTesting
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
/**
 * 屏幕捕获与图像处理（计划 §T2）。
 * 遵守规格：最长边 <= 2048px，输出像素 <= 4,000,000，单图体积 <= 5MiB。
 */
object ActivityCaptureProvider {

    const val MAX_CAPTURE_EDGE = 2048
    const val MAX_CAPTURE_PIXELS = 4_000_000
    const val MAX_PNG_BYTES = 5 * 1024 * 1024

    /**
     * 捕获指定 Activity 当前可见画面并转换为 PNG Data URL。
     * [hideViews] 包含在截图瞬间需要临时隐藏的原生视图（例如悬浮球自身）。
     */
    suspend fun captureActivityRaw(
        activity: Activity,
        hideViews: List<View> = emptyList(),
    ): Bitmap? = withContext(Dispatchers.Main) {
        val decorView = activity.window?.decorView ?: return@withContext null
        if (decorView.width <= 0 || decorView.height <= 0) return@withContext null

        val previousVisibilities = hideViews.map { it to it.visibility }
        try {
            hideViews.forEach { it.visibility = View.INVISIBLE }
            captureViewToBitmap(activity, decorView)
        } finally {
            previousVisibilities.forEach { (view, vis) -> view.visibility = vis }
        }
    }

    suspend fun cropAndEncodeBitmap(
        srcBitmap: Bitmap,
        normalizedCrop: RectF? = null,
    ): String? = withContext(Dispatchers.Default) {
        val cropped = if (normalizedCrop != null) {
            val left = (normalizedCrop.left * srcBitmap.width).roundToInt().coerceIn(0, srcBitmap.width - 1)
            val top = (normalizedCrop.top * srcBitmap.height).roundToInt().coerceIn(0, srcBitmap.height - 1)
            val right = (normalizedCrop.right * srcBitmap.width).roundToInt().coerceIn(left + 1, srcBitmap.width)
            val bottom = (normalizedCrop.bottom * srcBitmap.height).roundToInt().coerceIn(top + 1, srcBitmap.height)
            val w = maxOf(1, right - left)
            val h = maxOf(1, bottom - top)
            Bitmap.createBitmap(srcBitmap, left, top, w, h)
        } else {
            srcBitmap
        }

        try {
            val scaled = scaleToFitLimits(cropped)
            try {
                val pngBytes = compressToPng(scaled) ?: return@withContext null
                if (pngBytes.size > MAX_PNG_BYTES) {
                    return@withContext null
                }
                val base64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
                "data:image/png;base64,$base64"
            } finally {
                if (scaled !== cropped && scaled !== srcBitmap) {
                    scaled.recycle()
                }
            }
        } finally {
            if (cropped !== srcBitmap) {
                cropped.recycle()
            }
        }
    }

    /**
     * 捕获指定 Activity 当前可见画面并转换为 PNG Data URL。
     * [hideViews] 包含在截图瞬间需要临时隐藏的原生视图（例如悬浮球自身）。
     */
    suspend fun captureActivity(
        activity: Activity,
        hideViews: List<View> = emptyList(),
    ): String? {
        val raw = captureActivityRaw(activity, hideViews) ?: return null
        return try {
            cropAndEncodeBitmap(raw, null)
        } finally {
            raw.recycle()
        }
    }

    private suspend fun captureViewToBitmap(activity: Activity, view: View): Bitmap? {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val deferred = CompletableDeferred<Boolean>()
            try {
                PixelCopy.request(
                    activity.window,
                    bitmap,
                    { copyResult ->
                        deferred.complete(copyResult == PixelCopy.SUCCESS)
                    },
                    Handler(Looper.getMainLooper()),
                )
                val success = withTimeoutOrNull(1000) { deferred.await() }
                if (success == true) {
                    return bitmap
                }
            } catch (t: Throwable) {
                // 回退到 Canvas 绘制
            }
        }

        // 回退绘制
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    @VisibleForTesting
    internal fun scaleToFitLimits(src: Bitmap): Bitmap {
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        val maxEdge = maxOf(w, h)
        val area = w * h

        var factor = 1.0
        if (maxEdge > MAX_CAPTURE_EDGE) {
            factor = min(factor, MAX_CAPTURE_EDGE / maxEdge)
        }
        if (area > MAX_CAPTURE_PIXELS) {
            factor = min(factor, sqrt(MAX_CAPTURE_PIXELS / area))
        }

        if (factor >= 1.0) {
            return src
        }

        val targetW = maxOf(1, (w * factor).roundToInt())
        val targetH = maxOf(1, (h * factor).roundToInt())
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    @VisibleForTesting
    internal fun compressToPng(bitmap: Bitmap): ByteArray? {
        val stream = ByteArrayOutputStream()
        val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return if (success) stream.toByteArray() else null
    }
}
