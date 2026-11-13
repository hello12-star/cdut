package com.yh.schedule.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.yh.schedule.MainActivity

import com.yh.schedule.core.Course
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.SessionStore
import com.yh.schedule.core.TimetableExporter
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 今日课程桌面小组件（对齐参考源码 HFUT-Schedule 的 FocusWidgetReceiver：Glance）。
 *
 * 只读本地缓存，不联网：从 DataStore 取课表 JSON → 过滤今天要上的课。
 * 整块可点击，打开 App。
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = loadTodayCourses(context)
        provideContent {
            GlanceTheme {
                WidgetContent(items)
            }
        }
    }

    private suspend fun loadTodayCourses(context: Context): List<Course> {
        val store = SessionStore(context)
        val raw = store.timetableJson() ?: return emptyList()
        val all = runCatching { Json.decodeFromString<List<Course>>(raw) }.getOrDefault(emptyList())
        // 直接从 DataStore 读学期起始日：桌面刷新时应用进程可能没启动，
        // 内存里的 SchoolCalendar.termStartMonday 只是默认值，会算错周次。
        val termStart = store.termStartMonday()
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: SchoolCalendar.DEFAULT_TERM_START_MONDAY
        val today = LocalDate.now()
        val week = (ChronoUnit.WEEKS.between(termStart, today).toInt() + 1).coerceIn(1, 30)
        val dow = today.dayOfWeek.value
        return all.filter { it.dayOfWeek == dow && TimetableExporter.weeksOf(it).contains(week) }
            .sortedBy { it.startTime }
    }
}

private fun dip(v: Int) = Dp(v.toFloat())

@Composable
private fun WidgetContent(items: List<Course>) {
    val context = LocalContext.current
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(dip(20))
            .padding(dip(12))
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Text(
            text = "今日课程 · ${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(dip(6)))
        if (items.isEmpty()) {
            Text(
                text = "今天没有课",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
            return@Column
        }
        val now = LocalTime.now()
        LazyColumn(GlanceModifier.fillMaxWidth()) {
            items(items.take(6)) { c ->
                val ended = runCatching { LocalTime.parse(c.endTime) }.getOrNull()
                    ?.let { now > it } == true
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = dip(2)),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Text(
                        text = c.startTime,
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(GlanceModifier.width(dip(8)))
                    Text(
                        text = c.name + if (c.room.isNotBlank()) " · ${c.room}" else "",
                        maxLines = 1,
                        style = TextStyle(
                            color = if (ended) GlanceTheme.colors.onSurfaceVariant
                            else GlanceTheme.colors.onSurface,
                            textDecoration = if (ended) TextDecoration.LineThrough else null,
                        ),
                    )
                }
            }
        }
    }
}
