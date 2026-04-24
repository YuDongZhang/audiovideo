package com.audio.video.ui.screen.ffmpeglab.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.audio.video.ffmpeg.FFmpegCommand
import com.audio.video.ui.screen.ffmpeglab.CommandState
import com.audio.video.ui.screen.ffmpeglab.CommandStatus
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.EditorColors
import com.audio.video.ui.theme.ErrorRed
import com.audio.video.ui.theme.SuccessGreen
import com.audio.video.ui.theme.SurfaceContainerHigh
import com.audio.video.ui.theme.TextSecondary

/**
 * FFmpeg 命令卡片 — 展示命令名称、标签、执行按钮、结果
 * 点击展开显示完整命令文本和知识点说明
 */
@Composable
fun CommandCard(
    command: FFmpegCommand,
    state: CommandState?,
    isExpanded: Boolean,
    hasVideo: Boolean,
    onToggleExpand: () -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题行：名称 + 标签 + 执行按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = command.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        command.tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentBlue,
                                modifier = Modifier
                                    .background(
                                        AccentBlue.copy(alpha = 0.1f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 执行按钮 / 状态图标
                when (state?.status) {
                    CommandStatus.RUNNING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentBlue,
                            strokeWidth = 2.dp
                        )
                    }
                    CommandStatus.SUCCESS -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "成功",
                            tint = SuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    CommandStatus.FAILED -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "失败",
                            tint = ErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        IconButton(
                            onClick = onExecute,
                            enabled = hasVideo
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "执行",
                                tint = if (hasVideo) AccentBlue else TextSecondary
                            )
                        }
                    }
                }
            }

            // 耗时信息
            if (state?.status == CommandStatus.SUCCESS && state.result != null) {
                Text(
                    text = "耗时 ${state.result.duration}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 展开区：命令文本 + 详细说明 + 输出结果
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 实际执行的命令
                    if (state?.actualCommand?.isNotEmpty() == true) {
                        Text(
                            text = "$ ${state.actualCommand}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = AccentBlue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceContainerHigh, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState())
                        )
                    }

                    // 知识点说明
                    Text(
                        text = command.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                EditorColors.TimelineTrackBg,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    )

                    // probe 输出结果
                    if (state?.probeOutput?.isNotEmpty() == true) {
                        Text(
                            text = state.probeOutput,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceContainerHigh, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}
