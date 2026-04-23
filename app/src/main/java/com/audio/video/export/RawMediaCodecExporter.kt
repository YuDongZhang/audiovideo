package com.audio.video.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.audio.video.data.model.ExportConfig
import com.audio.video.data.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * 原始 MediaCodec 导出器 — 绕过 Transformer，直接操作编解码 API
 *
 * 完整的硬件编解码流水线：
 *
 *   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 *   │ MediaExtractor│ ──→ │ MediaCodec   │ ──→ │ MediaCodec   │ ──→ ┌──────────┐
 *   │  (解封装)     │     │  (解码器)    │     │  (编码器)    │     │MediaMuxer│
 *   └──────────────┘     └──────────────┘     └──────────────┘     │ (封装)   │
 *         ↑                    ↑                    ↑               └──────────┘
 *     读取压缩数据        解码为原始帧          编码为 H.264           写入 MP4
 *     (H.264 NALUs)      (YUV/Surface)        (压缩 NALUs)          容器
 *
 * Surface 中转模式（零拷贝）：
 *   解码器输出 Surface ←→ 编码器输入 Surface
 *   解码后的帧直接通过 Surface 传递给编码器，不经过 CPU 内存拷贝
 *
 * 关键编码参数：
 *   - 编码格式：H.264 (video/avc) 或 H.265 (video/hevc)
 *   - 码率模式：VBR (BITRATE_MODE_VBR) — 画面复杂时码率高，简单时低
 *   - I帧间隔：1秒 — 每秒一个关键帧，便于 seek
 *   - Profile/Level：Baseline (兼容性好) / High (压缩率高)
 */
class RawMediaCodecExporter(private val context: Context) {

    /**
     * 使用原始 MediaCodec API 导出单个视频片段
     *
     * 学习用途：展示完整的编解码流程，实际生产建议用 Transformer
     */
    suspend fun exportClip(
        clip: VideoClip,
        config: ExportConfig,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            // ===== 第1步：解封装 — MediaExtractor 打开文件，分离音视频轨 =====
            extractor.setDataSource(context, Uri.parse(clip.sourceUri), null)
            val videoTrackIndex = findTrack(extractor, "video/")
            if (videoTrackIndex < 0) return@withContext false

            extractor.selectTrack(videoTrackIndex)
            val inputFormat = extractor.getTrackFormat(videoTrackIndex)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            val width = config.resolution.width
            val height = config.resolution.height

            // ===== 第2步：配置编码器 =====
            // 编码器需要先创建，因为需要它的 Input Surface
            val outputMime = "video/avc" // H.264
            val encoderFormat = MediaFormat.createVideoFormat(outputMime, width, height).apply {
                // 码率：分辨率 × 质量系数
                val baseBitrate = width * height * 4 // 基准码率
                setInteger(MediaFormat.KEY_BIT_RATE,
                    (baseBitrate * config.quality.bitrateMultiplier).toInt())

                // 帧率：与输入一致或默认 30fps
                val fps = if (inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
                    inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)

                // I帧间隔（GOP大小）：1秒
                // GOP 结构：I帧(关键帧) 后跟 P帧/B帧，直到下一个I帧
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                // 颜色格式：Surface 模式（GPU直传，零拷贝）
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

                // 码率控制模式：VBR（可变码率）
                // CBR: 恒定码率，画面复杂时质量下降
                // VBR: 可变码率，质量稳定但文件大小不可预测
                // CQ:  恒定质量，码率完全由画面复杂度决定
                setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }

            encoder = MediaCodec.createEncoderByType(outputMime)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // 获取编码器的 Input Surface — 解码器输出直接渲染到这里
            val encoderSurface: Surface = encoder.createInputSurface()
            encoder.start()

            // ===== 第3步：配置解码器 =====
            // 解码器的输出 Surface 设为编码器的 Input Surface（Surface中转，零拷贝）
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, encoderSurface, null, 0)
            decoder.start()

            // ===== 第4步：配置 MediaMuxer（MP4 封装器） =====
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerVideoTrack = -1
            var muxerStarted = false

            // seek 到片段入点
            extractor.seekTo(clip.startTimeMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            // ===== 第5步：编解码循环 =====
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            val endTimeUs = clip.endTimeMs * 1000
            val startTimeUs = clip.startTimeMs * 1000
            val totalDurationUs = endTimeUs - startTimeUs
            val timeoutUs = 10_000L

            while (coroutineContext.isActive) {
                // ----- 送入压缩数据到解码器 -----
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0 || extractor.sampleTime > endTimeUs) {
                            // 发送 EOS（End Of Stream）信号
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // ----- 从解码器取出解码帧，渲染到编码器 Surface -----
                val decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (decoderOutputIndex >= 0) {
                    val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    // releaseOutputBuffer(index, render=true) → 渲染到 Surface → 编码器自动接收
                    decoder.releaseOutputBuffer(decoderOutputIndex, !isEos)
                    if (isEos) {
                        encoder.signalEndOfInputStream()
                    }
                }

                // ----- 从编码器取出压缩数据，写入 Muxer -----
                val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 编码器第一次输出时会先发送 format，用它初始化 Muxer
                    val newFormat = encoder.outputFormat
                    muxerVideoTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (encoderOutputIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(encoderOutputIndex)
                    if (outputBuffer != null && muxerStarted
                        && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        // 写入 MP4 容器（跳过 codec config 数据，Muxer 已从 format 获取）
                        // 调整时间戳：减去起始偏移，使输出从 0 开始
                        bufferInfo.presentationTimeUs -= startTimeUs
                        muxer.writeSampleData(muxerVideoTrack, outputBuffer, bufferInfo)

                        // 上报进度
                        val progressUs = bufferInfo.presentationTimeUs
                        onProgress((progressUs.toFloat() / totalDurationUs).coerceIn(0f, 1f))
                    }
                    encoder.releaseOutputBuffer(encoderOutputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break // 编码完成
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            // ===== 第6步：释放所有资源 =====
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    /** 在所有轨道中查找指定类型（"video/" 或 "audio/"）的轨道索引 */
    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimePrefix) == true) return i
        }
        return -1
    }
}
