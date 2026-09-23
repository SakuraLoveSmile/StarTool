package app.startool.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE)

/**
 * 共享日期栏：前一天 / 日期选择 / 后一天 / 今天。
 * 记录页与回看页共用同一实例语义（通过 SelectedDateStore 传入）。
 *
 * @param date 当前所选日期
 * @param recordedDates 有记录的日期集合（用于选择器中标记，可为空）
 */
@Composable
fun DateBar(
    date: LocalDate,
    today: LocalDate,
    recordedDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = { onSelect(date.minusDays(1)) },
            modifier = Modifier.testTag("date_prev"),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "前一天",
                tint = StarToolColors.TextPrimary,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(StarToolDimens.SpaceSm))
                .clickable { pickerOpen = true }
                .padding(horizontal = StarToolDimens.SpaceMd, vertical = StarToolDimens.SpaceXs)
                .testTag("date_picker")
                .semantics { contentDescription = "选择日期，当前 ${date.format(DATE_FMT)}" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date.format(DATE_FMT),
                    style = StarToolType.HourTitle,
                    color = StarToolColors.TextPrimary,
                )
                Spacer(Modifier.width(StarToolDimens.SpaceXs))
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = StarToolColors.TextSecondary,
                )
            }
            if (date == today) {
                Text(
                    text = "今天",
                    style = StarToolType.Caption,
                    color = StarToolColors.Primary,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (date != today) {
                TextButton(
                    onClick = { onSelect(today) },
                    modifier = Modifier
                        .heightIn(min = StarToolDimens.MinTouchTarget)
                        .testTag("date_today"),
                ) {
                    Text("今天", style = StarToolType.Body, color = StarToolColors.Primary)
                }
            }
            IconButton(
                onClick = { onSelect(date.plusDays(1)) },
                modifier = Modifier.testTag("date_next"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "后一天",
                    tint = StarToolColors.TextPrimary,
                )
            }
        }
    }

    if (pickerOpen) {
        DateWheelPickerDialog(
            initial = date,
            recordedDates = recordedDates,
            onDismiss = { pickerOpen = false },
            onConfirm = { picked ->
                pickerOpen = false
                onSelect(picked)
            },
        )
    }
}

/** 年/月/日三个滚轮的日期选择对话框（无第三方依赖，测试友好）。 */
@Composable
private fun DateWheelPickerDialog(
    initial: LocalDate,
    recordedDates: Set<LocalDate>,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }

    val maxDay = YearMonth.of(year, month).lengthOfMonth()
    if (day > maxDay) day = maxDay

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalDate.of(year, month, day)) },
                modifier = Modifier.testTag("date_picker_confirm"),
            ) { Text("确定", style = StarToolType.Body) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("date_picker_cancel"),
            ) { Text("取消", style = StarToolType.Body) }
        },
        title = { Text("选择日期", style = StarToolType.HourTitle) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val thisYear = LocalDate.now().year
                Wheel(
                    items = (thisYear - 10..thisYear + 1).toList(),
                    selected = year,
                    label = { "${it}年" },
                    onSelect = { year = it },
                    testTag = "date_wheel_year",
                )
                Wheel(
                    items = (1..12).toList(),
                    selected = month,
                    label = { "${it}月" },
                    onSelect = { month = it },
                    testTag = "date_wheel_month",
                )
                Wheel(
                    items = (1..maxDay).toList(),
                    selected = day,
                    label = { d ->
                        val has = recordedDates.contains(LocalDate.of(year, month, d))
                        if (has) "${d}日 ·" else "${d}日"
                    },
                    onSelect = { day = it },
                    testTag = "date_wheel_day",
                )
            }
        },
    )
}

@Composable
private fun Wheel(
    items: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    testTag: String,
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (items.indexOf(selected) - 1).coerceAtLeast(0),
    )
    LazyColumn(
        state = state,
        modifier = Modifier
            .height(160.dp)
            .testTag(testTag),
    ) {
        items(items.size) { i ->
            val v = items[i]
            val isSel = v == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(StarToolDimens.SpaceSm))
                    .clickable { onSelect(v) }
                    .padding(horizontal = StarToolDimens.SpaceMd, vertical = StarToolDimens.SpaceSm)
                    .testTag("${testTag}_item_$v"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(v),
                    style = StarToolType.Body,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) StarToolColors.Primary else StarToolColors.TextPrimary,
                )
            }
        }
    }
    LaunchedEffect(selected) {
        val idx = items.indexOf(selected)
        if (idx >= 0) state.animateScrollToItem((idx - 1).coerceAtLeast(0))
    }
}
