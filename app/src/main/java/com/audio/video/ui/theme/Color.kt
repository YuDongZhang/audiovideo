package com.audio.video.ui.theme

import androidx.compose.ui.graphics.Color

// ===== 背景色系 =====
val EditorBlack = Color(0xFF0D0D0D)
val SurfaceDark = Color(0xFF141414)
val SurfaceContainer = Color(0xFF1A1A1A)
val SurfaceContainerHigh = Color(0xFF242424)
val SurfaceContainerHighest = Color(0xFF2E2E2E)
val SurfaceBright = Color(0xFF383838)

// ===== 强调色 =====
val AccentBlue = Color(0xFF4A90FF)
val AccentBlueVariant = Color(0xFF3A7AE0)
val AccentBlueSurface = Color(0xFF1A2A4A)

// ===== 文字色 =====
val TextPrimary = Color(0xFFE8E8E8)
val TextSecondary = Color(0xFF9E9E9E)
val TextDisabled = Color(0xFF5A5A5A)
val TextOnAccent = Color(0xFFFFFFFF)

// ===== 语义色 =====
val ErrorRed = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF4CAF50)
val WarningAmber = Color(0xFFFFB300)

/** 编辑器专用颜色 — Material 3 token 之外的时间线相关色值 */
object EditorColors {
    val ClipDefault = Color(0xFF2D5A8E)
    val ClipSelected = Color(0xFF4A90FF)
    val ClipAudio = Color(0xFF3A8E5A)
    val Playhead = Color(0xFF4A90FF)
    val TrimHandle = Color(0xFFCCCCCC)
    val TimelineRuler = Color(0xFF444444)
    val TimelineRulerText = Color(0xFF777777)
    val TimelineTrackBg = Color(0xFF242424)
}
