package com.yh.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yh.schedule.core.Course
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.TimetableExporter
import com.yh.schedule.data.Repository
import kotlinx.coroutines.launch

/**
 * 课程搜索：在本地缓存的课表里按课程名 / 教师 / 教室检索。
 * 只查缓存，不发请求；没抓过课表时给提示。
 */
@Composable
fun CourseSearchScreen(
    repo: Repository,
    onBack: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val colorOverrides by repo.courseColors.collectAsState(initial = emptyMap())

    LaunchedEffect(Unit) {
        courses = repo.timetable(SchoolCalendar.currentWeek())
        loaded = true
    }

    val keyword = query.trim()
    val result = remember(courses, keyword) {
        if (keyword.isBlank()) courses
        else courses.filter {
            it.name.contains(keyword, ignoreCase = true) ||
                    it.teacher.contains(keyword, ignoreCase = true) ||
                    it.room.contains(keyword, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("课程搜索", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("课程名 / 教师 / 教室") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(8.dp))

        when {
            loaded && courses.isEmpty() -> Hint(
                "还没有课表数据",
                "先在「聚焦」页下拉刷新完成一次教务抓取，之后就能在这里搜索。",
            )
            loaded && result.isEmpty() -> Hint("没有匹配的课程", "换个关键词试试，比如教师姓氏或教室号。")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(result, key = { it.name + it.dayOfWeek + it.startTime + it.room }) { course ->
                    CourseRow(
                        course = course,
                        colorIndex = com.yh.schedule.core.CoursePalette.indexFor(
                            course.name, colorOverrides,
                        ),
                        onCycleColor = {
                            scope.launch {
                                val current = com.yh.schedule.core.CoursePalette.indexFor(
                                    course.name, colorOverrides,
                                )
                                val next = (current + 1) %
                                        com.yh.schedule.core.CoursePalette.backgrounds.size
                                repo.setCourseColor(course.name, next)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(title: String, body: String) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CourseRow(
    course: Course,
    colorIndex: Int,
    onCycleColor: () -> Unit,
) {
    val weeks = TimetableExporter.weeksOf(course)
    val weekText = when {
        weeks.isEmpty() -> ""
        weeks.size == 1 -> "仅第${weeks.first()}周"
        else -> "第${weeks.first()}-${weeks.last()}周（共${weeks.size}次）"
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 色块：点一下换下一个配色，课表块和导出长图会同步
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(com.yh.schedule.core.CoursePalette.background(colorIndex)))
                    .clickable { onCycleColor() },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
            Text(course.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append("周${"一二三四五六日"[course.dayOfWeek.coerceIn(1, 7) - 1]}")
                    append(" ${course.startTime}-${course.endTime}")
                    if (course.room.isNotBlank()) append(" · ${course.room}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (course.teacher.isNotBlank() || weekText.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        course.teacher.takeIf { it.isNotBlank() },
                        weekText.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }
}
