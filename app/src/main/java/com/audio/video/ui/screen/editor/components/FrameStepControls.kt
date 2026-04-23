package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.TextSecondary

/**
 * 逐帧控制按钮 — 前进/后退一帧
 *
 * 逐帧 seek 原理：
 *   ExoPlayer.seekTo(currentPosition ± frameDurationMs)
 *   frameDurationMs = 1000 / frameRate (30fps → 33ms)
 *
 * seek 到非关键帧的代价：
 *   播放器必须从前一个关键帧开始解码所有中间帧
 *   例如 GOP=30帧，seek 到第25帧 → 需要解码25帧才能显示
 *   这就是"seek 延迟"的根本原因
 */
@Composable
fun FrameStepControls(
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onStepBackward, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "上一帧",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "逐帧",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        IconButton(onClick = onStepForward, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "下一帧",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
