package com.yh.schedule.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
 * 今日课程桌面小组件（Glance）。
 *
 * 只读本地缓存，不联网。头部可手动刷新；底部显示明天的课程数量与第一节课，
 * 和「前一天 22:00 日历提醒」形成互补。
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            GlanceTheme {
                WidgetContent(data)
            }
        }
    }

    private suspend fun loadWidgetData(context: Context): WidgetData {
        val store = SessionStore(context)
        val raw = store.timetableJson() ?: return WidgetData(emptyList(), emptyList())
        val all = runCatching { Json.decodeFromString<List<Course>>(raw) }
            .getOrDefault(emptyList())
        // 直接从 DataStore 读学期起始日：桌面刷新时应用进程可能没启动，
        // 内存里的 SchoolCalendar.termStartMonday 只是默认值，会算错周次。
        val termStart = store.termStartMonday()
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: SchoolCalendar.DEFAULT_TERM_START_MONDAY

        fun coursesOf(date: LocalDate): List<Course> {
            val week = (ChronoUnit.WEEKS.between(termStart, date).toInt() + 1).coerceIn(1, 30)
            return all.filter {
                it.dayOfWeek == date.dayOfWeek.value &&
                        TimetableExporter.weeksOf(it).contains(week)
            }.sortedBy { it.startTime }
        }

        val today = LocalDate.now()
        return WidgetData(coursesOf(today), coursesOf(today.plusDays(1)))
    }
}

private data class WidgetData(
    val today: List<Course>,
    val tomorrow: List<Course>,
)

class RefreshTodayWidget : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        TodayWidget().update(context, glanceId)
    }
}

private fun dip(v: Int) = Dp(v.toFloat())

@Composable
private fun WidgetContent(data: WidgetData) {
    val context = LocalContext.current
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(dip(20))
            .padding(dip(12))
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Row(
            GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = "今日课程 · ${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "刷新",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshTodayWidget>()),
                style = TextStyle(color = GlanceTheme.colors.primary),
            )
        }
        Spacer(GlanceModifier.height(dip(6)))
        if (data.today.isEmpty()) {
            Text(
                text = "今天没有课",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            val now = LocalTime.now()
            LazyColumn(GlanceModifier.fillMaxWidth()) {
                items(data.today.take(6)) { c ->
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
        Spacer(GlanceModifier.height(dip(6)))
        val next = data.tomorrow.firstOrNull()
        Text(
            text = if (next == null) "明天没课"
            else "明天 ${data.tomorrow.size} 节课 · ${next.startTime} ${next.name}",
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}
