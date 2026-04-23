package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.data.model.TextOverlay
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.TextSecondary
import com.audio.video.util.formatTimeMs

/**
 * 文字贴纸编辑器
 *
 * 帧级文字叠加原理：
 *   每帧渲染时检查 currentTimeMs 是否在 overlay.startTimeMs ~ endTimeMs 范围内
 *   若在范围内，用 Canvas.drawText() 在指定位置绘制文字
 *   文字先渲染为 Bitmap → 上传为 GL 纹理 → Fragment Shader 混合叠加到视频帧
 */
@Composable
fun TextOverlayEditor(
    overlays: List<TextOverlay>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onAddOverlay: (TextOverlay) -> Unit,
    onRemoveOverlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 输入新文字
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("输入文字...", color = TextSecondary) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        cursorColor = AccentBlue
                    )
                )
                IconButton(onClick = {
                    if (inputText.isNotBlank()) {
                        onAddOverlay(
                            TextOverlay(
                                text = inputText,
                                startTimeMs = currentPositionMs,
                                endTimeMs = (currentPositionMs + 3000).coerceAtMost(totalDurationMs)
                            )
                        )
                        inputText = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "添加文字", tint = AccentBlue)
                }
            }

            // 已添加的文字列表
            overlays.forEach { overlay ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = overlay.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatTimeMs(overlay.startTimeMs)} - ${formatTimeMs(overlay.endTimeMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = { onRemoveOverlay(overlay.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = TextSecondary)
                    }
                }
            }
        }
    }
}
