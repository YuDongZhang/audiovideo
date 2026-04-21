package com.audio.video.player

/** 播放器状态 — 由 VideoPlayerManager 驱动更新 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L
)
