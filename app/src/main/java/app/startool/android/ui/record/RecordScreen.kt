package app.startool.android.ui.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.startool.android.AppContainer
import app.startool.android.domain.EntryDraft
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.HourlyKey
import app.startool.android.domain.OpResult
import app.startool.android.ui.components.DateBar
import app.startool.android.ui.components.ScoreEditorSheet
import app.startool.android.ui.components.StarRating
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

@Composable
fun RecordScreen(
    container: AppContainer,
    showSnackbar: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val selectedDate by container.selectedDateStore.date.collectAsState()
    val today = remember(container.clock) { LocalDate.now(container.clock) }
    val recordedDates by container.repository.observeRecordedDates().collectAsState(initial = emptySet())
    val dayEntries by container.repository.observeDay(selectedDate).collectAsState(initial = emptyList())
    val maintenanceBusy by container.maintenance.busy.collectAsState()

    // 映射到小时表
    val entriesByHour = remember(dayEntries) {
        dayEntries.associateBy { it.key.hour }
    }

    // 当前小时
    val currentHour = remember(container.clock, selectedDate) {
        val nowTime = LocalTime.now(container.clock)
        nowTime.hour
    }

    // 编辑面板状态
    var editingHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf<EntryDraft?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 首次进入时自动滚动
    var hasAutoScrolledForDate by rememberSaveable(selectedDate) { mutableStateOf(false) }
    LaunchedEffect(selectedDate, dayEntries) {
        if (!hasAutoScrolledForDate) {
            hasAutoScrolledForDate = true
            val targetHour = when {
                selectedDate == today -> currentHour
                dayEntries.isNotEmpty() -> dayEntries.minOf { it.key.hour }
                else -> 0
            }
            if (targetHour in 0..23) {
                listState.scrollToItem(targetHour)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 日期栏
        DateBar(
            date = selectedDate,
            today = today,
            recordedDates = recordedDates,
            onSelect = { container.selectedDateStore.select(it) },
            modifier = Modifier.padding(horizontal = StarToolDimens.PageHorizontalPadding, vertical = StarToolDimens.SpaceSm),
        )

        // 简短统计提示
        val recordedCount = dayEntries.size
        Text(
            text = if (selectedDate == today) "今天记录了 $recordedCount 个时段" else "该日记录了 $recordedCount 个时段",
            style = StarToolType.Caption,
            color = StarToolColors.TextSecondary,
            modifier = Modifier
                .padding(horizontal = StarToolDimens.PageHorizontalPadding, vertical = StarToolDimens.SpaceXs)
                .testTag("recorded_count_text"),
        )

        Spacer(Modifier.height(StarToolDimens.SpaceSm))

        // 24 个小时卡片列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = StarToolDimens.PageHorizontalPadding)
                .testTag("hour_list"),
            verticalArrangement = Arrangement.spacedBy(StarToolDimens.CardSpacing),
        ) {
            items(24) { hour ->
                val entry = entriesByHour[hour]
                val isFuture = when {
                    selectedDate.isAfter(today) -> true
                    selectedDate == today && hour > currentHour -> true
                    else -> false
                }
                val isCurrent = selectedDate == today && hour == currentHour

                HourCard(
                    hour = hour,
                    entry = entry,
                    isFuture = isFuture,
                    isCurrent = isCurrent,
                    enabled = !maintenanceBusy && (!isFuture || entry != null),
                    onClick = {
                        val initialDraft = if (entry != null) {
                            EntryDraft(
                                key = entry.key,
                                mentalScore = entry.mentalScore,
                                physicalScore = entry.physicalScore,
                                note = entry.note,
                            )
                        } else {
                            EntryDraft(
                                key = HourlyKey(selectedDate, hour),
                                mentalScore = null,
                                physicalScore = null,
                                note = "",
                            )
                        }
                        draft = initialDraft
                        editingHour = hour
                    },
                )
            }

            item {
                Spacer(Modifier.height(StarToolDimens.SpaceXl))
            }
        }
    }

    // 评分面板
    val activeHour = editingHour
    val currentDraft = draft
    if (activeHour != null && currentDraft != null) {
        val existing = entriesByHour[activeHour]
        ScoreEditorSheet(
            draft = currentDraft,
            existingEntry = existing,
            saving = isSaving,
            deleting = isDeleting,
            maintenanceBusy = maintenanceBusy,
            onDraftChange = { updated -> draft = updated },
            onSave = {
                if (currentDraft.isComplete) {
                    isSaving = true
                    scope.launch {
                        val res = container.repository.saveEntry(
                            key = currentDraft.key,
                            mentalScore = currentDraft.mentalScore!!,
                            physicalScore = currentDraft.physicalScore!!,
                            note = currentDraft.note,
                        )
                        isSaving = false
                        when (res) {
                            is OpResult.Success -> {
                                editingHour = null
                                draft = null
                                showSnackbar("已保存")
                            }
                            is OpResult.NoChange -> {
                                editingHour = null
                                draft = null
                            }
                            is OpResult.BlockedByMaintenance -> {
                                showSnackbar("数据备份或恢复进行中，暂不可修改")
                            }
                            is OpResult.Failure -> {
                                showSnackbar("保存失败：${res.cause.message ?: "未知错误"}")
                            }
                        }
                    }
                }
            },
            onDelete = {
                isDeleting = true
                scope.launch {
                    val res = container.repository.deleteEntry(currentDraft.key)
                    isDeleting = false
                    when (res) {
                        is OpResult.Success -> {
                            editingHour = null
                            draft = null
                            showSnackbar("已删除")
                        }
                        is OpResult.NoChange -> {
                            editingHour = null
                            draft = null
                        }
                        is OpResult.BlockedByMaintenance -> {
                            showSnackbar("数据备份或恢复进行中，暂不可删除")
                        }
                        is OpResult.Failure -> {
                            showSnackbar("删除失败：${res.cause.message ?: "未知错误"}")
                        }
                    }
                }
            },
            onDismiss = {
                editingHour = null
                draft = null
            },
        )
    }
}

@Composable
private fun HourCard(
    hour: Int,
    entry: HourlyEntry?,
    isFuture: Boolean,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val hourLabel = "%02d:00–%02d:59".format(hour, hour)

    val border = when {
        isCurrent && entry == null -> BorderStroke(2.dp, StarToolColors.Primary)
        else -> BorderStroke(StarToolDimens.CardBorderWidth, StarToolColors.CardBorder)
    }

    val cardColor = when {
        isFuture && entry == null -> StarToolColors.Background
        else -> StarToolColors.Surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StarToolDimens.CardCornerRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("hour_$hour"),
        shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
        border = border,
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StarToolDimens.CardPadding),
        ) {
            // 头部：时间段 + 状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hourLabel,
                    style = StarToolType.HourTitle,
                    color = if (isFuture && entry == null) StarToolColors.TextSecondary.copy(alpha = 0.6f) else StarToolColors.TextPrimary,
                )

                when {
                    entry != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Edit, contentDescription = null, tint = StarToolColors.TextSecondary, modifier = Modifier.height(16.dp).width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text = "点击修改", style = StarToolType.Caption, color = StarToolColors.TextSecondary)
                        }
                    }
                    isCurrent -> {
                        Text(
                            text = "当前时段 · 点击记录",
                            style = StarToolType.Caption,
                            color = StarToolColors.Primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    isFuture -> {
                        Text(
                            text = "尚未到此时段",
                            style = StarToolType.Caption,
                            color = StarToolColors.TextSecondary.copy(alpha = 0.6f),
                        )
                    }
                    else -> {
                        Text(
                            text = "未记录 · 点击补记",
                            style = StarToolType.Caption,
                            color = StarToolColors.TextSecondary,
                        )
                    }
                }
            }

            // 内容区
            if (entry != null) {
                Spacer(Modifier.height(StarToolDimens.SpaceSm))

                // 精神状态行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "精神状态",
                            style = StarToolType.Body,
                            color = StarToolColors.TextPrimary,
                            modifier = Modifier.width(72.dp),
                        )
                        StarRating(score = entry.mentalScore, filledColor = StarToolColors.Primary)
                    }
                    Text(
                        text = "${entry.mentalScore}分",
                        style = StarToolType.HourTitle,
                        color = StarToolColors.Primary,
                    )
                }

                Spacer(Modifier.height(StarToolDimens.SpaceXs))

                // 身体状态行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "身体状态",
                            style = StarToolType.Body,
                            color = StarToolColors.TextPrimary,
                            modifier = Modifier.width(72.dp),
                        )
                        StarRating(score = entry.physicalScore, filledColor = StarToolColors.Physical)
                    }
                    Text(
                        text = "${entry.physicalScore}分",
                        style = StarToolType.HourTitle,
                        color = StarToolColors.Physical,
                    )
                }

                // 备注
                if (entry.note.isNotBlank()) {
                    Spacer(Modifier.height(StarToolDimens.SpaceSm))
                    Text(
                        text = entry.note,
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
