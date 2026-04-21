package com.audio.video.util

/** 格式化为 "M:SS"，用于卡片、片段时长显示 */
fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** 格式化为 "MM:SS.ms"，用于播放控制栏精确时间显示 */
fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, millis)
}
