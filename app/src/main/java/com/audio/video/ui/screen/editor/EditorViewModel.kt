package com.audio.video.ui.screen.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audio.video.data.model.Project
import com.audio.video.data.model.TimelineState
import com.audio.video.data.model.VideoFilterType
import com.audio.video.data.model.VideoClip
import com.audio.video.data.repository.ProjectRepository
import android.net.Uri
import com.audio.video.editor.TimelineEngine
import com.audio.video.editor.WaveformExtractor
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
    val canRedo: Boolean = false,
    val waveforms: Map<String, FloatArray> = emptyMap()
)

/**
 * 编辑器 ViewModel — 核心状态管理中心
 * 协调播放器、时间线编辑操作和项目持久化
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val projectRepository = ProjectRepository(application)
    private val waveformExtractor = WaveformExtractor(application)
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
            loadWaveforms(project.clips)
        }
    }

    /** 为每个片段异步提取音频波形 */
    private fun loadWaveforms(clips: List<VideoClip>) {
        clips.forEach { clip ->
            // 已有波形的片段跳过
            if (_uiState.value.waveforms.containsKey(clip.id)) return@forEach
            viewModelScope.launch {
                val waveform = waveformExtractor.extract(
                    uri = Uri.parse(clip.sourceUri),
                    startMs = clip.startTimeMs,
                    endMs = clip.endTimeMs,
                    sampleCount = 80
                )
                val updated = _uiState.value.waveforms.toMutableMap()
                updated[clip.id] = waveform
                _uiState.value = _uiState.value.copy(waveforms = updated)
            }
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

    /** 设置选中片段的音量（0.0 ~ 2.0） */
    fun setClipVolume(volume: Float) {
        val selectedId = _uiState.value.timelineState.selectedClipId ?: return
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = clips.map { clip ->
            if (clip.id == selectedId) clip.copy(volume = volume.coerceIn(0f, 2f))
            else clip
        }
        updateClips(newClips)
    }

    /** 切换选中片段的静音状态 */
    fun toggleMute() {
        val selectedId = _uiState.value.timelineState.selectedClipId ?: return
        val clip = _uiState.value.timelineState.clips.find { it.id == selectedId } ?: return
        setClipVolume(if (clip.isMuted) 1.0f else 0f)
    }

    /** 设置选中片段的淡入时长 */
    fun setFadeIn(durationMs: Long) {
        val selectedId = _uiState.value.timelineState.selectedClipId ?: return
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = clips.map { clip ->
            if (clip.id == selectedId) clip.copy(fadeInMs = durationMs.coerceIn(0, clip.trimmedDurationMs / 2))
            else clip
        }
        updateClips(newClips)
    }

    /** 设置选中片段的淡出时长 */
    fun setFadeOut(durationMs: Long) {
        val selectedId = _uiState.value.timelineState.selectedClipId ?: return
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = clips.map { clip ->
            if (clip.id == selectedId) clip.copy(fadeOutMs = durationMs.coerceIn(0, clip.trimmedDurationMs / 2))
            else clip
        }
        updateClips(newClips)
    }

    /** 设置选中片段的滤镜类型 */
    fun setFilter(filterType: VideoFilterType) {
        val selectedId = _uiState.value.timelineState.selectedClipId ?: return
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = clips.map { clip ->
            if (clip.id == selectedId) clip.copy(filterType = filterType)
            else clip
        }
        updateClips(newClips)
    }

    /** 设置选中片段的播放速度（0.25x ~ 4.0x） */
    fun setSpeed(speed: Float) {
        val selectedId = _uiState.value.timelineState.selectedClipId ?: return
        val clips = _uiState.value.timelineState.clips
        pushUndo(clips)
        val newClips = clips.map { clip ->
            if (clip.id == selectedId) clip.copy(speed = speed.coerceIn(0.25f, 4.0f))
            else clip
        }
        updateClips(newClips)
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

    /** 更新片段列表并同步播放器、波形和持久化 */
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
            loadWaveforms(clips)
        }
        saveProject()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
