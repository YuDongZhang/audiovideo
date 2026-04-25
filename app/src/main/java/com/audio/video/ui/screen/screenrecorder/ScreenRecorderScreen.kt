package com.audio.video.ui.screen.screenrecorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.navigation.NavController
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.EditorBlack
import com.audio.video.ui.theme.TextSecondary

/**
 * 屏幕录制页面 — MediaProjection API
 *
 * 屏幕录制原理：
 *
 *   1. MediaProjectionManager.createScreenCaptureIntent()
 *      → 弹出系统授权对话框，用户确认后获得 MediaProjection 实例
 *
 *   2. MediaProjection.createVirtualDisplay()
 *      → 创建虚拟显示器，将屏幕画面输出到指定 Surface
 *      → 参数: 分辨率、DPI、Surface（来自 MediaRecorder 或 MediaCodec）
 *
 *   3. 录制方案 A — MediaRecorder（简单）：
 *      MediaRecorder.setVideoSource(SURFACE)
 *      MediaRecorder.setOutputFormat(MPEG_4)
 *      MediaRecorder.setVideoEncoder(H264)
 *      val surface = mediaRecorder.surface  ← 作为 VirtualDisplay 的输出
 *      mediaRecorder.start()
 *
 *   4. 录制方案 B — MediaCodec（灵活）：
 *      MediaCodec.createEncoderByType("video/avc")
 *      val surface = encoder.createInputSurface()  ← 作为 VirtualDisplay 的输出
 *      encoder.start()
 *      循环: dequeueOutputBuffer → writeSampleData 到 MediaMuxer
 *
 *   5. 系统音频采集（Android 10+）：
 *      MediaProjection 支持 AudioPlaybackCapture
 *      可以录制其他 app 播放的音频（不只是麦克风）
 *
 * 关键限制：
 *   - 需要用户手动授权（无法静默启动）
 *   - Android 10+ 需要前台 Service
 *   - 某些 app 的画面受 FLAG_SECURE 保护，录制时显示黑屏
 *
 * 注意：实际实现需要 Activity.startActivityForResult() 获取授权，
 * Compose 中通过 rememberLauncherForActivityResult() 处理。
 * 完整实现还需要 Foreground Service，此处仅展示架构和原理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenRecorderScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕录制") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "MediaProjection 屏幕录制",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = """
                    录制流程：

                    1. createScreenCaptureIntent()
                       → 系统弹出"是否允许录制屏幕"授权对话框

                    2. 用户确认 → 获得 MediaProjection 实例

                    3. createVirtualDisplay(name, width, height, dpi, flags, surface)
                       → 将屏幕画面输出到 Surface
                       → Surface 来自 MediaRecorder 或 MediaCodec

                    4. MediaRecorder/MediaCodec 将 Surface 上的帧编码为 H.264

                    5. MediaMuxer 封装为 MP4 文件

                    系统音频采集（Android 10+）：
                    · AudioPlaybackCaptureConfiguration
                    · 可以录制其他 app 的声音
                    · 比 AudioRecord(MIC) 更强大

                    限制：
                    · 需要前台 Service（Android 10+）
                    · FLAG_SECURE 的 app 画面显示黑屏
                    · 某些 ROM 可能限制录屏权限
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { /* 需要 startActivityForResult + Foreground Service，此处为演示 */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("开始录屏（需要 Foreground Service）")
            }

            Text(
                text = "完整实现需要：\n" +
                       "1. 创建 Foreground Service\n" +
                       "2. 在 Service 中获取 MediaProjection\n" +
                       "3. 创建 VirtualDisplay + MediaRecorder\n" +
                       "4. 前台通知显示录制状态",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
