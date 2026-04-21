package com.audio.video.ui.screen.export.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.SurfaceContainerHigh
import com.audio.video.ui.theme.TextSecondary

/** 导出进度条 — 带动画的线性进度指示器和百分比文字 */
@Composable
fun ExportProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "export_progress"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
            color = AccentBlue,
            trackColor = SurfaceContainerHigh
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
    }
}
