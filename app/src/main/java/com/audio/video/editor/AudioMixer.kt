package com.audio.video.editor

/**
 * PCM 音频混合器 — 将多路音频采样叠加为一路输出
 *
 * PCM 混合的本质：逐采样相加
 *   output[i] = source1[i] × vol1 + source2[i] × vol2 + ...
 *
 * 关键问题：
 *   1. 溢出（clipping）：两路都在峰值时相加超出 Short 范围
 *      方案 A：硬限幅 clamp(-32768, 32767) — 简单但有失真
 *      方案 B：软限幅 tanh(x) — 平滑过渡，音质更好
 *      方案 C：预先降低各路音量使总和不超限
 *
 *   2. 采样率不匹配：视频 48kHz + BGM 44.1kHz
 *      需要对 BGM 做重采样（线性插值或 sinc 插值）
 *
 *   3. 声道数不匹配：单声道 BGM + 立体声视频
 *      单声道复制到左右声道：L = R = mono
 */
object AudioMixer {

    /**
     * 混合两路 PCM 采样（16bit signed，归一化为 Float）
     *
     * @param source1 第一路采样值序列
     * @param vol1 第一路音量系数
     * @param source2 第二路采样值序列
     * @param vol2 第二路音量系数
     * @return 混合后的采样值序列（硬限幅到 [-1.0, 1.0]）
     */
    fun mix(
        source1: FloatArray,
        vol1: Float,
        source2: FloatArray,
        vol2: Float
    ): FloatArray {
        val length = maxOf(source1.size, source2.size)
        val output = FloatArray(length)

        for (i in 0 until length) {
            val s1 = if (i < source1.size) source1[i] * vol1 else 0f
            val s2 = if (i < source2.size) source2[i] * vol2 else 0f
            // 硬限幅（hard clipping）
            output[i] = (s1 + s2).coerceIn(-1f, 1f)
        }

        return output
    }

    /**
     * 软限幅混合 — 使用 tanh 函数平滑过渡，避免硬限幅失真
     *
     * tanh(x) 的特性：
     *   |x| < 0.5 时近似线性（不失真）
     *   |x| → ∞ 时趋近 ±1（平滑压缩，不硬切）
     */
    fun mixSoftClip(
        source1: FloatArray,
        vol1: Float,
        source2: FloatArray,
        vol2: Float
    ): FloatArray {
        val length = maxOf(source1.size, source2.size)
        val output = FloatArray(length)

        for (i in 0 until length) {
            val s1 = if (i < source1.size) source1[i] * vol1 else 0f
            val s2 = if (i < source2.size) source2[i] * vol2 else 0f
            val mixed = s1 + s2
            // tanh 软限幅
            output[i] = kotlin.math.tanh(mixed)
        }

        return output
    }

    /**
     * 线性重采样 — 将采样率从 srcRate 转换为 dstRate
     *
     * 线性插值：在相邻两个原始采样之间取加权平均
     *   比如 44100 → 48000：每个输出采样对应原始的 44100/48000 = 0.91875 个采样
     *   需要在原始采样之间做插值
     */
    fun resample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate) return input.copyOf()

        val ratio = srcRate.toDouble() / dstRate
        val outputLength = (input.size / ratio).toInt()
        val output = FloatArray(outputLength)

        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val fraction = (srcPos - srcIndex).toFloat()

            val sample1 = input.getOrElse(srcIndex) { 0f }
            val sample2 = input.getOrElse(srcIndex + 1) { sample1 }
            // 线性插值
            output[i] = sample1 + (sample2 - sample1) * fraction
        }

        return output
    }
}
