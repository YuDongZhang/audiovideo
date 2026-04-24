package com.audio.video.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * FFmpeg 执行管理器
 *
 * 设计为接口隔离 — 如果 ffmpeg-kit 依赖可用则真实执行命令，
 * 否则进入"学习模式"：只展示命令文本和知识点说明，不实际执行。
 *
 * ffmpeg-kit 底层原理：
 *   1. 将命令字符串解析为 argc/argv
 *   2. 通过 JNI 调用 FFmpeg 的 main() 函数（native .so 库）
 *   3. FFmpeg 内部完整流水线：
 *      解封装(demux) → 解码(decode) → 滤镜(filter) → 编码(encode) → 封装(mux)
 *   4. 通过回调返回日志和统计信息（已处理时长、速度等）
 *
 * 启用真实执行：
 *   1. 获取 ffmpeg-kit 的 .aar 文件（社区 fork 或自己编译）
 *   2. 放入 app/libs/ 目录
 *   3. build.gradle.kts 添加 implementation(files("libs/ffmpeg-kit-xxx.aar"))
 *   4. 本类会通过反射自动检测并切换到真实模式
 */
object FFmpegManager {

    /** 检测 ffmpeg-kit 是否可用 */
    val isAvailable: Boolean by lazy {
        try {
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /**
     * 执行 ffmpeg 命令
     * @param command 完整的 ffmpeg 参数（不含开头的 "ffmpeg"）
     * @param onProgress 进度回调，参数为已处理的时间（毫秒）
     * @return 执行结果
     */
    suspend fun execute(
        command: String,
        onProgress: ((timeMs: Long) -> Unit)? = null
    ): FFmpegResult = withContext(Dispatchers.IO) {
        if (isAvailable) {
            executeReal(command, onProgress)
        } else {
            executeSimulated(command)
        }
    }

    /**
     * 执行 ffprobe 探测命令
     */
    suspend fun probe(command: String): String = withContext(Dispatchers.IO) {
        if (isAvailable) {
            probeReal(command)
        } else {
            "[ 学习模式 — ffmpeg-kit 未安装 ]\n\n" +
            "实际执行此命令需要集成 ffmpeg-kit 库。\n" +
            "命令: ffprobe $command\n\n" +
            "预期输出: JSON 格式的视频元信息，包含\n" +
            "  · codec_name (编码格式)\n" +
            "  · width/height (分辨率)\n" +
            "  · r_frame_rate (帧率)\n" +
            "  · bit_rate (码率)\n" +
            "  · sample_rate (音频采样率)"
        }
    }

    /** 通过反射调用 ffmpeg-kit 真实执行 */
    private suspend fun executeReal(
        command: String,
        onProgress: ((timeMs: Long) -> Unit)?
    ): FFmpegResult {
        return try {
            val ffmpegKitClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            val executeMethod = ffmpegKitClass.getMethod("execute", String::class.java)
            val session = executeMethod.invoke(null, command)

            val getReturnCode = session.javaClass.getMethod("getReturnCode")
            val returnCode = getReturnCode.invoke(session)
            val isSuccess = returnCode?.javaClass?.getMethod("isValueSuccess")
                ?.invoke(returnCode) as? Boolean ?: false

            val getDuration = session.javaClass.getMethod("getDuration")
            val duration = getDuration.invoke(session) as? Long ?: 0

            val getOutput = session.javaClass.getMethod("getOutput")
            val output = getOutput.invoke(session) as? String ?: ""

            FFmpegResult(
                success = isSuccess,
                returnCode = 0,
                duration = duration,
                output = output,
                command = command
            )
        } catch (e: Exception) {
            FFmpegResult(
                success = false,
                returnCode = -1,
                duration = 0,
                output = "执行异常: ${e.message}",
                command = command
            )
        }
    }

    /** 反射调用 ffprobe */
    private fun probeReal(command: String): String {
        return try {
            val ffprobeKitClass = Class.forName("com.arthenica.ffmpegkit.FFprobeKit")
            val executeMethod = ffprobeKitClass.getMethod("execute", String::class.java)
            val session = executeMethod.invoke(null, command)
            val getOutput = session.javaClass.getMethod("getOutput")
            getOutput.invoke(session) as? String ?: ""
        } catch (e: Exception) {
            "ffprobe 执行异常: ${e.message}"
        }
    }

    /** 模拟执行 — 延迟后返回学习模式提示 */
    private suspend fun executeSimulated(command: String): FFmpegResult {
        delay(500) // 模拟执行延迟
        return FFmpegResult(
            success = true,
            returnCode = 0,
            duration = 500,
            output = "[ 学习模式 — ffmpeg-kit 未安装 ]\n\n" +
                     "命令已解析但未实际执行。\n" +
                     "请参考下方知识点说明了解此命令的作用。\n\n" +
                     "启用真实执行：\n" +
                     "1. 下载 ffmpeg-kit .aar 文件\n" +
                     "2. 放入 app/libs/ 目录\n" +
                     "3. build.gradle 添加 implementation(files(...))",
            command = command
        )
    }
}

/** FFmpeg 命令执行结果 */
data class FFmpegResult(
    val success: Boolean,
    val returnCode: Int,
    val duration: Long,
    val output: String,
    val command: String
)
