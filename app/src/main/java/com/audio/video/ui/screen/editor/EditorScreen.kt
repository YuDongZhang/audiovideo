package com.audio.video.ui.screen.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.audio.video.ui.navigation.AppRoutes
import com.audio.video.ui.screen.editor.components.EditorToolBar
import com.audio.video.ui.screen.editor.components.PlaybackControls
import com.audio.video.ui.screen.editor.components.Timeline
import com.audio.video.ui.screen.editor.components.VideoPreview
import com.audio.video.ui.screen.editor.components.FadeControl
import com.audio.video.ui.screen.editor.components.FilterSelector
import com.audio.video.ui.screen.editor.components.SpeedSelector
import com.audio.video.ui.screen.editor.components.VolumeControl

/**
 * 编辑器主页面 — 纵向排列：视频预览、播放控制、时间线、工具栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    navController: NavController,
    viewModel: EditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.project?.name ?: "编辑",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveProject()
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 有片段时显示导出按钮
                    if (uiState.timelineState.clips.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.saveProject()
                            navController.navigate(AppRoutes.export(projectId))
                        }) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "导出"
                            )
                        }
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
        ) {
            // 视频预览区
            VideoPreview(
                player = viewModel.playerManager.player,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 播放控制栏
            PlaybackControls(
                isPlaying = uiState.playerState.isPlaying,
                currentPositionMs = uiState.playerState.currentPositionMs,
                totalDurationMs = uiState.timelineState.totalDurationMs,
                onPlayPause = viewModel::togglePlayPause,
                onSeekToStart = viewModel::seekToStart,
                onSeekToEnd = viewModel::seekToEnd
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 时间线轨道（全局视图 + 双指缩放 + 音频波形）
            Timeline(
                state = uiState.timelineState,
                waveforms = uiState.waveforms,
                onClipSelected = viewModel::selectClip,
                onTrimStartDrag = { clipId, deltaPx ->
                    // 将拖拽像素转换为时间偏移量
                    val pxPerMs = 0.15f * viewModel.uiState.value.timelineState.zoomLevel
                    val deltaMs = (deltaPx / pxPerMs).toLong()
                    val clip = uiState.timelineState.clips.find { it.id == clipId } ?: return@Timeline
                    viewModel.trimClipStart(clipId, clip.startTimeMs + deltaMs)
                },
                onTrimEndDrag = { clipId, deltaPx ->
                    val pxPerMs = 0.15f * viewModel.uiState.value.timelineState.zoomLevel
                    val deltaMs = (deltaPx / pxPerMs).toLong()
                    val clip = uiState.timelineState.clips.find { it.id == clipId } ?: return@Timeline
                    viewModel.trimClipEnd(clipId, clip.endTimeMs + deltaMs)
                },
                onSeekTo = viewModel::seekTo,
                onZoomChanged = viewModel::updateZoomLevel,
                modifier = Modifier.fillMaxWidth()
            )

            // 选中片段时显示音量控制面板
            val selectedClip = uiState.timelineState.clips.find {
                it.id == uiState.timelineState.selectedClipId
            }
            if (selectedClip != null) {
                VolumeControl(
                    volume = selectedClip.volume,
                    onVolumeChanged = viewModel::setClipVolume,
                    onToggleMute = viewModel::toggleMute
                )
                FadeControl(
                    fadeInMs = selectedClip.fadeInMs,
                    fadeOutMs = selectedClip.fadeOutMs,
                    maxDurationMs = selectedClip.trimmedDurationMs,
                    onFadeInChanged = viewModel::setFadeIn,
                    onFadeOutChanged = viewModel::setFadeOut
                )
                FilterSelector(
                    selectedFilter = selectedClip.filterType,
                    onFilterSelected = viewModel::setFilter
                )
                SpeedSelector(
                    speed = selectedClip.speed,
                    onSpeedChanged = viewModel::setSpeed
                )
            }

            // 底部工具栏
            EditorToolBar(
                hasSelection = uiState.timelineState.selectedClipId != null,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onSplit = viewModel::splitAtPlayhead,
                onDelete = viewModel::deleteSelectedClip,
                onAddClip = {
                    viewModel.saveProject()
                    navController.navigate(AppRoutes.mediaPicker(projectId))
                },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo
            )
        }
    }
}
