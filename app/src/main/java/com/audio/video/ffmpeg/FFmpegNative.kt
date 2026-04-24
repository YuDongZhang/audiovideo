package com.audio.video.ffmpeg

/**
 * FFmpeg Native 层 JNI 接口
 *
 * Kotlin 侧声明 external 方法 → 编译时生成 JNI 符号 → 运行时链接到 .so 中的 C 函数
 *
 * JNI 加载流程：
 *   1. System.loadLibrary("clipforge_native")
 *      → 在 jniLibs/<abi>/ 或 CMake 产出中查找 libclipforge_native.so
 *   2. JVM 根据函数签名匹配 C 函数
 *      → external fun getVersion()
 *      → Java_com_audio_video_ffmpeg_FFmpegNative_getVersion()
 *
 * 类型映射：
 *   Kotlin String  → JNI jstring  → C const char*
 *   Kotlin Int     → JNI jint     → C int32_t
 *   Kotlin Long    → JNI jlong    → C int64_t
 *   Kotlin Float   → JNI jfloat   → C float
 */
class FFmpegNative {

    companion object {
        /** native 库是否加载成功 */
        var isLoaded = false
            private set

        init {
            try {
                System.loadLibrary("clipforge_native")
                isLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // native 库未编译或未包含在 APK 中
                isLoaded = false
            }
        }
    }

    /** 获取 FFmpeg 版本信息和 native 层状态 */
    external fun getVersion(): String

    /**
     * 获取视频文件的详细信息
     * 底层调用 avformat_open_input → avformat_find_stream_info → 遍历 streams
     */
    external fun getVideoInfo(path: String): String

    /**
     * 解码指定数量的视频帧
     * 底层调用 av_read_frame → avcodec_send_packet → avcodec_receive_frame
     * @return 实际解码的帧数
     */
    external fun decodeFrames(path: String, maxFrames: Int): Int

    /**
     * 完整的视频转码
     * 底层调用完整的 解封装→解码→编码→封装 流水线
     * @param callback 进度回调，onProgress(float) 范围 [0.0, 1.0]
     * @return 0=成功
     */
    external fun transcode(inputPath: String, outputPath: String, callback: TranscodeCallback?): Int

    /** 转码进度回调接口 */
    interface TranscodeCallback {
        fun onProgress(progress: Float)
    }
}
