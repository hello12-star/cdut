package com.yh.schedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yh.schedule.core.Course
import com.yh.schedule.core.SchoolCalendar
import java.time.temporal.ChronoUnit

/**
 * 课程详情页（全屏）。
 *
 * 布局与字段对齐参考源码 `ui/screen/home/search/.../totalCourse/TotalCourseUI.kt` 的 `DetailItems`：
 * 蓝色大标题 + 两列信息块（类型/周数、同班同学/学分）+ 虚线分组 + 单列信息（教师、开设学院/考察方式、
 * 课程代码、学期、班级、上课安排）。
 *
 * 数据来源说明：强智教务课表只给出 课程名/教师/地点/周次/节次，
 * 因此「类型、同班同学、学分、开设学院、考察方式、课程代码、班级」这些合工大专用字段
 * **拿不到就不渲染**（源码同样用 `?.let` 判空），不做任何编造。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courses: List<Course>,
    onDismiss: () -> Unit,
) {
    if (courses.isEmpty()) return
    // 与 AddEventScreen 同样用全屏 Dialog 承载，避免被父级 Box 的约束切掉
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CourseDetailContent(courses = courses, onDismiss = onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailContent(
    courses: List<Course>,
    onDismiss: () -> Unit,
) {
    val main = courses.first()
    val teachers = courses.mapNotNull { it.teacher.takeIf { t -> t.isNotBlank() } }.distinct()
    val weeksText = weeksRangeText(courses.flatMap { it.weeks })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                main.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 38.sp,
            )
            Spacer(Modifier.height(24.dp))

            // 周数（教务能提供）
            if (weeksText.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailItem(Icons.Filled.CalendarMonth, "周数", weeksText, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
            }

            DashedDivider(Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))

            // 教师
            if (teachers.isNotEmpty()) {
                DetailItem(Icons.Filled.PersonOutline, "教师", teachers.joinToString("、"))
                Spacer(Modifier.height(18.dp))
                DashedDivider(Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
            }

            // 学期（按开学周一推算）
            DetailItem(Icons.Filled.BookmarkBorder, "学期", termText())
            Spacer(Modifier.height(18.dp))

            // 上课安排
            val schedule = scheduleTexts(courses)
            if (schedule.isNotEmpty()) {
                DetailItem(
                    Icons.Filled.Schedule,
                    "上课安排",
                    schedule.joinToString("\n"),
                )
                Spacer(Modifier.height(18.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 单条信息：左侧图标 + 上方小字标签 + 下方内容 */
@Composable
private fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 16.sp, lineHeight = 22.sp)
        }
    }
}

/** 把 [2,3,4,5,9,10,11] 压成「2~5,9~11」 */
private fun weeksRangeText(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            parts.add(if (start == prev) "$start" else "$start~$prev")
            start = w
            prev = w
        }
    }
    parts.add(if (start == prev) "$start" else "$start~$prev")
    return parts.joinToString(",") + "周"
}

/** 上课安排：每周次一行「1~5,9~19周 周一 第一节~第二节 乙103 李雪琳」 */
private fun scheduleTexts(courses: List<Course>): List<String> =
    courses.map { c ->
        val dayName = "周" + listOf("一", "二", "三", "四", "五", "六", "日")
            .getOrElse(c.dayOfWeek - 1) { "?" }
        val from = cnSection(c.startSection)
        val to = cnSection(c.startSection + c.sectionCount - 1)
        buildString {
            append(weeksRangeText(c.weeks).ifBlank { "全周" })
            append(' ')
            append(dayName)
            append(' ')
            append(if (from == to) from else "$from~$to")
            if (c.room.isNotBlank()) {
                append(' ')
                append(c.room)
            }
            if (c.teacher.isNotBlank()) {
                append(' ')
                append(c.teacher)
            }
        }
    }.distinct()

/** 1 → 第一节 */
private fun cnSection(s: Int): String = "第" + when (s) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
    6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"; 10 -> "十"
    11 -> "十一"; 12 -> "十二"; else -> "$s"
} + "节"

/** 2026-2027学年第一学期（按 termStartMonday 所在年份推） */
private fun termText(): String {
    val start = SchoolCalendar.termStartMonday
    val y = start.year
    val firstTerm = start.monthValue >= 8
    return if (firstTerm) "$y-${y + 1}学年第一学期" else "$y-${y + 1}学年第二学期"
}

/** 虚线分隔（与添加页同款，Compose 无内置） */
@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = androidx.compose.ui.graphics.PathEffect
                .dashPathEffect(floatArrayOf(8f, 8f), 0f),
        )
    }
}
