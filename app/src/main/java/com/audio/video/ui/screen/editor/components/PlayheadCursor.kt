package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.EditorColors

/**
 * 播放头光标 — 醒目的蓝色竖线 + 顶部圆角矩形手柄
 * 手柄宽 16dp，方便触摸拖拽；竖线宽 3dp，视觉突出
 */
@Composable
fun PlayheadCursor(
    positionPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val offsetDp = with(density) { positionPx.toDp() }

    Canvas(
        modifier = modifier
            .offset(x = offsetDp - 8.dp) // 居中偏移（手柄宽度的一半）
            .width(16.dp)
            .fillMaxHeight()
    ) {
        val color = EditorColors.Playhead
        val centerX = size.width / 2

        // 顶部圆角矩形手柄（16dp × 20dp），方便拖拽
        val handleWidth = size.width
        val handleHeight = 20.dp.toPx()
        val handleCorner = 4.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(handleWidth, handleHeight),
            cornerRadius = CornerRadius(handleCorner, handleCorner)
        )

        // 手柄下方三角箭头指向竖线
        val arrowSize = 6.dp.toPx()
        val arrowPath = Path().apply {
            moveTo(centerX - arrowSize, handleHeight)
            lineTo(centerX + arrowSize, handleHeight)
            lineTo(centerX, handleHeight + arrowSize)
            close()
        }
        drawPath(arrowPath, color)

        // 竖线（3dp 宽），从箭头底部延伸到组件底部
        val lineWidth = 3.dp.toPx()
        drawLine(
            color = color,
            start = Offset(centerX, handleHeight + arrowSize),
            end = Offset(centerX, size.height),
            strokeWidth = lineWidth
        )
    }
}
