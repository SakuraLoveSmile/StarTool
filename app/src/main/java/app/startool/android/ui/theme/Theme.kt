package app.startool.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** 冻结的视觉令牌（计划 §7.1）。 */
object StarToolColors {
    val Background = Color(0xFFFAF8F3)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF24272B)
    val TextSecondary = Color(0xFF62666D)
    /** 主操作 / 精神状态 */
    val Primary = Color(0xFF6554A4)
    /** 身体状态 */
    val Physical = Color(0xFF237A68)
    val Error = Color(0xFFB3261E)
    val CardBorder = Color(0xFFDDDAD3)
}

private val StarToolColorScheme = lightColorScheme(
    primary = StarToolColors.Primary,
    onPrimary = Color.White,
    secondary = StarToolColors.Physical,
    onSecondary = Color.White,
    background = StarToolColors.Background,
    onBackground = StarToolColors.TextPrimary,
    surface = StarToolColors.Surface,
    onSurface = StarToolColors.TextPrimary,
    surfaceVariant = StarToolColors.Background,
    onSurfaceVariant = StarToolColors.TextSecondary,
    error = StarToolColors.Error,
    onError = Color.White,
    outline = StarToolColors.CardBorder,
)

/** 冻结的字体规格（计划 §7.1），跟随系统字体缩放。 */
object StarToolType {
    val PageTitle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp)
    val HourTitle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp)
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val Caption = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
}

private val StarToolTypography = Typography(
    headlineMedium = StarToolType.PageTitle,
    titleLarge = StarToolType.HourTitle,
    bodyLarge = StarToolType.Body,
    bodyMedium = StarToolType.Caption,
)

@Composable
fun StarToolTheme(content: @Composable () -> Unit) {
    // 首版固定浅色主题，不跟随系统深色。
    MaterialTheme(
        colorScheme = StarToolColorScheme,
        typography = StarToolTypography,
        content = content,
    )
}
