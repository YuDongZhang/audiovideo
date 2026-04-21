package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.EditorColors

/**
 * 时间刻度尺 — 同时承担播放头定位的交互入口
 *
 * 点击刻度尺任意位置 → 播放头跳转到该处。
 * 在刻度尺上水平拖拽 → 播放头跟随手指移动（搓碟效果）。
 * 这样与下方片段区域的"点击选中片段"互不冲突。
 */
@Composable
fun TimeRuler(
    totalDurationMs: Long,
    pxPerMs: Float,
    scrollOffsetPx: Float,
    onSeekTo: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp) // 加高到 36dp，增大可触摸区域
            .then(
                if (onSeekTo != null) {
                    Modifier
                        // 点击定位
                        .pointerInput(pxPerMs, scrollOffsetPx, totalDurationMs) {
                            detectTapGestures { offset ->
                                val tappedMs = ((scrollOffsetPx + offset.x) / pxPerMs).toLong()
                                onSeekTo(tappedMs.coerceIn(0, totalDurationMs))
                            }
                        }
                        // 拖拽搓碟
                        .pointerInput(pxPerMs, scrollOffsetPx, totalDurationMs) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val dragMs = ((scrollOffsetPx + change.position.x) / pxPerMs).toLong()
                                onSeekTo(dragMs.coerceIn(0, totalDurationMs))
                            }
                        }
                } else Modifier
            )
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
                val tickHeight = if (isMajor) size.height * 0.5f else size.height * 0.25f

                drawLine(
                    color = rulerColor,
                    start = Offset(x, size.height),
                    end = Offset(x, size.height - tickHeight),
                    strokeWidth = if (isMajor) 1.5f else 1f
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
                            textSize = 22f
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
