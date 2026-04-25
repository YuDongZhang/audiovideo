package com.audio.video.ui.screen.gldemo

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.audio.video.editor.gl.GLRendererDemo
import com.audio.video.ui.theme.EditorBlack
import com.audio.video.ui.theme.TextSecondary

/**
 * OpenGL ES Demo 页面 — 展示完整的 GPU 渲染管线
 *
 * 通过 AndroidView 嵌入 GLSurfaceView，显示一个旋转的 RGB 渐变三角形。
 *
 * GLSurfaceView 自动管理：
 *   - EGL 环境（Display → Config → Context → Surface）
 *   - 渲染线程（独立于 UI 线程，~60fps 调用 onDrawFrame）
 *   - Surface 生命周期
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GLDemoScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenGL ES Demo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorBlack,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = EditorBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // GLSurfaceView — 嵌入 Compose
            AndroidView(
                factory = { context ->
                    GLSurfaceView(context).apply {
                        setEGLContextClientVersion(2) // OpenGL ES 2.0
                        setRenderer(GLRendererDemo())
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY // 持续渲染
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // 管线说明
            Text(
                text = "GPU 渲染管线：\n" +
                       "顶点数据 → 顶点着色器(旋转变换) → 光栅化(三角形→像素)\n" +
                       "→ 片段着色器(颜色插值) → 帧缓冲 → 屏幕\n\n" +
                       "三角形三个顶点颜色(红/绿/蓝)在内部自动插值产生渐变",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
