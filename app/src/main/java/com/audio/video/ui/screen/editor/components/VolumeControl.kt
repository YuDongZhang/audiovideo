package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * 音量控制面板 — 选中片段时在工具栏上方显示
 *
 * 包含：静音按钮 + 音量滑块（0% ~ 200%）+ 百分比文字
 * volume 范围：0.0（静音）~ 2.0（增益2倍）
 */
@Composable
fun VolumeControl(
    volume: Float,
    onVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 静音切换按钮
            IconButton(onClick = onToggleMute, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (volume == 0f) "取消静音" else "静音",
                    tint = if (volume == 0f) TextSecondary else AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 音量滑块：0.0 ~ 2.0
            Slider(
                value = volume,
                onValueChange = onVolumeChanged,
                valueRange = 0f..2f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = SurfaceContainerHigh
                )
            )

            // 百分比显示
            Text(
                text = "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
