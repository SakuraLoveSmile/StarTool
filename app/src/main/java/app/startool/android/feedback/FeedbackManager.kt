package app.startool.android.feedback

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import app.startool.android.feedback.capture.ActivityCaptureProvider
import app.startool.android.feedback.storage.FeedbackSessionStorage
import app.startool.android.feedback.storage.KeystoreSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FeedbackManager(
    private val context: Context,
    val sessionStorage: FeedbackSessionStorage = KeystoreSessionStore(context),
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("startool_feedback_prefs", Context.MODE_PRIVATE)

    private val _fabEnabled = MutableStateFlow(prefs.getBoolean(KEY_FAB_ENABLED, true))
    val fabEnabled: StateFlow<Boolean> = _fabEnabled.asStateFlow()

    private val _isOverlayOpen = MutableStateFlow(false)
    val isOverlayOpen: StateFlow<Boolean> = _isOverlayOpen.asStateFlow()
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isCaptureSelectionOpen = MutableStateFlow(false)
    val isCaptureSelectionOpen: StateFlow<Boolean> = _isCaptureSelectionOpen.asStateFlow()

    private val _frozenBitmap = MutableStateFlow<Bitmap?>(null)
    val frozenBitmap: StateFlow<Bitmap?> = _frozenBitmap.asStateFlow()

    private val _releasePoint = MutableStateFlow<Offset?>(null)
    val releasePoint: StateFlow<Offset?> = _releasePoint.asStateFlow()

    private val _currentScreenshot = MutableStateFlow<String?>(null)
    val currentScreenshot: StateFlow<String?> = _currentScreenshot.asStateFlow()

    private val _currentPageLabel = MutableStateFlow("记录")
    val currentPageLabel: StateFlow<String> = _currentPageLabel.asStateFlow()

    fun setFabEnabled(enabled: Boolean) {
        _fabEnabled.value = enabled
        prefs.edit().putBoolean(KEY_FAB_ENABLED, enabled).apply()
    }

    suspend fun openFromFab(activity: Activity, currentPage: String) {
        _currentPageLabel.value = currentPage
        _isCapturing.value = true
        kotlinx.coroutines.delay(60)
        val screenshot = ActivityCaptureProvider.captureActivity(activity)
        _isCapturing.value = false
        _currentScreenshot.value = screenshot
        _isOverlayOpen.value = true
    }

    suspend fun retakeScreenshot(activity: Activity): String? {
        _isCapturing.value = true
        kotlinx.coroutines.delay(60)
        val screenshot = ActivityCaptureProvider.captureActivity(activity)
        _isCapturing.value = false
        if (screenshot != null) {
            _currentScreenshot.value = screenshot
        }
        return screenshot
    }

    fun openFromSettings(currentPage: String = "设置") {
        _currentPageLabel.value = currentPage
        // 从设置打开不截屏
        _currentScreenshot.value = null
        _isOverlayOpen.value = true
    }

    suspend fun openSelectionCaptureFromFab(activity: Activity, releasePoint: Offset, currentPage: String) {
        _currentPageLabel.value = currentPage
        _isCapturing.value = true
        kotlinx.coroutines.delay(60)
        val bitmap = ActivityCaptureProvider.captureActivityRaw(activity)
        _isCapturing.value = false
        if (bitmap != null) {
            _frozenBitmap.value?.recycle()
            _frozenBitmap.value = bitmap
            _releasePoint.value = releasePoint
            _isCaptureSelectionOpen.value = true
        }
    }

    suspend fun deliverSelection(normalizedCrop: RectF?) {
        val bitmap = _frozenBitmap.value
        _isCaptureSelectionOpen.value = false
        if (bitmap != null) {
            val dataUrl = ActivityCaptureProvider.cropAndEncodeBitmap(bitmap, normalizedCrop)
            bitmap.recycle()
            _frozenBitmap.value = null
            _releasePoint.value = null
            _currentScreenshot.value = dataUrl
            _isOverlayOpen.value = true
        }
    }

    fun cancelSelectionCapture() {
        _isCaptureSelectionOpen.value = false
        _frozenBitmap.value?.recycle()
        _frozenBitmap.value = null
        _releasePoint.value = null
    }

    fun closeFeedback() {
        _isOverlayOpen.value = false
        _currentScreenshot.value = null
        _isCaptureSelectionOpen.value = false
        _frozenBitmap.value?.recycle()
        _frozenBitmap.value = null
        _releasePoint.value = null
    }

    companion object {
        private const val KEY_FAB_ENABLED = "feedback_fab_enabled"
    }
}
