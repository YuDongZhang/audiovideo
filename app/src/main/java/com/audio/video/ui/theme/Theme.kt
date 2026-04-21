package com.audio.video.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** 暗色专业主题配色方案 — 强制暗色，不随系统切换 */
private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextOnAccent,
    primaryContainer = AccentBlueSurface,
    onPrimaryContainer = AccentBlue,
    secondary = AccentBlueVariant,
    onSecondary = TextOnAccent,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = TextSecondary,
    background = EditorBlack,
    onBackground = TextPrimary,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = SurfaceBright,
    outlineVariant = SurfaceContainer,
    error = ErrorRed,
    onError = TextOnAccent,
    inverseSurface = TextPrimary,
    inverseOnSurface = SurfaceDark
)

@Composable
fun AudioVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
