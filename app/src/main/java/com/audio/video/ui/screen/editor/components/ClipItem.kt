package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
 * 不处理点击事件 — 由 Timeline 统一处理触摸（定位播放头 + 自动选中片段）
 * 仅左右裁剪手柄处理拖拽
 */
@Composable
fun ClipItem(
    clip: VideoClip,
    widthDp: Dp,
    isSelected: Boolean,
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
        // 左侧裁剪手柄 — 仅选中时显示，拖拽调整入点
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

        // 中央时长标签
        Text(
            text = formatDurationMs(clip.trimmedDurationMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) TextOnAccent else TextSecondary,
            modifier = Modifier.align(Alignment.Center)
        )

        // 右侧裁剪手柄 — 仅选中时显示，拖拽调整出点
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
