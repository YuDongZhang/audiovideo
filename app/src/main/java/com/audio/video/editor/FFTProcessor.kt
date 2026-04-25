package com.audio.video.editor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 快速傅里叶变换（FFT）处理器
 *
 * FFT 是数字信号处理的核心算法，将时域信号转换为频域表示：
 *   时域：信号随时间变化的波形（PCM 采样值）
 *   频域：信号由哪些频率成分组成，各频率的强度（幅度谱）
 *
 * 原理（DFT 离散傅里叶变换）：
 *   X[k] = Σ x[n] × e^(-j2πkn/N)    (k=0..N-1)
 *   将 N 个时域采样变换为 N 个频域系数
 *
 * FFT 是 DFT 的快速算法（Cooley-Tukey）：
 *   DFT: O(N²) 复杂度
 *   FFT: O(N log N) 复杂度
 *   对 N=1024: DFT 需要 ~100万次运算，FFT 只需 ~1万次
 *
 * 输出含义：
 *   X[0]     = DC 分量（直流，即信号平均值）
 *   X[1]     = 基频分量（频率 = 采样率/N）
 *   X[k]     = 第 k 个频率分量（频率 = k × 采样率/N）
 *   X[N/2]   = 奈奎斯特频率（采样率/2，可表示的最高频率）
 *   X[N/2+1..N-1] = 共轭对称，通常丢弃
 *
 * 频率分辨率 = 采样率 / N
 *   例：采样率 44100Hz, N=1024 → 分辨率 = 43Hz
 */
object FFTProcessor {

    /**
     * 执行 FFT，返回幅度谱（归一化到 [0.0, 1.0]）
     *
     * @param samples PCM 采样值数组（长度必须是 2 的幂）
     * @return 幅度谱，长度 = samples.size / 2（只取前半部分，后半部分是共轭对称）
     */
    fun computeMagnitudeSpectrum(samples: FloatArray): FloatArray {
        val n = samples.size
        // 实部 = 输入采样，虚部初始为 0
        val real = samples.copyOf()
        val imag = FloatArray(n)

        // 执行原地 FFT（Cooley-Tukey 蝶形运算）
        fft(real, imag)

        // 计算幅度谱：|X[k]| = sqrt(real² + imag²)
        val halfN = n / 2
        val magnitudes = FloatArray(halfN)
        var maxMag = 0f

        for (k in 0 until halfN) {
            magnitudes[k] = sqrt(real[k] * real[k] + imag[k] * imag[k])
            if (magnitudes[k] > maxMag) maxMag = magnitudes[k]
        }

        // 归一化到 [0.0, 1.0]
        if (maxMag > 0f) {
            for (k in magnitudes.indices) {
                magnitudes[k] /= maxMag
            }
        }

        return magnitudes
    }

    /**
     * 将幅度谱分成 bandCount 个频段（对数分布，模拟人耳感知）
     *
     * 人耳对频率的感知是对数的：
     *   20Hz ~ 200Hz    低频（贝斯、鼓）
     *   200Hz ~ 2kHz    中频（人声、吉他）
     *   2kHz ~ 20kHz    高频（镲片、齿音）
     *
     * 对数分频使每个 band 覆盖的"听觉宽度"大致相等
     */
    fun groupIntoBands(spectrum: FloatArray, bandCount: Int): FloatArray {
        if (spectrum.isEmpty()) return FloatArray(bandCount)

        val bands = FloatArray(bandCount)
        val specLen = spectrum.size.toFloat()

        for (i in 0 until bandCount) {
            // 对数分布：低频段分配更多 FFT bin，高频段较少
            val startRatio = Math.pow((i.toDouble() / bandCount), 2.0)
            val endRatio = Math.pow(((i + 1).toDouble() / bandCount), 2.0)
            val start = (startRatio * specLen).toInt().coerceIn(0, spectrum.size - 1)
            val end = (endRatio * specLen).toInt().coerceIn(start + 1, spectrum.size)

            var sum = 0f
            for (j in start until end) {
                sum += spectrum[j]
            }
            bands[i] = sum / (end - start).coerceAtLeast(1)
        }

        return bands
    }

    /**
     * Cooley-Tukey FFT 原地算法（基-2 时间抽取）
     *
     * 核心思想：将 N 点 DFT 递归拆分为两个 N/2 点 DFT
     *   偶数索引采样 → N/2 点 DFT
     *   奇数索引采样 → N/2 点 DFT
     *   蝶形运算合并结果
     *
     * 蝶形运算（Butterfly）：
     *   X[k]     = E[k] + W_N^k × O[k]
     *   X[k+N/2] = E[k] - W_N^k × O[k]
     *   其中 W_N^k = e^(-j2πk/N) 是旋转因子（twiddle factor）
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        // 位逆序排列（bit-reversal permutation）
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                // 交换
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
        }

        // 蝶形运算（自底向上，迭代版本）
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len  // 旋转因子角度

            for (i in 0 until n step len) {
                for (k in 0 until halfLen) {
                    val theta = angle * k
                    val twiddleReal = cos(theta).toFloat()
                    val twiddleImag = sin(theta).toFloat()

                    val evenIdx = i + k
                    val oddIdx = i + k + halfLen

                    // 蝶形运算
                    val tReal = twiddleReal * real[oddIdx] - twiddleImag * imag[oddIdx]
                    val tImag = twiddleReal * imag[oddIdx] + twiddleImag * real[oddIdx]

                    real[oddIdx] = real[evenIdx] - tReal
                    imag[oddIdx] = imag[evenIdx] - tImag
                    real[evenIdx] = real[evenIdx] + tReal
                    imag[evenIdx] = imag[evenIdx] + tImag
                }
            }
            len = len shl 1
        }
    }
}
