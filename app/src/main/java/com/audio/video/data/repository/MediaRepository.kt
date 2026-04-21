package com.audio.video.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.audio.video.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 媒体仓库 — 通过 MediaStore 查询设备中的视频文件
 * 按添加时间倒序返回，自动过滤时长为 0 的无效条目
 */
class MediaRepository(private val context: Context) {

    /** 查询设备中所有视频，在 IO 线程执行 */
    suspend fun getDeviceVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                val duration = cursor.getLong(durationColumn)
                if (duration <= 0) continue

                videos.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameColumn) ?: "Unknown",
                        durationMs = duration,
                        size = cursor.getLong(sizeColumn),
                        dateAdded = cursor.getLong(dateColumn),
                        mimeType = cursor.getString(mimeColumn) ?: "video/mp4"
                    )
                )
            }
        }
        videos
    }
}
