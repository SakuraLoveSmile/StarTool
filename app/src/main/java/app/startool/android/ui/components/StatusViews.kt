package app.startool.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import app.startool.android.ui.theme.StarToolColors
import app.startool.android.ui.theme.StarToolDimens
import app.startool.android.ui.theme.StarToolType

/** 加载中：进度 + 文字，不使用假数据。 */
@Composable
fun LoadingView(message: String = "加载中…", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(StarToolDimens.SpaceXl)
            .testTag("loading_view"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceMd),
        ) {
            CircularProgressIndicator(color = StarToolColors.Primary)
            Text(message, style = StarToolType.Body, color = StarToolColors.TextSecondary)
        }
    }
}

/** 错误状态：可理解的原因 + 重试入口，不只是瞬时 Toast。 */
@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(StarToolDimens.SpaceXl)
            .testTag("error_view"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceMd),
        ) {
            Text(
                "出错了",
                style = StarToolType.HourTitle,
                color = StarToolColors.Error,
            )
            Text(
                message,
                style = StarToolType.Body,
                color = StarToolColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .heightIn(min = StarToolDimens.ButtonMinHeight)
                        .testTag("error_retry"),
                ) {
                    Text("重试", style = StarToolType.Body)
                }
            }
        }
    }
}

/** 空态：解释没有记录，可附操作入口。 */
@Composable
fun EmptyView(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(StarToolDimens.SpaceXl)
            .testTag("empty_view"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StarToolDimens.SpaceMd),
        ) {
            Text(
                message,
                style = StarToolType.Body,
                color = StarToolColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .heightIn(min = StarToolDimens.ButtonMinHeight)
                        .testTag("empty_action"),
                ) {
                    Text(actionLabel, style = StarToolType.Body)
                }
            }
        }
    }
}
