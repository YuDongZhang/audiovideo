package com.audio.video.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.audio.video.data.model.Project
import com.audio.video.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** 首页 UI 状态 */
data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * 首页 ViewModel — 管理项目列表的加载、创建和删除
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    /** 从本地存储加载所有项目 */
    fun loadProjects() {
        _uiState.value = _uiState.value.copy(
            projects = repository.getAllProjects(),
            isLoading = false
        )
    }

    /** 创建新项目，返回项目 ID 供导航使用 */
    fun createProject(): String {
        val id = UUID.randomUUID().toString()
        val project = Project(
            id = id,
            name = "项目 ${_uiState.value.projects.size + 1}"
        )
        repository.saveProject(project)
        loadProjects()
        return id
    }

    /** 删除指定项目 */
    fun deleteProject(id: String) {
        repository.deleteProject(id)
        loadProjects()
    }
}
