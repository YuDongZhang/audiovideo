# ClipForge 技术要点

## 架构概览

```
com.audio.video/
├── ui/                    # Jetpack Compose UI 层
│   ├── navigation/        # 导航路由（4个页面）
│   ├── theme/             # 暗色专业主题
│   └── screen/            # 各页面 Screen + ViewModel + 组件
├── data/                  # 数据层
│   ├── model/             # 数据模型（Project, VideoClip, TimelineState...）
│   └── repository/        # 持久化（SharedPreferences + Gson）
├── player/                # ExoPlayer 封装
├── editor/                # 时间线纯逻辑引擎
└── export/                # Media3 Transformer 导出
```

**架构模式**：MVVM，每个页面一个 ViewModel，通过 StateFlow 驱动 Compose UI。

---

## 1. 视频播放器 — ExoPlayer + ClippingConfiguration

### 核心思路

用 ExoPlayer 播放列表实现多片段无缝衔接。每个片段通过 `MediaItem.ClippingConfiguration` 设置入点/出点，ExoPlayer 自动处理裁剪和片段过渡。

```kotlin
// 为每个片段创建带裁剪配置的 MediaItem
MediaItem.Builder()
    .setUri(clip.sourceUri)
    .setClippingConfiguration(
        ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startTimeMs)  // 入点
            .setEndPositionMs(clip.endTimeMs)       // 出点
            .build()
    )
    .build()

// 设置播放列表，ExoPlayer 会按顺序无缝播放
player.setMediaItems(mediaItems)
player.prepare()
```

### 全局位置 ↔ 片段位置 转换

时间线上的播放位置是"全局毫秒"，但 ExoPlayer 的 seek 是"第 N 个片段的第 M 毫秒"。需要双向转换：

```
全局位置 8500ms，片段列表 [3000ms, 2000ms, 6000ms]
→ 8500 - 3000 - 2000 = 3500，落在第3个片段的 3500ms 处
→ player.seekTo(2, 3500)
```

**关键坑**：`ClippingConfiguration` 下 `player.seekTo(index, positionMs)` 的 `positionMs` 是相对于裁剪窗口起始的偏移量（从 0 开始），不是原始视频的绝对时间。同理 `player.currentPosition` 返回的也是窗口内偏移。

### 播放列表重建

编辑操作（分割、裁剪、删除）后需要重建播放列表。重建前保存当前播放位置，重建后恢复：

```kotlin
val previousPosition = state.currentPositionMs
player.setMediaItems(newMediaItems)
player.prepare()
seekTo(previousPosition.coerceIn(0, newTotalDuration))
```

### 位置跟踪

播放时以 16ms 间隔（约 60fps）轮询 `getCurrentGlobalPosition()`，通过 StateFlow 推送到 UI 驱动播放头移动。暂停时停止轮询。

---

## 2. 时间线 UI — 全局视图 + 双指缩放

### 自适应铺满屏幕

默认 `zoomLevel = 1.0` 时，所有片段刚好占满屏幕宽度：

```kotlin
val fitPxPerMs = screenWidthPx / totalDurationMs  // 基准比率
val pxPerMs = fitPxPerMs * zoomLevel               // 实际比率
```

每个片段宽度按时长占比分配。放大后超出屏幕宽度时自动启用水平滚动。

### 手势分区设计

为避免"点击选中片段"和"点击定位播放头"的冲突，采用**统一触摸处理 + 自动选中**：

| 区域 | 手势 | 行为 |
|---|---|---|
| 刻度尺（36dp） | 点击 / 拖拽 | 播放头跳转 + 自动选中所在片段 |
| 片段轨道（72dp） | 点击 / 拖拽 | 播放头跳转 + 自动选中所在片段 |
| 片段轨道 | 双指捏合 | 时间线缩放 |
| 选中片段左右边缘 | 水平拖拽 | 裁剪入点/出点 |

**核心：片段不处理点击事件**，所有触摸都由 Timeline 统一转换为时间位置。选中状态由播放头位置自动决定（播放头在哪个片段范围内，就选中哪个片段）。

### 双指缩放实现

使用低级手势 API，仅在检测到 2 根及以上手指时才处理缩放，单指触摸透传给子组件：

```kotlin
awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    do {
        val event = awaitPointerEvent()
        if (event.changes.size >= 2) {
            val zoom = event.calculateZoom()
            onZoomChanged(currentZoom * zoom)
            event.changes.forEach { it.consume() }
        }
    } while (event.changes.any { it.pressed })
}
```

---

## 3. 时间线编辑引擎 — 裁剪、分割与合并

### 3.1 数据模型

所有编辑操作的基础是 `VideoClip`，它描述了一个片段在原始视频中的"窗口"：

```kotlin
data class VideoClip(
    val id: String,              // 片段唯一标识
    val sourceUri: String,       // 原始视频文件 URI
    val startTimeMs: Long,       // 入点：从原始视频的哪个时间开始
    val endTimeMs: Long,         // 出点：到原始视频的哪个时间结束
    val originalDurationMs: Long, // 原始视频总时长（裁剪上限）
    val displayOrder: Int         // 在时间线上的排列顺序
) {
    val trimmedDurationMs: Long   // 有效时长 = endTimeMs - startTimeMs
}
```

**关键概念**：编辑操作**不修改原始视频文件**，只调整 `startTimeMs` / `endTimeMs` 这两个时间指针。所有裁剪都是"非破坏性编辑"，直到导出时才真正重新编码。

```
原始视频 (10s):  [=============================]
                  0s                          10s

入点=2s, 出点=7s: [     |=================|     ]
                       2s                 7s
                       ↑ startTimeMs      ↑ endTimeMs

有效时长 = 7000 - 2000 = 5000ms
```

### 3.2 坐标系统 — 三种时间

编辑引擎中存在三种时间坐标，必须严格区分：

| 坐标 | 含义 | 示例 |
|---|---|---|
| **原始时间** | 在源视频文件中的绝对位置 | `startTimeMs=2000, endTimeMs=7000` |
| **片段内时间** | 从片段入点算起的偏移量 | `0 ~ trimmedDurationMs`（0~5000ms） |
| **全局时间** | 在整条时间线上的位置 | 前面片段时长累加 + 当前片段内偏移 |

```
时间线: [片段A: 3s] [片段B: 5s] [片段C: 4s]
全局:    0---3s----3---8s-----8---12s
片段内:  0---3     0---5      0---4

全局 6s → 落在片段B → 片段内偏移 = 6 - 3 = 3s
       → 原始时间 = B.startTimeMs + 3s
```

**转换公式**：

```
全局时间 → (片段索引, 片段内偏移):
    accumulated = 0
    for clip in clips:
        if accumulated + clip.duration > globalPosition:
            return (clip, globalPosition - accumulated)
        accumulated += clip.duration

片段内偏移 → 原始时间:
    absoluteTime = clip.startTimeMs + offsetInClip

原始时间 → 片段内偏移:
    offsetInClip = absoluteTime - clip.startTimeMs
```

### 3.3 分割操作

在播放头位置将一个片段切成两半。**不拷贝视频数据**，只是把一个时间窗口拆成两个相邻窗口。

```
操作前:
  片段X: sourceUri=video.mp4, start=2000, end=8000 (时长6s)

在全局位置 globalPos 分割（假设片段内偏移=3500ms）:
  分割点绝对时间 = X.startTimeMs + 3500 = 5500ms

操作后:
  片段X:  sourceUri=video.mp4, start=2000, end=5500 (时长3.5s, 保留原ID)
  片段X': sourceUri=video.mp4, start=5500, end=8000 (时长2.5s, 新ID)
```

**算法流程**：

```
splitClip(clips, globalPositionMs):
  accumulated = 0
  for each clip (按 displayOrder 排序):
    clipDuration = clip.endTimeMs - clip.startTimeMs

    if accumulated < globalPositionMs < accumulated + clipDuration:
      // 分割点落在这个片段内
      splitPointInClip = globalPositionMs - accumulated
      absoluteSplitPoint = clip.startTimeMs + splitPointInClip

      前半段 = clip.copy(endTimeMs = absoluteSplitPoint)
      后半段 = clip.copy(id = newUUID, startTimeMs = absoluteSplitPoint)
      输出两个片段
    else:
      输出原片段（不变）

    accumulated += clipDuration
  重新编号 displayOrder
```

**边界保护**：分割点必须在片段内部（`accumulated < globalPos < accumulated + duration`），否则跳过不分割。

### 3.4 裁剪操作

拖拽片段左右边缘的手柄，调整入点或出点。

#### 裁剪入点（左手柄右移 → 片段缩短）

```
操作前: start=2000, end=8000 (时长6s)
拖拽左手柄右移 1.5s:
操作后: start=3500, end=8000 (时长4.5s)

约束: newStart ∈ [0, endTimeMs - 100ms]
       ↑ 不能为负   ↑ 至少保留100ms
```

#### 裁剪出点（右手柄左移 → 片段缩短）

```
操作前: start=2000, end=8000 (时长6s)
拖拽右手柄左移 2s:
操作后: start=2000, end=6000 (时长4s)

约束: newEnd ∈ [startTimeMs + 100ms, originalDurationMs]
       ↑ 至少保留100ms          ↑ 不能超过原始视频时长
```

#### 像素 → 时间 转换

用户拖拽的是像素偏移，需要转换为时间偏移：

```
用户拖拽 deltaPx 像素
pxPerMs = screenWidth / totalDurationMs * zoomLevel   // 时间线的像素/毫秒比率
deltaMs = deltaPx / pxPerMs                            // 转换为毫秒
newStartMs = clip.startTimeMs + deltaMs                // 应用到入点
```

### 3.5 合并（拼接）

多个片段在时间线上**按 `displayOrder` 顺序首尾相连**，就是"合并"。不需要专门的合并函数 — 片段列表本身就是合并结果。

```
添加新片段:
  从媒体选择器选中视频 → 创建 VideoClip(startTimeMs=0, endTimeMs=duration)
  → 追加到现有列表末尾，displayOrder = 现有片段数

时间线:  [已有片段A] [已有片段B] [新加片段C]
                                 ↑ displayOrder = 2
```

**播放时的合并**：ExoPlayer 播放列表天然支持多片段顺序播放。设置 `player.setMediaItems(list)` 后，播放器自动无缝衔接。

**导出时的合并**：Media3 Transformer 通过 `EditedMediaItemSequence` 将多个片段编码为单个输出文件：

```kotlin
val sequence = EditedMediaItemSequence.Builder(editedItems).build()
val composition = Composition.Builder(sequence).build()
transformer.start(composition, outputPath)
// Transformer 内部：解码每个片段 → 按顺序送入编码器 → 输出单个 MP4
```

### 3.6 删除与重排序

**删除**：从列表移除指定片段，重新编号 `displayOrder`：

```
操作前: [A(order=0), B(order=1), C(order=2)]
删除 B:  [A(order=0), C(order=1)]   ← C 的 order 从 2 变为 1
```

**重排序**：移动片段位置，其余片段依次后移/前移：

```
操作前: [A(0), B(1), C(2)]
将 C 移到 A 前面:
操作后: [C(0), A(1), B(2)]
```

### 3.7 编辑操作的完整数据流

```
用户操作（如分割）
    │
    ▼
EditorViewModel.splitAtPlayhead()
    │
    ├── 1. pushUndo(currentClips)         // 快照压入撤销栈
    │
    ├── 2. TimelineEngine.splitClip()     // 纯函数计算新片段列表
    │       输入: List<VideoClip> + 播放头位置
    │       输出: 新的 List<VideoClip>
    │
    ├── 3. updateClips(newClips)
    │       ├── 更新 UI 状态（TimelineState）
    │       ├── playerManager.setTimeline(newClips)  // 重建播放列表
    │       │     ├── 保存当前播放位置
    │       │     ├── player.setMediaItems(...)
    │       │     ├── player.prepare()
    │       │     └── seekTo(之前的位置)              // 恢复播放位置
    │       └── saveProject()                         // 持久化到本地
    │
    └── 4. UI 自动刷新（Compose 响应 StateFlow 变化）
            ├── 时间线重绘片段条
            ├── 播放头位置更新
            └── 视频预览帧更新
```

### 3.8 撤销/重做

基于**状态快照栈**，每次操作前完整保存 `List<VideoClip>`：

```
操作序列:  原始状态 → 分割 → 裁剪 → 删除

undoStack: [原始状态, 分割后状态, 裁剪后状态]
redoStack: []
当前状态:  删除后状态

执行撤销:
  undoStack: [原始状态, 分割后状态]
  redoStack: [删除后状态]
  当前状态:  裁剪后状态  ← 从 undoStack 弹出

再次撤销:
  undoStack: [原始状态]
  redoStack: [删除后状态, 裁剪后状态]
  当前状态:  分割后状态

执行重做:
  undoStack: [原始状态, 分割后状态]
  redoStack: [删除后状态]
  当前状态:  裁剪后状态  ← 从 redoStack 弹出
```

**规则**：
- 每次新操作：当前状态压入 undoStack，清空 redoStack
- 撤销：当前状态压入 redoStack，undoStack 弹出恢复
- 重做：当前状态压入 undoStack，redoStack 弹出恢复
- 栈深限制 50 步，超出时丢弃最老的快照

`VideoClip` 是 `data class`，`List.copy` 成本低（只复制引用），50 步快照的内存开销可忽略。

### 3.9 设计原则

| 原则 | 做法 |
|---|---|
| **非破坏性** | 只修改时间指针，不动原始文件 |
| **纯函数** | `TimelineEngine` 无状态无副作用，输入 → 输出 |
| **不可变** | 每次操作返回新的 `List<VideoClip>`，不修改原列表 |
| **单一数据源** | `EditorViewModel.uiState` 是唯一真相来源 |
| **编辑/播放同步** | 每次 `updateClips` 同时更新 UI 状态和播放器播放列表 |

---

## 4. 视频导出 — Media3 Transformer

### 导出流程

```
片段列表 → EditedMediaItem（带 ClippingConfiguration）
         → EditedMediaItemSequence（按顺序排列）
         → Composition
         → Transformer.start(composition, outputPath)
```

Transformer 使用硬件加速编码器（MediaCodec），自动处理片段拼接、转码和封装。

### 进度追踪

通过后台线程每 500ms 轮询 `transformer.getProgress(progressHolder)`，将 0~100 的整数进度转换为 0.0~1.0 浮点数推送到 UI。

### 写入相册

导出完成后通过 MediaStore API 将文件写入 `Movies/ClipForge/` 目录。Android Q+ 使用 `IS_PENDING` 机制确保写入过程中文件对其他应用不可见：

```kotlin
// 1. 插入 pending 状态的记录
values.put(IS_PENDING, 1)
val uri = contentResolver.insert(EXTERNAL_CONTENT_URI, values)
// 2. 写入文件内容
contentResolver.openOutputStream(uri).use { ... }
// 3. 取消 pending，文件变为可见
values.put(IS_PENDING, 0)
contentResolver.update(uri, values, null, null)
```

---

## 5. 暗色主题系统

强制暗色，不跟随系统。色板设计：

| 层级 | 色值 | 用途 |
|---|---|---|
| `#0D0D0D` | 最深背景 | 编辑器画布 |
| `#141414` | 页面背景 | 各页面 Surface |
| `#1A1A1A` | 卡片/面板 | 项目卡片、底部栏 |
| `#242424` | 轨道背景 | 时间线轨道 |
| `#4A90FF` | 强调色 | 播放头、选中态、主按钮 |
| `#2D5A8E` | 片段默认色 | 时间线上的片段条 |

Material 3 的 `darkColorScheme` 映射到自定义色板，`AudioVideoTheme` 只接受 `content` 参数，无明暗切换。

---

## 6. 权限适配

针对 Android 碎片化的存储权限：

| API 级别 | 所需权限 |
|---|---|
| 24 ~ 28 | `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` |
| 29 ~ 32 | `READ_EXTERNAL_STORAGE`（写入走 Scoped Storage） |
| 33+ | `READ_MEDIA_VIDEO` |

运行时通过 Accompanist Permissions 库在媒体选择页按需请求，未授权时显示引导界面。

---

## 7. 数据持久化

当前使用 SharedPreferences + Gson 序列化项目列表。每个 `Project` 包含嵌套的 `List<VideoClip>`，整体序列化为 JSON 字符串存储。

保存时机：
- 编辑操作后自动保存（`updateClips` → `saveProject`）
- 返回首页时保存
- 进入导出页前保存

后续可升级为 Room 数据库以支持更复杂的查询和更好的性能。

---

## 8. 依赖清单

| 库 | 版本 | 用途 |
|---|---|---|
| Media3 ExoPlayer | 1.5.1 | 视频播放 |
| Media3 Transformer | 1.5.1 | 视频导出/编码 |
| Navigation Compose | 2.9.0 | 页面导航 |
| Lifecycle ViewModel Compose | 2.10.0 | ViewModel + Compose 集成 |
| Coil Compose + Video | 2.7.0 | 视频缩略图加载 |
| Accompanist Permissions | 0.36.0 | 运行时权限 |
| Gson | 2.11.0 | JSON 序列化 |
| Compose BOM | 2024.09.00 | Compose 版本管理 |

**注意**：Compose BOM 2024.09.00 的 `FlowRow` 签名与新版不兼容（缺少 `FlowRowOverflow` 参数），已改用 `Row` 替代。

---

## 9. 音频波形可视化

### 解码链路

```
MediaExtractor.setDataSource(uri)     ← 打开媒体文件
    ↓
selectTrack(audioTrackIndex)          ← 选择音频轨（"audio/mp4a-latm" 等）
    ↓
MediaCodec.createDecoderByType(mime)  ← 创建对应格式的解码器
    ↓
解码循环:
    extractor.readSampleData() → codec.queueInputBuffer()   ← 送入压缩数据
    codec.dequeueOutputBuffer() → ByteBuffer (PCM 16bit)    ← 取出原始 PCM
    ↓
PCM Short 数组 → 多声道取平均 → 降采样取峰值 → 归一化 [0.0, 1.0]
    ↓
Canvas 绘制居中对称竖线条
```

### PCM 数据格式

```
16bit signed little-endian:
  每个采样 = 2 字节 (Short)，范围 [-32768, 32767]

双声道交织存储:
  [L0][R0][L1][R1][L2][R2]...
  合并为单声道: mono[i] = (|L[i]| + |R[i]|) / 2

常见参数:
  采样率: 44100Hz (CD) / 48000Hz (视频)
  位深: 16bit
  声道: 1(单声道) / 2(立体声)
```

### 降采样算法

将百万级 PCM 采样降到 ~80 个绘制点：

```
原始: [0.1, 0.3, 0.8, 0.2, 0.5, 0.9, ...]  (100万个采样)
分桶: 100万 / 80 = 12500 个采样一桶
每桶取峰值: bucket[i] = max(samples[i*12500 .. (i+1)*12500])
归一化: peak[i] = peak[i] / max(allPeaks)
结果: [0.12, 0.45, 1.0, 0.33, ...]  (80个值, 范围[0,1])
```

---

## 10. 音量控制与淡入淡出

### 音量增益

PCM 采样值乘以增益系数即为音量调节的本质：

```
原始采样: sample = 16000 (约半音量)
增益 1.5: output = sample × 1.5 = 24000 ← 增益放大
增益 0.0: output = 0                     ← 静音
增益 2.0: output = 32000                  ← 最大增益

溢出保护 (clipping):
  output = clamp(sample × gain, -32768, 32767)
```

预览时通过 `player.volume` 实时应用，导出时 `volume=0` 通过 `setRemoveAudio(true)` 移除音频轨。

### 音频包络 (Envelope)

淡入淡出的本质是对音量施加一个随时间变化的包络函数：

```
音量
 1.0 |        ┌──────────────────────┐
     |       /                        \
     |      /     正常播放区间          \
     |     /                            \
 0.0 |____/                              \____
     0   fadeIn                    fadeOut  duration
         ↑ 线性增长               线性衰减 ↑

gain(t):
  if t < fadeInMs:
    return t / fadeInMs                    ← 淡入：线性 0→1
  if t > duration - fadeOutMs:
    return (duration - t) / fadeOutMs      ← 淡出：线性 1→0
  else:
    return 1.0                             ← 正常
```

实时应用：位置跟踪协程每 16ms 计算一次 `AudioFadeCalculator.calculateVolumeAtPosition()`，将结果写入 `player.volume`，实现播放中的平滑淡入淡出。
