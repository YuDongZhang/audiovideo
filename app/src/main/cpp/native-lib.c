/**
 * native-lib.c — JNI 桥接层
 *
 * Kotlin ↔ C 的桥梁，通过 JNI (Java Native Interface) 连接：
 *
 *   Kotlin: FFmpegNative.getVersion()
 *     ↓ JNI 调用
 *   C: Java_com_audio_video_ffmpeg_FFmpegNative_getVersion(JNIEnv*, jobject)
 *     ↓ 链接
 *   FFmpeg: avcodec_configuration()
 *
 * JNI 函数命名规则：
 *   Java_<包名用下划线分隔>_<类名>_<方法名>(JNIEnv *env, jobject thiz, ...)
 *
 * JNI 基本类型映射：
 *   Kotlin String  ↔ jstring
 *   Kotlin Int     ↔ jint
 *   Kotlin Long    ↔ jlong
 *   Kotlin Boolean ↔ jboolean
 *   Kotlin ByteArray ↔ jbyteArray
 */

#include <jni.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "ClipForge_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ===== FFmpeg 头文件 =====
 * 取消注释以启用 FFmpeg 功能（需要先编译 FFmpeg 库）
 *
 * #include <libavformat/avformat.h>  // 解封装/封装
 * #include <libavcodec/avcodec.h>    // 编解码
 * #include <libavutil/avutil.h>      // 通用工具
 * #include <libavutil/imgutils.h>    // 图像工具
 * #include <libavfilter/avfilter.h>  // 滤镜
 * #include <libswresample/swresample.h> // 音频重采样
 * #include <libswscale/swscale.h>    // 图像缩放
 */

/**
 * 获取 native 层版本信息
 *
 * 当 FFmpeg 库可用时返回 FFmpeg 编译配置信息
 * 不可用时返回学习模式提示
 */
JNIEXPORT jstring JNICALL
Java_com_audio_video_ffmpeg_FFmpegNative_getVersion(JNIEnv *env, jobject thiz) {
    LOGI("getVersion called");

    /* 有 FFmpeg 时：
     * const char *version = avcodec_configuration();
     * char info[2048];
     * snprintf(info, sizeof(info),
     *     "FFmpeg 版本: %s\n"
     *     "libavcodec: %d.%d.%d\n"
     *     "libavformat: %d.%d.%d\n"
     *     "编译配置: %s",
     *     av_version_info(),
     *     LIBAVCODEC_VERSION_MAJOR, LIBAVCODEC_VERSION_MINOR, LIBAVCODEC_VERSION_MICRO,
     *     LIBAVFORMAT_VERSION_MAJOR, LIBAVFORMAT_VERSION_MINOR, LIBAVFORMAT_VERSION_MICRO,
     *     version);
     * return (*env)->NewStringUTF(env, info);
     */

    return (*env)->NewStringUTF(env,
        "Native 层已加载 (JNI 正常)\n"
        "FFmpeg 库未链接 — 需要先编译 FFmpeg .so 并配置 CMakeLists.txt\n\n"
        "JNI 桥接原理:\n"
        "  Kotlin -> JNI -> C/C++ -> FFmpeg libs\n\n"
        "编译步骤:\n"
        "  1. 运行 scripts/build_ffmpeg.sh 编译 FFmpeg\n"
        "  2. 将 .so 文件放入 app/src/main/jniLibs/<abi>/\n"
        "  3. 取消 CMakeLists.txt 中 FFmpeg 库的注释\n"
        "  4. 取消本文件中 #include 和函数实现的注释"
    );
}

/**
 * 获取视频文件信息（通过 FFmpeg C API）
 *
 * 完整的 FFmpeg 解封装流程：
 *   avformat_open_input()       → 打开文件
 *   avformat_find_stream_info() → 探测流信息
 *   遍历 fmt_ctx->streams[]    → 读取每条流的参数
 *   avformat_close_input()      → 关闭文件
 */
JNIEXPORT jstring JNICALL
Java_com_audio_video_ffmpeg_FFmpegNative_getVideoInfo(JNIEnv *env, jobject thiz, jstring path) {
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    LOGI("getVideoInfo: %s", file_path);

    /* 有 FFmpeg 时的完整实现：
     *
     * AVFormatContext *fmt_ctx = NULL;
     * int ret = avformat_open_input(&fmt_ctx, file_path, NULL, NULL);
     * if (ret < 0) {
     *     char err[256];
     *     av_strerror(ret, err, sizeof(err));
     *     (*env)->ReleaseStringUTFChars(env, path, file_path);
     *     return (*env)->NewStringUTF(env, err);
     * }
     *
     * avformat_find_stream_info(fmt_ctx, NULL);
     *
     * char info[4096];
     * int offset = 0;
     *
     * // 文件级信息
     * offset += snprintf(info + offset, sizeof(info) - offset,
     *     "格式: %s\n时长: %.2f 秒\n码率: %lld bps\n流数量: %d\n\n",
     *     fmt_ctx->iformat->long_name,
     *     fmt_ctx->duration / (double)AV_TIME_BASE,
     *     (long long)fmt_ctx->bit_rate,
     *     fmt_ctx->nb_streams);
     *
     * // 遍历每条流
     * for (int i = 0; i < fmt_ctx->nb_streams; i++) {
     *     AVStream *stream = fmt_ctx->streams[i];
     *     AVCodecParameters *par = stream->codecpar;
     *     const AVCodec *codec = avcodec_find_decoder(par->codec_id);
     *
     *     offset += snprintf(info + offset, sizeof(info) - offset,
     *         "--- 流 #%d ---\n", i);
     *
     *     if (par->codec_type == AVMEDIA_TYPE_VIDEO) {
     *         offset += snprintf(info + offset, sizeof(info) - offset,
     *             "类型: 视频\n"
     *             "编码: %s\n"
     *             "分辨率: %dx%d\n"
     *             "帧率: %.2f fps\n"
     *             "像素格式: %s\n\n",
     *             codec ? codec->name : "unknown",
     *             par->width, par->height,
     *             av_q2d(stream->avg_frame_rate),
     *             av_get_pix_fmt_name(par->format));
     *     } else if (par->codec_type == AVMEDIA_TYPE_AUDIO) {
     *         offset += snprintf(info + offset, sizeof(info) - offset,
     *             "类型: 音频\n"
     *             "编码: %s\n"
     *             "采样率: %d Hz\n"
     *             "声道数: %d\n"
     *             "位深: %d bit\n\n",
     *             codec ? codec->name : "unknown",
     *             par->sample_rate,
     *             par->ch_layout.nb_channels,
     *             par->bits_per_coded_sample);
     *     }
     * }
     *
     * avformat_close_input(&fmt_ctx);
     * (*env)->ReleaseStringUTFChars(env, path, file_path);
     * return (*env)->NewStringUTF(env, info);
     */

    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return (*env)->NewStringUTF(env, "[学习模式] FFmpeg 库未链接，无法探测视频信息");
}

/**
 * 解码视频帧（通过 FFmpeg C API）
 *
 * 核心数据流：
 *   AVPacket (压缩) → avcodec_send_packet() → 解码器 → avcodec_receive_frame() → AVFrame (原始)
 *
 * AVPacket vs AVFrame:
 *   AVPacket = 从文件读出的一个压缩数据包（可能包含一帧或多帧）
 *   AVFrame  = 解码后的一帧原始数据
 *     视频: data[0]=Y平面, data[1]=U平面, data[2]=V平面 (YUV420P)
 *     音频: data[0]=PCM采样数据
 *
 * YUV420P 格式:
 *   Y (亮度): 每个像素一个采样，分辨率 = 视频分辨率
 *   U (色度Cb): 每 2×2 像素共享一个采样，分辨率 = 视频的 1/4
 *   V (色度Cr): 同 U
 *   总数据量 = W×H × 1.5（比 RGB 的 W×H×3 省一半）
 */
JNIEXPORT jint JNICALL
Java_com_audio_video_ffmpeg_FFmpegNative_decodeFrames(
    JNIEnv *env, jobject thiz,
    jstring path, jint maxFrames
) {
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    LOGI("decodeFrames: %s, max=%d", file_path, maxFrames);

    /* 有 FFmpeg 时的完整解码循环：
     *
     * AVFormatContext *fmt_ctx = NULL;
     * avformat_open_input(&fmt_ctx, file_path, NULL, NULL);
     * avformat_find_stream_info(fmt_ctx, NULL);
     *
     * // 找到视频流
     * int video_idx = av_find_best_stream(
     *     fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
     *
     * // 创建解码器上下文
     * AVCodecParameters *par = fmt_ctx->streams[video_idx]->codecpar;
     * const AVCodec *codec = avcodec_find_decoder(par->codec_id);
     * AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
     * avcodec_parameters_to_context(codec_ctx, par);
     * avcodec_open2(codec_ctx, codec, NULL);
     *
     * AVPacket *pkt = av_packet_alloc();
     * AVFrame *frame = av_frame_alloc();
     * int frame_count = 0;
     *
     * // 解码循环
     * while (av_read_frame(fmt_ctx, pkt) >= 0 && frame_count < maxFrames) {
     *     if (pkt->stream_index == video_idx) {
     *         // 送入压缩包
     *         avcodec_send_packet(codec_ctx, pkt);
     *
     *         // 取出解码帧（一个 packet 可能产出多帧，如 B 帧重排序）
     *         while (avcodec_receive_frame(codec_ctx, frame) >= 0) {
     *             LOGI("帧 #%d: type=%c pts=%lld size=%dx%d format=%s",
     *                 frame_count,
     *                 av_get_picture_type_char(frame->pict_type),
     *                 (long long)frame->pts,
     *                 frame->width, frame->height,
     *                 av_get_pix_fmt_name(frame->format));
     *
     *             // frame->data[0] = Y 平面数据指针
     *             // frame->linesize[0] = Y 平面每行字节数（含对齐填充）
     *             // 可以在这里做像素级处理...
     *
     *             frame_count++;
     *         }
     *     }
     *     av_packet_unref(pkt);
     * }
     *
     * // Flush 解码器（取出缓冲区中残留的帧）
     * avcodec_send_packet(codec_ctx, NULL);
     * while (avcodec_receive_frame(codec_ctx, frame) >= 0) {
     *     frame_count++;
     * }
     *
     * // 释放资源
     * av_frame_free(&frame);
     * av_packet_free(&pkt);
     * avcodec_free_context(&codec_ctx);
     * avformat_close_input(&fmt_ctx);
     *
     * return frame_count;
     */

    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return 0;  // 学习模式返回 0 帧
}
