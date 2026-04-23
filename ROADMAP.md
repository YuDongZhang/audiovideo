# ClipForge 音视频学习路线 — 功能扩展计划

## 当前已覆盖的知识点

| 知识点 | 对应功能 | 涉及技术 |
|---|---|---|
| 视频播放 | 预览播放器 | ExoPlayer、Surface 渲染、播放列表 |
| 非破坏性剪辑 | 裁剪/分割/拼接 | ClippingConfiguration、时间坐标转换 |
| 硬件编码导出 | 视频导出 | Media3 Transformer、MediaCodec（底层） |
| 媒体文件访问 | 视频选择 | MediaStore、ContentResolver、Scoped Storage |

## 建议新增功能（按知识点分层）

---

### 第一层：音频处理（难度 ★★☆）

#### 1. 音频波形可视化

**知识点**：音频解码、PCM 数据提取、波形绘制

在时间线的片段条上显示音频波形，替代纯色背景。

```
实现路径：
MediaExtractor → 选择音频轨道 → MediaCodec 解码为 PCM
→ 对 PCM 采样取峰值（每 N 个采样取一个 max）
→ Canvas 绘制波形线条
```

**核心代码涉及**：
- `MediaExtractor.setDataSource()` 选择音频轨
- `MediaCodec` 解码 AAC/MP3 → PCM（ByteBuffer）
- PCM 格式理解：采样率、位深、声道数
- 降采样算法：将百万级采样降到几百个绘制点

**学到什么**：理解音频从压缩格式到原始 PCM 的解码过程，PCM 数据结构。

---

#### 2. 音量调节 + 静音

**知识点**：音频增益控制、ExoPlayer 音频处理

每个片段独立设置音量（0% ~ 200%），支持一键静音。

```
实现路径：
- 预览：ExoPlayer player.volume 或 AudioProcessor
- 导出：Transformer + AudioProcessor / Effects
```

**学到什么**：音频增益的本质（PCM 采样值乘以系数），溢出处理（clipping）。

---

#### 3. 背景音乐 / 音频混合

**知识点**：多轨音频混合（mixing）、PCM 叠加

添加一条独立的音乐轨道，与视频原声混合。

```
实现路径：
- 时间线新增音频轨道（AudioClip 模型）
- 预览：ExoPlayer 多 MediaSource 并行播放
- 导出：Transformer AudioMixer 或手动 PCM 混合

PCM 混合核心公式：
  output[i] = clamp(videoAudio[i] * vol1 + bgmAudio[i] * vol2, -32768, 32767)
```

**学到什么**：音频多轨混合原理、采样率对齐（重采样）、声道匹配。

---

#### 4. 淡入淡出

**知识点**：音频包络（envelope）、时间域处理

片段头尾添加淡入/淡出效果。

```
淡入：前 N 毫秒内，音量从 0 线性增长到 1
  gain(t) = t / fadeDurationMs      (0 ≤ t ≤ fadeDuration)
  pcm[i] = pcm[i] * gain(t)

淡出：后 N 毫秒内，音量从 1 线性衰减到 0
  gain(t) = (endTime - t) / fadeDurationMs
```

**学到什么**：时间域音频处理、包络函数、实时 vs 离线处理。

---

### 第二层：视频特效与滤镜（难度 ★★★）

#### 5. 视频滤镜（黑白、暖色、冷色、复古...）

**知识点**：OpenGL ES、Fragment Shader、GPU 渲染管线

通过 GPU Shader 实时处理每一帧画面。

```
实现路径：
- 预览：ExoPlayer + GlEffect（Media3 Effect 模块）
- 导出：Transformer + VideoFrameProcessor

GLSL 灰度滤镜示例：
  vec4 color = texture2D(uTexture, vTexCoord);
  float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
  gl_FragColor = vec4(gray, gray, gray, color.a);
```

**核心概念**：
- OpenGL ES 渲染管线：顶点着色器 → 光栅化 → 片段着色器
- SurfaceTexture：将视频帧作为 GL 纹理输入
- EGL 上下文：GPU 渲染环境搭建

**学到什么**：GPU 渲染管线、GLSL 编程、纹理采样、颜色空间。

---

#### 6. LUT 调色

**知识点**：3D LUT（Look-Up Table）、颜色映射

加载 .cube 文件实现电影级调色。

```
原理：LUT 是一个 3D 颜色映射表
  输入 RGB → 查表 → 输出新 RGB

GLSL 实现：将 LUT 编码为 3D 纹理，在 Fragment Shader 中采样
  vec3 lutCoord = color.rgb * (lutSize - 1.0) / lutSize + 0.5 / lutSize;
  vec3 mapped = texture3D(uLut, lutCoord).rgb;
```

**学到什么**：颜色空间映射、3D 纹理、调色工作流。

---

#### 7. 转场效果（交叉溶解、滑动、缩放...）

**知识点**：帧间混合、OpenGL 多纹理渲染

两个片段之间添加过渡动画。

```
交叉溶解 (Crossfade)：
  前片段最后 N 帧 + 后片段前 N 帧
  output = texA * (1 - progress) + texB * progress

滑动 (Slide)：
  output = mix(texA, texB, step(vTexCoord.x, progress))
```

**关键挑战**：转场期间需要同时解码两个视频源、两个纹理输入 Shader。

**学到什么**：多纹理 GPU 合成、帧级别的时间控制。

---

#### 8. 变速播放（慢动作 / 快进）

**知识点**：音视频同步、PTS 时间戳操作

每个片段独立设置播放速度（0.25x ~ 4x）。

```
实现路径：
- 预览：ExoPlayer PlaybackParameters(speed)
- 导出：Transformer SpeedChangeEffect

音视频同步问题：
  视频：丢帧（快进）或插帧（慢放）
  音频：时间拉伸（Sonic 算法）保持音调不变
```

**学到什么**：PTS/DTS 时间戳、音视频同步机制、音频时间拉伸算法。

---

### 第三层：底层编解码（难度 ★★★★）

#### 9. 自定义 MediaCodec 编码器

**知识点**：Android 硬件编解码 API、H.264/H.265 编码参数

绕过 Transformer，直接用 MediaCodec + MediaMuxer 实现导出。

```
流程：
MediaExtractor(输入) → MediaCodec(解码) → Surface → MediaCodec(编码) → MediaMuxer(输出)

关键参数：
  - 编码格式：H.264 (AVC) / H.265 (HEVC)
  - 码率控制：CBR / VBR / CQ
  - I帧间隔：GOP 结构
  - Profile & Level：Baseline / Main / High
```

**学到什么**：编解码器工作原理、码率控制策略、GOP 结构、硬件加速 vs 软件编码。

---

#### 10. 逐帧预览 / 关键帧理解

**知识点**：I帧/P帧/B帧、关键帧 seek

添加逐帧前进/后退按钮，可视化关键帧位置。

```
实现要点：
- MediaCodec BUFFER_FLAG_KEY_FRAME 识别关键帧
- seek 到非关键帧需要从前一个关键帧开始解码（seek 延迟的根本原因）
- 在时间线上用标记显示关键帧位置

ExoPlayer 精确 seek：
  player.seekTo(position)  // 默认 seek 到最近关键帧
  // 精确 seek 需要解码从关键帧到目标帧之间的所有帧
```

**学到什么**：视频帧类型（I/P/B）、GOP 结构、seek 性能瓶颈的根源。

---

#### 11. 视频录制（CameraX）

**知识点**：相机采集、预览渲染、音视频同步录制

内置录像功能，录完直接进入编辑。

```
CameraX 架构：
  Preview（预览流） → SurfaceProvider → 屏幕
  VideoCapture（录制流） → MediaCodec → MP4 文件

同时处理：
  - 视频帧：Camera → Surface → H.264 编码器
  - 音频帧：AudioRecord → AAC 编码器
  - MediaMuxer 将两路流合并写入 MP4 容器
```

**学到什么**：Camera2/CameraX 管线、音视频同步录制、容器格式（MP4 = ftyp + moov + mdat）。

---

#### 12. 画中画 / 多层合成

**知识点**：多视频源合成、OpenGL FBO（帧缓冲对象）

主视频上叠加一个小窗视频。

```
OpenGL 合成流程：
  1. 创建 FBO
  2. 将主视频渲染到全屏四边形
  3. 将画中画视频渲染到右下角小四边形
  4. 将 FBO 颜色附件输出到 Surface

关键：两路视频需要独立的 MediaCodec 解码器，同步到同一时间轴
```

**学到什么**：FBO 离屏渲染、多路视频同步解码、图层合成。

---

### 第四层：高级主题（难度 ★★★★★）

#### 13. 文字 / 贴纸叠加

**知识点**：视频帧上的图形叠加、时间轴控制

在指定时间段内显示文字或图片贴纸。

```
方案 A（OpenGL）：文字渲染为 Bitmap → 上传为纹理 → Shader 叠加
方案 B（Canvas）：在 VideoFrameProcessor 的 onDraw 中用 Canvas 绘制

时间控制：
  if (currentTimeMs in sticker.startTime..sticker.endTime) {
      drawSticker(canvas, sticker)
  }
```

---

#### 14. 视频抽帧生成 GIF

**知识点**：帧提取、GIF 编码、图像量化

选定时间范围，提取帧并编码为 GIF。

```
流程：
MediaCodec 解码指定时间范围的帧 → Bitmap 序列
→ 颜色量化（256色调色板）→ LZW 编码 → GIF 文件

关键：GIF 只支持 256 色，需要 NeuQuant / MedianCut 量化算法
```

---

#### 15. RTMP/RTSP 推流

**知识点**：流媒体协议、实时编码

将编辑好的视频实时推送到直播服务器。

```
采集 → 编码（H.264 + AAC） → RTMP 封装 → 网络发送

涉及：
  - RTMP 握手和消息格式
  - FLV Tag 封装
  - 网络抖动处理（缓冲队列）
```

---

## 推荐实施顺序

```
第一阶段（音频基础）：
  ① 音频波形可视化     ← 学会 MediaExtractor + MediaCodec 解码
  ② 音量调节 + 静音    ← 理解 PCM 数据操作
  ③ 淡入淡出           ← 时间域音频处理

第二阶段（GPU 渲染）：
  ④ 视频滤镜           ← 入门 OpenGL ES + GLSL
  ⑤ 变速播放           ← 理解 PTS 和音视频同步
  ⑥ 转场效果           ← 多纹理 GPU 合成

第三阶段（底层编解码）：
  ⑦ 自定义 MediaCodec   ← 彻底理解编解码流程
  ⑧ 逐帧预览 + 关键帧   ← 理解 I/P/B 帧和 GOP
  ⑨ 视频录制            ← 相机采集 + 实时编码

第四阶段（综合应用）：
  ⑩ 文字贴纸             ← 视频帧图形叠加
  ⑪ 背景音乐混合         ← 多轨音频处理
  ⑫ LUT 调色             ← 3D 纹理 + 颜色科学
```

## 知识点全景图

```
                    ┌─────────────────┐
                    │   音视频基础     │
                    ├─────────────────┤
                    │ 容器格式 MP4/MKV│ ← 视频录制、导出
                    │ 编码 H264/H265  │ ← MediaCodec、Transformer
                    │ 音频 AAC/PCM    │ ← 波形、混音、淡入淡出
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │  解码/播放  │  │  编辑/处理  │  │  编码/输出  │
     ├────────────┤  ├────────────┤  ├────────────┤
     │ ExoPlayer  │  │ 裁剪/分割  │  │ Transformer│
     │ MediaCodec │  │ 滤镜/调色  │  │ MediaCodec │
     │ Surface    │  │ 转场/变速  │  │ MediaMuxer │
     │ 帧提取     │  │ 文字贴纸  │  │ RTMP推流   │
     │ 关键帧seek │  │ 音频混合  │  │ GIF编码    │
     └────────────┘  └────────────┘  └────────────┘
              │              │              │
              ▼              ▼              ▼
     ┌─────────────────────────────────────────┐
     │          GPU 渲染管线 (OpenGL ES)        │
     ├─────────────────────────────────────────┤
     │ EGL 上下文 → 顶点着色器 → 光栅化        │
     │ → 片段着色器(滤镜/LUT/转场) → 帧缓冲   │
     │ SurfaceTexture → 纹理采样 → FBO合成     │
     └─────────────────────────────────────────┘
```
