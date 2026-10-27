package com.yh.schedule.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yh.schedule.core.TimeTableItem
import com.yh.schedule.core.TimeTableType

/**
 * 课程/日程详情弹层（对齐原版 `TimeTableDetail`）：
 * 时间（周几 + 起止）、地点、教师、类型。
 */
@Composable
fun CourseDetailDialog(
    item: TimeTableItem,
    onDismiss: () -> Unit,
) {
    val dayName = "周" + listOf("一", "二", "三", "四", "五", "六", "日")
        .getOrElse(item.dayOfWeek - 1) { "?" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        InfoLine(
                            "时间",
                            "$dayName ${item.startTime} ~ ${item.endTime}",
                        )
                        when (item.type) {
                            TimeTableType.COURSE -> {
                                item.place?.let { InfoLine("地点", it) }
                                item.teacher?.takeIf { it.isNotBlank() }
                                    ?.let { InfoLine("教师", it) }
                                InfoLine("类型", "课程")
                            }
                            TimeTableType.FOCUS -> {
                                InfoLine("类型", "自定义日程")
                                item.place?.let { InfoLine("描述", it) }
                            }
                            TimeTableType.EXAM -> {
                                item.place?.let { InfoLine("地点", it) }
                                InfoLine("类型", "考试")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 14.sp)
    }
}
