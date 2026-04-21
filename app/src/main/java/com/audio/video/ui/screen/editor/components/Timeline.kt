package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.audio.video.data.model.TimelineState
import com.audio.video.ui.theme.EditorColors

/**
 * 时间线组件 — 可水平滚动的片段轨道
 * 包含时间刻度尺、片段条和播放头，播放时自动跟随滚动
 */
@Composable
fun Timeline(
    state: TimelineState,
    onClipSelected: (String) -> Unit,
    onTrimStartDrag: (clipId: String, deltaPx: Float) -> Unit,
    onTrimEndDrag: (clipId: String, deltaPx: Float) -> Unit,
    onSeekTo: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // 基础像素/毫秒比率，乘以缩放系数得到当前比率
    val basePxPerMs = with(density) { 0.15f * density.density }
    val pxPerMs = basePxPerMs * state.zoomLevel
    val totalWidthPx = state.totalDurationMs * pxPerMs
    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val scrollState = rememberScrollState()

    // 播放中时自动滚动，使播放头保持在视口中央
    LaunchedEffect(state.currentPositionMs, state.isPlaying) {
        if (state.isPlaying) {
            val playheadPx = (state.currentPositionMs * pxPerMs).toInt()
            val viewportCenter = scrollState.viewportSize / 2
            val targetScroll = (playheadPx - viewportCenter).coerceAtLeast(0)
            scrollState.scrollTo(targetScroll)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 顶部时间刻度尺
        TimeRuler(
            totalDurationMs = state.totalDurationMs,
            pxPerMs = pxPerMs,
            scrollOffsetPx = scrollState.value.toFloat()
        )

        // 片段轨道区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(EditorColors.TimelineTrackBg)
                .horizontalScroll(scrollState)
                .pointerInput(Unit) {
                    // 点击轨道空白区域跳转播放位置
                    detectTapGestures { offset ->
                        val tappedMs = ((scrollState.value + offset.x) / pxPerMs).toLong()
                        onSeekTo(tappedMs.coerceIn(0, state.totalDurationMs))
                    }
                }
        ) {
            // 按顺序排列片段
            Row(modifier = Modifier.width(totalWidthDp)) {
                state.clips.sortedBy { it.displayOrder }.forEach { clip ->
                    val clipWidthDp = with(density) {
                        (clip.trimmedDurationMs * pxPerMs).toDp()
                    }
                    ClipItem(
                        clip = clip,
                        widthDp = clipWidthDp,
                        isSelected = clip.id == state.selectedClipId,
                        pxPerMs = pxPerMs,
                        onClick = { onClipSelected(clip.id) },
                        onTrimStartDrag = { delta -> onTrimStartDrag(clip.id, delta) },
                        onTrimEndDrag = { delta -> onTrimEndDrag(clip.id, delta) }
                    )
                }
            }

            // 播放头光标叠加层
            val playheadPx = state.currentPositionMs * pxPerMs - scrollState.value
            if (playheadPx >= 0) {
                PlayheadCursor(positionPx = playheadPx)
            }
        }
    }
}
