package app.startool.android.feedback.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 8个缩放控制点锚点（dx, dy ∈ {-1, 0, 1}）
 */
enum class HandleAnchor(val dx: Int, val dy: Int) {
    TopLeft(-1, -1),
    Top(0, -1),
    TopRight(1, -1),
    Left(-1, 0),
    Right(1, 0),
    BottomLeft(-1, 1),
    Bottom(0, 1),
    BottomRight(1, 1),
}

private const val MIN_SIZE_DP = 48f
private const val MAX_WIDTH_DP = 400f
private const val MAX_HEIGHT_DP = 260f

/**
 * 冻结画面上的局部选区覆盖层（计划 §T1 / Flutter CaptureSelectionOverlay 对齐实现）：
 * - 展示全屏冻结位图
 * - 8 控制点缩放、内部拖动平移、背景拖动重新框选
 * - 三分辅助线 + 选区外 45% 暗度遮罩
 * - 底部工具栏：「使用此区域」、「截取整个窗口」、「取消」
 */
@Composable
fun CaptureSelectionOverlay(
    visible: Boolean,
    bitmap: Bitmap?,
    releasePoint: Offset?, // 归一化落点 (0..1)
    onUseRegion: (RectF) -> Unit,
    onCaptureWhole: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible || bitmap == null) return

    BackHandler(enabled = visible) {
        onCancel()
    }

    val density = LocalDensity.current
    val minSizePx = with(density) { MIN_SIZE_DP.dp.toPx() }
    val maxWidthPx = with(density) { MAX_WIDTH_DP.dp.toPx() }
    val maxHeightPx = with(density) { MAX_HEIGHT_DP.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("capture_selection_overlay"),
    ) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()

        // 选区（屏幕像素坐标）
        var selection by remember(bitmap, releasePoint) {
            val center = if (releasePoint != null) {
                Offset(releasePoint.x * viewportW, releasePoint.y * viewportH)
            } else {
                Offset(viewportW / 2f, viewportH / 2f)
            }
            val w = min(maxWidthPx, viewportW * 0.8f).coerceAtLeast(minSizePx)
            val h = min(maxHeightPx, viewportH * 0.4f).coerceAtLeast(minSizePx)
            val left = (center.x - w / 2f).coerceIn(0f, max(0f, viewportW - w))
            val top = (center.y - h / 2f).coerceIn(0f, max(0f, viewportH - h))
            mutableStateOf(Rect(left, top, left + w, top + h))
        }

        // 重新框选中的实时矩形（null 表示未在框选）
        var framingRect by remember { mutableStateOf<Rect?>(null) }
        var framingStart by remember { mutableStateOf<Offset?>(null) }

        val activeRect = framingRect ?: selection

        // 1. 冻结位图铺满背景
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Frozen capture",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        // 2. 绘制层：选区外暗色压暗、边框、三分网格
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // 选区外 45% 压暗遮罩
            val fullPath = Path().apply {
                addRect(Rect(0f, 0f, canvasW, canvasH))
            }
            val selPath = Path().apply {
                addRect(activeRect)
            }
            clipPath(selPath, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.45f))
            }

            // 选区边框（框选中为强调蓝色 #0071E3，平时为白色）
            val strokeColor = if (framingRect != null) Color(0xFF0071E3) else Color.White
            drawRect(
                color = strokeColor,
                topLeft = Offset(activeRect.left, activeRect.top),
                size = Size(activeRect.width, activeRect.height),
                style = Stroke(width = 2.4.dp.toPx()),
            )

            // 三分辅助线（0.7dp，35% 白色透明度）
            val gridColor = Color.White.copy(alpha = 0.35f)
            val gridStroke = 0.7.dp.toPx()
            for (i in 1..2) {
                val dx = activeRect.left + activeRect.width * (i / 3f)
                val dy = activeRect.top + activeRect.height * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(dx, activeRect.top),
                    end = Offset(dx, activeRect.bottom),
                    strokeWidth = gridStroke,
                )
                drawLine(
                    color = gridColor,
                    start = Offset(activeRect.left, dy),
                    end = Offset(activeRect.right, dy),
                    strokeWidth = gridStroke,
                )
            }
        }

        // 3. 背景拖拽手势：在选区外部拖动 = 重新框选
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            framingStart = offset
                            framingRect = Rect(offset.x, offset.y, offset.x, offset.y)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val start = framingStart ?: return@detectDragGestures
                            val cur = change.position
                            val left = min(start.x, cur.x)
                            val top = min(start.y, cur.y)
                            val right = max(start.x, cur.x)
                            val bottom = max(start.y, cur.y)
                            framingRect = Rect(left, top, right, bottom)
                        },
                        onDragEnd = {
                            val live = framingRect
                            framingRect = null
                            framingStart = null
                            if (live != null) {
                                var w = live.width
                                var h = live.height
                                var l = live.left
                                var t = live.top
                                if (w < minSizePx) w = minSizePx
                                if (h < minSizePx) h = minSizePx
                                l = l.coerceIn(0f, max(0f, viewportW - w))
                                t = t.coerceIn(0f, max(0f, viewportH - h))
                                selection = Rect(l, t, l + w, t + h)
                            }
                        },
                        onDragCancel = {
                            framingRect = null
                            framingStart = null
                        },
                    )
                },
        )

        // 4. 选区本体拖拽手势：选区内部拖动 = 整体平移
        if (framingRect == null) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(selection.left.roundToInt(), selection.top.roundToInt()) }
                    .size(
                        width = with(density) { selection.width.toDp() },
                        height = with(density) { selection.height.toDp() },
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cur = selection
                            val newLeft = (cur.left + dragAmount.x).coerceIn(0f, max(0f, viewportW - cur.width))
                            val newTop = (cur.top + dragAmount.y).coerceIn(0f, max(0f, viewportH - cur.height))
                            selection = Rect(newLeft, newTop, newLeft + cur.width, newTop + cur.height)
                        }
                    },
            )
        }

        // 5. 八个控制点（仅在非重新框选状态展示）
        if (framingRect == null) {
            val visualSizeDp = 12.dp
            val hitSizeDp = 36.dp
            val hitSizePx = with(density) { hitSizeDp.toPx() }

            HandleAnchor.entries.forEach { anchor ->
                val cx = when {
                    anchor.dx < 0 -> selection.left
                    anchor.dx > 0 -> selection.right
                    else -> selection.left + selection.width / 2f
                }
                val cy = when {
                    anchor.dy < 0 -> selection.top
                    anchor.dy > 0 -> selection.bottom
                    else -> selection.top + selection.height / 2f
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset((cx - hitSizePx / 2f).roundToInt(), (cy - hitSizePx / 2f).roundToInt()) }
                        .size(hitSizeDp)
                        .pointerInput(anchor) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                var l = selection.left
                                var t = selection.top
                                var r = selection.right
                                var b = selection.bottom

                                if (anchor.dx < 0) {
                                    l = (l + dragAmount.x).coerceIn(0f, r - minSizePx)
                                } else if (anchor.dx > 0) {
                                    r = (r + dragAmount.x).coerceIn(l + minSizePx, viewportW)
                                }

                                if (anchor.dy < 0) {
                                    t = (t + dragAmount.y).coerceIn(0f, b - minSizePx)
                                } else if (anchor.dy > 0) {
                                    b = (b + dragAmount.y).coerceIn(t + minSizePx, viewportH)
                                }

                                selection = Rect(l, t, r, b)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val isCorner = anchor.dx != 0 && anchor.dy != 0
                    Box(
                        modifier = Modifier
                            .size(visualSizeDp)
                            .background(
                                color = Color.White,
                                shape = if (isCorner) CircleShape else RoundedCornerShape(2.dp),
                            )
                            .border(
                                width = 0.8.dp,
                                color = Color(0x66000000),
                                shape = if (isCorner) CircleShape else RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }

        // 6. 底部工具栏
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("feedback-capture-toolbar"),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "调整选区后确认；也可以在画面上拖动重新框选",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("feedback-capture-hint"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            val normLeft = (selection.left / viewportW).coerceIn(0f, 1f)
                            val normTop = (selection.top / viewportH).coerceIn(0f, 1f)
                            val normRight = (selection.right / viewportW).coerceIn(normLeft, 1f)
                            val normBottom = (selection.bottom / viewportH).coerceIn(normTop, 1f)
                            onUseRegion(RectF(normLeft, normTop, normRight, normBottom))
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("feedback-capture-use-region"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("使用此区域", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onCaptureWhole,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("feedback-capture-whole-window"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("截取整个窗口", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("feedback-capture-cancel"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("取消", maxLines = 1)
                    }
                }
            }
        }
    }
}
