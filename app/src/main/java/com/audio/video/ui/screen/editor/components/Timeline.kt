package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.audio.video.data.model.VideoClip
import com.audio.video.ui.theme.EditorColors

/**
 * 时间线组件 — 统一触摸交互
 *
 * 核心交互逻辑（和剪映一致）：
 * - 点击任意位置 → 播放头跳到该处 + 自动选中所在片段
 * - 水平拖拽 → 播放头跟随手指移动（搓碟）
 * - 双指捏合 → 缩放时间线
 * - 裁剪手柄 → 仅选中片段的左右边缘可拖拽裁剪
 *
 * 不存在手势冲突：点击永远定位播放头，片段选中由播放头位置自动决定。
 */
@Composable
fun Timeline(
    state: TimelineState,
    onClipSelected: (String) -> Unit,
    onTrimStartDrag: (clipId: String, deltaPx: Float) -> Unit,
    onTrimEndDrag: (clipId: String, deltaPx: Float) -> Unit,
    onSeekTo: (positionMs: Long) -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerMaxWidth = maxWidth
        val availableWidthPx = with(density) { containerMaxWidth.toPx() }

        val fitPxPerMs = if (state.totalDurationMs > 0) {
            availableWidthPx / state.totalDurationMs
        } else {
            with(density) { 0.15f * density.density }
        }

        val pxPerMs = fitPxPerMs * state.zoomLevel
        val totalWidthPx = state.totalDurationMs * pxPerMs
        val totalWidthDp = with(density) { totalWidthPx.toDp() }
        val needsScroll = totalWidthPx > availableWidthPx
        val scrollOffsetPx = if (needsScroll) scrollState.value.toFloat() else 0f

        // 播放时自动滚动
        LaunchedEffect(state.currentPositionMs, state.isPlaying, pxPerMs) {
            if (state.isPlaying && needsScroll) {
                val playheadPx = (state.currentPositionMs * pxPerMs).toInt()
                val viewportCenter = scrollState.viewportSize / 2
                val targetScroll = (playheadPx - viewportCenter).coerceAtLeast(0)
                scrollState.scrollTo(targetScroll)
            }
        }

        /** 将屏幕像素 x 坐标转换为时间线毫秒位置 */
        fun pxToMs(screenX: Float): Long {
            return ((scrollOffsetPx + screenX) / pxPerMs).toLong()
                .coerceIn(0, state.totalDurationMs)
        }

        /** 根据全局时间位置找到所在片段并选中 */
        fun selectClipAtPosition(positionMs: Long) {
            var accumulated = 0L
            for (clip in state.clips.sortedBy { it.displayOrder }) {
                accumulated += clip.trimmedDurationMs
                if (positionMs < accumulated) {
                    onClipSelected(clip.id)
                    return
                }
            }
            // 如果在最后一个片段之后，选中最后一个
            state.clips.maxByOrNull { it.displayOrder }?.let { onClipSelected(it.id) }
        }

        Column {
            // ===== 刻度尺 + 播放头定位区 =====
            // 高度 36dp，点击/拖拽 → 定位播放头 + 选中片段
            TimeRuler(
                totalDurationMs = state.totalDurationMs,
                pxPerMs = pxPerMs,
                scrollOffsetPx = scrollOffsetPx,
                onSeekTo = { ms ->
                    onSeekTo(ms)
                    selectClipAtPosition(ms)
                }
            )

            // ===== 片段轨道区域 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(EditorColors.TimelineTrackBg)
                    // 统一触摸处理：点击定位 + 拖拽搓碟 + 双指缩放
                    .pointerInput(pxPerMs, scrollOffsetPx, state.totalDurationMs) {
                        detectTapGestures { offset ->
                            val ms = pxToMs(offset.x)
                            onSeekTo(ms)
                            selectClipAtPosition(ms)
                        }
                    }
                    .pointerInput(pxPerMs, scrollOffsetPx, state.totalDurationMs) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val ms = pxToMs(change.position.x)
                            onSeekTo(ms)
                            selectClipAtPosition(ms)
                        }
                    }
                    .pointerInput(state.zoomLevel) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1.0f) {
                                        val newZoom = (state.zoomLevel * zoom).coerceIn(0.5f, 8.0f)
                                        onZoomChanged(newZoom)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .then(
                        if (needsScroll) Modifier.horizontalScroll(scrollState)
                        else Modifier
                    )
            ) {
                // 片段条排列（不处理点击，由上层统一管理）
                Row(
                    modifier = Modifier
                        .then(
                            if (needsScroll) Modifier.width(totalWidthDp)
                            else Modifier.fillMaxWidth()
                        )
                        .fillMaxHeight()
                ) {
                    state.clips.sortedBy { it.displayOrder }.forEach { clip ->
                        val clipWidthDp = if (needsScroll) {
                            with(density) { (clip.trimmedDurationMs * pxPerMs).toDp() }
                        } else {
                            val ratio = if (state.totalDurationMs > 0) {
                                clip.trimmedDurationMs.toFloat() / state.totalDurationMs
                            } else 1f
                            containerMaxWidth * ratio
                        }

                        ClipItem(
                            clip = clip,
                            widthDp = clipWidthDp,
                            isSelected = clip.id == state.selectedClipId,
                            onTrimStartDrag = { delta -> onTrimStartDrag(clip.id, delta) },
                            onTrimEndDrag = { delta -> onTrimEndDrag(clip.id, delta) }
                        )
                    }
                }

                // 播放头光标
                val playheadPx = state.currentPositionMs * pxPerMs - scrollOffsetPx
                if (playheadPx in -16f..availableWidthPx + 16f) {
                    PlayheadCursor(positionPx = playheadPx)
                }
            }
        }
    }
}
