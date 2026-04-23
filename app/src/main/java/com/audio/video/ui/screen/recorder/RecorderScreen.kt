package com.audio.video.ui.screen.recorder

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.EditorBlack
import com.audio.video.ui.theme.ErrorRed
import com.audio.video.ui.theme.TextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * 视频录制页面 — CameraX 采集 + 实时编码
 *
 * CameraX 架构：
 *   ProcessCameraProvider → 绑定 UseCase 到 Lifecycle
 *     - Preview（预览流）→ PreviewView → 屏幕实时显示
 *     - VideoCapture（录制流）→ Recorder → MediaCodec(H.264+AAC) → MP4
 *
 * 录制时同时处理：
 *   - 视频帧：Camera → Surface → H.264 硬件编码器
 *   - 音频帧：AudioRecord → AAC 编码器
 *   - MediaMuxer 将两路流合并写入 MP4 容器（ftyp + moov + mdat）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RecorderScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    var isRecording by remember { mutableStateOf(false) }
    var recording: Recording? by remember { mutableStateOf(null) }
    var useFrontCamera by remember { mutableStateOf(false) }

    // CameraX VideoCapture 实例
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录像") },
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
        if (!permissions.allPermissionsGranted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("需要相机和麦克风权限", color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = { permissions.launchMultiplePermissionRequest() }
                ) { Text("授予权限") }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // CameraX 预览
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val cameraSelector = if (useFrontCamera)
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            else CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, videoCapture
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 底部控制栏
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(EditorBlack.copy(alpha = 0.6f))
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 翻转摄像头
                    IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "翻转",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // 录制/停止按钮
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                recording?.stop()
                                recording = null
                                isRecording = false
                            } else {
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME,
                                        "ClipForge_${System.currentTimeMillis()}")
                                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        put(MediaStore.Video.Media.RELATIVE_PATH,
                                            "Movies/ClipForge")
                                    }
                                }
                                val outputOptions = MediaStoreOutputOptions.Builder(
                                    context.contentResolver,
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                ).setContentValues(contentValues).build()

                                recording = videoCapture.output
                                    .prepareRecording(context, outputOptions)
                                    .withAudioEnabled()
                                    .start(ContextCompat.getMainExecutor(context)) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Finalize -> {
                                                if (event.hasError()) {
                                                    Toast.makeText(context,
                                                        "录制失败", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context,
                                                        "已保存到相册", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                isRecording = true
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) ErrorRed else AccentBlue)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop
                                else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "停止" else "录制",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 占位保持对称
                    Box(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
