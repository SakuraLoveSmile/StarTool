package app.startool.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.startool.android.ui.backup.SettingsScreen
import app.startool.android.ui.history.HistoryScreen
import app.startool.android.ui.record.RecordScreen
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import kotlinx.coroutines.launch

enum class MainTab { Record, History }

/**
 * 应用根界面：顶部标题 + 设置入口，两个固定主页面的底部导航，
 * 以及设置页。集中维护导航状态，不引入导航框架。
 * 页面共享 AppContainer.selectedDateStore 中的所选日期。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarToolRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Record.name) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
            // 600dp 以上内容居中，最大宽度 560dp
            Box(modifier = Modifier.widthIn(max = StarToolDimens.ContentMaxWidth)) {
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
