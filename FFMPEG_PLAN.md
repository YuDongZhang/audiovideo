# FFmpeg Android 集成学习方案

## 为什么学 FFmpeg

当前项目用的是 Media3 Transformer（底层是 MediaCodec 硬件编码），它的局限：
- 只支持 Android 硬件编码器支持的格式（H.264/H.265/VP9）
- 无法做复杂的滤镜链（ffmpeg filter_complex）
- 不支持 GIF/WebP 动图导出
- 字幕烧录、画中画、多轨混音等复杂操作实现困难
- 不同设备的 MediaCodec 实现有兼容性差异

FFmpeg 是跨平台的"瑞士军刀"，几乎支持所有音视频格式和操作。

---

## 集成方案选择

| 方案 | 原理 | 包体积 | 难度 | 推荐 |
|---|---|---|---|---|
| **mobile-ffmpeg / ffmpeg-kit** | 预编译 so 库，Java 调用命令行 | 15~50MB | ★★ | 快速上手 |
| **自己交叉编译 FFmpeg** | NDK 编译 C 代码为 .so | 可裁剪 | ★★★★ | 深入学习 |
| **FFmpeg + JNI 自定义** | 写 C/JNI 直接调 FFmpeg API | 灵活 | ★★★★★ | 最深入 |

**建议路线：先用 ffmpeg-kit 快速跑通 → 再学交叉编译裁剪 → 最后尝试 JNI 调 API**

---

## 第一阶段：ffmpeg-kit 集成（命令行模式）

### 1.1 添加依赖

```kotlin
// build.gradle.kts
// ffmpeg-kit 有多个包，按需选择：
// min: 基础编解码（H.264/AAC/MP3）~15MB
// full: 全格式（加 libx265/libvpx/libwebp/ass 等）~50MB
implementation("com.arthenica:ffmpeg-kit-full:6.0-2")
```

### 1.2 基本用法

```kotlin
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

// 执行命令（和终端里敲 ffmpeg 一模一样）
val session = FFmpegKit.execute("-i input.mp4 -vf 'scale=1280:720' output.mp4")

if (ReturnCode.isSuccess(session.returnCode)) {
    // 成功
} else {
    val logs = session.allLogsAsString  // 失败日志
}

// 异步执行 + 进度回调
FFmpegKit.executeAsync("-i input.mp4 -c:v libx264 output.mp4",
    { session -> /* 完成回调 */ },
    { log -> /* 日志回调 */ },
    { stats -> /* 进度：stats.time 已处理时长 */ }
)
```

### 1.3 学习用的命令集（在 app 中逐个实现）

每个命令对应一个功能按钮，点击执行，展示命令和结果：

```bash
# ===== 基础操作 =====

# 1. 查看视频信息（不编码，纯探测）
ffprobe -v quiet -print_format json -show_format -show_streams input.mp4
# 输出：编码格式、分辨率、帧率、码率、时长、音频采样率...

# 2. 格式转换（转封装，不重新编码，极快）
ffmpeg -i input.mp4 -c copy output.mkv
# -c copy = 直接拷贝音视频流，不重新编码
# 理解"容器"和"编码"的区别：MP4/MKV 是容器，H.264/AAC 是编码

# 3. 转封装 + 重新编码
ffmpeg -i input.mp4 -c:v libx264 -c:a aac output.mp4
# -c:v libx264 = 视频用 x264 软件编码器（CPU）
# -c:a aac = 音频用 AAC 编码

# ===== 裁剪与拼接 =====

# 4. 时间裁剪（seek + duration）
ffmpeg -ss 00:00:05 -i input.mp4 -t 10 -c copy output.mp4
# -ss 5 = 从第5秒开始（放在 -i 前面 = 输入端 seek，快但不精确）
# -t 10 = 截取10秒
# 精确裁剪需要重新编码：去掉 -c copy

# 5. 多文件拼接（concat demuxer）
# 先创建 filelist.txt:
#   file 'clip1.mp4'
#   file 'clip2.mp4'
ffmpeg -f concat -safe 0 -i filelist.txt -c copy output.mp4

# ===== 视频处理 =====

# 6. 缩放分辨率
ffmpeg -i input.mp4 -vf "scale=1280:720" output.mp4
# -vf = video filter（视频滤镜链）

# 7. 调整帧率
ffmpeg -i input.mp4 -r 30 output.mp4
# 帧率降低 = 丢帧，帧率升高 = 复制帧

# 8. 裁剪画面区域（crop）
ffmpeg -i input.mp4 -vf "crop=640:480:100:50" output.mp4
# crop=w:h:x:y → 从(100,50)位置裁出640×480区域

# 9. 旋转视频
ffmpeg -i input.mp4 -vf "transpose=1" output.mp4
# 0=逆时针90+垂直翻转 1=顺时针90 2=逆时针90 3=顺时针90+垂直翻转

# 10. 变速（视频+音频同步）
ffmpeg -i input.mp4 -filter_complex "[0:v]setpts=0.5*PTS[v];[0:a]atempo=2.0[a]" -map "[v]" -map "[a]" output.mp4
# setpts=0.5*PTS → 视频2倍速（PTS减半）
# atempo=2.0 → 音频2倍速（时间拉伸，保持音调）

# ===== 音频处理 =====

# 11. 提取音频
ffmpeg -i input.mp4 -vn -c:a copy output.aac
# -vn = 去掉视频流

# 12. 替换音频（静音原声 + 加 BGM）
ffmpeg -i video.mp4 -i bgm.mp3 -c:v copy -map 0:v -map 1:a -shortest output.mp4

# 13. 混合音频（原声 + BGM）
ffmpeg -i video.mp4 -i bgm.mp3 -filter_complex "[0:a][1:a]amix=inputs=2:duration=first:dropout_transition=2[a]" -map 0:v -map "[a]" output.mp4
# amix: 多路音频混合滤镜

# 14. 音量调节
ffmpeg -i input.mp4 -af "volume=1.5" output.mp4
# volume=1.5 → 音量增益1.5倍

# 15. 淡入淡出
ffmpeg -i input.mp4 -af "afade=t=in:d=2,afade=t=out:st=8:d=2" output.mp4
# afade=t=in:d=2 → 前2秒淡入
# afade=t=out:st=8:d=2 → 从第8秒开始2秒淡出

# ===== 滤镜与特效 =====

# 16. 黑白滤镜
ffmpeg -i input.mp4 -vf "colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3" output.mp4

# 17. 色彩调整（亮度/对比度/饱和度）
ffmpeg -i input.mp4 -vf "eq=brightness=0.1:contrast=1.2:saturation=1.5" output.mp4

# 18. 添加文字水印
ffmpeg -i input.mp4 -vf "drawtext=text='ClipForge':fontsize=36:fontcolor=white:x=50:y=50" output.mp4

# 19. 添加图片水印（画中画）
ffmpeg -i video.mp4 -i logo.png -filter_complex "overlay=W-w-10:H-h-10" output.mp4
# overlay 滤镜：将第二个输入叠加到第一个上面
# W-w-10:H-h-10 = 右下角留10px边距

# 20. 生成 GIF
ffmpeg -i input.mp4 -ss 2 -t 5 -vf "fps=15,scale=320:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" output.gif
# fps=15 → 降到15帧
# palettegen + paletteuse → 生成最优256色调色板（GIF限制）

# ===== 高级操作 =====

# 21. filter_complex 多输入多输出
ffmpeg -i bg.mp4 -i pip.mp4 -filter_complex "[1:v]scale=320:180[pip];[0:v][pip]overlay=W-w-20:20" output.mp4
# 画中画：将第二个视频缩放后叠加到右上角

# 22. 关键帧信息提取
ffprobe -select_streams v -show_frames -show_entries frame=pict_type,pts_time -of csv input.mp4 | head -20
# 输出每帧的类型（I/P/B）和时间戳

# 23. 硬件加速编码（Android MediaCodec）
ffmpeg -i input.mp4 -c:v h264_mediacodec -b:v 5M output.mp4
# h264_mediacodec = 使用 Android 硬件编码器（如果 ffmpeg-kit 编译时包含了）
```

---

## 第二阶段：在 app 中封装 FFmpeg 功能

### 2.1 创建 FFmpegCommand 工具类

```
com.audio.video/
  ffmpeg/
    FFmpegManager.kt          ← 异步执行 + 进度 + 取消
    FFmpegCommands.kt          ← 所有命令的构建器（类型安全）
    FFmpegLabScreen.kt         ← "FFmpeg 实验室"页面
    FFmpegCommandCard.kt       ← 单个命令的展示卡片（命令 + 说明 + 执行按钮）
```

### 2.2 FFmpeg 实验室页面设计

做一个独立的 "FFmpeg 实验室" 页面，每个命令一张卡片：
- 顶部：命令名称 + 知识点标签（如"滤镜链"、"编码参数"）
- 中间：完整 ffmpeg 命令文本（可复制）
- 底部：执行按钮 + 进度条 + 耗时
- 展开区：命令参数详解

用户可以选一个视频，然后逐个执行各命令观察效果，理解每个参数的含义。

---

## 第三阶段：自己编译 FFmpeg（NDK 交叉编译）

### 3.1 为什么要自己编

- ffmpeg-kit 包含所有库，体积大（full 版 ~50MB）
- 自己编可以只选需要的功能（如只要 H.264+AAC，体积可压到 5MB）
- 理解交叉编译原理对 NDK 开发很有帮助

### 3.2 编译流程

```bash
# 1. 准备环境
export NDK=/path/to/android-ndk
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64

# 2. FFmpeg configure（arm64-v8a 为例）
./configure \
    --prefix=$OUTPUT_DIR \
    --target-os=android \
    --arch=aarch64 \
    --cpu=armv8-a \
    --cc=$TOOLCHAIN/bin/aarch64-linux-android24-clang \
    --cxx=$TOOLCHAIN/bin/aarch64-linux-android24-clang++ \
    --enable-cross-compile \
    --enable-shared \          # 生成 .so 动态库
    --disable-static \
    --disable-programs \       # 不编译 ffmpeg/ffprobe 可执行文件
    --disable-doc \
    --enable-small \           # 优化体积
    --enable-gpl \
    --enable-nonfree \
    # === 按需启用 ===
    --enable-decoder=h264,hevc,aac,mp3 \
    --enable-encoder=libx264,aac \
    --enable-muxer=mp4,mov \
    --enable-demuxer=mp4,mov,concat \
    --enable-filter=scale,overlay,drawtext,colorchannelmixer \
    --enable-protocol=file \
    # === 禁用不需要的 ===
    --disable-avdevice \
    --disable-swscale \        # 如果不需要软件缩放
    --disable-network

# 3. 编译
make -j$(nproc)
make install

# 4. 产出文件
$OUTPUT_DIR/lib/libavcodec.so
$OUTPUT_DIR/lib/libavformat.so
$OUTPUT_DIR/lib/libavutil.so
$OUTPUT_DIR/lib/libavfilter.so
$OUTPUT_DIR/lib/libswresample.so
```

### 3.3 JNI 桥接

```
Android App (Kotlin)
    ↓ JNI call
Native Layer (C/C++)
    ↓ link
FFmpeg libs (libavcodec.so, libavformat.so, ...)
```

```c
// native-lib.c
#include <jni.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>

JNIEXPORT jstring JNICALL
Java_com_audio_video_ffmpeg_FFmpegNative_getVersion(JNIEnv *env, jobject obj) {
    return (*env)->NewStringUTF(env, avcodec_configuration());
}
```

---

## 第四阶段：FFmpeg C API 深入（选做）

直接调用 FFmpeg 的 C API，完全绕过命令行：

```c
// 完整的解码流程
AVFormatContext *fmt_ctx = NULL;
avformat_open_input(&fmt_ctx, filename, NULL, NULL);    // 打开文件
avformat_find_stream_info(fmt_ctx, NULL);                // 探测流信息

int video_stream = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
AVCodecParameters *codecpar = fmt_ctx->streams[video_stream]->codecpar;

const AVCodec *codec = avcodec_find_decoder(codecpar->codec_id);  // 找解码器
AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
avcodec_parameters_to_context(codec_ctx, codecpar);
avcodec_open2(codec_ctx, codec, NULL);                   // 打开解码器

AVPacket *pkt = av_packet_alloc();
AVFrame *frame = av_frame_alloc();

while (av_read_frame(fmt_ctx, pkt) >= 0) {               // 读取压缩包
    if (pkt->stream_index == video_stream) {
        avcodec_send_packet(codec_ctx, pkt);              // 送入解码器
        while (avcodec_receive_frame(codec_ctx, frame) >= 0) {  // 取出解码帧
            // frame->data[0] = Y 平面
            // frame->data[1] = U 平面
            // frame->data[2] = V 平面
            // 可以做任何像素级处理...
        }
    }
    av_packet_unref(pkt);
}
```

这个层级的学习可以理解：
- AVPacket（压缩数据包）vs AVFrame（原始帧）的区别
- YUV 颜色空间（Y=亮度 U/V=色度）
- 像素格式转换（YUV420P → RGB）
- 编码器的 flush 机制

---

## 推荐实施步骤

```
第1步：集成 ffmpeg-kit 依赖                        [0.5天]
第2步：创建 FFmpegManager 异步执行封装              [0.5天]
第3步：实现 "FFmpeg 实验室" 页面 + 前5个基础命令    [1天]
第4步：实现剩余命令（滤镜、音频、GIF 等）          [1天]
第5步：将现有的 Transformer 导出替换/补充为 FFmpeg   [1天]
第6步：（选做）NDK 交叉编译自定义 FFmpeg            [2天]
第7步：（选做）JNI 调 FFmpeg C API                  [2天]
```
