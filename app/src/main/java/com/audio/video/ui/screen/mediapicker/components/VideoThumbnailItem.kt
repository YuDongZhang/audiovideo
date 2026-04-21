package com.audio.video.ui.screen.mediapicker.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.audio.video.data.model.MediaItem
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.SurfaceContainerHigh
import com.audio.video.ui.theme.TextOnAccent
import com.audio.video.util.formatDurationMs

/**
 * 视频缩略图网格项 — 展示首帧画面、时长、选中状态
 */
@Composable
fun VideoThumbnailItem(
    mediaItem: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (isSelected) Modifier.border(2.dp, AccentBlue, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        // 通过 Coil 的 VideoFrameDecoder 解码视频首帧作为缩略图
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(mediaItem.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = mediaItem.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 右下角时长标签
        Text(
            text = formatDurationMs(mediaItem.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = TextOnAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(
                    color = SurfaceContainerHigh.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // 右上角选中图标
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已选中",
                tint = AccentBlue,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
            )
        }
    }
}
