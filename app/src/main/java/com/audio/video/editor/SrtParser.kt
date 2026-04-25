package com.audio.video.editor

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * SRT 字幕文件解析器
 *
 * SRT (SubRip Text) 是最简单也最普及的字幕格式。
 *
 * 文件格式：
 *   1                              ← 序号
 *   00:00:01,000 --> 00:00:04,500  ← 时间范围（起始 --> 结束）
 *   Hello World                    ← 字幕文本（可多行）
 *                                  ← 空行分隔
 *   2
 *   00:00:05,000 --> 00:00:08,000
 *   This is subtitle number two
 *
 * 时间格式：HH:MM:SS,mmm（小时:分:秒,毫秒）
 *
 * 与视频同步：
 *   播放时每帧检查 currentTimeMs 是否在某条字幕的时间范围内
 *   if (currentTime in subtitle.startMs..subtitle.endMs) → 显示该字幕
 */
object SrtParser {

    /**
     * 解析 SRT 文件内容
     * @return 字幕条目列表，按起始时间排序
     */
    fun parse(inputStream: InputStream): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines = reader.readLines()
        reader.close()

        var i = 0
        while (i < lines.size) {
            // 跳过空行
            if (lines[i].isBlank()) { i++; continue }

            // 第一行：序号（跳过）
            val indexLine = lines[i].trim()
            if (indexLine.toIntOrNull() == null) { i++; continue }
            i++

            // 第二行：时间范围
            if (i >= lines.size) break
            val timeLine = lines[i].trim()
            i++
            val timeRange = parseTimeLine(timeLine) ?: continue

            // 后续行：字幕文本（直到空行或文件末尾）
            val textLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                textLines.add(lines[i].trim())
                i++
            }

            if (textLines.isNotEmpty()) {
                entries.add(
                    SubtitleEntry(
                        index = entries.size + 1,
                        startMs = timeRange.first,
                        endMs = timeRange.second,
                        text = textLines.joinToString("\n")
                    )
                )
            }
        }

        return entries.sortedBy { it.startMs }
    }

    /**
     * 解析时间范围行：00:00:01,000 --> 00:00:04,500
     * @return Pair(startMs, endMs)
     */
    private fun parseTimeLine(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null
        val startMs = parseTimestamp(parts[0].trim()) ?: return null
        val endMs = parseTimestamp(parts[1].trim()) ?: return null
        return Pair(startMs, endMs)
    }

    /**
     * 解析单个时间戳：00:01:23,456 → 83456ms
     *
     * 计算：hours×3600000 + minutes×60000 + seconds×1000 + milliseconds
     */
    private fun parseTimestamp(timestamp: String): Long? {
        // 格式：HH:MM:SS,mmm 或 HH:MM:SS.mmm
        val cleaned = timestamp.replace(',', '.')
        val parts = cleaned.split(":")
        if (parts.size != 3) return null

        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val secParts = parts[2].split(".")
        val seconds = secParts[0].toLongOrNull() ?: return null
        val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0 else 0

        return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
    }
}

/**
 * 字幕条目
 * @param index 序号
 * @param startMs 起始时间（毫秒）
 * @param endMs 结束时间（毫秒）
 * @param text 字幕文本（可包含换行）
 */
data class SubtitleEntry(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)
