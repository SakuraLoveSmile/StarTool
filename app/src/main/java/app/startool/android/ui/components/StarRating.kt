package app.startool.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.startool.android.domain.HourlyEntry

/**
 * 十颗小星的静态展示：实心数对应分数。
 * 纯装饰（不接受点击）；无障碍语义由调用方提供，
 * 内部用 clearAndSetSemantics 让星星不被读屏重复朗读。
 */
@Composable
fun StarRating(
    score: Int,
    filledColor: Color,
    modifier: Modifier = Modifier,
    emptyColor: Color = Color(0xFFC9C5BB),
    starSize: Int = 14,
) {
    require(score in HourlyEntry.SCORE_MIN..HourlyEntry.SCORE_MAX) { "score out of range" }
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (i in 1..10) {
            Icon(
                imageVector = if (i <= score) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (i <= score) filledColor else emptyColor,
                modifier = Modifier.size(starSize.dp),
            )
        }
    }
}
