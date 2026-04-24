package com.audio.video.ui.screen.ffmpeglab

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.audio.video.ui.screen.ffmpeglab.components.CommandCard
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.TextSecondary

/**
 * FFmpeg 实验室页面 — 独立入口，不干预编辑器功能
 *
 * 使用流程：
 * 1. 选择一个视频文件
 * 2. 浏览分组命令列表
 * 3. 点击执行按钮运行命令
 * 4. 展开查看完整命令、参数说明、执行结果
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegLabScreen(
    navController: NavController,
    viewModel: FFmpegLabViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 视频选择器
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = getFileName(context, uri)
        viewModel.setVideoUri(uri, name)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FFmpeg 实验室") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 视频选择区
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { videoPicker.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Text("选择视频", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (uiState.selectedVideoName.isNotEmpty()) {
                        Text(
                            text = uiState.selectedVideoName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            // 命令分组列表
            uiState.groups.forEach { group ->
                item {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                items(group.commands, key = { it.name }) { command ->
                    CommandCard(
                        command = command,
                        state = uiState.commandStates[command.name],
                        isExpanded = uiState.expandedCommand == command.name,
                        hasVideo = uiState.selectedVideoUri != null,
                        onToggleExpand = { viewModel.toggleExpand(command.name) },
                        onExecute = { viewModel.executeCommand(command) }
                    )
                }
            }
        }
    }
}

/** 从 content URI 获取文件名 */
private fun getFileName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment ?: "video"
}
