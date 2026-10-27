package com.yh.schedule.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 课程（来源：强智教务 HTML 解析）。
 *
 * 对齐参考源码 HFUT-Schedule 的 TimeTableItem 设计：
 * 课表按**时间轴**渲染（startTime/endTime 为 HH:mm），而非节次行。
 * 多段周次（如 `2-5,9-19(周)`）展开为 [weeks]，用于精确过滤。
 */
@Serializable
data class Course(
    val name: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,      // 1=周一 ... 7=周日
    val startSection: Int,   // 1..12（保留：用于兜底与调试）
    val sectionCount: Int,
    val startWeek: Int,
    val endWeek: Int,
    /** 0=每周, 1=单周, 2=双周 */
    val weekParity: Int = 0,
    val startTime: String,
    val endTime: String,
    /** 具体上课周次（多段周次展开）；为空时回退 startWeek..endWeek + 奇偶 */
    val weeks: List<Int> = emptyList(),
)

/** 课表项类型（对齐源码 TimeTableType） */
enum class TimeTableType { COURSE, FOCUS, EXAM }

/**
 * 课表渲染单元：课程 / 自定义日程 / 考试 统一成时间轴上的方块。
 * 对齐源码 HFUT-Schedule: `ui/screen/home/calendar/timetable/logic/TimeTableItem.kt`
 */
data class TimeTableItem(
    val type: TimeTableType,
    val name: String,
    val dayOfWeek: Int,      // 1..7
    val startTime: String,   // HH:mm
    val endTime: String,     // HH:mm
    val place: String? = null,
    val teacher: String? = null,
    /** FOCUS 类型对应的日程 id */
    val eventId: Long? = null,
) {
    val titleWithTeacher: String
        get() = if (teacher.isNullOrBlank()) name else "$name@$teacher"
}

data class Exam(
    val course: String,
    val date: LocalDate,
    val timeRange: String,
    val room: String,
)

data class GradeItem(
    val term: String,
    val course: String,
    val credit: Double,
    val score: Double,
    val gradePoint: Double?,
)

/** 日程类型（对齐原版 CustomEventType：日程 / DDL） */
enum class EventType { SCHEDULE, DEADLINE }

/**
 * 自定义日程（对齐原版 CustomEventDTO）：
 * - [type] 日程（有始有终，显示在课表格子）/ DDL（只看截止时间）
 * - 起止各含**日期 + 时间**，支持跨天
 * - [remark] 为自动生成的时间描述（如「第8周 周三 08:00」），作为卡片 overline
 * - [note] 为用户填写的备注/描述，作为卡片 supporting
 */
data class CustomEvent(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate = date,
    val startTime: String = "08:00",
    val endTime: String = "09:00",
    val note: String = "",
    val type: EventType = EventType.SCHEDULE,
    val remark: String = "",
)

/** 聚焦页顶部的"重要安排"卡片数据 */
data class ImportantCard(
    val icon: String,
    val title: String,
    val value: String,
)

object SchoolCalendar {

    /**
     * 学期第一周周一。
     * 2026-2027-1：经核对，10/19-10/25 为**第 8 周**（同学口径），故第一周从 8/31 起算。
     * （此前误设 9/7，导致周次整体偏小 1 周）
     */
    var termStartMonday: LocalDate = LocalDate.of(2026, 8, 31)

    fun currentWeek(today: LocalDate = LocalDate.now()): Int {
        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(termStartMonday, today).toInt() + 1
        return weeks.coerceAtLeast(1)
    }

    fun chineseDay(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

    fun dateHeader(today: LocalDate = LocalDate.now()): String {
        val w = currentWeek(today)
        return "%s 第%d周 %s".format(
            today.format(DateTimeFormatter.ofPattern("MM-dd")),
            w, chineseDay(today.dayOfWeek)
        )
    }

    /** 课程是否在指定周上课：优先使用多段周次 [Course.weeks] */
    fun courseInWeek(c: Course, week: Int): Boolean {
        if (c.weeks.isNotEmpty()) return week in c.weeks
        if (week < c.startWeek || week > c.endWeek) return false
        return when (c.weekParity) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }

    /** 某日课程 */
    fun coursesOfDay(all: List<Course>, date: LocalDate, week: Int): List<Course> =
        all.filter { it.dayOfWeek == date.dayOfWeek.value && courseInWeek(it, week) }
}
