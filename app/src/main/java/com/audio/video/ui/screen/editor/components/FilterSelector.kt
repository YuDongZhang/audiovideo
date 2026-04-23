package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.audio.video.data.model.VideoFilterType
import com.audio.video.ui.theme.AccentBlue
import com.audio.video.ui.theme.SurfaceContainerHigh
import com.audio.video.ui.theme.TextOnAccent
import com.audio.video.ui.theme.TextSecondary

/**
 * 滤镜选择器 — 水平滚动的滤镜色块列表
 * 每个色块用对应滤镜的特征色表示，当前选中的有蓝色边框
 */
@Composable
fun FilterSelector(
    selectedFilter: VideoFilterType,
    onFilterSelected: (VideoFilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(VideoFilterType.entries) { filter ->
                FilterChipItem(
                    filterType = filter,
                    isSelected = filter == selectedFilter,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    filterType: VideoFilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val previewColor = filterPreviewColor(filterType)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(previewColor)
                .then(
                    if (isSelected) Modifier.border(2.dp, AccentBlue, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (filterType == VideoFilterType.NONE) {
                Text("原", style = MaterialTheme.typography.labelSmall, color = TextOnAccent)
            }
        }
        Text(
            text = filterType.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AccentBlue else TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 每种滤镜的特征预览色 */
private fun filterPreviewColor(type: VideoFilterType): Color = when (type) {
    VideoFilterType.NONE -> Color(0xFF3A3A3A)
    VideoFilterType.GRAYSCALE -> Color(0xFF808080)
    VideoFilterType.WARM -> Color(0xFFCC8844)
    VideoFilterType.COOL -> Color(0xFF4488CC)
    VideoFilterType.VINTAGE -> Color(0xFF8B7355)
    VideoFilterType.VIVID -> Color(0xFFFF6B6B)
    VideoFilterType.INVERT -> Color(0xFF222222)
}
