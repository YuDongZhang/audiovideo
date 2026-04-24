# FFmpeg 技术要点

## 1. FFmpeg 是什么

FFmpeg 不是一个程序，而是一套工具集：

```
ffmpeg   — 音视频转换/处理的核心工具
ffprobe  — 媒体信息探测工具（只读不写）
ffplay   — 简易播放器（Android 上不用）

核心库：
  libavformat   — 解封装/封装（MP4/MKV/FLV 容器的读写）
  libavcodec    — 编解码（H.264/H.265/AAC/MP3 等）
  libavfilter   — 滤镜框架（scale/crop/overlay/drawtext 等）
  libavutil     — 通用工具（像素格式转换、数学运算）
  libswresample — 音频重采样（44.1kHz ↔ 48kHz）
  libswscale    — 图像缩放和像素格式转换
```

## 2. FFmpeg 命令结构

```
ffmpeg [全局选项] [输入选项] -i 输入文件 [输出选项] 输出文件

例：ffmpeg -ss 5 -i input.mp4 -t 10 -c:v libx264 -crf 23 output.mp4
     ↑          ↑              ↑    ↑              ↑
     输入选项    输入文件         输出时长  视频编码器   质量因子
```

### 选项前缀含义

```
-c:v → codec:video（视频编码器）
-c:a → codec:audio（音频编码器）
-b:v → bitrate:video（视频码率）
-b:a → bitrate:audio（音频码率）
-vf  → video filter（视频滤镜链）
-af  → audio filter（音频滤镜链）
-vn  → video no（去掉视频）
-an  → audio no（去掉音频）
```

## 3. 容器 vs 编码 — 最基本的概念

```
MP4 文件 = 容器(MP4) + 视频编码(H.264) + 音频编码(AAC)
MKV 文件 = 容器(MKV) + 视频编码(H.264) + 音频编码(AAC)

容器（信封）：负责组织、索引、同步多条流
  MP4 → 最通用，流媒体友好（moov 可前置）
  MKV → 最灵活，支持任意编码和多字幕
  FLV → 直播推流常用
  TS  → 广播电视，HLS 分片格式

编码（信纸）：压缩算法
  H.264 (AVC) → 当前最普及，兼容性最好
  H.265 (HEVC) → 比 H.264 省 50% 码率，但编码慢
  VP9  → Google 开源，YouTube 使用
  AV1  → 新一代开源，比 H.265 再省 30%
  AAC  → 音频主流，iTunes/Android 默认
  Opus → 低延迟音频，WebRTC/Discord 使用
```

### 转封装 vs 转码

```
转封装（-c copy）：
  只是换容器，不动编码数据，极快（1GB 视频 < 1秒）
  ffmpeg -i input.mp4 -c copy output.mkv

转码（指定编码器）：
  解码 → 处理 → 重新编码，慢但可以改变编码参数
  ffmpeg -i input.mp4 -c:v libx264 output.mp4
```

## 4. 编码参数详解

### CRF（恒定质量因子）— 最推荐的码率控制方式

```
-crf 值     视觉效果        文件大小
  0        无损（原始质量）   极大
  18       视觉无损          大
  23       默认（推荐）      适中
  28       质量可见下降       小
  51       最差质量          最小

原理：CRF 模式下编码器自动分配码率
  画面复杂（运动/细节多）→ 码率自动升高
  画面简单（静止/纯色）→ 码率自动降低
  最终效果：质量恒定，文件大小不可预测
```

### Preset（编码速度预设）

```
-preset 值       编码速度    压缩效率    文件大小
  ultrafast      最快        最差        最大
  veryfast       很快        较差        较大
  medium         平衡        中等        适中（默认）
  slow           慢          较好        较小
  veryslow       最慢        最好        最小

同样的 CRF 值，preset 越慢 → 编码器花更多时间优化 → 同质量下文件更小
```

### 码率模式对比

```
CRF（恒定质量）：-crf 23
  质量恒定，码率自动波动
  适合：本地存储、质量优先

CBR（恒定码率）：-b:v 5M -maxrate 5M -bufsize 10M
  码率恒定（±），质量波动
  适合：直播推流、带宽固定场景

VBR（可变码率）：-b:v 5M
  目标码率 5Mbps，允许波动
  平衡方案
```

## 5. 滤镜系统 — FFmpeg 最强大的部分

### 滤镜链 (-vf)

单条流的线性处理链，滤镜间用逗号连接：

```
-vf "scale=1280:720,crop=1200:700:40:10,eq=brightness=0.1"
     ↑                ↑                    ↑
     先缩放            再裁剪               最后调色
```

### 复杂滤镜图 (-filter_complex)

多输入/多输出的 DAG（有向无环图）：

```
-filter_complex "[0:v]scale=1280:720[bg]; [1:v]scale=320:180[pip]; [bg][pip]overlay=W-w-20:20[out]"

解读：
  [0:v] → scale → [bg]           第0个输入的视频缩放为1280×720
  [1:v] → scale → [pip]          第1个输入的视频缩放为320×180
  [bg] + [pip] → overlay → [out]  将小窗叠加到大画面右上角

[标签] 语法：
  [0:v] = 第0个输入(-i)的视频流
  [0:a] = 第0个输入的音频流
  [名字] = 自定义中间结果标签
```

### 常用滤镜速查

| 滤镜 | 作用 | 示例 |
|---|---|---|
| `scale` | 缩放 | `scale=1280:-1` (宽1280,高自动) |
| `crop` | 裁剪 | `crop=iw:ih-200:0:100` (去黑边) |
| `overlay` | 叠加 | `overlay=10:10` (画中画/水印) |
| `drawtext` | 文字 | `drawtext=text='hi':fontsize=36` |
| `eq` | 调色 | `eq=brightness=0.1:saturation=1.5` |
| `colorchannelmixer` | 颜色矩阵 | 灰度/sepia/色调偏移 |
| `setpts` | 改变PTS | `setpts=0.5*PTS` (2倍速) |
| `fps` | 改帧率 | `fps=30` |
| `transpose` | 旋转 | `transpose=1` (顺时针90°) |
| `hflip/vflip` | 翻转 | 水平/垂直镜像 |
| `volume` | 音量 | `volume=1.5` (1.5倍) |
| `afade` | 淡入淡出 | `afade=t=in:d=2` |
| `atempo` | 音频变速 | `atempo=2.0` (保持音调) |
| `amix` | 音频混合 | 多路音频叠加 |
| `palettegen` | 调色板生成 | GIF 256色优化 |
| `paletteuse` | 调色板应用 | GIF 量化 |

## 6. Seek 精度问题 — 面试高频

```
输入端 seek（-ss 在 -i 前面）：
  ffmpeg -ss 10 -i input.mp4 -t 5 -c copy output.mp4
  ·  FFmpeg 直接跳到第 10 秒附近的关键帧
  · 快，但可能不精确（偏差 ≤ 1个 GOP 时长）
  · 适合：快速裁剪，配合 -c copy

解码端 seek（-ss 在 -i 后面）：
  ffmpeg -i input.mp4 -ss 10 -t 5 -c:v libx264 output.mp4
  · FFmpeg 从头解码到第 10 秒，然后开始输出
  · 慢，但帧级精确
  · 必须重新编码（不能用 -c copy）

混合 seek：
  ffmpeg -ss 8 -i input.mp4 -ss 2 -t 5 -c:v libx264 output.mp4
  · 先跳到第 8 秒（快速），再精确定位到第 10 秒
  · 兼顾速度和精度
```

## 7. GIF 导出 — 量化算法

```
GIF 限制：每帧最多 256 色（8bit 调色板）
视频原始：每帧 16.7M 色（24bit RGB）

量化（Quantization）：从 16.7M 色中选出最优的 256 色

FFmpeg 的两步走：
  1. palettegen 全局扫描所有帧，统计颜色分布，生成最优调色板
  2. paletteuse 用调色板将每帧量化为 256 色

split 技巧：
  将视频流复制为两路 → 一路生成调色板 → 另一路用调色板
  [v]split[s0][s1]; [s0]palettegen[p]; [s1][p]paletteuse

常见量化算法：
  MedianCut: 递归将颜色空间切成 256 个桶
  NeuQuant:  用神经网络学习最优调色板
  Octree:    八叉树空间划分
```

## 8. Android 集成方案

### 方案对比

| 方案 | 包体积 | 维护状态 | API |
|---|---|---|---|
| ffmpeg-kit (arthenica) | 15~50MB | **已停维，包已下架** | 命令行字符串 |
| Bytedeco FFmpeg | ~30MB | 活跃维护 | Java 绑定 C API |
| 自己 NDK 编译 | 可裁剪至 5MB | 完全自控 | JNI + C API |
| 社区 fork .aar | 15~50MB | 看维护者 | 命令行字符串 |

### 自己编译的优势

```
ffmpeg-kit-full-gpl: ~50MB（包含 x265/libvpx/libwebp/ass/...）

自己编译只选需要的：
  ./configure --enable-decoder=h264,hevc,aac \
              --enable-encoder=libx264,aac \
              --enable-filter=scale,overlay,drawtext \
              --disable-everything-else
  → 产出 ~5MB

理解交叉编译：
  · NDK 提供 ARM/x86 的交叉编译工具链
  · --target-os=android --arch=aarch64
  · 产出 .so 动态库，通过 JNI 加载
```

## 9. FFmpeg 内部流水线

```
输入文件
   │
   ▼
┌─────────────┐
│ AVFormatContext │  ← libavformat: 解封装（读取 MP4/MKV 容器）
│  (demux)        │     拆分出视频包(AVPacket)和音频包(AVPacket)
└──────┬──────────┘
       │ AVPacket（压缩数据包）
       ▼
┌─────────────┐
│ AVCodecContext │  ← libavcodec: 解码
│  (decode)      │     H.264 NALUs → YUV420P 原始帧
└──────┬─────────┘
       │ AVFrame（原始帧，YUV/PCM）
       ▼
┌─────────────┐
│ AVFilterGraph │  ← libavfilter: 滤镜处理
│  (filter)     │     scale → crop → overlay → drawtext
└──────┬────────┘
       │ AVFrame（处理后的帧）
       ▼
┌─────────────┐
│ AVCodecContext │  ← libavcodec: 编码
│  (encode)      │     YUV420P → H.264 NALUs
└──────┬─────────┘
       │ AVPacket（压缩数据包）
       ▼
┌─────────────┐
│ AVFormatContext │  ← libavformat: 封装（写入 MP4 容器）
│  (mux)          │     添加 moov/mdat 结构
└─────────────────┘
       │
       ▼
    输出文件

关键数据类型：
  AVPacket = 压缩数据（从文件读出 / 编码器产出）
  AVFrame  = 原始数据（解码器产出 / 送入编码器）
  YUV420P  = 视频像素格式（Y=亮度, U/V=色度, 4:2:0 采样）
  PCM      = 音频原始格式（每采样 16bit signed）
```

## 10. 项目中的 FFmpeg 实验室

### 架构设计

```
ffmpeg/
  FFmpegManager.kt     ← 执行封装，反射检测 ffmpeg-kit 可用性
  FFmpegCommands.kt    ← 所有学习命令的定义（6 组 15 个命令）

ui/screen/ffmpeglab/
  FFmpegLabScreen.kt   ← 实验室主页面
  FFmpegLabViewModel.kt← 视频选择 + 命令执行状态管理
  components/
    CommandCard.kt     ← 命令卡片（名称+标签+执行按钮+知识点展开）
```

### 学习模式

如果 ffmpeg-kit 库不可用（依赖未安装），FFmpegManager 会：
- 通过 `Class.forName()` 反射检测
- 自动切换到"学习模式"
- 仍然展示完整的命令文本和参数说明
- 执行按钮返回模拟结果 + 启用指引

### 命令分组

| 组 | 命令数 | 知识点 |
|---|---|---|
| 信息探测 | 2 | ffprobe、元信息、关键帧/GOP |
| 容器与编码 | 2 | -c copy、libx264、CRF、preset |
| 裁剪与拼接 | 2 | -ss/-t、输入端/解码端 seek |
| 视频滤镜 | 6 | scale、crop、colorchannelmixer、eq、drawtext、GIF |
| 音频处理 | 3 | -vn、volume、afade |
| 高级操作 | 3 | filter_complex、setpts、atempo、-map |

每个命令卡片展开后包含：
- 实际执行的完整命令文本（可复制）
- 逐参数的中文说明
- 底层原理和等价的代码对照

---

## 11. NDK 交叉编译（第三阶段）

### 编译脚本要点

```bash
# 核心 configure 参数
./configure \
    --target-os=android \       # 目标系统
    --arch=aarch64 \            # CPU 架构（arm64-v8a）
    --cc=$TOOLCHAIN/bin/aarch64-linux-android24-clang \  # NDK 提供的交叉编译器
    --enable-cross-compile \    # 开启交叉编译
    --enable-shared \           # 产出 .so 动态库
    --disable-everything \      # 先全部禁用
    --enable-decoder=h264,hevc,aac \  # 按需启用
    --enable-encoder=libx264,aac \
    --enable-filter=scale,overlay \
    ...
```

### 裁剪策略

```
全量编译 (ffmpeg-kit-full):  ~50MB
只选 H.264+AAC+基础滤镜:    ~5-8MB
只选解码器（不编码）:        ~3MB

关键：--disable-everything 然后逐个 --enable-xxx
```

### 产出文件

```
output/arm64-v8a/lib/
  libavformat.so    ← 解封装/封装
  libavcodec.so     ← 编解码
  libavutil.so      ← 通用工具
  libavfilter.so    ← 滤镜
  libswresample.so  ← 音频重采样
  libswscale.so     ← 图像缩放

output/arm64-v8a/include/
  libavformat/*.h   ← C 头文件，JNI 代码 #include 用
  libavcodec/*.h
  ...
```

---

## 12. JNI 桥接层（第三阶段）

### 调用链路

```
Kotlin                    JNI                         C/C++
FFmpegNative.getVersion() → JNICALL → Java_..._getVersion(JNIEnv*) → avcodec_configuration()
                          ↑                          ↑
                   System.loadLibrary()        链接 libavcodec.so
                   加载 libclipforge_native.so
```

### JNI 函数命名规则

```c
// Kotlin: package com.audio.video.ffmpeg, class FFmpegNative, fun getVersion()
// C 函数名 = Java_ + 包名(点换下划线) + _类名_ + 方法名
JNIEXPORT jstring JNICALL
Java_com_audio_video_ffmpeg_FFmpegNative_getVersion(JNIEnv *env, jobject thiz)
```

### CMake 构建配置

```cmake
# 编译 JNI 库
add_library(clipforge_native SHARED native-lib.c ffmpeg_cmd.c)

# 链接 FFmpeg 预编译库
add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/libavcodec.so)

target_link_libraries(clipforge_native avformat avcodec avutil ...)
```

---

## 13. FFmpeg C API 完整转码流程（第四阶段）

### 核心数据类型

```
AVFormatContext — 文件/容器级别的上下文（整个 MP4 文件）
AVStream        — 一条流（视频流/音频流/字幕流）
AVCodecContext  — 编解码器上下文（配置参数）
AVPacket        — 一个压缩数据包（从文件读出 / 编码器产出）
AVFrame         — 一帧原始数据（解码器产出 / 送入编码器）
```

### 转码流水线完整步骤

```
1. avformat_open_input()        打开输入文件
2. avformat_find_stream_info()  探测流信息
3. av_find_best_stream()        找到视频/音频流
4. avcodec_find_decoder()       找到解码器
5. avcodec_open2()              打开解码器

6. avformat_alloc_output_context2()  创建输出上下文
7. avcodec_find_encoder()       找到编码器
8. avcodec_open2()              打开编码器
9. avformat_write_header()      写入文件头

10. 循环：
    av_read_frame()             读取压缩包
    avcodec_send_packet()       送入解码器
    avcodec_receive_frame()     取出原始帧
    [像素处理/滤镜]
    avcodec_send_frame()        送入编码器
    avcodec_receive_packet()    取出压缩包
    av_interleaved_write_frame() 写入输出文件

11. flush 编码器（取出缓冲区残留帧）
12. av_write_trailer()          写入文件尾（moov atom）
13. 释放所有资源
```

### 时间戳转换

```c
// 不同流的时间基(time_base)不同，跨流操作必须转换
av_packet_rescale_ts(pkt,
    encoder_ctx->time_base,          // 源时间基
    output_stream->time_base);       // 目标时间基

// 手动转换
int64_t new_pts = av_rescale_q(old_pts,
    (AVRational){1, 90000},          // 源：1/90000 秒
    (AVRational){1, 30});            // 目标：1/30 秒
```

### YUV 像素格式

```
YUV420P — 最常用的视频像素格式

  Y 平面（亮度）: 每个像素一个采样
    ┌────────────┐
    │ Y Y Y Y Y Y│  分辨率 = 视频分辨率 (1920×1080)
    │ Y Y Y Y Y Y│  每个 Y = 1 字节 (0~255)
    │ Y Y Y Y Y Y│
    └────────────┘

  U 平面（色度 Cb）: 每 2×2 像素共享一个采样
    ┌──────┐
    │ U U U│  分辨率 = 视频的 1/4 (960×540)
    │ U U U│
    └──────┘

  V 平面（色度 Cr）: 同 U
    ┌──────┐
    │ V V V│
    │ V V V│
    └──────┘

  总数据量 = W×H + W×H/4 + W×H/4 = W×H × 1.5
  比 RGB (W×H×3) 节省 50% 空间
  人眼对亮度敏感，对色彩不敏感 → 色度可以降采样
```
