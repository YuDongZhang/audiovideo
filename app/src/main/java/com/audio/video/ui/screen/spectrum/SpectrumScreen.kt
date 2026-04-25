package com.audio.video.ui.screen.spectrum

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.Manifest
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audio.video.editor.FFTProcessor
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.EditorBlack
import com.audio.video.ui.theme.TextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 实时音频频谱页面 — 麦克风采集 + FFT + 频谱可视化
 *
 * 数据流：
 *   麦克风 → AudioRecord（PCM 16bit, 44100Hz）→ FFT → 频段分组 → Canvas 绘制
 *
 * AudioRecord 核心参数：
 *   采样率 44100Hz: CD 质量，覆盖人耳 20Hz~20kHz（奈奎斯特定理：采样率 ≥ 2×最高频率）
 *   单声道 MONO: 频谱分析不需要立体声
 *   16bit PCM: 每采样 2 字节，范围 [-32768, 32767]
 *   缓冲区 1024 采样: FFT 窗口大小（2的幂），频率分辨率 = 44100/1024 ≈ 43Hz
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SpectrumScreen(navController: NavController) {
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    var isRecording by remember { mutableStateOf(false) }
    var spectrumBands by remember { mutableStateOf(FloatArray(32)) }
    var captureJob by remember { mutableStateOf<Job?>(null) }

    // 停止时清理资源
    DisposableEffect(Unit) {
        onDispose {
            captureJob?.cancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音频频谱") },
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
            // 频谱可视化区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                SpectrumBarView(
                    bands = spectrumBands,
                    modifier = Modifier.fillMaxSize()
                )

                // 频率标签
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("低频\n20Hz", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("中频\n1kHz", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("高频\n20kHz", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            // 知识点说明
            Text(
                text = "FFT 将时域 PCM 信号变换为频域幅度谱\n" +
                       "窗口大小 1024 采样 → 频率分辨率 ≈ 43Hz\n" +
                       "32 个频段（对数分布，模拟人耳感知）",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 控制按钮
            if (!micPermission.status.isGranted) {
                Button(
                    onClick = { micPermission.launchPermissionRequest() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) { Text("授予麦克风权限") }
            } else {
                Button(
                    onClick = {
                        if (isRecording) {
                            captureJob?.cancel()
                            captureJob = null
                            isRecording = false
                            spectrumBands = FloatArray(32)
                        } else {
                            isRecording = true
                            captureJob = startAudioCapture { bands ->
                                spectrumBands = bands
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else AccentBlue
                    )
                ) {
                    Icon(
                        if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Text(
                        if (isRecording) "  停止" else "  开始采集",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 启动麦克风采集 + FFT 分析协程
 *
 * AudioRecord 循环：
 *   1. read() 读取 1024 个 PCM 采样（阻塞等待麦克风数据）
 *   2. Short → Float 归一化到 [-1.0, 1.0]
 *   3. FFT 变换得到频域幅度谱
 *   4. 分成 32 个频段（对数分布）
 *   5. 回调到 UI 层刷新
 */
private fun startAudioCapture(onBands: (FloatArray) -> Unit): Job {
    return CoroutineScope(Dispatchers.IO).launch {
        val sampleRate = 44100
        val bufferSize = 1024 // FFT 窗口大小（必须是 2 的幂）
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, bufferSize * 2)
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) return@launch

        val shortBuffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)

        audioRecord.startRecording()

        try {
            while (isActive) {
                val read = audioRecord.read(shortBuffer, 0, bufferSize)
                if (read <= 0) continue

                // Short PCM → Float 归一化 [-1.0, 1.0]
                for (i in 0 until read) {
                    floatBuffer[i] = shortBuffer[i] / 32768f
                }

                // FFT → 幅度谱 → 分频段
                val spectrum = FFTProcessor.computeMagnitudeSpectrum(floatBuffer)
                val bands = FFTProcessor.groupIntoBands(spectrum, 32)

                onBands(bands)
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }
}
