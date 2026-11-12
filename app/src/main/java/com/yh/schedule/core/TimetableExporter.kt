package com.yh.schedule.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.io.File

/**
 * 课表导出：.ics（可导入系统日历）与长图（当前周快照）。
 *
 * 这里只做纯生成，不碰文件/权限——调用方决定写到哪（SAF 或应用私有目录）。
 */
object TimetableExporter {

    private val ICS_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** 每门课按上课周次展开成多次事件；时间用"浮动时间"，不绑定时区，导入后按本地时间走 */
    fun toIcs(courses: List<Course>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//CDUT Helper//Timetable//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")

        courses.forEachIndexed { index, course ->
            val weeks = weeksOf(course)
            val start = runCatching { LocalTime.parse(course.startTime) }.getOrNull() ?: return@forEachIndexed
            val end = runCatching { LocalTime.parse(course.endTime) }.getOrNull() ?: return@forEachIndexed
            weeks.forEach { week ->
                val date = SchoolCalendar.termStartMonday
                    .plusWeeks((week - 1).toLong())
                    .plusDays((course.dayOfWeek - 1).toLong())
                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:cdut-$index-$week-${course.dayOfWeek}@yh.schedule\r\n")
                sb.append("DTSTAMP:").append(ICS_TIME.format(java.time.LocalDateTime.now())).append("\r\n")
                sb.append("DTSTART:").append(ICS_TIME.format(date.atTime(start))).append("\r\n")
                sb.append("DTEND:").append(ICS_TIME.format(date.atTime(end))).append("\r\n")
                sb.append("SUMMARY:").append(escape(course.name)).append("\r\n")
                if (course.room.isNotBlank()) {
                    sb.append("LOCATION:").append(escape(course.room)).append("\r\n")
                }
                if (course.teacher.isNotBlank()) {
                    sb.append("DESCRIPTION:").append(escape(course.teacher)).append("\r\n")
                }
                sb.append("END:VEVENT\r\n")
            }
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    fun weeksOf(course: Course): List<Int> {
        if (course.weeks.isNotEmpty()) return course.weeks.distinct().sorted()
        val range = course.startWeek..course.endWeek
        return when (course.weekParity) {
            1 -> range.filter { it % 2 == 1 }
            2 -> range.filter { it % 2 == 0 }
            else -> range.toList()
        }
    }

    /**
     * 当前周课表长图：7 天 × 08:00–22:00。
     * 只画本周要上的课，避免整学期挤在一张图上糊成一片。
     */
    fun toBitmap(courses: List<Course>, week: Int, weekLabel: String): Bitmap {
        val width = 1080
        val leftAxis = 96f
        val topTitle = 96f
        val dayHeader = 64f
        val startMinute = 8 * 60
        val endMinute = 22 * 60
        val hourHeight = 82f
        val bottomPad = 36f
        val gridHeight = (endMinute - startMinute) / 60f * hourHeight
        val height = (topTitle + dayHeader + gridHeight + bottomPad).toInt()

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#FAFAFA"))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666")
            textSize = 26f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6E6E6")
            strokeWidth = 1f
        }
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("掌上成理 · $weekLabel", 36f, 62f, titlePaint)

        val colWidth = (width - leftAxis) / 7f
        val gridTop = topTitle + dayHeader
        val todayDow = LocalDate.now().dayOfWeek.value
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

        // 日期表头
        for (d in 1..7) {
            val cx = leftAxis + colWidth * (d - 1) + colWidth / 2
            val isToday = d == todayDow
            canvas.drawText(
                "周${dayNames[d - 1]}",
                cx,
                topTitle + 42f,
                if (isToday) todayPaint else dayPaint,
            )
        }

        // 时间刻度与横线
        var hour = startMinute / 60
        while (hour <= endMinute / 60) {
            val y = gridTop + (hour * 60 - startMinute) / 60f * hourHeight
            canvas.drawLine(leftAxis, y, width.toFloat(), y, linePaint)
            canvas.drawText(String.format("%02d:00", hour), leftAxis - 12f, y + 8f, hourPaint)
            hour++
        }
        // 竖线
        for (d in 0..7) {
            val x = leftAxis + colWidth * d
            canvas.drawLine(x, gridTop, x, gridTop + gridHeight, linePaint)
        }

        // 课程块
        val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val courseTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102A43")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val courseSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334E68")
            textSize = 20f
        }

        courses.filter { weeksOf(it).contains(week) }.forEach { course ->
            val s = runCatching { LocalTime.parse(course.startTime) }.getOrNull() ?: return@forEach
            val e = runCatching { LocalTime.parse(course.endTime) }.getOrNull() ?: return@forEach
            val day = course.dayOfWeek.coerceIn(1, 7)
            val top = gridTop + (s.hour * 60 + s.minute - startMinute) / 60f * hourHeight
            val bottom = gridTop + (e.hour * 60 + e.minute - startMinute) / 60f * hourHeight
            val left = leftAxis + colWidth * (day - 1) + 3f
            val right = left + colWidth - 6f
            val rect = RectF(left, top + 2f, right, (bottom - 2f).coerceAtLeast(top + 18f))

            blockPaint.color = paletteColor(course.name)
            canvas.drawRoundRect(rect, 10f, 10f, blockPaint)

            val pad = 10f
            var ty = rect.top + 28f
            wrapText(canvas, course.name, courseTitle, rect.width() - pad * 2).forEach { line ->
                canvas.drawText(line, rect.left + pad, ty, courseTitle)
                ty += 26f
            }
            if (course.room.isNotBlank() && ty + 20f < rect.bottom) {
                wrapText(canvas, course.room, courseSub, rect.width() - pad * 2).take(2).forEach { line ->
                    if (ty + 20f < rect.bottom) {
                        canvas.drawText(line, rect.left + pad, ty, courseSub)
                        ty += 22f
                    }
                }
            }
        }

        return bmp
    }

    private fun wrapText(canvas: Canvas, text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        for (ch in text) {
            val next = current + ch
            if (paint.measureText(next) > maxWidth && current.isNotEmpty()) {
                lines += current
                current = ch.toString()
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    /** 按课程名取稳定颜色（浅色底，保证深色文字可读） */
    private fun paletteColor(name: String): Int {
        val palette = intArrayOf(
            0xFFE3F2FD.toInt(), 0xFFE8F5E9.toInt(), 0xFFFFF3E0.toInt(),
            0xFFF3E5F5.toInt(), 0xFFE0F7FA.toInt(), 0xFFFFF8E1.toInt(),
            0xFFFCE4EC.toInt(), 0xFFEDE7F6.toInt(),
        )
        val hash = name.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
        return palette[hash % palette.size]
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    // ---------- 落盘与分享 ----------

    /** 写到应用自己的下载目录（无需存储权限），返回文件 */
    fun writeToDownloads(context: android.content.Context, name: String, write: (java.io.OutputStream) -> Unit): File {
        val dir = (context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir)
        dir.mkdirs()
        val file = File(dir, name)
        file.outputStream().use(write)
        return file
    }

    /** 弹系统分享面板，用户可以存到网盘/微信/文件管理器 */
    fun share(context: android.content.Context, file: File, mime: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(send, "分享课表")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
