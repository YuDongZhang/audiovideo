/**
 * ffmpeg_cmd.c — 用 FFmpeg C API 实现完整的视频转码流水线
 *
 * 这是 FFmpeg 学习的终极目标：理解从源码级别的完整编解码流程
 *
 * 流水线架构：
 *
 *   输入文件 (MP4)
 *       │
 *   avformat_open_input()          ← 解封装器打开容器
 *       │
 *   av_read_frame() ──→ AVPacket  ← 读取一个压缩数据包
 *       │
 *   avcodec_send_packet()         ← 送入解码器
 *       │
 *   avcodec_receive_frame() ──→ AVFrame (YUV420P)  ← 取出原始帧
 *       │
 *   sws_scale()                   ← 像素格式转换（可选）
 *       │
 *   avcodec_send_frame()          ← 送入编码器
 *       │
 *   avcodec_receive_packet() ──→ AVPacket  ← 取出压缩数据包
 *       │
 *   av_interleaved_write_frame()  ← 写入输出容器
 *       │
 *   输出文件 (MP4)
 *
 *
 * 关键概念图：
 *
 *   时间戳 (Timestamp):
 *     PTS (Presentation Time Stamp) = 显示时间，决定帧何时显示
 *     DTS (Decode Time Stamp) = 解码时间，决定帧何时送入解码器
 *     对于 B 帧：DTS < PTS（先解码，后显示）
 *     对于 I/P 帧：DTS ≤ PTS
 *
 *   时间基 (Time Base):
 *     时间戳的单位，用 AVRational {num, den} 表示
 *     例：time_base = {1, 90000} 表示 1/90000 秒
 *     实际时间 = pts × time_base = pts / 90000 秒
 *     不同流的 time_base 不同，跨流操作需要 av_rescale_q() 转换
 *
 *   像素格式 (Pixel Format):
 *     YUV420P: Y/U/V 三平面，U/V 为 Y 的 1/4（最常用，几乎所有编码器都支持）
 *     NV12:    Y 平面 + UV 交织平面（Android Camera 常用）
 *     NV21:    Y 平面 + VU 交织平面（Android Camera 旧 API）
 *     RGB24:   RGB 逐像素排列（显示用，不适合编码）
 *     RGBA:    带 Alpha 通道（合成用）
 */

#include <jni.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "ClipForge_FFmpeg"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ===== 启用 FFmpeg 后取消注释 =====
 *
 * #include <libavformat/avformat.h>
 * #include <libavcodec/avcodec.h>
 * #include <libavutil/avutil.h>
 * #include <libavutil/opt.h>
 * #include <libavutil/imgutils.h>
 * #include <libavutil/timestamp.h>
 * #include <libswscale/swscale.h>
 * #include <libswresample/swresample.h>
 */

/**
 * 完整的视频转码函数（C API 版本）
 *
 * 对比 FFmpeg 命令行：
 *   命令行: ffmpeg -i input.mp4 -c:v libx264 -crf 23 -c:a aac output.mp4
 *   C API:  本函数做的事情和上面那条命令完全一样
 *
 * 区别：
 *   命令行 → FFmpeg 帮你搭建整个流水线
 *   C API  → 你自己手动搭建每一步，完全可控
 *
 * @param env JNI 环境
 * @param thiz Java 对象引用
 * @param input_path 输入文件路径
 * @param output_path 输出文件路径
 * @param callback Java 层的进度回调对象
 * @return 0=成功, <0=失败
 */
JNIEXPORT jint JNICALL
Java_com_audio_video_ffmpeg_FFmpegNative_transcode(
    JNIEnv *env, jobject thiz,
    jstring input_path, jstring output_path,
    jobject callback
) {
    const char *input = (*env)->GetStringUTFChars(env, input_path, NULL);
    const char *output = (*env)->GetStringUTFChars(env, output_path, NULL);

    LOGI("transcode: %s → %s", input, output);

    /* ===== 完整转码流程（启用 FFmpeg 后取消注释） =====
     *
     * int ret = 0;
     *
     * // ========== 1. 打开输入文件 ==========
     * AVFormatContext *ifmt_ctx = NULL;
     * ret = avformat_open_input(&ifmt_ctx, input, NULL, NULL);
     * if (ret < 0) { LOGE("无法打开输入文件"); goto end; }
     *
     * ret = avformat_find_stream_info(ifmt_ctx, NULL);
     * if (ret < 0) { LOGE("无法探测流信息"); goto end; }
     *
     * // ========== 2. 找到视频/音频流 ==========
     * int video_in_idx = av_find_best_stream(ifmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
     * int audio_in_idx = av_find_best_stream(ifmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
     *
     * // ========== 3. 创建解码器 ==========
     * // 视频解码器
     * AVCodecParameters *video_par = ifmt_ctx->streams[video_in_idx]->codecpar;
     * const AVCodec *video_dec = avcodec_find_decoder(video_par->codec_id);
     * AVCodecContext *video_dec_ctx = avcodec_alloc_context3(video_dec);
     * avcodec_parameters_to_context(video_dec_ctx, video_par);
     * avcodec_open2(video_dec_ctx, video_dec, NULL);
     *
     * // 音频解码器
     * AVCodecParameters *audio_par = ifmt_ctx->streams[audio_in_idx]->codecpar;
     * const AVCodec *audio_dec = avcodec_find_decoder(audio_par->codec_id);
     * AVCodecContext *audio_dec_ctx = avcodec_alloc_context3(audio_dec);
     * avcodec_parameters_to_context(audio_dec_ctx, audio_par);
     * avcodec_open2(audio_dec_ctx, audio_dec, NULL);
     *
     * // ========== 4. 创建输出文件和编码器 ==========
     * AVFormatContext *ofmt_ctx = NULL;
     * avformat_alloc_output_context2(&ofmt_ctx, NULL, NULL, output);
     *
     * // 视频编码器 (H.264)
     * const AVCodec *video_enc = avcodec_find_encoder(AV_CODEC_ID_H264);
     * AVCodecContext *video_enc_ctx = avcodec_alloc_context3(video_enc);
     * video_enc_ctx->width = video_dec_ctx->width;
     * video_enc_ctx->height = video_dec_ctx->height;
     * video_enc_ctx->pix_fmt = AV_PIX_FMT_YUV420P;  // H.264 标准输入格式
     * video_enc_ctx->time_base = (AVRational){1, 30};
     * video_enc_ctx->bit_rate = 5000000;  // 5Mbps
     * video_enc_ctx->gop_size = 30;       // 每30帧一个关键帧
     *
     * // CRF 模式（推荐）
     * av_opt_set(video_enc_ctx->priv_data, "crf", "23", 0);
     * av_opt_set(video_enc_ctx->priv_data, "preset", "medium", 0);
     *
     * avcodec_open2(video_enc_ctx, video_enc, NULL);
     *
     * // 在输出容器中添加视频流
     * AVStream *video_out = avformat_new_stream(ofmt_ctx, NULL);
     * avcodec_parameters_from_context(video_out->codecpar, video_enc_ctx);
     * int video_out_idx = video_out->index;
     *
     * // 音频编码器 (AAC)
     * const AVCodec *audio_enc = avcodec_find_encoder(AV_CODEC_ID_AAC);
     * AVCodecContext *audio_enc_ctx = avcodec_alloc_context3(audio_enc);
     * audio_enc_ctx->sample_rate = audio_dec_ctx->sample_rate;
     * audio_enc_ctx->ch_layout = audio_dec_ctx->ch_layout;
     * audio_enc_ctx->sample_fmt = audio_enc->sample_fmts[0];  // AAC 需要的格式
     * audio_enc_ctx->bit_rate = 128000;  // 128kbps
     * audio_enc_ctx->time_base = (AVRational){1, audio_enc_ctx->sample_rate};
     * avcodec_open2(audio_enc_ctx, audio_enc, NULL);
     *
     * AVStream *audio_out = avformat_new_stream(ofmt_ctx, NULL);
     * avcodec_parameters_from_context(audio_out->codecpar, audio_enc_ctx);
     * int audio_out_idx = audio_out->index;
     *
     * // ========== 5. 打开输出文件 ==========
     * avio_open(&ofmt_ctx->pb, output, AVIO_FLAG_WRITE);
     * avformat_write_header(ofmt_ctx, NULL);
     *
     * // ========== 6. 转码主循环 ==========
     * AVPacket *pkt = av_packet_alloc();
     * AVFrame *frame = av_frame_alloc();
     *
     * int64_t total_duration = ifmt_ctx->duration;  // 总时长（AV_TIME_BASE 单位）
     *
     * while (av_read_frame(ifmt_ctx, pkt) >= 0) {
     *     if (pkt->stream_index == video_in_idx) {
     *         // 视频：解码 → 编码
     *         avcodec_send_packet(video_dec_ctx, pkt);
     *         while (avcodec_receive_frame(video_dec_ctx, frame) >= 0) {
     *             // 可以在这里做像素级处理（滤镜、缩放等）
     *
     *             // 送入编码器
     *             frame->pict_type = AV_PICTURE_TYPE_NONE;  // 让编码器自己决定帧类型
     *             avcodec_send_frame(video_enc_ctx, frame);
     *             AVPacket *enc_pkt = av_packet_alloc();
     *             while (avcodec_receive_packet(video_enc_ctx, enc_pkt) >= 0) {
     *                 // 时间基转换：编码器 time_base → 输出流 time_base
     *                 av_packet_rescale_ts(enc_pkt,
     *                     video_enc_ctx->time_base,
     *                     ofmt_ctx->streams[video_out_idx]->time_base);
     *                 enc_pkt->stream_index = video_out_idx;
     *                 av_interleaved_write_frame(ofmt_ctx, enc_pkt);
     *             }
     *             av_packet_free(&enc_pkt);
     *
     *             // 回调进度到 Java 层
     *             if (callback != NULL && total_duration > 0) {
     *                 int64_t current_pts = av_rescale_q(frame->pts,
     *                     ifmt_ctx->streams[video_in_idx]->time_base,
     *                     (AVRational){1, AV_TIME_BASE});
     *                 float progress = (float)current_pts / total_duration;
     *                 jclass cls = (*env)->GetObjectClass(env, callback);
     *                 jmethodID method = (*env)->GetMethodID(cls, "onProgress", "(F)V");
     *                 (*env)->CallVoidMethod(env, callback, method, progress);
     *             }
     *         }
     *     }
     *     // 音频处理类似，省略...
     *     av_packet_unref(pkt);
     * }
     *
     * // ========== 7. Flush 编码器 ==========
     * // 解码器/编码器内部有缓冲区，需要 flush 取出残留的帧
     * avcodec_send_frame(video_enc_ctx, NULL);  // NULL = flush 信号
     * AVPacket *flush_pkt = av_packet_alloc();
     * while (avcodec_receive_packet(video_enc_ctx, flush_pkt) >= 0) {
     *     av_packet_rescale_ts(flush_pkt,
     *         video_enc_ctx->time_base,
     *         ofmt_ctx->streams[video_out_idx]->time_base);
     *     flush_pkt->stream_index = video_out_idx;
     *     av_interleaved_write_frame(ofmt_ctx, flush_pkt);
     * }
     * av_packet_free(&flush_pkt);
     *
     * // ========== 8. 写入文件尾 + 释放资源 ==========
     * av_write_trailer(ofmt_ctx);  // 写入 moov atom（MP4 索引）
     *
     * av_frame_free(&frame);
     * av_packet_free(&pkt);
     * avcodec_free_context(&video_dec_ctx);
     * avcodec_free_context(&audio_dec_ctx);
     * avcodec_free_context(&video_enc_ctx);
     * avcodec_free_context(&audio_enc_ctx);
     * avformat_close_input(&ifmt_ctx);
     * avio_closep(&ofmt_ctx->pb);
     * avformat_free_context(ofmt_ctx);
     *
     * end:
     */

    (*env)->ReleaseStringUTFChars(env, input_path, input);
    (*env)->ReleaseStringUTFChars(env, output_path, output);

    LOGI("transcode 完成（学习模式）");
    return 0;
}
