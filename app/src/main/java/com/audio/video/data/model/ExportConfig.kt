package com.audio.video.data.model

/** 导出配置 — 分辨率、质量、格式 */
data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val quality: ExportQuality = ExportQuality.HIGH,
    val format: ExportFormat = ExportFormat.MP4
)

enum class ExportResolution(val width: Int, val height: Int, val label: String) {
    RES_480P(854, 480, "480p"),
    RES_720P(1280, 720, "720p"),
    RES_1080P(1920, 1080, "1080p"),
    RES_4K(3840, 2160, "4K")
}

enum class ExportQuality(val label: String, val bitrateMultiplier: Float) {
    LOW("低", 0.5f),
    MEDIUM("中", 1.0f),
    HIGH("高", 2.0f)
}

enum class ExportFormat(val mimeType: String, val extension: String) {
    MP4("video/mp4", "mp4")
}
