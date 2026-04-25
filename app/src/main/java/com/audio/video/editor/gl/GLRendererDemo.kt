package com.audio.video.editor.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 自定义 OpenGL ES 2.0 渲染器 — 从零搭建 GPU 渲染管线
 *
 * OpenGL ES 渲染管线：
 *
 *   顶点数据 (CPU)
 *       │
 *       ▼
 *   ┌──────────────┐
 *   │ 顶点着色器    │ ← 每个顶点执行一次，计算屏幕位置
 *   │ (Vertex Shader)│    输入: attribute (顶点坐标、颜色)
 *   └──────┬───────┘    输出: gl_Position (裁剪空间坐标)
 *          │
 *          ▼
 *   ┌──────────────┐
 *   │ 光栅化        │ ← GPU 硬件自动执行
 *   │ (Rasterize)  │    将三角形转为像素片段
 *   └──────┬───────┘    对 varying 变量做插值
 *          │
 *          ▼
 *   ┌──────────────┐
 *   │ 片段着色器    │ ← 每个像素执行一次，计算颜色
 *   │ (Fragment)   │    输入: varying (插值后的颜色/纹理坐标)
 *   └──────┬───────┘    输出: gl_FragColor (RGBA 颜色)
 *          │
 *          ▼
 *   帧缓冲 → 屏幕显示
 *
 * EGL 环境：
 *   EGLDisplay → EGLConfig → EGLContext → EGLSurface
 *   GLSurfaceView 自动管理 EGL 生命周期，我们只需实现 Renderer 接口
 */
class GLRendererDemo : GLSurfaceView.Renderer {

    // Shader 程序 ID
    private var programId = 0

    // 顶点缓冲
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer

    // 时间（用于动画）
    private var startTime = System.nanoTime()

    // ===== 顶点着色器 GLSL =====
    // attribute: 每顶点不同的输入（从 CPU 传入）
    // varying: 传给片段着色器的输出（光栅化时自动插值）
    // uniform: 每帧相同的全局变量（如变换矩阵）
    private val vertexShaderCode = """
        attribute vec4 aPosition;    // 顶点位置 (x, y, z, w)
        attribute vec4 aColor;       // 顶点颜色 (r, g, b, a)
        varying vec4 vColor;         // 传给片段着色器（三角形内部自动插值）
        uniform float uTime;         // 时间（用于动画）

        void main() {
            // 顶点位置加上时间相关的偏移（旋转动画）
            float angle = uTime * 0.5;
            mat2 rotation = mat2(cos(angle), -sin(angle),
                                 sin(angle),  cos(angle));
            vec2 rotated = rotation * aPosition.xy;
            gl_Position = vec4(rotated, 0.0, 1.0);
            vColor = aColor;
        }
    """.trimIndent()

    // ===== 片段着色器 GLSL =====
    // 每个像素执行一次，输出该像素的颜色
    private val fragmentShaderCode = """
        precision mediump float;     // 中等精度（移动端标准）
        varying vec4 vColor;         // 从顶点着色器插值得到的颜色

        void main() {
            gl_FragColor = vColor;   // 直接使用插值颜色
        }
    """.trimIndent()

    /**
     * Surface 创建时调用 — 初始化 GL 环境
     * 对应 EGL: eglCreateContext + eglMakeCurrent
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置清屏颜色（深色背景）
        GLES20.glClearColor(0.05f, 0.05f, 0.05f, 1.0f)

        // 创建三角形顶点数据
        setupVertexData()

        // 编译 Shader + 链接程序
        programId = createProgram(vertexShaderCode, fragmentShaderCode)
    }

    /**
     * Surface 尺寸变化时调用 — 设置视口
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * 每帧绘制 — 核心渲染循环
     * GLSurfaceView 默认 ~60fps 调用
     */
    override fun onDrawFrame(gl: GL10?) {
        // 清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 使用着色器程序
        GLES20.glUseProgram(programId)

        // 传入时间 uniform（用于旋转动画）
        val timeLocation = GLES20.glGetUniformLocation(programId, "uTime")
        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000f
        GLES20.glUniform1f(timeLocation, elapsed)

        // 绑定顶点位置 attribute
        val positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,                   // 每个顶点 2 个分量 (x, y)
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        // 绑定顶点颜色 attribute
        val colorHandle = GLES20.glGetAttribLocation(programId, "aColor")
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(
            colorHandle,
            4,                   // 每个颜色 4 个分量 (r, g, b, a)
            GLES20.GL_FLOAT,
            false,
            0,
            colorBuffer
        )

        // 绘制三角形（3 个顶点）
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)

        // 解绑
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    /** 创建三角形的顶点坐标和颜色数据 */
    private fun setupVertexData() {
        // 三角形三个顶点的坐标 (NDC: -1.0 ~ 1.0)
        val vertices = floatArrayOf(
            0.0f,  0.6f,   // 顶部
            -0.5f, -0.4f,  // 左下
            0.5f, -0.4f    // 右下
        )
        vertexBuffer = createFloatBuffer(vertices)

        // 三个顶点的颜色（RGB渐变 — 光栅化时自动插值）
        val colors = floatArrayOf(
            1.0f, 0.2f, 0.2f, 1.0f,  // 红
            0.2f, 1.0f, 0.2f, 1.0f,  // 绿
            0.3f, 0.5f, 1.0f, 1.0f   // 蓝
        )
        colorBuffer = createFloatBuffer(colors)
    }

    /** FloatArray → native FloatBuffer（GL 需要 native 内存） */
    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
            .apply { position(0) }
    }

    /**
     * 编译 Shader + 链接为程序
     *
     * Shader 编译流程：
     *   1. glCreateShader() → 创建着色器对象
     *   2. glShaderSource() → 载入 GLSL 源码
     *   3. glCompileShader() → 编译为 GPU 指令
     *   4. glCreateProgram() → 创建程序对象
     *   5. glAttachShader() → 附加顶点+片段着色器
     *   6. glLinkProgram() → 链接为可执行程序
     */
    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        // 检查链接结果
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("程序链接失败: $log")
        }

        return program
    }

    /** 编译单个 Shader */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            val typeName = if (type == GLES20.GL_VERTEX_SHADER) "顶点" else "片段"
            throw RuntimeException("${typeName}着色器编译失败: $log")
        }

        return shader
    }
}
