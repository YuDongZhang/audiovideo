package com.audio.video.data.model

import java.util.UUID

/** 项目数据模型 — 包含片段列表和基本元信息 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnailUri: String? = null,
    val clips: List<VideoClip> = emptyList(),
    val totalDurationMs: Long = clips.sumOf { it.trimmedDurationMs }
)
