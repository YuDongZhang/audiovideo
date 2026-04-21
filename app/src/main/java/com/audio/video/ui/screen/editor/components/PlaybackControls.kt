package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.TextOnAccent
import com.audio.video.ui.theme.TextSecondary
import com.audio.video.util.formatTimeMs

/**
 * 播放控制栏 — 播放/暂停、跳至起点/终点、时间进度显示
 */
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onPlayPause: () -> Unit,
    onSeekToStart: () -> Unit,
    onSeekToEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onSeekToStart) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "跳至起点",
                    tint = TextSecondary
                )
            }

            // 中央播放/暂停按钮，强调色背景
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = AccentBlue,
                    contentColor = TextOnAccent
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }

            IconButton(onClick = onSeekToEnd) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "跳至终点",
                    tint = TextSecondary
                )
            }
        }

        // 当前时间 / 总时长
        Text(
            text = "${formatTimeMs(currentPositionMs)} / ${formatTimeMs(totalDurationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
