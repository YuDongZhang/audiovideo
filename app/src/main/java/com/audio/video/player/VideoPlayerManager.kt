package com.audio.video.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.audio.video.data.model.VideoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 视频播放管理器 — 封装 ExoPlayer 生命周期
 * 负责根据时间线片段构建播放列表（带裁剪配置），并以 16ms 间隔跟踪全局播放位置
 */
class VideoPlayerManager(context: Context, private val scope: CoroutineScope) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var positionJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPositionTracking() else stopPositionTracking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(
                        totalDurationMs = player.duration.coerceAtLeast(0)
                    )
                }
            }
        })
    }

    /**
     * 根据片段列表构建播放列表
     * 每个片段通过 ClippingConfiguration 设置入点/出点，ExoPlayer 会无缝衔接播放
     */
    fun setTimeline(clips: List<VideoClip>) {
        val mediaItems = clips.sortedBy { it.displayOrder }.map { clip ->
            MediaItem.Builder()
                .setUri(Uri.parse(clip.sourceUri))
                .setClippingConfiguration(
                    ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startTimeMs)
                        .setEndPositionMs(clip.endTimeMs)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()

        val totalDuration = clips.sumOf { it.trimmedDurationMs }
        _state.value = _state.value.copy(totalDurationMs = totalDuration)
    }

    fun play() { player.play() }
    fun pause() { player.pause() }
    fun togglePlayPause() { if (player.isPlaying) pause() else play() }

    /**
     * 跳转到全局时间线位置（毫秒）
     * 需要将全局位置换算为对应片段的局部位置
     */
    fun seekTo(positionMs: Long) {
        var remaining = positionMs
        for (i in 0 until player.mediaItemCount) {
            val itemDuration = player.getMediaItemAt(i).clippingConfiguration.endPositionMs -
                    player.getMediaItemAt(i).clippingConfiguration.startPositionMs
            if (remaining < itemDuration || i == player.mediaItemCount - 1) {
                player.seekTo(i, remaining + player.getMediaItemAt(i).clippingConfiguration.startPositionMs)
                _state.value = _state.value.copy(currentPositionMs = positionMs)
                return
            }
            remaining -= itemDuration
        }
    }

    /**
     * 计算当前的全局播放位置
     * 累加当前片段之前所有片段的时长 + 当前片段内的偏移量
     */
    fun getCurrentGlobalPosition(): Long {
        if (player.mediaItemCount == 0) return 0L
        var position = 0L
        for (i in 0 until player.currentMediaItemIndex) {
            val item = player.getMediaItemAt(i)
            position += item.clippingConfiguration.endPositionMs - item.clippingConfiguration.startPositionMs
        }
        val currentItem = player.getMediaItemAt(player.currentMediaItemIndex)
        position += (player.currentPosition - currentItem.clippingConfiguration.startPositionMs)
            .coerceAtLeast(0)
        return position
    }

    /** 启动位置跟踪协程，每 16ms 更新一次播放位置 */
    private fun startPositionTracking() {
        stopPositionTracking()
        positionJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                _state.value = _state.value.copy(
                    currentPositionMs = getCurrentGlobalPosition()
                )
                delay(16)
            }
        }
    }

    private fun stopPositionTracking() {
        positionJob?.cancel()
        positionJob = null
    }

    /** 释放播放器资源 */
    fun release() {
        stopPositionTracking()
        player.release()
    }
}
