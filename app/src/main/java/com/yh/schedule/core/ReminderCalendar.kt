package com.yh.schedule.core

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 上课 / 考试提醒：写入系统日历，由日历 App 在前一天 22:00 弹通知。
 *
 * 不依赖后台常驻或通知权限：每个有课/有考试的日期生成一条 22:00 的日历事件，
 * 事件里带 0 分钟提醒（即事件开始时提醒）。用户重新同步时会先删除本应用
 * 之前写入的提醒（以 [MARK] 标记），避免重复。
 */
object ReminderCalendar {

    private const val MARK = "[掌上成理提醒]"
    private const val REMINDER_HOUR = 22
    private const val REMINDER_MINUTE = 0
    private const val SEMESTER_WEEKS = 20L

    data class ReminderDay(
        val date: LocalDate,
        val courses: List<Course>,
        val exams: List<Exam>,
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * 展开需要提醒的日期：从明天到学期第 20 周，只保留有课或有考试的日期。
     * 同一天既有课又有考试时合并成一条提醒，避免 22:00 弹两次。
     */
    fun buildDays(
        courses: List<Course>,
        exams: List<Exam>,
        termStart: LocalDate,
        from: LocalDate = LocalDate.now().plusDays(1),
        to: LocalDate = termStart.plusWeeks(SEMESTER_WEEKS),
    ): List<ReminderDay> {
        if (to.isBefore(from)) return emptyList()
        val examByDate = exams.groupBy { it.date }
        val result = mutableListOf<ReminderDay>()
        var date = from
        while (!date.isAfter(to)) {
            val week = ChronoUnit.WEEKS.between(termStart, date).toInt() + 1
            val dayCourses = courses
                .filter { it.dayOfWeek == date.dayOfWeek.value && SchoolCalendar.courseInWeek(it, week) }
                .sortedBy { it.startTime }
            val dayExams = examByDate[date].orEmpty().sortedBy { it.timeRange }
            if (dayCourses.isNotEmpty() || dayExams.isNotEmpty()) {
                result += ReminderDay(date, dayCourses, dayExams)
            }
            date = date.plusDays(1)
        }
        return result
    }

    /**
     * 写入系统日历。返回写入的提醒天数。
     * 会先删除本应用之前写入的提醒，重复调用不会产生重复事件。
     */
    fun sync(
        context: Context,
        courses: List<Course>,
        exams: List<Exam>,
        termStart: LocalDate,
    ): Int {
        if (!hasPermission(context)) throw SecurityException("缺少日历权限")
        val calendarId = writableCalendarId(context)
            ?: createLocalCalendar(context)
            ?: throw IllegalStateException("没有可写的系统日历，请先在日历 App 中添加一个日历")
        clearMarkedEvents(context)

        val zone = ZoneId.systemDefault()
        val resolver = context.contentResolver
        val days = buildDays(courses, exams, termStart)
        days.forEach { day ->
            val remindAt = LocalDateTime.of(
                day.date.minusDays(1),
                LocalTime.of(REMINDER_HOUR, REMINDER_MINUTE),
            )
            val title = when {
                day.courses.isNotEmpty() && day.exams.isNotEmpty() -> "明天有课和考试"
                day.exams.isNotEmpty() -> "明天有考试"
                else -> "明天有课"
            }
            val description = buildString {
                append(MARK).append('\n')
                append(day.date.monthValue).append('月').append(day.date.dayOfMonth).append('日')
                append("（").append(SchoolCalendar.chineseDay(day.date.dayOfWeek)).append("）")
                if (day.courses.isNotEmpty()) {
                    append("\n上课：")
                    day.courses.forEach { c ->
                        append('\n').append(c.startTime).append('-').append(c.endTime)
                        append(' ').append(c.name)
                        if (c.room.isNotBlank()) append(" · ").append(c.room)
                    }
                }
                if (day.exams.isNotEmpty()) {
                    append("\n考试：")
                    day.exams.forEach { e ->
                        append('\n').append(e.timeRange).append(' ').append(e.course)
                        if (e.room.isNotBlank()) append(" · ").append(e.room)
                    }
                }
            }
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(
                    CalendarContract.Events.DTSTART,
                    remindAt.atZone(zone).toInstant().toEpochMilli(),
                )
                put(
                    CalendarContract.Events.DTEND,
                    remindAt.plusMinutes(15).atZone(zone).toInstant().toEpochMilli(),
                )
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return@forEach
            val eventId = ContentUris.parseId(uri)
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 0)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            runCatching { resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) }
        }
        return days.size
    }

    /** 删除本应用写入的全部提醒（跨日历，按 [MARK] 识别） */
    fun clear(context: Context) {
        if (!hasPermission(context)) throw SecurityException("缺少日历权限")
        clearMarkedEvents(context)
    }

    private fun clearMarkedEvents(context: Context) {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf("%$MARK%"),
            null,
        ) ?: return
        val ids = mutableListOf<Long>()
        cursor.use {
            while (it.moveToNext()) ids += it.getLong(0)
        }
        ids.forEach { id ->
            runCatching {
                resolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                    null,
                    null,
                )
            }
        }
    }

    /**
     * 设备上没有任何可写日历时，创建一个本地日历。
     * 模拟器/极简系统常见这种情况；有 Google/厂商日历的用户不会走到这里。
     */
    private fun createLocalCalendar(context: Context): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "local")
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "local")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "掌上成理")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "掌上成理")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF1565C0.toInt())
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "local")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 0)
        }
        val inserted = context.contentResolver.insert(uri, values) ?: return null
        return ContentUris.parseId(inserted)
    }

    /** 优先主日历，取第一个可写（contributor 及以上）且可见的日历 */
    private fun writableCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? " +
                "AND ${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val sort = "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }
}
