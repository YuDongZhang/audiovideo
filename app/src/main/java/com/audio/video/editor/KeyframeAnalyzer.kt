package com.audio.video.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 关键帧分析器 — 提取视频中所有 I 帧（关键帧）的时间戳
 *
 * 视频帧类型：
 *   I帧 (Intra frame / Keyframe)：完整图像，可独立解码
 *   P帧 (Predicted frame)：参考前面的帧，只编码差异
 *   B帧 (Bidirectional frame)：参考前后帧，压缩率最高
 *
 * GOP (Group of Pictures) 结构示例：
 *   I P P B B P P B B I P P B B ...
 *   ↑                 ↑
 *   关键帧             关键帧
 *
 * 为什么关键帧重要：
 *   - seek 只能精确到最近的关键帧（从关键帧开始解码到目标帧）
 *   - 关键帧越密集 → seek 越快，但文件越大
 *   - 关键帧间隔通常 1~5 秒
 *
 * 检测方法：MediaExtractor.getSampleFlags() 包含 SAMPLE_FLAG_SYNC 标志
 */
class KeyframeAnalyzer(private val context: Context) {

    /**
     * 提取指定时间范围内所有关键帧的时间戳（毫秒）
     *
     * 不需要 MediaCodec 解码，只通过 MediaExtractor 扫描采样标志位
     */
    suspend fun getKeyframeTimestamps(
        uri: Uri,
        startMs: Long = 0,
        endMs: Long = Long.MAX_VALUE
    ): List<Long> = withContext(Dispatchers.IO) {
        val keyframes = mutableListOf<Long>()
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = findVideoTrack(extractor)
            if (videoTrack < 0) return@withContext emptyList()

            extractor.selectTrack(videoTrack)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0 || sampleTimeUs > endMs * 1000) break

                // SAMPLE_FLAG_SYNC = 关键帧（I帧）标志
                val flags = extractor.sampleFlags
                if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    keyframes.add(sampleTimeUs / 1000) // 微秒转毫秒
                }

                if (!extractor.advance()) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
        }

        keyframes
    }

    /**
     * 获取视频的帧率和编码信息
     */
    suspend fun getVideoInfo(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = findVideoTrack(extractor)
            if (videoTrack < 0) return@withContext null

            val format = extractor.getTrackFormat(videoTrack)
            VideoInfo(
                mime = format.getString(MediaFormat.KEY_MIME) ?: "unknown",
                width = format.getIntOrNull(MediaFormat.KEY_WIDTH) ?: 0,
                height = format.getIntOrNull(MediaFormat.KEY_HEIGHT) ?: 0,
                frameRate = format.getIntOrNull(MediaFormat.KEY_FRAME_RATE) ?: 0,
                bitrate = format.getIntOrNull(MediaFormat.KEY_BIT_RATE) ?: 0,
                durationMs = if (format.containsKey(MediaFormat.KEY_DURATION))
                    format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0
            )
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    private fun MediaFormat.getIntOrNull(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }
}

/** 视频编码信息 */
data class VideoInfo(
    val mime: String,       // 编码格式："video/avc"(H.264), "video/hevc"(H.265)
    val width: Int,
    val height: Int,
    val frameRate: Int,     // 帧率
    val bitrate: Int,       // 码率 (bps)
    val durationMs: Long    // 时长
)
