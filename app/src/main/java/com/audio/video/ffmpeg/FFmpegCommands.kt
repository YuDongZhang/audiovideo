package com.audio.video.ffmpeg

/**
 * FFmpeg 学习命令集 — 每个命令附带知识点说明
 *
 * 命令分类：
 *   1. 探测信息（ffprobe）
 *   2. 转封装/转码
 *   3. 裁剪与拼接
 *   4. 视频处理（滤镜链 -vf）
 *   5. 音频处理（音频滤镜 -af）
 *   6. 复合操作（filter_complex）
 */
object FFmpegCommands {

    /** 获取所有学习命令分组 */
    fun getAllGroups(): List<CommandGroup> = listOf(
        probeGroup(),
        containerGroup(),
        trimGroup(),
        videoFilterGroup(),
        audioFilterGroup(),
        advancedGroup()
    )

    // ===== 第1组：信息探测 =====
    private fun probeGroup() = CommandGroup(
        name = "信息探测",
        description = "不编码，只读取文件元信息",
        commands = listOf(
            FFmpegCommand(
                name = "查看视频信息",
                isProbe = true,
                buildCommand = { input, _ ->
                    "-v quiet -print_format json -show_format -show_streams \"$input\""
                },
                explanation = """
                    ffprobe 是 FFmpeg 的探测工具，只读不写。
                    -print_format json: 输出 JSON 格式
                    -show_format: 显示容器信息（时长、码率、格式名）
                    -show_streams: 显示每条流的详细信息（编码、分辨率、帧率、采样率）

                    从输出中能学到：
                    · codec_name: 编码格式（h264/hevc/aac）
                    · width/height: 分辨率
                    · r_frame_rate: 帧率
                    · bit_rate: 码率（bps）
                    · sample_rate: 音频采样率
                    · channels: 声道数
                """.trimIndent(),
                tags = listOf("ffprobe", "元信息")
            ),
            FFmpegCommand(
                name = "提取关键帧列表",
                isProbe = true,
                buildCommand = { input, _ ->
                    "-select_streams v -show_frames -show_entries frame=pict_type,pts_time -of csv \"$input\""
                },
                explanation = """
                    逐帧扫描，输出每帧的类型和时间戳：
                    frame,I,0.000000  ← I帧（关键帧），可独立解码
                    frame,P,0.033000  ← P帧（预测帧），参考前帧
                    frame,B,0.066000  ← B帧（双向帧），参考前后帧

                    通过输出可以直观看到 GOP 结构
                """.trimIndent(),
                tags = listOf("ffprobe", "关键帧", "GOP")
            )
        )
    )

    // ===== 第2组：容器与编码 =====
    private fun containerGroup() = CommandGroup(
        name = "容器与编码",
        description = "理解容器格式和编码格式的区别",
        commands = listOf(
            FFmpegCommand(
                name = "转封装（不重编码）",
                buildCommand = { input, output ->
                    "-i \"$input\" -c copy \"$output.mkv\""
                },
                explanation = """
                    -c copy: 直接拷贝音视频流，不重新编码
                    MP4 → MKV：只是换了容器（信封），内容（编码数据）不变
                    所以极快（几乎瞬间完成），因为不需要解码和重编码

                    容器 vs 编码：
                    · 容器（MP4/MKV/AVI/FLV）= 信封，负责组织和索引
                    · 编码（H.264/H.265/VP9）= 信纸，压缩后的实际数据
                    · 同一个编码可以装在不同容器里
                """.trimIndent(),
                tags = listOf("容器", "-c copy")
            ),
            FFmpegCommand(
                name = "H.264 软件编码",
                buildCommand = { input, output ->
                    "-i \"$input\" -c:v libx264 -preset medium -crf 23 -c:a aac -b:a 128k \"$output.mp4\""
                },
                explanation = """
                    -c:v libx264: 使用 x264 软件编码器（CPU 计算，兼容性最好）
                    -preset medium: 编码速度/质量平衡
                      ultrafast → 最快但质量差，文件大
                      veryslow → 最慢但质量高，文件小
                    -crf 23: 恒定质量因子（0=无损, 18=视觉无损, 23=默认, 28=较差）
                      CRF 模式下码率由画面复杂度自动决定
                    -c:a aac -b:a 128k: 音频 AAC 编码，128kbps 码率
                """.trimIndent(),
                tags = listOf("H.264", "CRF", "preset")
            )
        )
    )

    // ===== 第3组：裁剪与拼接 =====
    private fun trimGroup() = CommandGroup(
        name = "裁剪与拼接",
        description = "时间裁剪、多文件拼接",
        commands = listOf(
            FFmpegCommand(
                name = "时间裁剪（快速）",
                buildCommand = { input, output ->
                    "-ss 2 -i \"$input\" -t 5 -c copy \"$output.mp4\""
                },
                explanation = """
                    -ss 2: 从第2秒开始（放在 -i 前 = 输入端 seek，快速但可能不精确）
                    -t 5: 截取5秒
                    -c copy: 不重新编码，极快

                    精确 vs 快速：
                    · -ss 在 -i 前面：输入端 seek，跳到最近关键帧，快但不精确
                    · -ss 在 -i 后面：解码端 seek，逐帧定位，精确但慢
                    · 精确裁剪需去掉 -c copy，让 FFmpeg 重新编码
                """.trimIndent(),
                tags = listOf("裁剪", "-ss", "-t")
            ),
            FFmpegCommand(
                name = "时间裁剪（精确）",
                buildCommand = { input, output ->
                    "-i \"$input\" -ss 2 -t 5 -c:v libx264 -c:a aac \"$output.mp4\""
                },
                explanation = """
                    -ss 在 -i 后面：解码端 seek
                    不用 -c copy：需要重新编码才能精确到帧
                    慢但裁剪点精确（非关键帧位置也能准确裁切）
                """.trimIndent(),
                tags = listOf("裁剪", "精确")
            )
        )
    )

    // ===== 第4组：视频滤镜 =====
    private fun videoFilterGroup() = CommandGroup(
        name = "视频滤镜 (-vf)",
        description = "视频滤镜链：缩放、裁剪、调色、变速、文字",
        commands = listOf(
            FFmpegCommand(
                name = "缩放分辨率",
                buildCommand = { input, output ->
                    "-i \"$input\" -vf \"scale=1280:720\" -c:a copy \"$output.mp4\""
                },
                explanation = """
                    -vf "scale=1280:720": 视频滤镜 → 缩放到 1280×720
                    scale=1280:-1: 宽度1280，高度自动保持比例
                    scale=-2:720: 高度720，宽度自动（-2 确保偶数，编码器要求）
                    -c:a copy: 音频不变，只处理视频
                """.trimIndent(),
                tags = listOf("scale", "分辨率")
            ),
            FFmpegCommand(
                name = "裁剪画面区域",
                buildCommand = { input, output ->
                    "-i \"$input\" -vf \"crop=iw/2:ih/2:iw/4:ih/4\" -c:a copy \"$output.mp4\""
                },
                explanation = """
                    crop=w:h:x:y → 从(x,y)位置裁出 w×h 区域
                    iw/ih = 输入宽/高（可以用表达式）
                    这个命令裁出中心 1/4 区域

                    常用：crop=iw:ih-200:0:100 → 去掉上方100px和下方100px（去黑边）
                """.trimIndent(),
                tags = listOf("crop", "画面裁剪")
            ),
            FFmpegCommand(
                name = "黑白滤镜",
                buildCommand = { input, output ->
                    "-i \"$input\" -vf \"colorchannelmixer=.299:.587:.114:0:.299:.587:.114:0:.299:.587:.114\" -c:a copy \"$output.mp4\""
                },
                explanation = """
                    colorchannelmixer: 颜色通道混合器（和我们的颜色矩阵原理一样）
                    .299:.587:.114 = BT.601 亮度权重
                    R/G/B 三行都用相同权重 → 输出灰度图

                    等价的 GLSL: float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                """.trimIndent(),
                tags = listOf("调色", "颜色矩阵")
            ),
            FFmpegCommand(
                name = "亮度/对比度/饱和度",
                buildCommand = { input, output ->
                    "-i \"$input\" -vf \"eq=brightness=0.1:contrast=1.3:saturation=1.5\" -c:a copy \"$output.mp4\""
                },
                explanation = """
                    eq 滤镜：
                    · brightness: 亮度 [-1.0, 1.0]，0=不变
                    · contrast: 对比度 [0, 2.0]，1.0=不变
                    · saturation: 饱和度 [0, 3.0]，1.0=不变，0=灰度

                    底层实现：
                    亮度 = 每个像素加偏移
                    对比度 = (pixel - 0.5) × contrast + 0.5
                    饱和度 = mix(gray, color, saturation)
                """.trimIndent(),
                tags = listOf("eq", "调色")
            ),
            FFmpegCommand(
                name = "添加文字水印",
                buildCommand = { input, output ->
                    "-i \"$input\" -vf \"drawtext=text='ClipForge':fontsize=48:fontcolor=white:x=50:y=50:shadowx=2:shadowy=2\" -c:a copy \"$output.mp4\""
                },
                explanation = """
                    drawtext 滤镜：在视频帧上绘制文字
                    · text: 文字内容
                    · fontsize: 字号
                    · fontcolor: 颜色（支持 #RRGGBB 和颜色名）
                    · x/y: 位置坐标（支持表达式，如 x=(w-tw)/2 居中）
                    · shadowx/shadowy: 阴影偏移

                    支持时间变量：text='%{pts\:hms}' → 显示当前时间戳
                """.trimIndent(),
                tags = listOf("drawtext", "水印")
            ),
            FFmpegCommand(
                name = "生成 GIF 动图",
                buildCommand = { input, output ->
                    "-i \"$input\" -ss 0 -t 3 -vf \"fps=12,scale=320:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse\" \"$output.gif\""
                },
                explanation = """
                    GIF 导出需要两步走（因为 GIF 只支持 256 色）：
                    1. palettegen: 从视频帧中分析最优 256 色调色板
                    2. paletteuse: 用这个调色板量化每一帧

                    split: 将视频流分成两路（一路生成调色板，一路用调色板）
                    fps=12: 降到 12 帧（GIF 不需要高帧率）
                    scale=320:-1: 缩小尺寸
                    flags=lanczos: 高质量缩放算法
                """.trimIndent(),
                tags = listOf("GIF", "调色板", "量化")
            )
        )
    )

    // ===== 第5组：音频处理 =====
    private fun audioFilterGroup() = CommandGroup(
        name = "音频处理 (-af)",
        description = "提取音频、混音、音量、淡入淡出",
        commands = listOf(
            FFmpegCommand(
                name = "提取音频",
                buildCommand = { input, output ->
                    "-i \"$input\" -vn -c:a copy \"$output.aac\""
                },
                explanation = """
                    -vn: 去掉视频流（v=video, n=no）
                    -c:a copy: 音频流直接拷贝（不重新编码）

                    类似地：
                    -an: 去掉音频流
                    -sn: 去掉字幕流
                """.trimIndent(),
                tags = listOf("提取", "-vn")
            ),
            FFmpegCommand(
                name = "音量调节",
                buildCommand = { input, output ->
                    "-i \"$input\" -af \"volume=1.5\" -c:v copy \"$output.mp4\""
                },
                explanation = """
                    -af "volume=1.5": 音频滤镜 → 音量增益 1.5 倍
                    volume=0.5: 减半
                    volume=0: 静音
                    volume=2: 加倍（注意溢出 clipping）

                    也支持 dB 单位：volume=3dB → 约 1.41 倍
                """.trimIndent(),
                tags = listOf("volume", "增益")
            ),
            FFmpegCommand(
                name = "淡入淡出",
                buildCommand = { input, output ->
                    "-i \"$input\" -af \"afade=t=in:d=2,afade=t=out:st=8:d=2\" -c:v copy \"$output.mp4\""
                },
                explanation = """
                    afade 滤镜：音频淡入淡出
                    afade=t=in:d=2 → 前 2 秒淡入（音量 0→1）
                    afade=t=out:st=8:d=2 → 从第 8 秒开始 2 秒淡出（音量 1→0）

                    多个滤镜用逗号连接形成"滤镜链"
                    底层和我们的 AudioFadeCalculator 原理一样：gain(t) 包络函数
                """.trimIndent(),
                tags = listOf("afade", "包络")
            )
        )
    )

    // ===== 第6组：高级操作 =====
    private fun advancedGroup() = CommandGroup(
        name = "高级操作 (filter_complex)",
        description = "多输入多输出、画中画、变速",
        commands = listOf(
            FFmpegCommand(
                name = "视频变速（2倍速）",
                buildCommand = { input, output ->
                    "-i \"$input\" -filter_complex \"[0:v]setpts=0.5*PTS[v];[0:a]atempo=2.0[a]\" -map \"[v]\" -map \"[a]\" \"$output.mp4\""
                },
                explanation = """
                    filter_complex: 复杂滤镜图（多输入/多输出）

                    [0:v]setpts=0.5*PTS[v]:
                    · [0:v] = 第 0 个输入的视频流
                    · setpts=0.5*PTS → 每帧的时间戳减半 → 2倍速播放
                    · [v] = 输出标签

                    [0:a]atempo=2.0[a]:
                    · atempo=2.0 → 音频 2 倍速（内置 WSOLA 时间拉伸，保持音调）
                    · atempo 范围 [0.5, 100]，超出需要链式：atempo=2.0,atempo=2.0 = 4倍

                    -map "[v]" -map "[a]": 选择输出流
                """.trimIndent(),
                tags = listOf("PTS", "atempo", "变速")
            ),
            FFmpegCommand(
                name = "视频慢放（0.5倍速）",
                buildCommand = { input, output ->
                    "-i \"$input\" -filter_complex \"[0:v]setpts=2.0*PTS[v];[0:a]atempo=0.5[a]\" -map \"[v]\" -map \"[a]\" \"$output.mp4\""
                },
                explanation = """
                    setpts=2.0*PTS → 时间戳加倍 → 0.5 倍速慢放
                    atempo=0.5 → 音频 0.5 倍速（拉伸一倍时长，音调不变）

                    PTS (Presentation Time Stamp) 原理：
                    原始: 帧@0ms, 帧@33ms, 帧@66ms
                    ×2:   帧@0ms, 帧@66ms, 帧@132ms → 慢放
                """.trimIndent(),
                tags = listOf("PTS", "慢放")
            ),
            FFmpegCommand(
                name = "替换音频（静音原声+加BGM）",
                buildCommand = { input, output ->
                    "-i \"$input\" -an -c:v copy \"$output.mp4\""
                },
                explanation = """
                    这是简化版 — 先去掉原声音频
                    -an: 移除所有音频流
                    -c:v copy: 视频不变

                    完整的替换音频命令需要两个输入：
                    ffmpeg -i video.mp4 -i bgm.mp3 -c:v copy -map 0:v -map 1:a -shortest output.mp4
                    · -map 0:v: 从第 0 个输入取视频
                    · -map 1:a: 从第 1 个输入取音频
                    · -shortest: 以较短的流为准结束
                """.trimIndent(),
                tags = listOf("-map", "替换音频")
            )
        )
    )
}

/** 命令分组 */
data class CommandGroup(
    val name: String,
    val description: String,
    val commands: List<FFmpegCommand>
)

/** 单个 FFmpeg 命令定义 */
data class FFmpegCommand(
    val name: String,
    val isProbe: Boolean = false,
    val buildCommand: (inputPath: String, outputPath: String) -> String,
    val explanation: String,
    val tags: List<String> = emptyList()
)
