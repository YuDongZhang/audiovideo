package com.audio.video.ui.screen.export

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.util.UnstableApi
import com.audio.video.data.model.ExportConfig
import com.audio.video.data.model.ExportQuality
import com.audio.video.data.model.ExportResolution
import com.audio.video.data.repository.ProjectRepository
import com.audio.video.export.VideoExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 导出流程状态枚举 */
enum class ExportStatus {
    IDLE, EXPORTING, SUCCESS, ERROR
}

/** 导出页 UI 状态 */
data class ExportUiState(
    val config: ExportConfig = ExportConfig(),
    val progress: Float = 0f,
    val status: ExportStatus = ExportStatus.IDLE,
    val outputUri: Uri? = null,
    val errorMessage: String? = null,
    val clipCount: Int = 0,
    val totalDurationMs: Long = 0L
)

/**
 * 导出 ViewModel — 管理导出配置、执行 Transformer 导出、跟踪进度
 */
@UnstableApi
class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val projectRepository = ProjectRepository(application)
    private val exportManager = VideoExportManager(application)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var projectId: String? = null

    /** 加载项目信息以显示片段数和总时长 */
    fun loadProject(projectId: String) {
        this.projectId = projectId
        val project = projectRepository.getProject(projectId) ?: return
        _uiState.value = _uiState.value.copy(
            clipCount = project.clips.size,
            totalDurationMs = project.totalDurationMs
        )
    }

    /** 更新导出分辨率 */
    fun updateResolution(resolution: ExportResolution) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(resolution = resolution)
        )
    }

    /** 更新导出质量 */
    fun updateQuality(quality: ExportQuality) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(quality = quality)
        )
    }

    /** 开始导出 — 通过 Media3 Transformer 拼接并编码视频 */
    fun startExport() {
        val pid = projectId ?: return
        val project = projectRepository.getProject(pid) ?: return
        if (project.clips.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            status = ExportStatus.EXPORTING,
            progress = 0f,
            errorMessage = null
        )

        exportManager.export(
            clips = project.clips,
            config = _uiState.value.config,
            onProgress = { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            },
            onComplete = { uri ->
                _uiState.value = _uiState.value.copy(
                    status = ExportStatus.SUCCESS,
                    outputUri = uri,
                    progress = 1f
                )
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    status = ExportStatus.ERROR,
                    errorMessage = error
                )
            }
        )
    }

    /** 取消正在进行的导出 */
    fun cancelExport() {
        exportManager.cancel()
        _uiState.value = _uiState.value.copy(
            status = ExportStatus.IDLE,
            progress = 0f
        )
    }

    override fun onCleared() {
        super.onCleared()
        exportManager.cancel()
    }
}
