package com.audio.video.data.model

import java.util.UUID

/**
 * 文字叠加层 — 在视频帧上显示的文字元素
 *
 * 渲染方式：在 VideoFrameProcessor 或 Canvas 叠加层中
 * 根据当前播放时间判断是否在 [startTimeMs, endTimeMs] 范围内
 * 若在范围内则在 (positionX, positionY) 位置绘制文字
 *
 * positionX / positionY: 归一化坐标 [0.0, 1.0]，(0,0)=左上角，(1,1)=右下角
 */
data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.8f,
    val fontSize: Float = 24f,
    val color: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x80000000
)
