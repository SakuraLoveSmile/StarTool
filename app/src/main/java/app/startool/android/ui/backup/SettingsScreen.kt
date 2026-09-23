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
import androidx.compose.material3.TextButton
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
                    text = "版本：0.1.0 (versionCode 1)\n" +
                        "平台：Android 原生 Jetpack Compose\n" +
                        "存储：仅保存在本机数据库，无账号、无网络连接、无广告。\n" +
                        "说明：评分仅反映个人主观状态自评，不作为医学或生理健康诊断结论。如需卸载应用，请先导出备份文件。",
                    style = StarToolType.Caption,
                    color = StarToolColors.TextSecondary,
                )
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
