package com.audio.video.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.audio.video.data.model.VideoClip
import com.audio.video.editor.AudioFadeCalculator
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
    private var currentClips: List<VideoClip> = emptyList()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPositionTracking() else stopPositionTracking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(
                        totalDurationMs = currentClips.sumOf { it.trimmedDurationMs }
                    )
                }
            }

            // 片段切换时更新音量（每个片段独立音量）
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyCurrentClipVolume()
            }
        })
        player.playWhenReady = false
    }

    /**
     * 根据片段列表构建播放列表
     * 保存当前播放位置，重建后恢复到同一位置
     */
    fun setTimeline(clips: List<VideoClip>) {
        if (clips.isEmpty()) return

        val previousPositionMs = _state.value.currentPositionMs
        currentClips = clips.sortedBy { it.displayOrder }

        val mediaItems = currentClips.map { clip ->
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

        val wasPlaying = player.isPlaying
        player.setMediaItems(mediaItems)
        player.prepare()

        val totalDuration = currentClips.sumOf { it.trimmedDurationMs }
        _state.value = _state.value.copy(totalDurationMs = totalDuration)

        // 恢复到之前的位置（如果仍在范围内）
        val restorePosition = previousPositionMs.coerceIn(0, totalDuration)
        seekTo(restorePosition)
        applyCurrentClipVolume()

        if (wasPlaying) player.play()
    }

    fun play() { player.play() }
    fun pause() { player.pause() }
    fun togglePlayPause() { if (player.isPlaying) pause() else play() }

    /** 根据当前播放片段的 volume 和 speed 字段设置播放器参数 */
    private fun applyCurrentClipVolume() {
        val index = player.currentMediaItemIndex
        if (index in currentClips.indices) {
            player.volume = currentClips[index].volume.coerceIn(0f, 2f)
            // 应用片段速度 — ExoPlayer 内置 Sonic 算法做音频时间拉伸，保持音调
            val speed = currentClips[index].speed
            if (player.playbackParameters.speed != speed) {
                player.playbackParameters = PlaybackParameters(speed)
            }
        }
    }

    /**
     * 跳转到全局时间线位置（毫秒）
     * 将全局位置换算为对应片段索引 + 片段内偏移量
     */
    fun seekTo(positionMs: Long) {
        if (currentClips.isEmpty()) return

        var remaining = positionMs.coerceAtLeast(0)
        for (i in currentClips.indices) {
            val clipDuration = currentClips[i].trimmedDurationMs
            if (remaining < clipDuration || i == currentClips.lastIndex) {
                val seekPositionInClip = remaining.coerceAtMost(clipDuration)
                player.seekTo(i, seekPositionInClip)
                _state.value = _state.value.copy(currentPositionMs = positionMs)
                return
            }
            remaining -= clipDuration
        }
    }

    /**
     * 计算当前的全局播放位置
     * 累加当前片段之前所有片段的时长 + 当前片段内的偏移量
     */
    fun getCurrentGlobalPosition(): Long {
        if (currentClips.isEmpty() || player.mediaItemCount == 0) return 0L

        val currentIndex = player.currentMediaItemIndex
            .coerceIn(0, currentClips.lastIndex)

        var position = 0L
        for (i in 0 until currentIndex) {
            position += currentClips[i].trimmedDurationMs
        }
        // player.currentPosition 对 clipped media 返回片段内偏移量
        position += player.currentPosition.coerceAtLeast(0)
        return position
    }

    /** 启动位置跟踪协程，每 16ms 更新播放位置并实时应用淡入淡出 */
    private fun startPositionTracking() {
        stopPositionTracking()
        positionJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val globalPos = getCurrentGlobalPosition()
                _state.value = _state.value.copy(currentPositionMs = globalPos)

                // 实时计算淡入淡出增益并应用到播放器音量
                val volume = AudioFadeCalculator.calculateVolumeAtPosition(currentClips, globalPos)
                player.volume = volume.coerceIn(0f, 2f)

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
