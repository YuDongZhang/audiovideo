package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.audio.video.ui.theme.EditorColors

/**
 * 音频波形绘制组件
 *
 * 将归一化的波形数据（0.0~1.0）绘制为居中对称的竖线条
 * 每个数据点画一条从中线向上下延伸的线，高度 = amplitude × 可用高度的一半
 */
@Composable
fun WaveformView(
    waveform: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = EditorColors.ClipAudio
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (waveform.isEmpty()) return@Canvas

        val barCount = waveform.size
        val barWidth = size.width / barCount
        val centerY = size.height / 2
        val maxAmplitude = size.height * 0.4f // 上下各占 40%，留 10% 边距

        for (i in waveform.indices) {
            val amplitude = waveform[i] * maxAmplitude
            val x = i * barWidth + barWidth / 2

            // 从中线向上下对称绘制
            drawLine(
                color = color,
                start = Offset(x, centerY - amplitude),
                end = Offset(x, centerY + amplitude),
                strokeWidth = (barWidth * 0.6f).coerceAtLeast(1f)
            )
        }
    }
}
