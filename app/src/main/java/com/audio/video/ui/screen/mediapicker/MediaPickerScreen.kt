package com.audio.video.ui.screen.mediapicker

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.audio.video.ui.navigation.AppRoutes
import com.audio.video.ui.screen.mediapicker.components.SelectionBottomBar
import com.audio.video.ui.screen.mediapicker.components.VideoThumbnailItem
import com.audio.video.ui.theme.TextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * 媒体选择页 — 浏览设备视频并多选添加到项目
 * 首次进入时会请求存储/视频读取权限
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MediaPickerScreen(
    projectId: String,
    navController: NavController,
    viewModel: MediaPickerViewModel = viewModel()
) {
    // 根据 Android 版本选择对应权限
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择视频") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
        if (!permissionState.status.isGranted) {
            // 权限未授予时的引导界面
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "需要授权才能访问视频文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("授予权限")
                }
            }
        } else {
            val uiState by viewModel.uiState.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // 三列网格展示设备视频
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 8.dp, end = 8.dp, top = 8.dp, bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.mediaItems, key = { it.id }) { item ->
                            VideoThumbnailItem(
                                mediaItem = item,
                                isSelected = uiState.selectedIds.contains(item.id),
                                onClick = { viewModel.toggleSelection(item.id) }
                            )
                        }
                    }

                    // 底部选择确认栏
                    SelectionBottomBar(
                        selectedCount = uiState.selectedIds.size,
                        onConfirm = {
                            viewModel.confirmSelection(projectId)
                            navController.navigate(AppRoutes.editor(projectId)) {
                                popUpTo(AppRoutes.HOME) { inclusive = false }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
