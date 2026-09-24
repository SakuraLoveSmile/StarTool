package app.startool.android.feedback.ui
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import app.startool.android.feedback.bridge.FeedbackNativeBridge
import app.startool.android.feedback.storage.FeedbackSessionStorage
import kotlinx.coroutines.launch

/**
 * 原生 WebView 适配层容器（计划 §T2）。
 * 通过 WebViewAssetLoader 加载本地 HTTPS 资源，支持图片选择、外链打开、返回键逐层退出。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FeedbackOverlay(
    visible: Boolean,
    pageLabel: String,
    sessionStorage: FeedbackSessionStorage,
    screenshotDataUrl: String?,
    isCapturing: Boolean = false,
    onRetakeRequested: (suspend () -> String?)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // 系统图片选择契约
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    // 返回键分发
    BackHandler(enabled = visible) {
        val wv = webViewRef
        if (wv != null) {
            wv.evaluateJavascript("window.startoolHandleBackPressed();") { result ->
                if (result != "true") {
                    onDismiss()
                }
            }
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (isCapturing) 0f else 1f)
            // 背景透明，由内部 Web feedback-widget 提供浅色/深色全屏面板
            .testTag("feedback_overlay"),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val targetActivity = ctx.findActivity() ?: activity ?: context.findActivity()
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(0) // 透明背景

                    val assetLoader = WebViewAssetLoader.Builder()
                        .setDomain("appassets.androidplatform.net")
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                        .build()

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }

                    val nativeBridge = FeedbackNativeBridge(
                        activity = targetActivity ?: (ctx as? Activity ?: activity!!),
                        sessionStorage = sessionStorage,
                        onRetakeRequested = onRetakeRequested,
                        onPanelStateChangedListener = { isOpen ->
                            if (!isOpen) {
                                onDismiss()
                            }
                        },
                        onPageReadyListener = {
                            // 页面就绪后，根据是否有截图打开对应面板
                            val script = if (screenshotDataUrl != null) {
                                "window.startoolOpenWithCapture('$screenshotDataUrl');"
                            } else {
                                "window.startoolOpenWithoutCapture();"
                            }
                            post { evaluateJavascript(script, null) }
                        }
                    )
                    addJavascriptInterface(nativeBridge, "StarToolFeedbackNative")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val res = assetLoader.shouldInterceptRequest(request.url)
                            Log.d("FeedbackWebView", "Intercept: ${request.url} -> ${if (res != null) "hit" else "miss"}")
                            return res
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            Log.d("FeedbackWebView", "PageStarted: $url")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d("FeedbackWebView", "PageFinished: $url")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            Log.e("FeedbackWebView", "ReceivedError: ${request?.url} code=${error?.errorCode} desc=${error?.description}")
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            if (url.startsWith("https://appassets.androidplatform.net/")) {
                                return false
                            }
                            nativeBridge.openExternal(url)
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d(
                                "FeedbackWebView",
                                "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}"
                            )
                            return true
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            fileChooserCallback?.onReceiveValue(null)
                            fileChooserCallback = filePathCallback
                            fileChooserLauncher.launch("image/*")
                            return true
                        }
                    }

                    val encodedPage = Uri.encode(pageLabel)
                    val appId = "com.sakurasep.startool"
                    val apiBase = "https://feedback.xn--fhqths51enha.cn"
                    val appVersion = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
                    } catch (t: Throwable) {
                        "0.1.0"
                    }
                    val initialUrl = "https://appassets.androidplatform.net/assets/feedback/index.html?page=$encodedPage&appId=$appId&appVersion=$appVersion&apiBase=${Uri.encode(apiBase)}"
                    webViewRef = this
                    loadUrl(initialUrl)
                }
            },
            update = { wv ->
                wv.evaluateJavascript("if (typeof window.startoolSetPage === 'function') { window.startoolSetPage('$pageLabel'); }", null)
            }
        )

    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
