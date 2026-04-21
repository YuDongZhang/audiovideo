package com.audio.video.editor

import com.audio.video.data.model.VideoClip
import java.util.UUID

/**
 * 时间线编辑引擎 — 纯函数实现，无 Android 依赖，方便单元测试
 * 提供分割、裁剪入点/出点、删除、重排序等操作
 */
object TimelineEngine {

    /**
     * 在全局时间线位置分割片段
     * 将包含该位置的片段一分为二，生成新的片段 ID
     */
    fun splitClip(clips: List<VideoClip>, globalPositionMs: Long): List<VideoClip> {
        var accumulated = 0L
        val result = mutableListOf<VideoClip>()
        var orderIndex = 0

        for (clip in clips.sortedBy { it.displayOrder }) {
            val clipDuration = clip.trimmedDurationMs
            if (accumulated + clipDuration > globalPositionMs && accumulated < globalPositionMs) {
                val splitPointInClip = globalPositionMs - accumulated
                val absoluteSplitPoint = clip.startTimeMs + splitPointInClip

                // 前半段：保留原 ID，缩短结束时间
                result.add(
                    clip.copy(
                        endTimeMs = absoluteSplitPoint,
                        displayOrder = orderIndex++
                    )
                )
                // 后半段：生成新 ID，从分割点开始
                result.add(
                    clip.copy(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = absoluteSplitPoint,
                        displayOrder = orderIndex++
                    )
                )
            } else {
                result.add(clip.copy(displayOrder = orderIndex++))
            }
            accumulated += clipDuration
        }
        return result
    }

    /** 调整入点 — 最小保留 100ms 时长 */
    fun trimClipStart(clips: List<VideoClip>, clipId: String, newStartMs: Long): List<VideoClip> {
        return clips.map { clip ->
            if (clip.id == clipId) {
                clip.copy(startTimeMs = newStartMs.coerceIn(0, clip.endTimeMs - 100))
            } else clip
        }
    }

    /** 调整出点 — 不超过原始视频时长，最小保留 100ms */
    fun trimClipEnd(clips: List<VideoClip>, clipId: String, newEndMs: Long): List<VideoClip> {
        return clips.map { clip ->
            if (clip.id == clipId) {
                clip.copy(endTimeMs = newEndMs.coerceIn(clip.startTimeMs + 100, clip.originalDurationMs))
            } else clip
        }
    }

    /** 删除片段并重新排序 */
    fun deleteClip(clips: List<VideoClip>, clipId: String): List<VideoClip> {
        return clips.filter { it.id != clipId }.mapIndexed { index, clip ->
            clip.copy(displayOrder = index)
        }
    }

    /** 调整片段顺序 */
    fun reorderClip(clips: List<VideoClip>, fromIndex: Int, toIndex: Int): List<VideoClip> {
        val mutable = clips.sortedBy { it.displayOrder }.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable.mapIndexed { index, clip -> clip.copy(displayOrder = index) }
    }
}
