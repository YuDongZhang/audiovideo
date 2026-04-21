package com.audio.video.ui.screen.export.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.data.model.ExportQuality
import com.audio.video.data.model.ExportResolution
import com.audio.video.ui.theme.AccentBlue

/** 分辨率选择器 — 以 FilterChip 形式展示分辨率选项 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResolutionSelector(
    selectedResolution: ExportResolution,
    onResolutionSelected: (ExportResolution) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "分辨率",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
        ) {
            ExportResolution.entries.forEach { resolution ->
                FilterChip(
                    selected = resolution == selectedResolution,
                    onClick = { onResolutionSelected(resolution) },
                    label = { Text(resolution.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
            }
        }
    }
}

/** 质量选择器 — 以 FilterChip 形式展示质量选项 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QualitySelector(
    selectedQuality: ExportQuality,
    onQualitySelected: (ExportQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "画质",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
        ) {
            ExportQuality.entries.forEach { quality ->
                FilterChip(
                    selected = quality == selectedQuality,
                    onClick = { onQualitySelected(quality) },
                    label = { Text(quality.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
            }
        }
    }
}
