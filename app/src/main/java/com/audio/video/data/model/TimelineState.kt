package com.audio.video.data.model

/**
 * 时间线状态 — 驱动时间线 UI 的完整快照
 *
 * transitions: 转场效果映射，key = 后一个片段的 clipId
 *   例如 transitions["clipB"] = CrossFade(500ms) 表示 clipA → clipB 之间有 500ms 交叉溶解
 */
data class TimelineState(
    val clips: List<VideoClip> = emptyList(),
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = clips.sumOf { it.trimmedDurationMs },
    val zoomLevel: Float = 1.0f,
    val scrollOffsetPx: Float = 0f,
    val selectedClipId: String? = null,
    val isPlaying: Boolean = false,
    val transitions: Map<String, TransitionEffect> = emptyMap()
)
