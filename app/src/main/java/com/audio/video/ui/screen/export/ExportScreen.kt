package com.audio.video.ui.screen.export

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.audio.video.ui.screen.export.components.ExportProgressBar
import com.audio.video.ui.screen.export.components.QualitySelector
import com.audio.video.ui.screen.export.components.ResolutionSelector
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.ErrorRed
import com.audio.video.ui.theme.SuccessGreen
import com.audio.video.ui.theme.TextSecondary
import com.audio.video.util.formatDurationMs

/**
 * 导出页面 — 配置分辨率/质量，启动导出，显示进度和结果
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExportScreen(
    projectId: String,
    navController: NavController,
    viewModel: ExportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.status == ExportStatus.EXPORTING) {
                            viewModel.cancelExport()
                        }
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 项目概况信息
            Column {
                Text(
                    text = "${uiState.clipCount} 个片段",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "时长：${formatDurationMs(uiState.totalDurationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            when (uiState.status) {
                // 空闲状态 — 显示配置选项
                ExportStatus.IDLE -> {
                    ResolutionSelector(
                        selectedResolution = uiState.config.resolution,
                        onResolutionSelected = viewModel::updateResolution
                    )

                    QualitySelector(
                        selectedQuality = uiState.config.quality,
                        onQualitySelected = viewModel::updateQuality
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = viewModel::startExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue
                        ),
                        enabled = uiState.clipCount > 0
                    ) {
                        Text(
                            text = "开始导出",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 导出中 — 显示进度条和取消按钮
                ExportStatus.EXPORTING -> {
                    Spacer(modifier = Modifier.weight(1f))

                    ExportProgressBar(progress = uiState.progress)

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = viewModel::cancelExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                // 导出成功 — 显示结果和分享按钮
                ExportStatus.SUCCESS -> {
                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "成功",
                            tint = SuccessGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "导出完成！",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "视频已保存至 Movies/ClipForge",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("返回编辑")
                        }
                        Button(
                            onClick = {
                                uiState.outputUri?.let { uri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "分享视频")
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("分享", modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                // 导出失败 — 显示错误信息和重试按钮
                ExportStatus.ERROR -> {
                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "导出失败",
                            style = MaterialTheme.typography.titleLarge,
                            color = ErrorRed
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "未知错误",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = viewModel::startExport,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("重试")
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
