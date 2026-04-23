package com.audio.video.data.model

import java.util.UUID

/**
 * 视频片段 — 记录源视频 URI、入点/出点、音量、滤镜、速度和在时间线上的位置
 *
 * volume: 音量系数，0.0=静音，1.0=原始音量，2.0=增益2倍
 * fadeInMs / fadeOutMs: 淡入淡出时长（毫秒），0=不使用
 * filterType: 视频滤镜类型
 * speed: 播放速度倍率，0.25x~4.0x
 */
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long,
    val originalDurationMs: Long,
    val displayOrder: Int,
    val volume: Float = 1.0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val filterType: VideoFilterType = VideoFilterType.NONE,
    val speed: Float = 1.0f
) {
    val trimmedDurationMs: Long get() = endTimeMs - startTimeMs
    val isMuted: Boolean get() = volume == 0f
}

/** 视频滤镜类型枚举 */
enum class VideoFilterType(val label: String) {
    NONE("原片"),
    GRAYSCALE("黑白"),
    WARM("暖色"),
    COOL("冷色"),
    VINTAGE("复古"),
    VIVID("鲜艳"),
    INVERT("反色")
}
