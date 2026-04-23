package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.SurfaceContainerHigh
import com.audio.video.ui.theme.TextSecondary

/**
 * 淡入淡出控制面板
 *
 * 淡入：片段开头 N 毫秒内音量从 0 线性增长到设定音量
 * 淡出：片段结尾 N 毫秒内音量从设定音量线性衰减到 0
 *
 * 音频包络公式：
 *   淡入 gain(t) = t / fadeInMs           (0 ≤ t ≤ fadeInMs)
 *   淡出 gain(t) = (endTime - t) / fadeOutMs  (endTime - fadeOutMs ≤ t ≤ endTime)
 *   其余区间 gain = 1.0
 */
@Composable
fun FadeControl(
    fadeInMs: Long,
    fadeOutMs: Long,
    maxDurationMs: Long,
    onFadeInChanged: (Long) -> Unit,
    onFadeOutChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxFade = (maxDurationMs / 2).coerceAtMost(5000L) // 最长 5 秒

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 淡入控制
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("淡入", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Slider(
                    value = fadeInMs.toFloat(),
                    onValueChange = { onFadeInChanged(it.toLong()) },
                    valueRange = 0f..maxFade.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = SurfaceContainerHigh
                    )
                )
                Text(
                    text = "${fadeInMs / 1000f}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // 淡出控制
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("淡出", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Slider(
                    value = fadeOutMs.toFloat(),
                    onValueChange = { onFadeOutChanged(it.toLong()) },
                    valueRange = 0f..maxFade.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = SurfaceContainerHigh
                    )
                )
                Text(
                    text = "${fadeOutMs / 1000f}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
