package com.audio.video.editor

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * 3D LUT (.cube) 文件解析器
 *
 * LUT (Look-Up Table) 调色原理：
 *   输入 RGB → 在 3D 查找表中查询 → 输出新 RGB
 *   本质是一个 RGB 三维空间的颜色映射
 *
 * .cube 文件格式：
 *   TITLE "filmLook"           ← 可选标题
 *   LUT_3D_SIZE 33             ← 每个维度的采样点数（33³ = 35937 个颜色点）
 *   0.0 0.0 0.0                ← R=0, G=0, B=0 时的输出颜色
 *   0.01 0.0 0.0               ← R 稍增时的输出颜色
 *   ...                        ← R 变化最快（内层循环），然后 G，最后 B
 *
 * GPU 实现：
 *   将 LUT 数据上传为 3D 纹理（GL_TEXTURE_3D）
 *   Fragment Shader 中用输入 RGB 作为纹理坐标采样：
 *     vec3 lutCoord = inputColor.rgb * (size - 1.0) / size + 0.5 / size;
 *     vec3 outputColor = texture3D(uLut, lutCoord).rgb;
 *
 * 三线性插值：
 *   GPU 硬件自动在 8 个最近邻 LUT 点之间做三线性插值
 *   所以即使 LUT 只有 33³ 个点，也能覆盖整个色彩空间
 */
object LutParser {

    /**
     * 解析 .cube 文件
     * @return Pair(size, data) — size 为每维采样数，data 为 RGBRGBRGB... 浮点数组
     */
    fun parseCube(inputStream: InputStream): LutData? {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var size = 0
        val colors = mutableListOf<Float>()

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()

                // 跳过注释和空行
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                // 解析 LUT 尺寸
                if (trimmed.startsWith("LUT_3D_SIZE")) {
                    size = trimmed.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
                    continue
                }

                // 跳过其他元数据行
                if (trimmed.startsWith("TITLE") || trimmed.startsWith("DOMAIN_")) continue

                // 解析颜色数据行（三个浮点数 R G B）
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    try {
                        colors.add(parts[0].toFloat()) // R
                        colors.add(parts[1].toFloat()) // G
                        colors.add(parts[2].toFloat()) // B
                    } catch (_: NumberFormatException) {
                        continue
                    }
                }
            }
        }

        if (size <= 0 || colors.size != size * size * size * 3) return null
        return LutData(size, colors.toFloatArray())
    }
}

/**
 * 3D LUT 数据
 * @param size 每维采样数（如 33 → 33×33×33 = 35937 个颜色点）
 * @param data RGB 浮点数组，长度 = size³ × 3，排列顺序 R 变化最快
 */
data class LutData(
    val size: Int,
    val data: FloatArray
) {
    /** LUT 条目总数 */
    val entryCount: Int get() = size * size * size

    /**
     * CPU 端 LUT 查找（用于预览或低性能设备）
     *
     * 最近邻查找（不插值，精度较低但简单）
     * @param r 输入红色 [0.0, 1.0]
     * @param g 输入绿色 [0.0, 1.0]
     * @param b 输入蓝色 [0.0, 1.0]
     * @return 输出 RGB 数组 [r, g, b]
     */
    fun lookup(r: Float, g: Float, b: Float): FloatArray {
        val ri = (r * (size - 1)).toInt().coerceIn(0, size - 1)
        val gi = (g * (size - 1)).toInt().coerceIn(0, size - 1)
        val bi = (b * (size - 1)).toInt().coerceIn(0, size - 1)

        // .cube 文件中 R 变化最快 → 索引 = R + G×size + B×size²
        val index = (bi * size * size + gi * size + ri) * 3
        return floatArrayOf(
            data.getOrElse(index) { r },
            data.getOrElse(index + 1) { g },
            data.getOrElse(index + 2) { b }
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LutData) return false
        return size == other.size && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * size + data.contentHashCode()
}
