package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.EditorColors

/**
 * 时间刻度尺 — 在时间线顶部绘制时间标记
 * 根据缩放级别自动调整刻度间距（1s/5s/10s/30s）
 */
@Composable
fun TimeRuler(
    totalDurationMs: Long,
    pxPerMs: Float,
    scrollOffsetPx: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val rulerColor = EditorColors.TimelineRuler
        val textColor = EditorColors.TimelineRulerText

        val viewWidthPx = size.width
        // 根据像素密度选择合适的刻度间距
        val intervalMs = when {
            pxPerMs > 0.5f -> 1000L
            pxPerMs > 0.1f -> 5000L
            pxPerMs > 0.02f -> 10000L
            else -> 30000L
        }

        val startMs = (scrollOffsetPx / pxPerMs).toLong().coerceAtLeast(0)
        val endMs = ((scrollOffsetPx + viewWidthPx) / pxPerMs).toLong()
            .coerceAtMost(totalDurationMs)

        val firstTick = (startMs / intervalMs) * intervalMs
        var tickMs = firstTick

        while (tickMs <= endMs) {
            val x = tickMs * pxPerMs - scrollOffsetPx
            if (x >= 0 && x <= viewWidthPx) {
                // 主刻度（每5个间隔）比普通刻度高
                val isMajor = tickMs % (intervalMs * 5) == 0L
                val tickHeight = if (isMajor) size.height * 0.6f else size.height * 0.3f

                drawLine(
                    color = rulerColor,
                    start = Offset(x, size.height),
                    end = Offset(x, size.height - tickHeight),
                    strokeWidth = 1f
                )

                // 在主刻度处绘制时间文字
                if (isMajor || intervalMs >= 5000L) {
                    val seconds = tickMs / 1000
                    val min = seconds / 60
                    val sec = seconds % 60
                    val label = "%d:%02d".format(min, sec)

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x,
                        size.height * 0.35f,
                        android.graphics.Paint().apply {
                            color = textColor.hashCode()
                            textSize = 20f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }
            }
            tickMs += intervalMs
        }
    }
}
