package com.audio.video.editor

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.RgbMatrix
import com.audio.video.data.model.VideoFilterType

/**
 * 视频滤镜工厂 — 基于 4×4 颜色矩阵变换（RgbMatrix）
 *
 * 颜色矩阵原理：每个像素的 RGBA 值乘以 4×4 矩阵得到新颜色
 *   [R']   [m0  m4  m8  m12] [R]
 *   [G'] = [m1  m5  m9  m13] [G]
 *   [B']   [m2  m6  m10 m14] [B]
 *   [A']   [m3  m7  m11 m15] [A]
 *
 * 列主序（OpenGL 标准），floatArray[16]
 * 单位矩阵（不变）：对角线为 1，其余为 0
 * 灰度：每行都用亮度权重 (0.299, 0.587, 0.114)
 * 暖色：增大 R 系数，减小 B 系数
 */
@UnstableApi
object VideoFilterFactory {

    /** 根据滤镜类型创建 GlEffect，返回 null 表示无滤镜 */
    fun createEffect(filterType: VideoFilterType): GlEffect? {
        if (filterType == VideoFilterType.NONE) return null
        val matrix = getColorMatrix(filterType)
        return RgbMatrix { _, _ -> matrix }
    }

    /** 获取指定滤镜的 4×4 颜色变换矩阵（列主序） */
    fun getColorMatrix(filterType: VideoFilterType): FloatArray {
        val m = floatArrayOf(
            1f, 0f, 0f, 0f,  // 第1列
            0f, 1f, 0f, 0f,  // 第2列
            0f, 0f, 1f, 0f,  // 第3列
            0f, 0f, 0f, 1f   // 第4列
        )

        when (filterType) {
            VideoFilterType.NONE -> { /* 单位矩阵 */ }

            VideoFilterType.GRAYSCALE -> {
                // ITU-R BT.601 亮度公式：L = 0.299R + 0.587G + 0.114B
                // 三行都用相同权重 → RGB 输出一致 → 灰度
                m[0] = 0.299f; m[4] = 0.587f; m[8] = 0.114f   // R'
                m[1] = 0.299f; m[5] = 0.587f; m[9] = 0.114f   // G'
                m[2] = 0.299f; m[6] = 0.587f; m[10] = 0.114f  // B'
            }

            VideoFilterType.WARM -> {
                // R 通道增益 1.2，B 通道衰减 0.8
                m[0] = 1.2f; m[5] = 1.05f; m[10] = 0.8f
            }

            VideoFilterType.COOL -> {
                // B 通道增益 1.3，R 通道衰减 0.8
                m[0] = 0.8f; m[5] = 0.95f; m[10] = 1.3f
            }

            VideoFilterType.VINTAGE -> {
                // 经典 sepia tone 矩阵
                m[0] = 0.393f; m[4] = 0.769f; m[8] = 0.189f
                m[1] = 0.349f; m[5] = 0.686f; m[9] = 0.168f
                m[2] = 0.272f; m[6] = 0.534f; m[10] = 0.131f
            }

            VideoFilterType.VIVID -> {
                // 饱和度增强：拉大颜色通道差异
                m[0] = 1.3f;   m[4] = -0.15f; m[8] = -0.15f
                m[1] = -0.15f; m[5] = 1.3f;   m[9] = -0.15f
                m[2] = -0.15f; m[6] = -0.15f; m[10] = 1.3f
            }

            VideoFilterType.INVERT -> {
                // 反色：new = 1 - old
                // 矩阵对角线 -1，第4列偏移 +1
                m[0] = -1f; m[5] = -1f; m[10] = -1f
                m[12] = 1f; m[13] = 1f; m[14] = 1f
            }
        }
        return m
    }
}
