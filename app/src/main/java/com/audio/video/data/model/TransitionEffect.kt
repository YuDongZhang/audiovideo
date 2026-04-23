package com.audio.video.data.model

/**
 * 转场效果 — 定义两个相邻片段之间的过渡动画
 *
 * 原理：转场期间同时显示前后两个片段的画面
 * - 交叉溶解 (Crossfade): outputColor = colorA × (1-t) + colorB × t
 * - 渐黑 (Fade to Black): 前片段淡出到黑 → 后片段从黑淡入
 *
 * t = 转场进度 [0.0, 1.0]
 * durationMs = 转场时长，从前片段末尾的 durationMs/2 到后片段开头的 durationMs/2
 */
data class TransitionEffect(
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 500L
)

enum class TransitionType(val label: String) {
    NONE("无"),
    CROSSFADE("溶解"),
    FADE_BLACK("渐黑")
}
