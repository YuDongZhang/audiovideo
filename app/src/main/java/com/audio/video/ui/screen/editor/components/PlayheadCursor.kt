package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.EditorColors

/** 播放头光标 — 时间线上的蓝色竖线 + 顶部三角指示器 */
@Composable
fun PlayheadCursor(
    positionPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val offsetDp = with(density) { positionPx.toDp() }

    Canvas(
        modifier = modifier
            .offset(x = offsetDp)
            .width(2.dp)
            .fillMaxHeight()
    ) {
        val color = EditorColors.Playhead

        // Triangle at top
        val triangleSize = 8.dp.toPx()
        val trianglePath = Path().apply {
            moveTo(size.width / 2 - triangleSize, 0f)
            lineTo(size.width / 2 + triangleSize, 0f)
            lineTo(size.width / 2, triangleSize)
            close()
        }
        drawPath(trianglePath, color)

        // Vertical line
        drawLine(
            color = color,
            start = Offset(size.width / 2, triangleSize),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}
