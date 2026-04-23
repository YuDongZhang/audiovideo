package com.audio.video.editor

import com.audio.video.data.model.VideoClip

/**
 * 音频淡入淡出增益计算器
 *
 * 淡入：片段入点后 fadeInMs 毫秒内，增益从 0 线性增长到 1
 * 淡出：片段出点前 fadeOutMs 毫秒内，增益从 1 线性衰减到 0
 * 最终音量 = clip.volume × fadeGain
 *
 * 时间域音频包络（envelope）：
 *   gain(t) = 1.0  （正常区间）
 *   gain(t) = t / fadeInDuration  （淡入区间，t 从片段起点算起）
 *   gain(t) = (duration - t) / fadeOutDuration  （淡出区间）
 */
object AudioFadeCalculator {

    /**
     * 计算指定时间位置的淡入淡出增益
     *
     * @param clip 当前片段
     * @param positionInClipMs 在片段内的偏移量（0 = 片段起点）
     * @return 增益系数 [0.0, 1.0]，乘以 clip.volume 得到最终音量
     */
    fun calculateFadeGain(clip: VideoClip, positionInClipMs: Long): Float {
        val duration = clip.trimmedDurationMs
        if (duration <= 0) return 1f

        var gain = 1f

        // 淡入区间
        if (clip.fadeInMs > 0 && positionInClipMs < clip.fadeInMs) {
            gain *= (positionInClipMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f)
        }

        // 淡出区间
        if (clip.fadeOutMs > 0 && positionInClipMs > duration - clip.fadeOutMs) {
            val remaining = duration - positionInClipMs
            gain *= (remaining.toFloat() / clip.fadeOutMs).coerceIn(0f, 1f)
        }

        return gain
    }

    /**
     * 计算指定全局时间位置的最终音量
     *
     * @param clips 所有片段
     * @param globalPositionMs 全局时间线位置
     * @return 最终音量 = clip.volume × fadeGain
     */
    fun calculateVolumeAtPosition(clips: List<VideoClip>, globalPositionMs: Long): Float {
        var accumulated = 0L
        for (clip in clips.sortedBy { it.displayOrder }) {
            val clipDuration = clip.trimmedDurationMs
            if (globalPositionMs < accumulated + clipDuration) {
                val positionInClip = globalPositionMs - accumulated
                val fadeGain = calculateFadeGain(clip, positionInClip)
                return clip.volume * fadeGain
            }
            accumulated += clipDuration
        }
        return 1f
    }
}
