package com.audio.video.ui.screen.spectrum

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.audio.video.ui.theme.AccentBlue

/**
 * 音频频谱可视化组件 — 柱状图模式
 *
 * 将 FFT 输出的频段数据绘制为垂直柱状条：
 *   每根柱子 = 一个频段的幅度
 *   从左到右 = 低频 → 高频
 *   柱子高度 = 该频段的能量强度
 *
 * 颜色渐变：低频蓝色 → 中频青色 → 高频紫色
 */
@Composable
fun SpectrumBarView(
    bands: FloatArray,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (bands.isEmpty()) return@Canvas

        val barCount = bands.size
        val totalWidth = size.width
        val barWidth = totalWidth / barCount * 0.75f
        val gap = totalWidth / barCount * 0.25f
        val maxHeight = size.height * 0.9f

        for (i in bands.indices) {
            val amplitude = bands[i].coerceIn(0f, 1f)
            val barHeight = amplitude * maxHeight
            val x = i * (barWidth + gap) + gap / 2

            // 颜色渐变：低频蓝 → 中频青 → 高频紫
            val ratio = i.toFloat() / barCount
            val color = when {
                ratio < 0.33f -> lerp(Color(0xFF4A90FF), Color(0xFF00BCD4), ratio * 3)
                ratio < 0.66f -> lerp(Color(0xFF00BCD4), Color(0xFFAB47BC), (ratio - 0.33f) * 3)
                else -> lerp(Color(0xFFAB47BC), Color(0xFFFF4081), (ratio - 0.66f) * 3)
            }

            // 从底部向上绘制圆角矩形柱子
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

/** 线性颜色插值 */
private fun lerp(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = 1f
    )
}
