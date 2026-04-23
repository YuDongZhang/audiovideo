package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.TextSecondary

/**
 * 变速选择器 — 预设速度档位
 *
 * 变速原理：
 * - 视频：修改 PTS 时间戳间隔（2x = 间隔减半 → 快放）
 * - 音频：时间拉伸算法（Sonic）保持音调不变
 *   快放：压缩音频时长，音调不升高
 *   慢放：拉伸音频时长，音调不降低
 */
@Composable
fun SpeedSelector(
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "速度",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(end = 4.dp)
            )
            presets.forEach { preset ->
                val isSelected = kotlin.math.abs(speed - preset) < 0.01f
                FilterChip(
                    selected = isSelected,
                    onClick = { onSpeedChanged(preset) },
                    label = {
                        Text(
                            text = "${preset}x",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
            }
        }
    }
}
