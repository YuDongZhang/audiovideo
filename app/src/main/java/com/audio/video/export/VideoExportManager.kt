package com.audio.video.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.audio.video.data.model.ExportConfig
import com.audio.video.data.model.VideoClip
import java.io.File

/**
 * 视频导出管理器 — 使用 Media3 Transformer 拼接裁剪后的片段并编码输出
 * 导出完成后通过 MediaStore 写入相册，使视频在系统图库中可见
 */
@UnstableApi
class VideoExportManager(private val context: Context) {

    private var transformer: Transformer? = null

    /**
     * 执行视频导出
     * @param clips 要拼接的片段列表（已按 displayOrder 排序）
     * @param config 导出配置（分辨率、质量、格式）
     * @param onProgress 进度回调 (0.0 ~ 1.0)
     * @param onComplete 导出完成回调，返回 MediaStore Uri
     * @param onError 导出失败回调
     */
    fun export(
        clips: List<VideoClip>,
        config: ExportConfig,
        onProgress: (Float) -> Unit,
        onComplete: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val outputFile = File(
            context.cacheDir,
            "export_${System.currentTimeMillis()}.${config.format.extension}"
        )

        // 为每个片段创建带裁剪配置的 EditedMediaItem
        val editedItems = clips.sortedBy { it.displayOrder }.map { clip ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(clip.sourceUri))
                .setClippingConfiguration(
                    ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startTimeMs)
                        .setEndPositionMs(clip.endTimeMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(mediaItem).build()
        }

        // 构建 Composition：序列 → 组合
        val sequence = EditedMediaItemSequence.Builder(editedItems).build()
        val composition = Composition.Builder(sequence).build()

        transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    // 导出完成后写入 MediaStore
                    val uri = saveToMediaStore(outputFile, config)
                    outputFile.delete()
                    onComplete(uri ?: Uri.fromFile(outputFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    outputFile.delete()
                    onError(exportException.message ?: "导出失败")
                }
            })
            .build()

        transformer?.start(composition, outputFile.absolutePath)

        // 后台线程轮询导出进度
        val progressHolder = androidx.media3.transformer.ProgressHolder()
        Thread {
            while (transformer != null) {
                try {
                    val state = transformer?.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress / 100f)
                    }
                    Thread.sleep(500)
                } catch (_: Exception) {
                    break
                }
            }
        }.start()
    }

    /** 取消正在进行的导出 */
    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    /** 将导出文件写入 MediaStore，使其在系统图库中可见 */
    private fun saveToMediaStore(file: File, config: ExportConfig): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, config.format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipForge")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        // Android Q+ 需要将 IS_PENDING 置为 0 才能对其他应用可见
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }

        return uri
    }
}
