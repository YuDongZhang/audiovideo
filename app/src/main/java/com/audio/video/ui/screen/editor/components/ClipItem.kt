package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audio.video.data.model.VideoClip
import com.audio.video.ui.theme.EditorColors
import com.audio.video.ui.theme.TextOnAccent
import com.audio.video.ui.theme.TextSecondary
import com.audio.video.util.formatDurationMs

/**
 * 时间线上的单个片段条
 * 背景显示音频波形，中央叠加时长标签
 * 选中时显示左右裁剪手柄
 */
@Composable
fun ClipItem(
    clip: VideoClip,
    widthDp: Dp,
    isSelected: Boolean,
    waveform: FloatArray?,
    onTrimStartDrag: (deltaPx: Float) -> Unit,
    onTrimEndDrag: (deltaPx: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    val bgColor = if (isSelected) EditorColors.ClipSelected.copy(alpha = 0.3f) else EditorColors.ClipDefault
    val borderMod = if (isSelected) Modifier.border(2.dp, EditorColors.ClipSelected, shape) else Modifier

    Box(
        modifier = modifier
            .width(widthDp)
            .height(56.dp)
            .clip(shape)
            .then(borderMod)
            .background(bgColor)
    ) {
        // 音频波形背景
        if (waveform != null && waveform.isNotEmpty()) {
            WaveformView(
                waveform = waveform,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isSelected) 10.dp else 2.dp),
                color = if (isSelected)
                    EditorColors.ClipAudio.copy(alpha = 0.8f)
                else
                    EditorColors.ClipAudio.copy(alpha = 0.5f)
            )
        }

        // 中央时长标签
        Text(
            text = formatDurationMs(clip.trimmedDurationMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) TextOnAccent else TextSecondary,
            modifier = Modifier.align(Alignment.Center)
        )

        // 左侧裁剪手柄 — 仅选中时显示
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(EditorColors.TrimHandle.copy(alpha = 0.8f))
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            onTrimStartDrag(dragAmount)
                        }
                    }
            )
        }

        // 右侧裁剪手柄 — 仅选中时显示
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(EditorColors.TrimHandle.copy(alpha = 0.8f))
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            onTrimEndDrag(dragAmount)
                        }
                    }
            )
        }
    }
}
