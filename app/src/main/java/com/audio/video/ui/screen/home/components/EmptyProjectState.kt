package com.audio.video.ui.screen.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audio.video.ui.theme.TextDisabled
import com.audio.video.ui.theme.TextSecondary

/**
 * 项目列表为空时的占位提示组件
 */
@Composable
fun EmptyProjectState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = TextDisabled
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "创建你的第一个项目",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击 + 开始剪辑",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDisabled
        )
    }
}
