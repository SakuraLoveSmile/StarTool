package app.startool.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import app.startool.android.domain.BackupFileGateway
import app.startool.android.ui.theme.StarToolTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as StarToolApplication).container

    /** 同一时刻只允许一个 SAF 选择器挂起。 */
    private val pickerMutex = Mutex()
    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            pendingSave?.complete(uri)
            pendingSave = null
        }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingOpen?.complete(uri)
            pendingOpen = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.currentActivity = this
        container.fileGateway = SafBackupFileGateway()

        setContent {
            StarToolTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StarToolRoot(container)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (container.currentActivity === this) {
            container.currentActivity = null
        }
        if (container.fileGateway !== null) {
            pendingSave?.complete(null)
            pendingOpen?.complete(null)
            pendingSave = null
            pendingOpen = null
            container.fileGateway = null
        }
    }

    /** SAF 桥接实现（计划 §5 / §6.2 BackupFileGateway）。 */
    private inner class SafBackupFileGateway : BackupFileGateway {

        override suspend fun pickSaveLocation(suggestedFileName: String): Uri? =
            pickerMutex.withLock {
                val deferred = CompletableDeferred<Uri?>()
                pendingSave = deferred
                try {
                    createDocumentLauncher.launch(suggestedFileName)
                } catch (t: Throwable) {
                    pendingSave = null
                    return@withLock null
                }
                deferred.await()
            }

        override suspend fun pickOpenFile(): Uri? = pickerMutex.withLock {
            val deferred = CompletableDeferred<Uri?>()
            pendingOpen = deferred
            try {
                // JSON 为主，同时允许通用类型以兼容各文件管理器对 MIME 的标注
                openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            } catch (t: Throwable) {
                pendingOpen = null
                return@withLock null
            }
            deferred.await()
        }

        override suspend fun readAll(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }

        override suspend fun writeAll(uri: Uri, bytes: ByteArray): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } != null
                }.getOrDefault(false)
            }

        override suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
            }.getOrDefault(false)
        }
    }
}
