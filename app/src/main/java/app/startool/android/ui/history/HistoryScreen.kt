package app.startool.android.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.startool.android.AppContainer
import app.startool.android.MainTab
import app.startool.android.domain.EntryDraft
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.OpResult
import app.startool.android.ui.components.DateBar
import app.startool.android.ui.components.EmptyView
import app.startool.android.ui.components.ScoreEditorSheet
import app.startool.android.ui.components.StarRating
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
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

    var editingEntry by remember { mutableStateOf<HourlyEntry?>(null) }
    var draft by remember { mutableStateOf<EntryDraft?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部日期栏
        DateBar(
            date = selectedDate,
            today = today,
            recordedDates = recordedDates,
            onSelect = { container.selectedDateStore.select(it) },
            modifier = Modifier.padding(
                horizontal = StarToolDimens.PageHorizontalPadding,
                vertical = StarToolDimens.SpaceSm,
            ),
        )

        if (dayEntries.isEmpty()) {
            EmptyView(
                message = "这天还没有记录",
                actionLabel = "去记录",
                onAction = { container.requestTab(MainTab.Record) },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = StarToolDimens.PageHorizontalPadding)
                    .testTag("history_list"),
                verticalArrangement = Arrangement.spacedBy(StarToolDimens.CardSpacing),
            ) {
                // 1. 统计数量
                item {
                    Text(
                        text = "已记录 ${dayEntries.size} 个时段",
                        style = StarToolType.Caption,
                        color = StarToolColors.TextSecondary,
                        modifier = Modifier
                            .padding(vertical = StarToolDimens.SpaceXs)
                            .testTag("history_count_text"),
                    )
                }

                // 2. 图例说明
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceLg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 精神图例
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("chart_legend_mental"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(StarToolColors.Primary, RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(StarToolDimens.SpaceSm))
                            Text(
                                text = "精神状态（紫实线·圆）",
                                style = StarToolType.Caption,
                                color = StarToolColors.TextPrimary,
                            )
                        }

                        // 身体图例
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("chart_legend_physical"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(StarToolColors.Physical),
                            )
                            Spacer(Modifier.width(StarToolDimens.SpaceSm))
                            Text(
                                text = "身体状态（绿虚线·菱）",
                                style = StarToolType.Caption,
                                color = StarToolColors.TextPrimary,
                            )
                        }
                    }
                }

                // 3. Canvas 双曲线图表
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(StarToolDimens.ChartHeight)
                            .testTag("history_chart"),
                        shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = StarToolColors.Surface),
                        border = androidx.compose.foundation.BorderStroke(
                            StarToolDimens.CardBorderWidth,
                            StarToolColors.CardBorder,
                        ),
                    ) {
                        HistoryCanvasChart(
                            entries = dayEntries,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(StarToolDimens.SpaceSm),
                        )
                    }
                }

                // 4. 明细列表说明
                item {
                    Text(
                        text = "时段明细",
                        style = StarToolType.HourTitle,
                        color = StarToolColors.TextPrimary,
                        modifier = Modifier.padding(top = StarToolDimens.SpaceSm),
                    )
                }

                // 5. 各时段明细项
                items(dayEntries, key = { it.key.hour }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(StarToolDimens.CardCornerRadius))
                            .clickable {
                                editingEntry = entry
                                draft = EntryDraft(
                                    key = entry.key,
                                    mentalScore = entry.mentalScore,
                                    physicalScore = entry.physicalScore,
                                    note = entry.note,
                                )
                            }
                            .testTag("history_item_${entry.key.hour}"),
                        shape = RoundedCornerShape(StarToolDimens.CardCornerRadius),
                        border = androidx.compose.foundation.BorderStroke(
                            StarToolDimens.CardBorderWidth,
                            StarToolColors.CardBorder,
                        ),
                        colors = CardDefaults.cardColors(containerColor = StarToolColors.Surface),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(StarToolDimens.CardPadding),
                        ) {
                            Text(
                                text = entry.key.hourLabel,
                                style = StarToolType.HourTitle,
                                color = StarToolColors.TextPrimary,
                            )

                            Spacer(Modifier.height(StarToolDimens.SpaceSm))

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

                item {
                    Spacer(Modifier.height(StarToolDimens.SpaceXl))
                }
            }
        }
    }

    // 评分编辑面板（直接复用 ScoreEditorSheet）
    val activeEntry = editingEntry
    val currentDraft = draft
    if (activeEntry != null && currentDraft != null) {
        ScoreEditorSheet(
            draft = currentDraft,
            existingEntry = activeEntry,
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
                                editingEntry = null
                                draft = null
                                showSnackbar("已保存")
                            }
                            is OpResult.NoChange -> {
                                editingEntry = null
                                draft = null
                            }
                            is OpResult.BlockedByMaintenance -> {
                                showSnackbar("备份恢复中，暂不可修改")
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
                            editingEntry = null
                            draft = null
                            showSnackbar("已删除")
                        }
                        is OpResult.NoChange -> {
                            editingEntry = null
                            draft = null
                        }
                        is OpResult.BlockedByMaintenance -> {
                            showSnackbar("备份恢复中，暂不可删除")
                        }
                        is OpResult.Failure -> {
                            showSnackbar("删除失败：${res.cause.message ?: "未知错误"}")
                        }
                    }
                }
            },
            onDismiss = {
                editingEntry = null
                draft = null
            },
        )
    }
}

/**
 * Compose Canvas 双曲线图表：
 * 横轴固定 0..23，主刻度 00、06、12、18、23
 * 纵轴固定 0..10，主刻度 0、2、4、6、8、10
 * 直线连接相邻且都有记录的小时，漏记不跨越连线
 * 精神状态：紫色实线与圆点
 * 身体状态：青绿虚线与菱形
 * 同分时点位不篡改，嵌套呈现保证可辨
 */
@Composable
private fun HistoryCanvasChart(
    entries: List<HourlyEntry>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val padLeft = 32.dp.toPx()
        val padRight = 16.dp.toPx()
        val padTop = 14.dp.toPx()
        val padBottom = 24.dp.toPx()

        val plotLeft = padLeft
        val plotRight = size.width - padRight
        val plotTop = padTop
        val plotBottom = size.height - padBottom

        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        fun xForHour(hour: Int): Float =
            plotLeft + (hour / 23f) * plotWidth

        fun yForScore(score: Int): Float =
            plotBottom - (score / 10f) * plotHeight

        val gridColor = Color(0xFFE5E2DA)
        val labelStyle = TextStyle(
            color = Color(0xFF62666D),
            fontSize = 10.sp,
        )

        // 1. 绘制 Y 轴主刻度线与标签 (0, 2, 4, 6, 8, 10)
        val yTicks = listOf(0, 2, 4, 6, 8, 10)
        for (tick in yTicks) {
            val y = yForScore(tick)
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
            val measure = textMeasurer.measure("$tick", labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = "$tick",
                topLeft = Offset(plotLeft - measure.size.width - 6.dp.toPx(), y - measure.size.height / 2f),
                style = labelStyle,
            )
        }

        // 2. 绘制 X 轴标签 (00, 06, 12, 18, 23)
        val xTicks = listOf(0 to "00", 6 to "06", 12 to "12", 18 to "18", 23 to "23")
        for ((h, label) in xTicks) {
            val x = xForHour(h)
            drawLine(
                color = gridColor,
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx(),
            )
            val measure = textMeasurer.measure(label, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(x - measure.size.width / 2f, plotBottom + 4.dp.toPx()),
                style = labelStyle,
            )
        }

        val sorted = entries.sortedBy { it.key.hour }
        if (sorted.isEmpty()) return@Canvas

        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

        // 3. 绘制线段：仅连接相邻的小时 (e_{i+1}.hour == e_i.hour + 1)
        for (i in 0 until sorted.size - 1) {
            val curr = sorted[i]
            val next = sorted[i + 1]
            if (next.key.hour == curr.key.hour + 1) {
                // 精神状态连线（紫色实线）
                drawLine(
                    color = StarToolColors.Primary,
                    start = Offset(xForHour(curr.key.hour), yForScore(curr.mentalScore)),
                    end = Offset(xForHour(next.key.hour), yForScore(next.mentalScore)),
                    strokeWidth = 2.5.dp.toPx(),
                )
                // 身体状态连线（青绿虚线）
                drawLine(
                    color = StarToolColors.Physical,
                    start = Offset(xForHour(curr.key.hour), yForScore(curr.physicalScore)),
                    end = Offset(xForHour(next.key.hour), yForScore(next.physicalScore)),
                    strokeWidth = 2.5.dp.toPx(),
                    pathEffect = dashedEffect,
                )
            }
        }

        // 4. 绘制数据点
        val circleRadius = 4.5.dp.toPx()
        val diamondRadius = 5.5.dp.toPx()

        for (entry in sorted) {
            val cx = xForHour(entry.key.hour)
            val ym = yForScore(entry.mentalScore)
            val yp = yForScore(entry.physicalScore)

            if (entry.mentalScore == entry.physicalScore) {
                // 同分：菱形在外（青绿轮廓），圆点在内（紫色实心），坐标不变且两者均清晰可见
                val diamondPath = Path().apply {
                    moveTo(cx, ym - diamondRadius - 2f)
                    lineTo(cx + diamondRadius + 2f, ym)
                    lineTo(cx, ym + diamondRadius + 2f)
                    lineTo(cx - diamondRadius - 2f, ym)
                    close()
                }
                drawPath(diamondPath, color = StarToolColors.Physical, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = Color.White, radius = circleRadius + 1f, center = Offset(cx, ym))
                drawCircle(color = StarToolColors.Primary, radius = circleRadius, center = Offset(cx, ym))
            } else {
                // 精神状态：紫实心圆 + 白边
                drawCircle(color = Color.White, radius = circleRadius + 1.5.dp.toPx(), center = Offset(cx, ym))
                drawCircle(color = StarToolColors.Primary, radius = circleRadius, center = Offset(cx, ym))

                // 身体状态：青绿实心菱形 + 白边
                val diamondOuter = Path().apply {
                    val r = diamondRadius + 1.5.dp.toPx()
                    moveTo(cx, yp - r)
                    lineTo(cx + r, yp)
                    lineTo(cx, yp + r)
                    lineTo(cx - r, yp)
                    close()
                }
                drawPath(diamondOuter, color = Color.White)

                val diamondInner = Path().apply {
                    moveTo(cx, yp - diamondRadius)
                    lineTo(cx + diamondRadius, yp)
                    lineTo(cx, yp + diamondRadius)
                    lineTo(cx - diamondRadius, yp)
                    close()
                }
                drawPath(diamondInner, color = StarToolColors.Physical)
            }
        }
    }
}
