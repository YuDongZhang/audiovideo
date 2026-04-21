package com.audio.video.data.model

/** 时间线状态 — 驱动时间线 UI 的完整快照 */
data class TimelineState(
    val clips: List<VideoClip> = emptyList(),
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = clips.sumOf { it.trimmedDurationMs },
    val zoomLevel: Float = 1.0f,
    val scrollOffsetPx: Float = 0f,
    val selectedClipId: String? = null,
    val isPlaying: Boolean = false
)
