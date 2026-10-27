package com.yh.schedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yh.schedule.core.SchoolCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** UTC 毫秒 → LocalDate（M3 日期选择器统一用 UTC 零点表示） */
private fun millisToDate(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

private fun dateToMillis(d: LocalDate): Long =
    d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** 距开学第几周（1 起），对齐课表周次口径 */
private fun weekOfTerm(d: LocalDate): Int =
    (ChronoUnit.WEEKS.between(SchoolCalendar.termStartMonday, d).toInt() + 1).coerceAtLeast(1)

/**
 * 起止日期选择。
 *
 * 交互对齐参考源码 `DateRangePickerDialog`：**第一次点起始日、第二次点结束日**，
 * 两次点击即完成范围选择，而不是「先选开始、再切到结束各开一次弹窗」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YhDateRangePickerDialog(
    initialStart: LocalDate? = null,
    initialEnd: LocalDate? = null,
    onSelected: (Pair<LocalDate, LocalDate>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.let { dateToMillis(it) },
        initialSelectedEndDateMillis = initialEnd?.let { dateToMillis(it) },
    )

    val start = state.selectedStartDateMillis?.let { millisToDate(it) }
    val end = state.selectedEndDateMillis?.let { millisToDate(it) }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = {
                    if (start != null && end != null) onSelected(start to end)
                    onDismiss()
                },
            ) { Text("完成") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("取消") }
        },
    ) {
        DateRangePicker(
            state = state,
            title = {},
            headline = {
                Text(
                    "开始 " + (start?.toString() ?: "未选择") +
                            (start?.let { " (第${weekOfTerm(it)}周)" } ?: "") + "\n" +
                            "结束 " + (end?.toString() ?: "未选择") +
                            (end?.let { " (第${weekOfTerm(it)}周)" } ?: ""),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            },
            showModeToggle = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(4.dp),
        )
    }
}

/**
 * 单组时分滚轮（滚轮本体，不含标题/按钮）。
 *
 * 对齐参考源码 `TimePicker`：小时 0-23，分钟按 5 分钟步进
 * （`skipMinutes=true` 时源码就是 5 分钟粒度）。
 */
@Composable
fun YhTimePicker(
    initial: String,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit,
) {
    var init = initial.split(":").mapNotNull { it.toIntOrNull() }
    if (init.size != 2) init = listOf(0, 0)

    var hour by remember { mutableStateOf(init[0]) }
    var minute by remember { mutableStateOf(init[1]) }

    LaunchedEffect(hour, minute) {
        onSelected("%02d:%02d".format(hour, minute))
    }

    val hours = remember { (0..23).toList() }
    val minutes = remember { (0..59 step 5).toList() }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            data = hours,
            initialSelectedIndex = init[0],
            modifier = Modifier.weight(1f),
            onSelect = { _, h -> hour = h },
        ) { h ->
            Text("%02d".format(h), style = MaterialTheme.typography.titleLarge)
        }

        Text(":", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)

        WheelPicker(
            data = minutes,
            initialSelectedIndex = minutes.indexOf(init[1]).coerceAtLeast(0),
            modifier = Modifier.weight(1f),
            onSelect = { _, m -> minute = m },
        ) { m ->
            Text("%02d".format(m), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * 起止时间选择弹窗。
 *
 * 对齐参考源码 `TimeRangePickerDialog`：日程类型给「起 + 止」两组滚轮，
 * DDL 类型只给「截止」一组。
 */
@Composable
fun YhTimeRangePickerDialog(
    isSchedule: Boolean,
    defaultValue: Pair<String, String>,
    onSelected: (Pair<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var startTime by remember { mutableStateOf(defaultValue.first) }
    var endTime by remember { mutableStateOf(defaultValue.second) }
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(startTime, endTime) {
        enabled = endTime.isNotBlank()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isSchedule) "输入起止时间" else "输入截止时间",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (isSchedule) {
                    YhTimePicker(initial = defaultValue.first) { startTime = it }
                    HorizontalDivider()
                }
                YhTimePicker(initial = defaultValue.second) { endTime = it }

                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("取消") }
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            if (isSchedule) onSelected(startTime to endTime)
                            else onSelected(startTime to endTime)
                            onDismiss()
                        },
                    ) { Text("完成") }
                }
            }
        }
    }
}
