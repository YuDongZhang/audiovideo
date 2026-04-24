package com.audio.video.ui.screen.ffmpeglab

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audio.video.ffmpeg.CommandGroup
import com.audio.video.ffmpeg.FFmpegCommand
import com.audio.video.ffmpeg.FFmpegCommands
import com.audio.video.ffmpeg.FFmpegManager
import com.audio.video.ffmpeg.FFmpegResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 单个命令的执行状态 */
data class CommandState(
    val commandName: String,
    val status: CommandStatus = CommandStatus.IDLE,
    val result: FFmpegResult? = null,
    val probeOutput: String = "",
    val progressMs: Long = 0,
    val actualCommand: String = ""
)

enum class CommandStatus {
    IDLE, RUNNING, SUCCESS, FAILED
}

/** FFmpeg 实验室页面状态 */
data class FFmpegLabUiState(
    val groups: List<CommandGroup> = FFmpegCommands.getAllGroups(),
    val selectedVideoUri: String? = null,
    val selectedVideoName: String = "",
    val commandStates: Map<String, CommandState> = emptyMap(),
    val expandedCommand: String? = null
)

/**
 * FFmpeg 实验室 ViewModel — 管理视频选择和命令执行
 */
class FFmpegLabViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FFmpegLabUiState())
    val uiState: StateFlow<FFmpegLabUiState> = _uiState.asStateFlow()

    private val outputDir = File(application.cacheDir, "ffmpeg_lab").also { it.mkdirs() }

    /** 设置待处理的视频 URI */
    fun setVideoUri(uri: Uri, displayName: String) {
        // 将 content:// URI 拷贝到 app 缓存目录（FFmpeg 需要文件路径）
        val inputFile = File(outputDir, "input_${System.currentTimeMillis()}.mp4")
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            inputFile.outputStream().use { output -> input.copyTo(output) }
        }
        _uiState.value = _uiState.value.copy(
            selectedVideoUri = inputFile.absolutePath,
            selectedVideoName = displayName
        )
    }

    /** 切换命令展开/折叠 */
    fun toggleExpand(commandName: String) {
        _uiState.value = _uiState.value.copy(
            expandedCommand = if (_uiState.value.expandedCommand == commandName) null else commandName
        )
    }

    /** 执行指定命令 */
    fun executeCommand(command: FFmpegCommand) {
        val inputPath = _uiState.value.selectedVideoUri ?: return
        val outputPath = File(outputDir, "out_${System.currentTimeMillis()}").absolutePath

        val actualCommand = command.buildCommand(inputPath, outputPath)

        updateCommandState(command.name, CommandState(
            commandName = command.name,
            status = CommandStatus.RUNNING,
            actualCommand = if (command.isProbe) "ffprobe $actualCommand" else "ffmpeg $actualCommand"
        ))

        viewModelScope.launch {
            if (command.isProbe) {
                val output = FFmpegManager.probe(actualCommand)
                updateCommandState(command.name, CommandState(
                    commandName = command.name,
                    status = CommandStatus.SUCCESS,
                    probeOutput = output.take(3000),
                    actualCommand = "ffprobe $actualCommand"
                ))
            } else {
                val result = FFmpegManager.execute(actualCommand) { timeMs ->
                    val current = _uiState.value.commandStates[command.name]
                    if (current != null) {
                        updateCommandState(command.name, current.copy(progressMs = timeMs))
                    }
                }
                updateCommandState(command.name, CommandState(
                    commandName = command.name,
                    status = if (result.success) CommandStatus.SUCCESS else CommandStatus.FAILED,
                    result = result,
                    actualCommand = "ffmpeg $actualCommand"
                ))
            }
        }
    }

    private fun updateCommandState(name: String, state: CommandState) {
        val states = _uiState.value.commandStates.toMutableMap()
        states[name] = state
        _uiState.value = _uiState.value.copy(commandStates = states)
    }
}
