package app.startool.android.feedback.ui

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.SweepGradient as AndroidSweepGradient
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Flutter 原版灵感球 (Inspiration Orb) 样式实现：
 * - 静止 48dp，拖拽中平滑展开至 72dp
 * - 透明中心 + 白色高光边缘 + 青/紫/暖橙低透明度缓慢流动光晕
 * - 外圈柔化阴影 (0x24000000, blur: 12dp, offset: 4dp)
 * - 拖动松手自动贴边，点击呼出反馈
 */
@Composable
fun FeedbackFab(
    visible: Boolean,
    onClick: () -> Unit,
    onDragRelease: (Offset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val baseSize = 48.dp
    val baseSizePx = with(density) { baseSize.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    // 动态直径：静止 48dp，拖拽展开 72dp
    val currentDiameter by animateDpAsState(
        targetValue = if (isDragging) 72.dp else 48.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "OrbDiameterAnimation",
    )

    // 光晕旋转动效（与 Flutter 端 2400ms duration 保持一致）
    val infiniteTransition = rememberInfiniteTransition(label = "OrbGlowInfiniteTransition")
    val flowAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "OrbGlowFlowAngle",
    )

    // 悬浮球贴边位置追踪（以 48dp 为基准对齐边缘）
    var offsetX by remember { mutableFloatStateOf(screenWidthPx - baseSizePx - marginPx) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx * 0.7f) }

    LaunchedEffect(screenWidthPx, screenHeightPx) {
        val maxX = (screenWidthPx - baseSizePx - marginPx).coerceAtLeast(marginPx)
        val maxY = (screenHeightPx - baseSizePx - marginPx).coerceAtLeast(marginPx)
        val centerX = offsetX + baseSizePx / 2f
        offsetX = if (centerX < screenWidthPx / 2f) marginPx else maxX
        offsetY = offsetY.coerceIn(marginPx, maxY)
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        // 居中偏移修正：当尺寸从 48dp 放大到 72dp 时，中心点保持不变
        val deltaSizePx = with(density) { (currentDiameter - baseSize).toPx() / 2f }
        val renderX = offsetX - deltaSizePx
        val renderY = offsetY - deltaSizePx

        Box(
            modifier = Modifier
                .offset { IntOffset(renderX.roundToInt(), renderY.roundToInt()) }
                .size(currentDiameter)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color(0x33000000),
                    spotColor = Color(0x33000000),
                )
                .testTag("feedback_fab")
                .semantics {
                    role = Role.Button
                    contentDescription = "灵感球：拖动指出问题或点击反馈"
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (!isDragging) {
                            onClick()
                        }
                    },
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            totalDragDistance = 0f
                        },
                        onDragEnd = {
                            val wasDragged = isDragging || totalDragDistance > 8f * density.density
                            isDragging = false
                            val releaseCenterX = offsetX + baseSizePx / 2f
                            val releaseCenterY = offsetY + baseSizePx / 2f
                            // 贴边逻辑：靠近左侧吸附左边，靠近右侧吸附右边
                            val centerX = offsetX + baseSizePx / 2f
                            offsetX = if (centerX < screenWidthPx / 2f) {
                                marginPx
                            } else {
                                screenWidthPx - baseSizePx - marginPx
                            }
                            if (wasDragged) {
                                val normX = (releaseCenterX / screenWidthPx).coerceIn(0f, 1f)
                                val normY = (releaseCenterY / screenHeightPx).coerceIn(0f, 1f)
                                onDragRelease(Offset(normX, normY))
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        totalDragDistance += sqrt(dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y)
                        if (totalDragDistance > 8f * density.density) {
                            isDragging = true
                        }
                        offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, screenWidthPx - baseSizePx - marginPx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, screenHeightPx - baseSizePx - marginPx)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                // 1. 青 / 紫 / 暖橙低透明度流动光晕（CustomPaint _OrbPainter）
                drawIntoCanvas { canvas ->
                    val glowPaint = AndroidPaint().apply {
                        isAntiAlias = true
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = radius * 0.5f
                        maskFilter = BlurMaskFilter(
                            5f * density.density,
                            BlurMaskFilter.Blur.NORMAL,
                        )
                        // Flutter colors:
                        // Color.fromARGB(70, 64, 224, 255) -> 0x4640E0FF (青)
                        // Color.fromARGB(56, 154, 120, 255) -> 0x389A78FF (紫)
                        // Color.fromARGB(72, 255, 164, 64) -> 0x48FFA440 (暖橙)
                        val colors = intArrayOf(
                            0x4640E0FF.toInt(),
                            0x389A78FF.toInt(),
                            0x48FFA440.toInt(),
                            0x4640E0FF.toInt(),
                        )
                        val positions = floatArrayOf(0f, 0.333f, 0.666f, 1f)
                        val sweep = AndroidSweepGradient(center.x, center.y, colors, positions)
                        val matrix = Matrix()
                        matrix.postRotate(flowAngle, center.x, center.y)
                        sweep.setLocalMatrix(matrix)
                        shader = sweep
                    }
                    canvas.nativeCanvas.drawCircle(center.x, center.y, radius * 0.82f, glowPaint)
                }

                // 2. 白色高光边缘 (Colors.white, alpha: 0.95, strokeWidth: 1.6dp)
                val whiteEdgeWidth = 1.6.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f),
                    radius = max(1f, radius - 1.4.dp.toPx()),
                    style = Stroke(width = whiteEdgeWidth),
                )

                // 3. 内侧极细次级描边 (Colors.black, alpha: 0.10, strokeWidth: 0.8dp)
                val innerStrokeWidth = 0.8.dp.toPx()
                drawCircle(
                    color = Color.Black.copy(alpha = 0.10f),
                    radius = max(1f, radius - 3.2.dp.toPx()),
                    style = Stroke(width = innerStrokeWidth),
                )
            }
        }
    }
}
