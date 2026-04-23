package com.audio.video.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * 音频波形提取器
 *
 * 完整链路：MediaExtractor 分离音频轨 → MediaCodec 解码为 PCM → 降采样取峰值 → 归一化
 *
 * PCM 数据格式：16bit 有符号整数（Short），范围 [-32768, 32767]
 * 单声道：每个采样 2 字节
 * 双声道：左右交替，每帧 4 字节（取左右平均值合并为单声道）
 */
class WaveformExtractor(private val context: Context) {

    /**
     * 提取指定时间范围内的音频波形数据
     *
     * @param uri 视频/音频文件 URI
     * @param startMs 起始时间（毫秒），对应片段入点
     * @param endMs 结束时间（毫秒），对应片段出点
     * @param sampleCount 输出的采样点数量（对应 UI 上的绘制列数）
     * @return 归一化的波形数据，每个值 ∈ [0.0, 1.0]
     */
    suspend fun extract(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        sampleCount: Int = 100
    ): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            // 第一步：找到音频轨道
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) return@withContext FloatArray(sampleCount)

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext FloatArray(sampleCount)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // 第二步：seek 到起始位置
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            // 第三步：创建解码器，解码为 PCM
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmSamples = decodeToPcm(codec, extractor, startMs, endMs, channelCount)

            codec.stop()
            codec.release()

            // 第四步：降采样取峰值，生成指定数量的波形点
            downsamplePeaks(pcmSamples, sampleCount)
        } catch (e: Exception) {
            FloatArray(sampleCount)
        } finally {
            extractor.release()
        }
    }

    /** 在所有轨道中查找第一条音频轨 */
    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }

    /**
     * 使用 MediaCodec 将压缩音频解码为 PCM 采样值
     *
     * 解码循环：
     *   1. 从 MediaExtractor 读取压缩数据 → 送入 Codec 输入缓冲区
     *   2. 从 Codec 输出缓冲区取出解码后的 PCM 数据
     *   3. 将 PCM ByteBuffer 转为 Short 数组（16bit 采样）
     *   4. 多声道取平均值合并为单声道
     */
    private fun decodeToPcm(
        codec: MediaCodec,
        extractor: MediaExtractor,
        startMs: Long,
        endMs: Long,
        channelCount: Int
    ): List<Float> {
        val samples = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        val timeoutUs = 10_000L
        val endUs = endMs * 1000

        while (true) {
            // 送入压缩数据
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0 || extractor.sampleTime > endUs) {
                        // 到达文件末尾或超出范围，发送 EOS
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // 取出解码后的 PCM 数据
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    codec.releaseOutputBuffer(outputIndex, false)
                    break
                }
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null) {
                    // PCM 16bit：每 2 字节一个 Short 采样
                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    val shortBuffer = outputBuffer.asShortBuffer()
                    val sampleCountInBuffer = shortBuffer.remaining()

                    for (i in 0 until sampleCountInBuffer step channelCount) {
                        // 多声道取平均值
                        var sum = 0f
                        for (ch in 0 until channelCount) {
                            if (i + ch < sampleCountInBuffer) {
                                sum += abs(shortBuffer.get(i + ch).toFloat())
                            }
                        }
                        samples.add(sum / channelCount)
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                break
            }
        }

        return samples
    }

    /**
     * 将原始 PCM 采样降采样为指定数量的波形峰值
     *
     * 算法：将采样平均分成 sampleCount 个桶，每个桶取峰值（最大绝对值）
     * 最后归一化到 [0.0, 1.0] 范围
     */
    private fun downsamplePeaks(samples: List<Float>, sampleCount: Int): FloatArray {
        if (samples.isEmpty()) return FloatArray(sampleCount)

        val peaks = FloatArray(sampleCount)
        val samplesPerBucket = samples.size.toFloat() / sampleCount

        for (i in 0 until sampleCount) {
            val start = (i * samplesPerBucket).toInt()
            val end = ((i + 1) * samplesPerBucket).toInt().coerceAtMost(samples.size)
            var peak = 0f
            for (j in start until end) {
                peak = max(peak, samples[j])
            }
            peaks[i] = peak
        }

        // 归一化：最大值映射为 1.0
        val maxPeak = peaks.maxOrNull() ?: 1f
        if (maxPeak > 0) {
            for (i in peaks.indices) {
                peaks[i] = peaks[i] / maxPeak
            }
        }

        return peaks
    }
}
