package com.audio.video.ui.screen.mediapicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audio.video.data.model.VideoClip
import com.audio.video.data.model.MediaItem
import com.audio.video.data.repository.MediaRepository
import com.audio.video.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 媒体选择页 UI 状态 */
data class MediaPickerUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true
)

/**
 * 媒体选择页 ViewModel — 查询设备视频、管理多选状态、将选中视频写入项目
 */
class MediaPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepository = MediaRepository(application)
    private val projectRepository = ProjectRepository(application)
    private val _uiState = MutableStateFlow(MediaPickerUiState())
    val uiState: StateFlow<MediaPickerUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    /** 通过 MediaStore 查询设备中的视频 */
    private fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val videos = mediaRepository.getDeviceVideos()
            _uiState.value = _uiState.value.copy(
                mediaItems = videos,
                isLoading = false
            )
        }
    }

    /** 切换某个视频的选中/取消选中状态 */
    fun toggleSelection(id: Long) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    /** 确认选择，将选中视频转换为 VideoClip 并追加到项目中 */
    fun confirmSelection(projectId: String) {
        val selected = _uiState.value.mediaItems.filter {
            _uiState.value.selectedIds.contains(it.id)
        }
        val clips = selected.mapIndexed { index, item ->
            VideoClip(
                sourceUri = item.uri.toString(),
                startTimeMs = 0L,
                endTimeMs = item.durationMs,
                originalDurationMs = item.durationMs,
                displayOrder = index
            )
        }
        val existing = projectRepository.getProject(projectId)
        if (existing != null) {
            val existingClips = existing.clips
            val newClips = existingClips + clips.mapIndexed { i, clip ->
                clip.copy(displayOrder = existingClips.size + i)
            }
            projectRepository.saveProject(
                existing.copy(
                    clips = newClips,
                    thumbnailUri = newClips.firstOrNull()?.sourceUri,
                    totalDurationMs = newClips.sumOf { it.trimmedDurationMs }
                )
            )
        }
    }
}
