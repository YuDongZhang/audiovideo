package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.audio.video.ui.theme.EditorBlack

/**
 * 视频预览组件 — 通过 AndroidView 嵌入 Media3 PlayerView
 * 隐藏默认控制器，由外部 PlaybackControls 控制播放
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
    player: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(EditorBlack)
    ) {
        if (player != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    view.player = player
                }
            )
        }
    }
}
