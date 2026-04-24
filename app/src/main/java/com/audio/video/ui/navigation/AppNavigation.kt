package com.audio.video.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.audio.video.ui.screen.editor.EditorScreen
import com.audio.video.ui.screen.export.ExportScreen
import com.audio.video.ui.screen.home.HomeScreen
import com.audio.video.ui.screen.mediapicker.MediaPickerScreen
import com.audio.video.ui.screen.ffmpeglab.FFmpegLabScreen
import com.audio.video.ui.screen.recorder.RecorderScreen

/** 应用路由定义 */
object AppRoutes {
    const val HOME = "home"
    const val MEDIA_PICKER = "mediaPicker/{projectId}"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"
    const val RECORDER = "recorder"
    const val FFMPEG_LAB = "ffmpegLab"

    fun mediaPicker(projectId: String) = "mediaPicker/$projectId"
    fun editor(projectId: String) = "editor/$projectId"
    fun export(projectId: String) = "export/$projectId"
}

/** 应用导航宿主 — 4 个页面：首页、媒体选择、编辑器、导出 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier
    ) {
        composable(AppRoutes.HOME) {
            HomeScreen(navController = navController)
        }

        composable(AppRoutes.RECORDER) {
            RecorderScreen(navController = navController)
        }

        composable(AppRoutes.FFMPEG_LAB) {
            FFmpegLabScreen(navController = navController)
        }

        composable(
            route = AppRoutes.MEDIA_PICKER,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            MediaPickerScreen(
                projectId = projectId,
                navController = navController
            )
        }

        composable(
            route = AppRoutes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            EditorScreen(
                projectId = projectId,
                navController = navController
            )
        }

        composable(
            route = AppRoutes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ExportScreen(
                projectId = projectId,
                navController = navController
            )
        }
    }
}
