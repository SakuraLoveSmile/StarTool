package app.startool.android.feedback.bridge

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import app.startool.android.feedback.diagnostics.DiagnosticCollector
import app.startool.android.feedback.model.SessionData
import app.startool.android.feedback.storage.FeedbackSessionStorage
import java.io.File
import java.io.FileOutputStream

class FeedbackNativeBridge(
    private val activity: Activity,
    private val sessionStorage: FeedbackSessionStorage,
    private val onPanelStateChangedListener: ((Boolean) -> Unit)? = null,
    private val onPageReadyListener: (() -> Unit)? = null,
    private val onRetakeRequested: (suspend () -> String?)? = null,
) {

    @JavascriptInterface
    fun getSession(apiBase: String, appId: String): String? {
        val session = sessionStorage.loadSession(apiBase, appId) ?: return null
        return session.toJson()
    }

    @JavascriptInterface
    fun saveSession(apiBase: String, appId: String, accessToken: String, expiresAt: Long) {
        if (accessToken.isNotEmpty()) {
            sessionStorage.saveSession(apiBase, appId, SessionData(accessToken, expiresAt))
        }
    }

    @JavascriptInterface
    fun clearSession(apiBase: String, appId: String) {
        sessionStorage.clearSession(apiBase, appId)
    }

    @JavascriptInterface
    fun getDiagnostics(): String {
        val diag = DiagnosticCollector.collect(activity)
        return diag.toJsonString()
    }

    @JavascriptInterface
    fun requestRetakeScreenshot(): String? {
        val retake = onRetakeRequested ?: return null
        return kotlinx.coroutines.runBlocking {
            retake.invoke()
        }
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        activity.runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (t: Throwable) {
                // 忽略未安装浏览器等异常
            }
        }
    }

    @JavascriptInterface
    fun onPanelStateChanged(isOpen: Boolean) {
        activity.runOnUiThread {
            onPanelStateChangedListener?.invoke(isOpen)
        }
    }

    @JavascriptInterface
    fun onPageReady() {
        activity.runOnUiThread {
            onPageReadyListener?.invoke()
        }
    }

    @JavascriptInterface
    fun saveAttachment(filename: String, mimeType: String, dataUrlOrBase64: String) {
        val appContext = activity.applicationContext
        Thread {
            try {
                val base64Data = if (dataUrlOrBase64.contains(",")) {
                    dataUrlOrBase64.substringAfter(",")
                } else {
                    dataUrlOrBase64
                }
                val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
                saveBytesToDownload(appContext, filename, mimeType, bytes)
            } catch (t: Throwable) {
                // 保存失败静默
            }
        }.start()
    }

    private fun saveBytesToDownload(context: android.content.Context, filename: String, mimeType: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(bytes) }
        }
    }
}
