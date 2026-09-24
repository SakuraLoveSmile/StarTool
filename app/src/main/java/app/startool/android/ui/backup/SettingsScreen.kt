package app.startool.android.ui.backup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import android.content.Intent
import android.net.Uri
import app.startool.android.update.model.UpdateCheckResult
import app.startool.android.update.model.UpdateManifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.startool.android.AppContainer
import app.startool.android.backup.BackupCoordinator
import app.startool.android.backup.ExportResult
import app.startool.android.backup.PreviewResult
import app.startool.android.domain.ConflictChoice
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.HourlyKey
import app.startool.android.domain.ImportApplyResult
import app.startool.android.domain.ImportConflict
import app.startool.android.domain.ImportPlan
import app.startool.android.domain.ImportReport
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import app.startool.android.ui.update.UpdateProgressSection
import app.startool.android.update.model.UpdateProxySource
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val TS_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val coordinator = remember(container) {
        BackupCoordinator(container.repository, container.maintenance, container.clock)
    }
    val maintenanceBusy by container.maintenance.busy.collectAsState()

    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }

    var currentPlan by remember { mutableStateOf<ImportPlan?>(null) }
    var importResultReport by remember { mutableStateOf<ImportReport?>(null) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var activeUpdateManifest by remember { mutableStateOf<UpdateManifest?>(null) }
    var autoCheckEnabledState by remember { mutableStateOf(container.updatePreferenceStore.autoCheckEnabled) }
    var proxySourceState by remember { mutableStateOf(container.updatePreferenceStore.proxySource) }
    var customProxyPrefixState by remember { mutableStateOf(container.updatePreferenceStore.customProxyPrefix) }
    var showProxyMenu by remember { mutableStateOf(false) }
    val fabEnabled by container.feedbackManager.fabEnabled.collectAsState()
    // P2：应用内下载状态。由 container 单例持有，离开设置页不会丢失。
    val downloadState by container.updateDownloadManager.state.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StarToolDimens.PageHorizontalPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        // 返回与标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = StarToolDimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = StarToolDimens.MinTouchTarget),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = StarToolColors.TextPrimary,
                )
            }
            Text(
                text = "数据管理与设置",
                style = StarToolType.HourTitle,
                color = StarToolColors.TextPrimary,
            )
        }

        Spacer(Modifier.height(StarToolDimens.SpaceMd))

        // 数据备份卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = StarToolColors.Surface),
            border = BorderStroke(StarToolDimens.CardBorderWidth, StarToolColors.CardBorder),
        ) {
            Column(modifier = Modifier.padding(StarToolDimens.CardPadding)) {
                Text(
                    text = "导出数据备份",
                    style = StarToolType.HourTitle,
                    color = StarToolColors.TextPrimary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceXs))
                Text(
                    text = "将本机所有小时评分和备注导出为一个未加密的 JSON 备份文件。导出时会保存完整数据快照。",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceMd))

                Button(
                    onClick = {
                        val gateway = container.fileGateway
                        if (gateway == null) {
                            scope.launch { showSnackbar("系统文件服务暂不可用") }
                            return@Button
                        }
                        isExporting = true
                        scope.launch {
                            val res = coordinator.exportBackup(gateway)
                            isExporting = false
                            when (res) {
                                is ExportResult.Success -> {
                                    showSnackbar("备份成功导出（共 ${res.count} 条记录）")
                                }
                                is ExportResult.Cancelled -> {
                                    // 用户取消，不报错
                                }
                                is ExportResult.Failure -> {
                                    showSnackbar("导出失败：${res.message}")
                                }
                            }
                        }
                    },
                    enabled = !maintenanceBusy && !isExporting && !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = StarToolDimens.ButtonMinHeight)
                        .testTag("settings_export"),
                    colors = ButtonDefaults.buttonColors(containerColor = StarToolColors.Primary),
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("export_progress"),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(StarToolDimens.SpaceSm))
                        Text("正在导出备份...", style = StarToolType.Body)
                    } else {
                        Text("导出备份文件", style = StarToolType.Body)
                    }
                }
            }
        }

        Spacer(Modifier.height(StarToolDimens.SpaceLg))

        // 数据恢复卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = StarToolColors.Surface),
            border = BorderStroke(StarToolDimens.CardBorderWidth, StarToolColors.CardBorder),
        ) {
            Column(modifier = Modifier.padding(StarToolDimens.CardPadding)) {
                Text(
                    text = "恢复数据备份",
                    style = StarToolType.HourTitle,
                    color = StarToolColors.TextPrimary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceXs))
                Text(
                    text = "从 StarTool 导出的 JSON 备份中恢复记录。系统将逐项比对并提供冲突预览，确认前不会修改本机数据。",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceMd))

                OutlinedButton(
                    onClick = {
                        val gateway = container.fileGateway
                        if (gateway == null) {
                            scope.launch { showSnackbar("系统文件服务暂不可用") }
                            return@OutlinedButton
                        }
                        isImporting = true
                        scope.launch {
                            val res = coordinator.pickAndPreview(gateway)
                            isImporting = false
                            when (res) {
                                is PreviewResult.Success -> {
                                    currentPlan = res.plan
                                }
                                is PreviewResult.Cancelled -> {
                                    // 用户取消
                                }
                                is PreviewResult.Failure -> {
                                    showSnackbar("备份文件校验失败：${res.message}")
                                }
                            }
                        }
                    },
                    enabled = !maintenanceBusy && !isExporting && !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = StarToolDimens.ButtonMinHeight)
                        .testTag("settings_import"),
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("import_progress"),
                            color = StarToolColors.Primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(StarToolDimens.SpaceSm))
                        Text("正在校验备份文件...", style = StarToolType.Body)
                    } else {
                        Text("选择备份文件恢复", style = StarToolType.Body, color = StarToolColors.Primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(StarToolDimens.SpaceLg))

        // 应用说明卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_about"),
            shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = StarToolColors.Surface),
            border = BorderStroke(StarToolDimens.CardBorderWidth, StarToolColors.CardBorder),
        ) {
            Column(modifier = Modifier.padding(StarToolDimens.CardPadding)) {
                Text(
                    text = "关于 StarTool",
                    style = StarToolType.HourTitle,
                    color = StarToolColors.TextPrimary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                Text(
                    text = "版本：${container.currentVersionName} (versionCode ${container.currentVersionCode})\n" +
                        "平台：Android 原生 Jetpack Compose\n" +
                        "存储：仅保存在本机数据库，无账号、无网络连接、无广告。\n" +
                        "说明：评分仅反映个人主观状态自评，不作为医学或生理健康诊断结论。如需卸载应用，请先导出备份文件。",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(StarToolDimens.SpaceLg))

        // 检查更新卡片（计划 §T1）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_update_card"),
            shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = StarToolColors.Surface),
            border = BorderStroke(StarToolDimens.CardBorderWidth, StarToolColors.CardBorder),
        ) {
            Column(modifier = Modifier.padding(StarToolDimens.CardPadding)) {
                Text(
                    text = "版本与更新",
                    style = StarToolType.HourTitle,
                    color = StarToolColors.TextPrimary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceXs))
                Text(
                    text = "当前版本：${container.currentVersionName} (versionCode ${container.currentVersionCode})\n发布来源：SakuraLoveSmile/StarTool 稳定版 Releases",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceMd))

                // 自动检查更新开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启动时自动检查更新", style = StarToolType.Body)
                        Text(
                            text = "开启后每天至多自动检测一次，发现新版时不遮挡使用。",
                            style = StarToolType.Caption,
                            color = StarToolColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(StarToolDimens.SpaceSm))
                    Switch(
                        checked = autoCheckEnabledState,
                        onCheckedChange = { checked ->
                            autoCheckEnabledState = checked
                            container.updatePreferenceStore.autoCheckEnabled = checked
                        },
                        modifier = Modifier.testTag("settings_update_auto_switch"),
                    )
                }
                Spacer(Modifier.height(StarToolDimens.SpaceMd))

                // 下载加速 / 镜像源选择
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("更新下载源 / 镜像加速", style = StarToolType.Body)
                    Text(
                        text = "国内网络可切换至代理镜像源，加速版本检测与安装包下载。",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                    )
                    Spacer(Modifier.height(StarToolDimens.SpaceSm))

                    Box {
                        OutlinedButton(
                            onClick = { showProxyMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_update_proxy_selector"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(proxySourceState.displayName, style = StarToolType.Body)
                        }
                        DropdownMenu(
                            expanded = showProxyMenu,
                            onDismissRequest = { showProxyMenu = false },
                        ) {
                            UpdateProxySource.entries.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.displayName, style = StarToolType.Body) },
                                    onClick = {
                                        proxySourceState = source
                                        container.updatePreferenceStore.proxySource = source
                                        showProxyMenu = false
                                    },
                                )
                            }
                        }
                    }

                    if (proxySourceState == UpdateProxySource.Custom) {
                        Spacer(Modifier.height(StarToolDimens.SpaceSm))
                        OutlinedTextField(
                            value = customProxyPrefixState,
                            onValueChange = { input ->
                                customProxyPrefixState = input
                                container.updatePreferenceStore.customProxyPrefix = input
                            },
                            label = { Text("代理前缀（例如 https://ghproxy.net/）", style = StarToolType.Caption) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_update_custom_proxy_input"),
                        )
                    }
                }

                Spacer(Modifier.height(StarToolDimens.SpaceMd))
                // 状态说明文本
                if (updateStatusText != null) {
                    Text(
                        text = updateStatusText.orEmpty(),
                        style = StarToolType.Caption,
                        color = if (updateCheckResult is UpdateCheckResult.NetworkError || updateCheckResult is UpdateCheckResult.InvalidManifest) StarToolColors.Error else StarToolColors.TextPrimary,
                        modifier = Modifier.testTag("settings_update_status"),
                    )
                    Spacer(Modifier.height(StarToolDimens.SpaceSm))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm),
                ) {
                    // 手动检查按钮
                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            updateStatusText = "正在检查更新..."
                            scope.launch {
                                val res = container.updateChecker.checkUpdate(isManual = true)
                                isCheckingUpdate = false
                                updateCheckResult = res
                                when (res) {
                                    is UpdateCheckResult.UpdateAvailable -> {
                                        updateStatusText = "发现新版本：${res.manifest.versionName}"
                                        activeUpdateManifest = res.manifest
                                    }
                                    is UpdateCheckResult.UpToDate -> {
                                        updateStatusText = "当前已是最新版本"
                                    }
                                    is UpdateCheckResult.NoReleaseFound -> {
                                        updateStatusText = "尚未发布正式版本"
                                    }
                                    is UpdateCheckResult.IncompatibleSystem -> {
                                        updateStatusText = "新版本暂不兼容当前系统（需 Android API ${res.minSdk}+）"
                                    }
                                    is UpdateCheckResult.NetworkError -> {
                                        updateStatusText = "检查更新失败：${res.message}"
                                    }
                                    is UpdateCheckResult.InvalidManifest -> {
                                        updateStatusText = "版本清单验证失败：${res.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = StarToolDimens.ButtonMinHeight)
                            .testTag("settings_update_check_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = StarToolColors.Primary),
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(StarToolDimens.SpaceSm))
                            Text("正在检查...", style = StarToolType.Body)
                        } else {
                            Text("检查新版本", style = StarToolType.Body)
                        }
                    }

                    // 若失败提供重试按钮
                    if (updateCheckResult is UpdateCheckResult.NetworkError || updateCheckResult is UpdateCheckResult.InvalidManifest) {
                        OutlinedButton(
                            onClick = {
                                isCheckingUpdate = true
                                updateStatusText = "正在重试..."
                                scope.launch {
                                    val res = container.updateChecker.checkUpdate(isManual = true)
                                    isCheckingUpdate = false
                                    updateCheckResult = res
                                    when (res) {
                                        is UpdateCheckResult.UpdateAvailable -> {
                                            updateStatusText = "发现新版本：${res.manifest.versionName}"
                                            activeUpdateManifest = res.manifest
                                        }
                                        is UpdateCheckResult.UpToDate -> {
                                            updateStatusText = "当前已是最新版本"
                                        }
                                        is UpdateCheckResult.NoReleaseFound -> {
                                            updateStatusText = "尚未发布正式版本"
                                        }
                                        is UpdateCheckResult.IncompatibleSystem -> {
                                            updateStatusText = "新版本暂不兼容当前系统"
                                        }
                                        is UpdateCheckResult.NetworkError -> {
                                            updateStatusText = "重试失败：${res.message}"
                                        }
                                        is UpdateCheckResult.InvalidManifest -> {
                                            updateStatusText = "清单验证失败：${res.message}"
                                        }
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate,
                            modifier = Modifier
                                .heightIn(min = StarToolDimens.ButtonMinHeight)
                                .testTag("settings_update_retry_button"),
                        ) {
                            Text("重试", style = StarToolType.Body)
                        }
                    }
                }

                Spacer(Modifier.height(StarToolDimens.SpaceSm))

                // P2：应用内下载进度 / 失败重试。状态由 container 单例持有，
                // 离开设置页再回来不会丢失。
                UpdateProgressSection(
                    state = downloadState,
                    onCancel = { container.updateDownloadManager.cancel() },
                    onRetry = { container.updateDownloadManager.retry() },
                    modifier = Modifier.testTag("settings_update_download_section"),
                )
            }
        }

        Spacer(Modifier.height(StarToolDimens.SpaceLg))

        // 帮助与反馈卡片（计划 §T2）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_feedback_card"),
            shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = StarToolColors.Surface),
            border = BorderStroke(StarToolDimens.CardBorderWidth, StarToolColors.CardBorder),
        ) {
            Column(modifier = Modifier.padding(StarToolDimens.CardPadding)) {
                Text(
                    text = "帮助与反馈",
                    style = StarToolType.HourTitle,
                    color = StarToolColors.TextPrimary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceXs))
                Text(
                    text = "遇到任何使用疑惑、崩溃问题或改进想法，可在此提交反馈或查看回复。反馈服务提供安全的会话与日志脱敏保护。",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                )
                Spacer(Modifier.height(StarToolDimens.SpaceMd))

                // 悬浮球开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("显示快捷反馈悬浮球", style = StarToolType.Body)
                        Text(
                            text = "在记录与回看页面显示右侧快捷悬浮球，点击可一键自动截屏并快速反馈。",
                            style = StarToolType.Caption,
                            color = StarToolColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(StarToolDimens.SpaceSm))
                    Switch(
                        checked = fabEnabled,
                        onCheckedChange = { checked ->
                            container.feedbackManager.setFabEnabled(checked)
                        },
                        modifier = Modifier.testTag("settings_feedback_fab_switch"),
                    )
                }

                Spacer(Modifier.height(StarToolDimens.SpaceMd))

                Button(
                    onClick = {
                        // 从设置打开不截屏
                        container.feedbackManager.openFromSettings("设置")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = StarToolDimens.ButtonMinHeight)
                        .testTag("settings_feedback_entry"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071E3)),
                ) {
                    Text("打开问题反馈面板", style = StarToolType.Body)
                }
            }
        }
        Spacer(Modifier.height(StarToolDimens.SpaceXl))
    }

    // 导入预览与冲突解决对话框 / 界面
    val plan = currentPlan
    if (plan != null) {
        ImportPreviewDialog(
            plan = plan,
            isApplying = isApplying,
            onDismiss = { currentPlan = null },
            onConfirm = { choices ->
                isApplying = true
                scope.launch {
                    val result = coordinator.applyImport(plan, choices)
                    isApplying = false
                    when (result) {
                        is ImportApplyResult.Completed -> {
                            currentPlan = null
                            importResultReport = result.report
                            showSnackbar("备份恢复完成：${result.report.summary}")
                        }
                        is ImportApplyResult.PreviewStale -> {
                            showSnackbar("预览期间本机数据已发生变动，请重新选择文件预检")
                            currentPlan = null
                        }
                        is ImportApplyResult.Failure -> {
                            showSnackbar("恢复写入失败：${result.cause.message ?: "未知错误"}")
                        }
                    }
                }
            },
        )
    }

    // 导入完成结果对话框
    val report = importResultReport
    if (report != null) {
        AlertDialog(
            onDismissRequest = { importResultReport = null },
            title = { Text("恢复结果", style = StarToolType.HourTitle) },
            text = {
                Text(
                    text = report.summary,
                    style = StarToolType.Body,
                    modifier = Modifier.testTag("import_result"),
                )
            },
            confirmButton = {
                TextButton(onClick = { importResultReport = null }) {
                    Text("完成", style = StarToolType.Body)
                }
            },
        )
    }

    // 更新详情对话框（计划 §T1 / 契约 §4）
    val updateManifest = activeUpdateManifest
    if (updateManifest != null) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { activeUpdateManifest = null },
            modifier = Modifier.testTag("update_dialog"),
            title = {
                Text(
                    text = "发现新版本 ${updateManifest.versionName}",
                    style = StarToolType.HourTitle,
                    modifier = Modifier.testTag("update_dialog_title"),
                )
            },
            text = {
                Column {
                    Text(
                        text = "更新说明：\n" + updateManifest.notes,
                        style = StarToolType.Body,
                        modifier = Modifier.testTag("update_dialog_notes"),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 优先应用内下载（P2）；清单没有可用官方直链时降级为跳浏览器
                        val started = container.updateDownloadManager.start(updateManifest)
                        activeUpdateManifest = null
                        if (!started) {
                            try {
                                val targetUrl = container.updateChecker.getEffectiveReleaseUrl(updateManifest)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            } catch (t: Throwable) {
                                scope.launch { showSnackbar("无法调起浏览器下载") }
                            }
                        }
                    },
                    modifier = Modifier.testTag("update_dialog_download"),
                    colors = ButtonDefaults.buttonColors(containerColor = StarToolColors.Primary),
                ) {
                    Text("下载更新", style = StarToolType.Body)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceXs)) {
                    TextButton(
                        onClick = {
                            activeUpdateManifest = null
                            container.updateChecker.markRemindLater()
                        },
                        modifier = Modifier.testTag("update_dialog_remind_later"),
                    ) {
                        Text("稍后提醒", style = StarToolType.Caption)
                    }
                    TextButton(
                        onClick = {
                            activeUpdateManifest = null
                            container.updateChecker.ignoreVersion(updateManifest.versionCode)
                        },
                        modifier = Modifier.testTag("update_dialog_ignore"),
                    ) {
                        Text("忽略此版本", style = StarToolType.Caption)
                    }
                    TextButton(
                        onClick = { activeUpdateManifest = null },
                        modifier = Modifier.testTag("update_dialog_close"),
                    ) {
                        Text("关闭", style = StarToolType.Caption)
                    }
                }
            },
        )
    }
}

@Composable
private fun ImportPreviewDialog(
    plan: ImportPlan,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Map<HourlyKey, ConflictChoice>) -> Unit,
) {
    // 冲突项的选择状态映射，默认全为 KeepLocal
    val conflictChoices = remember(plan) {
        mutableStateMapOf<HourlyKey, ConflictChoice>().apply {
            for (c in plan.conflicts) {
                put(c.key, ConflictChoice.KeepLocal)
            }
        }
    }

    val replacedCount = conflictChoices.values.count { it == ConflictChoice.UseBackup }
    val keptLocalCount = plan.conflicts.size - replacedCount
    val summaryText = "新增 ${plan.additions.size} 条，使用备份替换 $replacedCount 条，" +
        "保留本机冲突 $keptLocalCount 条，跳过相同记录 ${plan.identical.size} 条。"

    AlertDialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        modifier = Modifier.testTag("import_preview"),
        title = {
            Text(text = "恢复备份预检", style = StarToolType.HourTitle)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                Text(
                    text = summaryText,
                    style = StarToolType.Body,
                    fontWeight = FontWeight.Bold,
                    color = StarToolColors.TextPrimary,
                    modifier = Modifier.testTag("import_summary_text"),
                )

                Spacer(Modifier.height(StarToolDimens.SpaceMd))

                if (plan.conflicts.isNotEmpty()) {
                    Text(
                        text = "发现 ${plan.conflicts.size} 项冲突，请逐项选择保留本机或使用备份：",
                        style = StarToolType.Caption,
                        color = StarToolColors.Primary,
                    )
                    Spacer(Modifier.height(StarToolDimens.SpaceSm))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .testTag("conflict_list"),
                        verticalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceSm),
                    ) {
                        itemsIndexed(plan.conflicts) { index, conflict ->
                            ConflictItemCard(
                                index = index,
                                conflict = conflict,
                                choice = conflictChoices[conflict.key] ?: ConflictChoice.KeepLocal,
                                onChoiceChange = { conflictChoices[conflict.key] = it },
                            )
                        }
                    }
                } else {
                    Text(
                        text = "所有备份记录均无内容冲突，可以直接导入。",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(conflictChoices.toMap()) },
                enabled = !isApplying,
                modifier = Modifier.testTag("confirm_import"),
                colors = ButtonDefaults.buttonColors(containerColor = StarToolColors.Primary),
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(StarToolDimens.SpaceSm))
                    Text("正在写入...", style = StarToolType.Body)
                } else {
                    Text("确认恢复", style = StarToolType.Body)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isApplying,
            ) {
                Text("取消", style = StarToolType.Body)
            }
        },
    )
}

@Composable
private fun ConflictItemCard(
    index: Int,
    conflict: ImportConflict,
    choice: ConflictChoice,
    onChoiceChange: (ConflictChoice) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conflict_item_$index"),
        shape = RoundedCornerShape(StarToolDimens.SpaceSm),
        colors = CardDefaults.cardColors(containerColor = StarToolColors.Background),
        border = BorderStroke(1.dp, StarToolColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(StarToolDimens.SpaceSm)) {
            Text(
                text = "${conflict.key.date} ${conflict.key.hourLabel}",
                style = StarToolType.Body,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(StarToolDimens.SpaceXs))

            // 本机内容
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onChoiceChange(ConflictChoice.KeepLocal) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = choice == ConflictChoice.KeepLocal,
                    onClick = { onChoiceChange(ConflictChoice.KeepLocal) },
                    modifier = Modifier.testTag("conflict_keep_local_$index"),
                    colors = RadioButtonDefaults.colors(selectedColor = StarToolColors.Primary),
                )
                Column {
                    Text(
                        text = "保留本机：精神 ${conflict.local.mentalScore}分 / 身体 ${conflict.local.physicalScore}分",
                        style = StarToolType.Caption,
                        fontWeight = if (choice == ConflictChoice.KeepLocal) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (conflict.local.note.isNotBlank()) {
                        Text(
                            text = "备注: ${conflict.local.note}",
                            style = StarToolType.Caption,
                            color = StarToolColors.TextSecondary,
                        )
                    }
                    Text(
                        text = "更新时间: ${TS_FMT.format(conflict.local.updatedAt)}",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                    )
                }
            }

            // 备份内容
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onChoiceChange(ConflictChoice.UseBackup) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = choice == ConflictChoice.UseBackup,
                    onClick = { onChoiceChange(ConflictChoice.UseBackup) },
                    modifier = Modifier.testTag("conflict_use_backup_$index"),
                    colors = RadioButtonDefaults.colors(selectedColor = StarToolColors.Primary),
                )
                Column {
                    Text(
                        text = "使用备份：精神 ${conflict.backup.mentalScore}分 / 身体 ${conflict.backup.physicalScore}分",
                        style = StarToolType.Caption,
                        fontWeight = if (choice == ConflictChoice.UseBackup) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (conflict.backup.note.isNotBlank()) {
                        Text(
                            text = "备注: ${conflict.backup.note}",
                            style = StarToolType.Caption,
                            color = StarToolColors.TextSecondary,
                        )
                    }
                    Text(
                        text = "更新时间: ${TS_FMT.format(conflict.backup.updatedAt)}",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                    )
                }
            }
        }
    }
}
