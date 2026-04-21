package com.audio.video.data.repository

import android.content.Context
import com.audio.video.data.model.Project
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 项目仓库 — 使用 SharedPreferences + Gson 持久化项目列表
 * 后续可升级为 Room 数据库
 */
class ProjectRepository(context: Context) {

    private val prefs = context.getSharedPreferences("projects", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** 获取所有项目 */
    fun getAllProjects(): List<Project> {
        val json = prefs.getString(KEY_PROJECTS, null) ?: return emptyList()
        val type = object : TypeToken<List<Project>>() {}.type
        return gson.fromJson(json, type)
    }

    /** 根据 ID 获取单个项目 */
    fun getProject(id: String): Project? {
        return getAllProjects().find { it.id == id }
    }

    /** 保存或更新项目（已存在则更新，否则插入到列表头部） */
    fun saveProject(project: Project) {
        val projects = getAllProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            projects[index] = project.copy(updatedAt = System.currentTimeMillis())
        } else {
            projects.add(0, project)
        }
        persist(projects)
    }

    /** 删除指定项目 */
    fun deleteProject(id: String) {
        val projects = getAllProjects().filter { it.id != id }
        persist(projects)
    }

    private fun persist(projects: List<Project>) {
        prefs.edit().putString(KEY_PROJECTS, gson.toJson(projects)).apply()
    }

    companion object {
        private const val KEY_PROJECTS = "project_list"
    }
}
