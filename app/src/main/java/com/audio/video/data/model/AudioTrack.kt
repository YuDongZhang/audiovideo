package com.audio.video.data.model

import java.util.UUID

/**
 * 背景音乐轨道 — 独立于视频片段的音频层
 *
 * 音频混合原理（PCM 叠加）：
 *   output[i] = clamp(videoAudio[i] × videoVol + bgmAudio[i] × bgmVol, -32768, 32767)
 *
 * 多轨混合时需要注意：
 *   1. 采样率对齐：如果 BGM 是 44100Hz 而视频是 48000Hz，需要重采样
 *   2. 声道匹配：单声道 BGM 需要复制到双声道
 *   3. 溢出保护：两路叠加后可能超出 16bit 范围，需要 clamp 或 soft-clip
 */
data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val name: String,
    val durationMs: Long,
    val startOffsetMs: Long = 0,
    val volume: Float = 0.5f,
    val isLooping: Boolean = false
)
