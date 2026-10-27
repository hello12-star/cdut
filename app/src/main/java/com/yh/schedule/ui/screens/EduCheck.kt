package com.yh.schedule.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 教务系统检查卡（对齐原版「正在检查教务系统登陆状态 / 如教务系统封网可能较慢，请稍候」）。
 *
 * 检查中：左侧转动圆环；
 * 失败：变红色「后台认证失败」，可点击重试，并在 2.5 秒后自动收起（不常驻打扰）。
 */
@Composable
internal fun EduCheckCard(
    checking: Boolean,
    /** 非空表示失败，内容就是失败原因（直接显示给用户看，便于排查） */
    error: String?,
    onRetry: () -> Unit,
) {
    val failed = error != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (failed) it.clickable { onRetry() } else it }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 同理：只有 failed 才用红色刷新图标；成功退出动画期间继续显示转圈，不闪红
            if (failed) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                // 注意：抓取成功时 checking 和 failed 都是 false，但退出动画期间卡片还在，
                // 若按 if (checking) ... else "后台认证失败" 写，就会在成功那一刻闪一下红字。
                // 所以只有 failed 才显示失败态，其余一律沿用"正在检查"。
                Text(
                    text = if (failed) "后台认证失败（点此重试）" else "正在检查教务系统登陆状态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (failed) error.orEmpty() else "如教务系统封网可能较慢，请稍候",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
