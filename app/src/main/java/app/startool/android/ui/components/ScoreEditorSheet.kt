package app.startool.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.startool.android.domain.EntryDraft
import app.startool.android.domain.HourlyEntry
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHEET_DATE_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreEditorSheet(
    draft: EntryDraft,
    existingEntry: HourlyEntry?,
    saving: Boolean,
    deleting: Boolean,
    maintenanceBusy: Boolean,
    onDraftChange: (EntryDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val hasChanges = remember(draft, existingEntry) {
        if (existingEntry == null) {
            draft.mentalScore != null || draft.physicalScore != null || draft.note.isNotEmpty()
        } else {
            draft.mentalScore != existingEntry.mentalScore ||
                draft.physicalScore != existingEntry.physicalScore ||
                draft.note != existingEntry.note
        }
    }

    val handleDismissRequest = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    BackHandler(enabled = true) {
        handleDismissRequest()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = handleDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = StarToolDimens.SheetTopCornerRadius, topEnd = StarToolDimens.SheetTopCornerRadius),
        containerColor = StarToolColors.Surface,
        dragHandle = null,
        modifier = Modifier.testTag("edit_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = StarToolDimens.PageHorizontalPadding, vertical = StarToolDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "${draft.key.date.format(SHEET_DATE_FMT)} ${draft.key.hourLabel}",
                        style = StarToolType.HourTitle,
                        color = StarToolColors.TextPrimary,
                        modifier = Modifier.testTag("sheet_date_hour"),
                    )
                    Text(
                        text = if (existingEntry == null) "新建时段记录" else "修改时段记录",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                    )
                }

                IconButton(
                    onClick = handleDismissRequest,
                    modifier = Modifier.heightIn(min = StarToolDimens.MinTouchTarget),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = StarToolColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(StarToolDimens.SpaceMd))

            // 精神状态打分项
            ScoreInputSection(
                title = "精神状态",
                lowDesc = "疲惫、难以专注、难以调节",
                highDesc = "清醒、专注、调节自如",
                score = draft.mentalScore,
                accentColor = StarToolColors.Primary,
                sliderTestTag = "mental_slider",
                plusTestTag = "mental_plus",
                minusTestTag = "mental_minus",
                zeroTestTag = "mental_set_zero",
                scoreTextTag = "mental_score_text",
                enabled = !saving && !deleting && !maintenanceBusy,
                onScoreChange = { newScore ->
                    onDraftChange(draft.copy(mentalScore = newScore))
                },
            )

            Spacer(Modifier.height(StarToolDimens.SpaceLg))

            // 身体状态打分项
            ScoreInputSection(
                title = "身体状态",
                lowDesc = "疲惫、饥饿或身体不适",
                highDesc = "精力充足、身体舒适",
                score = draft.physicalScore,
                accentColor = StarToolColors.Physical,
                sliderTestTag = "physical_slider",
                plusTestTag = "physical_plus",
                minusTestTag = "physical_minus",
                zeroTestTag = "physical_set_zero",
                scoreTextTag = "physical_score_text",
                enabled = !saving && !deleting && !maintenanceBusy,
                onScoreChange = { newScore ->
                    onDraftChange(draft.copy(physicalScore = newScore))
                },
            )

            Spacer(Modifier.height(StarToolDimens.SpaceLg))

            // 备注框
            val codePointCount = draft.note.codePointCount(0, draft.note.length)
            val isNoteOverLimit = codePointCount > HourlyEntry.NOTE_MAX_CODEPOINTS

            OutlinedTextField(
                value = draft.note,
                onValueChange = { newNote ->
                    onDraftChange(draft.copy(note = newNote))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_field"),
                label = { Text("备注（可选）") },
                placeholder = { Text("记录此时段的活动或感受（最多200字）") },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "$codePointCount/${HourlyEntry.NOTE_MAX_CODEPOINTS}",
                            color = if (isNoteOverLimit) StarToolColors.Error else StarToolColors.TextSecondary,
                            style = StarToolType.Caption,
                            modifier = Modifier.testTag("note_counter"),
                        )
                    }
                },
                isError = isNoteOverLimit,
                maxLines = 4,
                enabled = !saving && !deleting && !maintenanceBusy,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StarToolColors.Primary,
                    unfocusedBorderColor = StarToolColors.CardBorder,
                ),
            )

            Spacer(Modifier.height(StarToolDimens.SpaceMd))

            // 校验与提示文案
            val canSave = draft.isComplete && !isNoteOverLimit && !saving && !deleting && !maintenanceBusy
            val missingPrompt = when {
                maintenanceBusy -> "正在进行数据备份或恢复，请稍候"
                draft.mentalScore == null && draft.physicalScore == null -> "请填写精神状态与身体状态评分"
                draft.mentalScore == null -> "请填写精神状态评分"
                draft.physicalScore == null -> "请填写身体状态评分"
                isNoteOverLimit -> "备注字数超过200字上限"
                else -> null
            }

            if (missingPrompt != null) {
                Text(
                    text = missingPrompt,
                    style = StarToolType.Caption,
                    color = if (isNoteOverLimit || maintenanceBusy) StarToolColors.Error else StarToolColors.TextSecondary,
                    modifier = Modifier.padding(bottom = StarToolDimens.SpaceSm),
                )
            }

            // 保存按钮
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = StarToolDimens.MinTouchTarget)
                    .testTag("save_entry"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StarToolColors.Primary,
                    disabledContainerColor = StarToolColors.CardBorder,
                ),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(StarToolDimens.SpaceSm))
                    Text("正在保存...", style = StarToolType.Body)
                } else {
                    Text("保存", style = StarToolType.Body)
                }
            }

            // 删除按钮（仅修改已有记录时显示）
            if (existingEntry != null) {
                Spacer(Modifier.height(StarToolDimens.SpaceSm))
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    enabled = !saving && !deleting && !maintenanceBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = StarToolDimens.MinTouchTarget)
                        .testTag("delete_entry"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StarToolColors.Error,
                    ),
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp,
                            color = StarToolColors.Error,
                        )
                        Spacer(Modifier.width(StarToolDimens.SpaceSm))
                        Text("正在删除...", style = StarToolType.Body)
                    } else {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = StarToolColors.Error)
                        Spacer(Modifier.width(StarToolDimens.SpaceXs))
                        Text("删除此记录", style = StarToolType.Body)
                    }
                }
            }

            Spacer(Modifier.height(StarToolDimens.SpaceLg))
        }
    }

    // 放弃未保存修改确认对话框
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("未保存修改", style = StarToolType.HourTitle) },
            text = { Text("您有尚未保存的评分或备注修改，确定要放弃吗？", style = StarToolType.Body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    },
                    modifier = Modifier.testTag("discard_confirm"),
                ) {
                    Text("放弃修改", color = StarToolColors.Error, style = StarToolType.Body)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                    modifier = Modifier.testTag("discard_keep_editing"),
                ) {
                    Text("继续编辑", style = StarToolType.Body)
                }
            },
            modifier = Modifier.testTag("discard_dialog"),
        )
    }

    // 删除确认对话框
    if (showDeleteConfirmDialog && existingEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("删除记录确认", style = StarToolType.HourTitle) },
            text = {
                Text(
                    text = "确定要删除 ${existingEntry.key.date} ${existingEntry.key.hourLabel} 的记录吗？删除后不可撤销。",
                    style = StarToolType.Body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                ) {
                    Text("确认删除", color = StarToolColors.Error, style = StarToolType.Body)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", style = StarToolType.Body)
                }
            },
        )
    }
}

@Composable
private fun ScoreInputSection(
    title: String,
    lowDesc: String,
    highDesc: String,
    score: Int?,
    accentColor: Color,
    sliderTestTag: String,
    plusTestTag: String,
    minusTestTag: String,
    zeroTestTag: String,
    scoreTextTag: String,
    enabled: Boolean,
    onScoreChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StarToolColors.Background, RoundedCornerShape(StarToolDimens.SpaceMd))
            .padding(StarToolDimens.SpaceMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = StarToolType.HourTitle, color = StarToolColors.TextPrimary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (score != null) "${score}分" else "未选择",
                    style = StarToolType.HourTitle,
                    color = if (score != null) accentColor else StarToolColors.TextSecondary,
                    modifier = Modifier
                        .testTag(scoreTextTag)
                        .semantics {
                            contentDescription = if (score != null) "$title，${score}分，满分10分" else "$title 未选择"
                        },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "0: $lowDesc", style = StarToolType.Caption, color = StarToolColors.TextSecondary)
            Text(text = "10: $highDesc", style = StarToolType.Caption, color = StarToolColors.TextSecondary)
        }

        Spacer(Modifier.height(StarToolDimens.SpaceSm))

        // 滑杆控制
        Slider(
            value = (score ?: 0).toFloat(),
            onValueChange = { flt ->
                if (enabled) onScoreChange(flt.toInt().coerceIn(0, 10))
            },
            valueRange = 0f..10f,
            steps = 9,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StarToolColors.CardBorder,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(sliderTestTag)
                .semantics {
                    contentDescription = "$title 滑杆，当前 ${score ?: 0} 分"
                },
        )

        // 加减与设为 0 分快捷操作行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (score == null) {
                OutlinedButton(
                    onClick = { if (enabled) onScoreChange(0) },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = StarToolDimens.MinTouchTarget)
                        .testTag(zeroTestTag),
                ) {
                    Text("设为 0 分", style = StarToolType.Caption)
                }
            } else {
                TextButton(
                    onClick = { if (enabled) onScoreChange(0) },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = StarToolDimens.MinTouchTarget)
                        .testTag(zeroTestTag),
                ) {
                    Text("置零", style = StarToolType.Caption, color = StarToolColors.TextSecondary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        if (enabled) {
                            val current = score ?: 0
                            onScoreChange((current - 1).coerceAtLeast(0))
                        }
                    },
                    enabled = enabled && (score == null || score > 0),
                    modifier = Modifier
                        .heightIn(min = StarToolDimens.MinTouchTarget)
                        .testTag(minusTestTag),
                ) {
                    Text("－", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(StarToolDimens.SpaceSm))

                FilledTonalButton(
                    onClick = {
                        if (enabled) {
                            val current = score ?: 0
                            onScoreChange((current + 1).coerceAtMost(10))
                        }
                    },
                    enabled = enabled && (score == null || score < 10),
                    modifier = Modifier
                        .heightIn(min = StarToolDimens.MinTouchTarget)
                        .testTag(plusTestTag),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "加1分")
                }
            }
        }
    }
}
