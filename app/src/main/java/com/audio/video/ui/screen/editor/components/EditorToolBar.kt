package com.audio.video.ui.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.TextDisabled
import com.audio.video.ui.theme.TextSecondary

/**
 * 编辑器底部工具栏 — 分割、删除、添加片段、撤销、重做
 */
@Composable
fun EditorToolBar(
    hasSelection: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onAddClip: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                icon = { Icon(Icons.Default.ContentCut, contentDescription = "分割", modifier = Modifier.size(22.dp)) },
                label = "分割",
                enabled = hasSelection,
                onClick = onSplit
            )
            ToolButton(
                icon = { Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(22.dp)) },
                label = "删除",
                enabled = hasSelection,
                onClick = onDelete
            )
            ToolButton(
                icon = { Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(22.dp)) },
                label = "添加",
                enabled = true,
                onClick = onAddClip
            )
            ToolButton(
                icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销", modifier = Modifier.size(22.dp)) },
                label = "撤销",
                enabled = canUndo,
                onClick = onUndo
            )
            ToolButton(
                icon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做", modifier = Modifier.size(22.dp)) },
                label = "重做",
                enabled = canRedo,
                onClick = onRedo
            )
        }
    }
}

/** 工具栏单个按钮 — 图标 + 文字标签 */
@Composable
private fun ToolButton(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) TextSecondary else TextDisabled

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
