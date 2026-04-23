package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.data.model.TransitionEffect
import com.audio.video.data.model.TransitionType
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.SurfaceContainerHigh
import com.audio.video.ui.theme.TextSecondary

/**
 * 转场选择器 — 选择转场类型和时长
 *
 * 交叉溶解 (Crossfade)：
 *   GPU 同时渲染两个纹理，Fragment Shader 输出:
 *   gl_FragColor = texA * (1.0 - progress) + texB * progress
 *
 * 渐黑 (Fade to Black)：
 *   前半段: texA * (1.0 - progress*2)  → 渐暗到黑
 *   后半段: texB * (progress*2 - 1.0)  → 从黑渐亮
 */
@Composable
fun TransitionSelector(
    transition: TransitionEffect,
    onTransitionChanged: (TransitionEffect) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 转场类型选择
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("转场", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                TransitionType.entries.forEach { type ->
                    FilterChip(
                        selected = transition.type == type,
                        onClick = {
                            onTransitionChanged(transition.copy(type = type))
                        },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = AccentBlue
                        )
                    )
                }
            }

            // 转场时长滑块（仅非 NONE 时显示）
            if (transition.type != TransitionType.NONE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("时长", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = transition.durationMs.toFloat(),
                        onValueChange = {
                            onTransitionChanged(transition.copy(durationMs = it.toLong()))
                        },
                        valueRange = 200f..2000f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentBlue,
                            activeTrackColor = AccentBlue,
                            inactiveTrackColor = SurfaceContainerHigh
                        )
                    )
                    Text(
                        text = "${transition.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
