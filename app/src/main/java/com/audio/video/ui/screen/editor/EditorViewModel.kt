package com.audio.video.ui.screen.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audio.video.data.model.Project
import com.audio.video.data.model.TimelineState
import com.audio.video.data.model.VideoClip
import com.audio.video.data.repository.ProjectRepository
import com.audio.video.editor.TimelineEngine
import com.audio.video.player.PlayerState
import com.audio.video.player.VideoPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 编辑器页面 UI 状态 */
data class EditorUiState(
    val project: Project? = null,
    val timelineState: TimelineState = TimelineState(),
    val playerState: PlayerState = PlayerState(),
    val isLoading: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

/**
 * 编辑器 ViewModel — 核心状态管理中心
 * 协调播放器、时间线编辑操作和项目持久化
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val projectRepository = ProjectRepository(application)
    val playerManager = VideoPlayerManager(application, viewModelScope)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // 撤销/重做栈，最多保存50步
    private val undoStack = mutableListOf<List<VideoClip>>()
    private val redoStack = mutableListOf<List<VideoClip>>()

    init {
        // 订阅播放器状态，同步更新到 UI 状态
        viewModelScope.launch {
            playerManager.state.collect { playerState ->
                _uiState.value = _uiState.value.copy(
                    playerState = playerState,
                    timelineState = _uiState.value.timelineState.copy(
                        currentPositionMs = playerState.currentPositionMs,
                        isPlaying = playerState.isPlaying
                    )
                )
            }
        }
    }

    /** 加载项目数据并初始化播放器播放列表 */
    fun loadProject(projectId: String) {
        val project = projectRepository.getProject(projectId) ?: return
        _uiState.value = _uiState.value.copy(
            project = project,
            timelineState = TimelineState(
                clips = project.clips,
                totalDurationMs = project.clips.sumOf { it.trimmedDurationMs }
            ),
            isLoading = false
        )
        if (project.clips.isNotEmpty()) {
            playerManager.setTimeline(project.clips)
        }
    }

    /** 切换播放/暂停 */
    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    /** 跳转到时间线起始位置 */
    fun seekToStart() {
        playerManager.seekTo(0)
    }

    /** 跳转到时间线末尾位置 */
    fun seekToEnd() {
        val total = _uiState.value.timelineState.totalDurationMs
        playerManager.seekTo((total - 100).coerceAtLeast(0))
    }

    /** 跳转到指定时间位置（毫秒） */
    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    /** 选中/取消选中某个片段 */
    fun selectClip(clipId: String?) {
        _uiState.value = _uiState.value.copy(
            timelineState = _uiState.value.timelineState.copy(selectedClipId = clipId)
        )
    }

    /** 在播放头位置分割当前片段 */
    fun splitAtPlayhead() {
        val state = _uiState.value.timelineState
        val clips = state.clips
        val position = state.currentPositionMs
        pushUndo(clips)
        val newClips = TimelineEngine.splitClip(clips, position)
        updateClips(newClips)
    }

    /** 调整片段的入点（起始时间） */
    fun trimClipStart(clipId: String, newStartMs: Long) {
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = TimelineEngine.trimClipStart(clips, clipId, newStartMs)
        updateClips(newClips)
    }

    /** 调整片段的出点（结束时间） */
    fun trimClipEnd(clipId: String, newEndMs: Long) {
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = TimelineEngine.trimClipEnd(clips, clipId, newEndMs)
        updateClips(newClips)
    }

    /** 删除当前选中的片段 */
    fun deleteSelectedClip() {
        val selectedId = _uiState.value.timelineState.selectedClipId ?: return
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = TimelineEngine.deleteClip(clips, selectedId)
        updateClips(newClips)
        selectClip(null)
    }

    /** 撤销上一步操作 */
    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.timelineState.clips
        redoStack.add(current)
        val previous = undoStack.removeAt(undoStack.lastIndex)
        updateClips(previous, pushToUndo = false)
    }

    /** 重做上一步撤销的操作 */
    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.timelineState.clips
        undoStack.add(current)
        val next = redoStack.removeAt(redoStack.lastIndex)
        updateClips(next, pushToUndo = false)
    }

    /** 更新时间线缩放级别 */
    fun updateZoomLevel(zoom: Float) {
        _uiState.value = _uiState.value.copy(
            timelineState = _uiState.value.timelineState.copy(
                zoomLevel = zoom.coerceIn(0.5f, 5.0f)
            )
        )
    }

    /** 保存项目到本地存储 */
    fun saveProject() {
        val project = _uiState.value.project ?: return
        val clips = _uiState.value.timelineState.clips
        projectRepository.saveProject(
            project.copy(
                clips = clips,
                totalDurationMs = clips.sumOf { it.trimmedDurationMs },
                thumbnailUri = clips.firstOrNull()?.sourceUri
            )
        )
    }

    /** 将当前片段列表快照压入撤销栈 */
    private fun pushUndo(clips: List<VideoClip>) {
        undoStack.add(clips)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    /** 更新片段列表并同步播放器和持久化 */
    private fun updateClips(clips: List<VideoClip>, pushToUndo: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            timelineState = _uiState.value.timelineState.copy(
                clips = clips,
                totalDurationMs = clips.sumOf { it.trimmedDurationMs },
                selectedClipId = _uiState.value.timelineState.selectedClipId
            ),
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        if (clips.isNotEmpty()) {
            playerManager.setTimeline(clips)
        }
        saveProject()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
