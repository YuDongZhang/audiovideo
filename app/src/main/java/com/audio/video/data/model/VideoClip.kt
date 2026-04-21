package com.audio.video.data.model

import java.util.UUID

/** 视频片段 — 记录源视频 URI、入点/出点和在时间线上的位置 */
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long,
    val originalDurationMs: Long,
    val displayOrder: Int
) {
    val trimmedDurationMs: Long get() = endTimeMs - startTimeMs
}
