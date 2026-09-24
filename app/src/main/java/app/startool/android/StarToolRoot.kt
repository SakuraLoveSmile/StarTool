package app.startool.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.startool.android.feedback.ui.CaptureSelectionOverlay
import app.startool.android.feedback.ui.FeedbackFab
import app.startool.android.feedback.ui.FeedbackOverlay
import app.startool.android.ui.backup.SettingsScreen
import app.startool.android.ui.history.HistoryScreen
import app.startool.android.ui.record.RecordScreen
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import app.startool.android.update.model.UpdateCheckResult
import app.startool.android.update.model.UpdateManifest
import kotlinx.coroutines.launch

enum class MainTab { Record, History }

/**
 * 应用根界面：顶部标题 + 设置入口，两个固定主页面的底部导航，
 * 设置页，快捷反馈悬浮球与更新检查机制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarToolRoot(container: AppContainer) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.Record.name) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 自动更新检查状态
    val discoveredUpdate by container.updateChecker.discoveredUpdate.collectAsState()
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    var showUpdateDetailsDialog by rememberSaveable { mutableStateOf(false) }

    // 反馈状态
    val fabEnabled by container.feedbackManager.fabEnabled.collectAsState()
    val isOverlayOpen by container.feedbackManager.isOverlayOpen.collectAsState()
    val isCapturing by container.feedbackManager.isCapturing.collectAsState()
    val isCaptureSelectionOpen by container.feedbackManager.isCaptureSelectionOpen.collectAsState()
    val frozenBitmap by container.feedbackManager.frozenBitmap.collectAsState()
    val releasePoint by container.feedbackManager.releasePoint.collectAsState()
    val currentScreenshot by container.feedbackManager.currentScreenshot.collectAsState()
    val currentPageLabel by container.feedbackManager.currentPageLabel.collectAsState()

    // 启动进入前台后异步检查更新（节流 24 小时，不阻塞前台）
    LaunchedEffect(Unit) {
        container.updateChecker.checkUpdate(isManual = false)
    }

    // 子页面可通过 container.mainTabRequests 请求切换主页面（如空态"去记录"）。
    LaunchedEffect(container) {
        container.mainTabRequests.collect { requested ->
            if (requested != null) {
                tab = requested.name
                container.mainTabRequests.value = null
            }
        }
    }

    val showSnackbar: suspend (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }.join()
    }

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = StarToolColors.Background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (settingsOpen) "设置" else "StarTool (Gemini)",
                            style = StarToolType.PageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (!settingsOpen) {
                            IconButton(
                                onClick = { settingsOpen = true },
                                modifier = Modifier.testTag("settings_entry"),
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "设置与数据管理",
                                    tint = StarToolColors.TextPrimary,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = StarToolColors.Background,
                    ),
                )
            },
            bottomBar = {
                if (!settingsOpen) {
                    NavigationBar(containerColor = StarToolColors.Surface) {
                        NavigationBarItem(
                            selected = tab == MainTab.Record.name,
                            onClick = { tab = MainTab.Record.name },
                            icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            label = { Text("记录", style = StarToolType.Caption) },
                            modifier = Modifier.testTag("nav_record"),
                        )
                        NavigationBarItem(
                            selected = tab == MainTab.History.name,
                            onClick = { tab = MainTab.History.name },
                            icon = {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            },
                            label = { Text("回看", style = StarToolType.Caption) },
                            modifier = Modifier.testTag("nav_history"),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = StarToolDimens.ContentMaxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 自动发现更新的轻量横幅提示（不遮挡主功能）
                    val updateManifest = discoveredUpdate
                    if (updateManifest != null && !bannerDismissed && !settingsOpen && !isOverlayOpen) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("update_banner"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                            border = BorderStroke(1.dp, StarToolColors.Primary.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "发现新版本 ${updateManifest.versionName}",
                                        style = StarToolType.Body,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "点击查看更新说明并前往 GitHub 下载",
                                        style = StarToolType.Caption,
                                        color = StarToolColors.TextSecondary,
                                    )
                                }
                                Row {
                                    TextButton(
                                        onClick = { showUpdateDetailsDialog = true },
                                        modifier = Modifier.testTag("update_banner_action"),
                                    ) {
                                        Text("查看", color = StarToolColors.Primary)
                                    }
                                    TextButton(
                                        onClick = { bannerDismissed = true },
                                    ) {
                                        Text("忽略", color = StarToolColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            settingsOpen -> SettingsScreen(
                                container = container,
                                onBack = { settingsOpen = false },
                                showSnackbar = showSnackbar,
                            )
                            tab == MainTab.Record.name -> RecordScreen(
                                container = container,
                                showSnackbar = showSnackbar,
                            )
                            else -> HistoryScreen(
                                container = container,
                                showSnackbar = showSnackbar,
                            )
                        }
                    }
                }
            }
        }

        // 仅在记录与回看页面展示 48dp 贴边悬浮球（设置页隐藏，截屏时隐藏，选区流程时隐藏）
        if (!settingsOpen && !isOverlayOpen && !isCapturing && !isCaptureSelectionOpen) {
            FeedbackFab(
                visible = fabEnabled,
                onClick = {
                    val activity = container.currentActivity ?: (context as? Activity)
                    if (activity != null) {
                        scope.launch {
                            val curTab = if (tab == MainTab.Record.name) "记录" else "回看"
                            container.feedbackManager.openFromFab(activity, curTab)
                        }
                    }
                },
                onDragRelease = { normPoint ->
                    val activity = container.currentActivity ?: (context as? Activity)
                    if (activity != null) {
                        scope.launch {
                            val curTab = if (tab == MainTab.Record.name) "记录" else "回看"
                            container.feedbackManager.openSelectionCaptureFromFab(activity, normPoint, curTab)
                        }
                    }
                },
            )
        }

        // 局部选区覆盖层（拖动灵感球后进入）
        CaptureSelectionOverlay(
            visible = isCaptureSelectionOpen,
            bitmap = frozenBitmap,
            releasePoint = releasePoint,
            onUseRegion = { region ->
                scope.launch {
                    container.feedbackManager.deliverSelection(region)
                }
            },
            onCaptureWhole = {
                scope.launch {
                    container.feedbackManager.deliverSelection(null)
                }
            },
            onCancel = {
                container.feedbackManager.cancelSelectionCapture()
            },
        )

        // 原生 WebView 反馈弹层
        FeedbackOverlay(
            visible = isOverlayOpen,
            pageLabel = currentPageLabel,
            sessionStorage = container.feedbackManager.sessionStorage,
            screenshotDataUrl = currentScreenshot,
            isCapturing = isCapturing,
            onRetakeRequested = {
                val act = container.currentActivity ?: (context as? Activity)
                if (act != null) {
                    container.feedbackManager.retakeScreenshot(act)
                } else null
            },
            onDismiss = { container.feedbackManager.closeFeedback() },
        )

        // 横幅点击后的更新详情弹窗
        val manifestForDialog = discoveredUpdate
        if (showUpdateDetailsDialog && manifestForDialog != null) {
            AlertDialog(
                onDismissRequest = { showUpdateDetailsDialog = false },
                modifier = Modifier.testTag("update_dialog"),
                title = {
                    Text(
                        text = "发现新版本 ${manifestForDialog.versionName}",
                        style = StarToolType.HourTitle,
                        modifier = Modifier.testTag("update_dialog_title"),
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "更新说明：\n" + manifestForDialog.notes,
                            style = StarToolType.Body,
                            modifier = Modifier.testTag("update_dialog_notes"),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUpdateDetailsDialog = false
                            try {
                                val targetUrl = container.updateChecker.getEffectiveReleaseUrl(manifestForDialog)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (t: Throwable) {
                                scope.launch { showSnackbar("无法调起浏览器下载") }
                            }
                        },
                        modifier = Modifier.testTag("update_dialog_download"),
                        colors = ButtonDefaults.buttonColors(containerColor = StarToolColors.Primary),
                    ) {
                        Text("前往下载", style = StarToolType.Body)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceXs)) {
                        TextButton(
                            onClick = {
                                showUpdateDetailsDialog = false
                                bannerDismissed = true
                                container.updateChecker.clearDiscoveredUpdate()
                                container.updateChecker.markRemindLater()
                            },
                            modifier = Modifier.testTag("update_dialog_remind_later"),
                        ) {
                            Text("稍后提醒", style = StarToolType.Caption)
                        }
                        TextButton(
                            onClick = {
                                showUpdateDetailsDialog = false
                                bannerDismissed = true
                                container.updateChecker.clearDiscoveredUpdate()
                                container.updateChecker.ignoreVersion(manifestForDialog.versionCode)
                            },
                            modifier = Modifier.testTag("update_dialog_ignore"),
                        ) {
                            Text("忽略此版本", style = StarToolType.Caption)
                        }
                        TextButton(
                            onClick = { showUpdateDetailsDialog = false },
                            modifier = Modifier.testTag("update_dialog_close"),
                        ) {
                            Text("关闭", style = StarToolType.Caption)
                        }
                    }
                },
            )
        }
    }
}
