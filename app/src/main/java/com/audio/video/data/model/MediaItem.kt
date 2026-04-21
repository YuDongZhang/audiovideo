package com.audio.video.data.model

import android.net.Uri

/** 设备媒体项 — 从 MediaStore 查询到的视频元信息 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val mimeType: String
)
