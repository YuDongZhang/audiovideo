package com.audio.video.ui.screen.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.data.model.Project
import com.audio.video.ui.theme.SurfaceContainerHigh
import com.audio.video.ui.theme.TextDisabled
import com.audio.video.ui.theme.TextSecondary
import com.audio.video.util.formatDurationMs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 项目卡片组件，展示项目缩略图、名称、时长和更新时间
 */
@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            // 缩略图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = TextDisabled
                )
                // 右下角时长标签
                if (project.totalDurationMs > 0) {
                    Text(
                        text = formatDurationMs(project.totalDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    )
                }
            }

            // 项目信息行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = formatDate(project.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // 更多操作菜单
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
