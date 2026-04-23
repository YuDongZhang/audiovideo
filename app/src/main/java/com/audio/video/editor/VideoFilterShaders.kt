package com.audio.video.editor

import com.audio.video.data.model.VideoFilterType

/**
 * GLSL Fragment Shader 滤镜集合
 *
 * 每个滤镜本质上是一个 Fragment Shader，对视频每一帧的每个像素做颜色变换。
 *
 * GPU 渲染管线简述：
 *   顶点着色器 → 光栅化 → Fragment Shader(滤镜) → 帧缓冲 → 显示/编码
 *
 * Fragment Shader 输入：
 *   - uTexSampler: 视频帧纹理（SurfaceTexture → GL_TEXTURE_EXTERNAL_OES）
 *   - vTexCoord: 纹理坐标 (0,0)~(1,1)
 *
 * Fragment Shader 输出：
 *   - gl_FragColor: 变换后的像素颜色 RGBA
 */
object VideoFilterShaders {

    /** 获取指定滤镜类型的 Fragment Shader GLSL 代码 */
    fun getFragmentShader(filterType: VideoFilterType): String {
        return when (filterType) {
            VideoFilterType.NONE -> PASSTHROUGH
            VideoFilterType.GRAYSCALE -> GRAYSCALE
            VideoFilterType.WARM -> WARM
            VideoFilterType.COOL -> COOL
            VideoFilterType.VINTAGE -> VINTAGE
            VideoFilterType.VIVID -> VIVID
            VideoFilterType.INVERT -> INVERT
        }
    }

    // ===== 直通（不处理） =====
    private const val PASSTHROUGH = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexSampler;
        void main() {
            gl_FragColor = texture2D(uTexSampler, vTexCoord);
        }
    """

    // ===== 黑白（亮度公式：ITU-R BT.601） =====
    // 人眼对绿色最敏感，所以绿色权重最高
    private const val GRAYSCALE = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexSampler;
        void main() {
            vec4 color = texture2D(uTexSampler, vTexCoord);
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            gl_FragColor = vec4(gray, gray, gray, color.a);
        }
    """

    // ===== 暖色（增强红色通道，减弱蓝色通道） =====
    private const val WARM = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexSampler;
        void main() {
            vec4 color = texture2D(uTexSampler, vTexCoord);
            color.r = min(color.r * 1.2, 1.0);
            color.g = color.g * 1.05;
            color.b = color.b * 0.8;
            gl_FragColor = color;
        }
    """

    // ===== 冷色（增强蓝色通道，减弱红色通道） =====
    private const val COOL = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexSampler;
        void main() {
            vec4 color = texture2D(uTexSampler, vTexCoord);
            color.r = color.r * 0.8;
            color.g = color.g * 0.95;
            color.b = min(color.b * 1.3, 1.0);
            gl_FragColor = color;
        }
    """

    // ===== 复古（降饱和度 + 暖色偏移 + 暗角） =====
    private const val VINTAGE = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexSampler;
        void main() {
            vec4 color = texture2D(uTexSampler, vTexCoord);
            // 降饱和度
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            color.rgb = mix(color.rgb, vec3(gray), 0.4);
            // 棕色偏移（sepia tone）
            color.r = min(color.r * 1.15 + 0.05, 1.0);
            color.g = color.g * 1.0;
            color.b = color.b * 0.85;
            // 暗角效果
            vec2 center = vTexCoord - 0.5;
            float dist = length(center);
            float vignette = smoothstep(0.5, 0.2, dist);
            color.rgb *= mix(0.6, 1.0, vignette);
            gl_FragColor = color;
        }
    """

    // ===== 鲜艳（增强饱和度） =====
    // 将 RGB 转到 HSL 调高饱和度的简化版：拉远离灰度线的距离
    private const val VIVID = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexSampler;
        void main() {
            vec4 color = texture2D(uTexSampler, vTexCoord);
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            // 饱和度增强：将颜色拉离灰度轴
            color.rgb = clamp(mix(vec3(gray), color.rgb, 1.5), 0.0, 1.0);
            // 轻微对比度增强
            color.rgb = clamp((color.rgb - 0.5) * 1.1 + 0.5, 0.0, 1.0);
            gl_FragColor = color;
        }
    """

    // ===== 反色（颜色取反） =====
    private const val INVERT = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexSampler;
        void main() {
            vec4 color = texture2D(uTexSampler, vTexCoord);
            gl_FragColor = vec4(1.0 - color.rgb, color.a);
        }
    """
}
