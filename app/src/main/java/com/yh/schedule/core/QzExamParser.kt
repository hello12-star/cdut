package com.yh.schedule.core

import java.time.LocalDate

/**
 * 强智「考试安排」表格 → 结构化 [Exam]。
 *
 * 页面是筛选表单 + 一张结果表，列顺序各学期可能不同，所以这里按表头关键词定位列，
 * 定位不到再按单元格内容特征回退（日期/时间/带"楼|室|馆|场"的字样）。
 * 解析不出日期的行直接丢弃——宁可少显示，也不要把筛选条件那一行当成考试。
 */
object QzExamParser {

    private val YEAR_MONTH_DAY = Regex(
        "(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})"
    )

    /** 只有月日，如 12-25 / 12月25日 */
    private val MONTH_DAY = Regex("(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})")

    private val TIME_RANGE = Regex(
        "(\\d{1,2})\\s*[:：]\\s*(\\d{2})\\s*[-~～—至到]+\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2})"
    )

    /** 半角/全角冒号混用的单个时刻，用于"考试时间"里只写了开始时刻的情况 */
    private val SINGLE_TIME = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})")

    fun parse(rows: List<List<String>>): List<Exam> {
        if (rows.size < 2) return emptyList()
        val header = rows.first().map { it.trim() }
        // 列名里有「课程编号」和「课程名称」，必须优先取名称，否则会把课程号当成课程
        val courseCol = header.indexOfFirst {
            it.contains("课程名称") || it.contains("科目") || it.contains("考试名称")
        }.takeIf { it >= 0 }
            ?: header.indexOfFirst { it.contains("课程") && !it.contains("编号") }
        val timeCol = header.indexOfFirst {
            it.contains("时间") || it.contains("日期")
        }
        val roomCol = header.indexOfFirst {
            it.contains("考场") || it.contains("地点") || it.contains("教室")
        }

        val out = mutableListOf<Exam>()
        for (raw in rows.drop(1)) {
            val cells = raw.map { it.trim() }
            if (cells.all { it.isBlank() }) continue

            val timeCell = cells.getOrNull(timeCol)?.takeIf { it.isNotBlank() }
                ?: cells.firstOrNull { parseDate(it) != null }
                ?: cells.firstOrNull { TIME_RANGE.containsMatchIn(it) }
            val date = parseDate(timeCell.orEmpty())
                ?: parseDate(cells.joinToString(" "))
                ?: continue

            val course = cells.getOrNull(courseCol)?.takeIf { it.isNotBlank() }
                ?: cells.firstOrNull { it.length > 1 && parseDate(it) == null && !looksLikeRoom(it) }
                ?: "考试"

            val room = cells.getOrNull(roomCol)?.takeIf { it.isNotBlank() }
                ?: cells.firstOrNull { looksLikeRoom(it) }
                ?: ""

            out += Exam(
                course = course,
                date = date,
                timeRange = timeRangeOf(timeCell.orEmpty()),
                room = room,
            )
        }
        return out
    }

    fun parseDate(text: String): LocalDate? {
        if (text.isBlank()) return null
        YEAR_MONTH_DAY.find(text)?.let { m ->
            return runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()
        }
        MONTH_DAY.find(text.replace(YEAR_MONTH_DAY, ""))?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return null
            val day = m.groupValues[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            val today = LocalDate.now()
            // 没写年份按当前学年理解：已经过去半年以上的，算到下一年
            var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
            if (date.isBefore(today.minusDays(180))) {
                date = runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() ?: date
            }
            return date
        }
        return null
    }

    private fun timeRangeOf(text: String): String {
        TIME_RANGE.find(text)?.let { m ->
            fun two(v: String) = v.padStart(2, '0')
            return two(m.groupValues[1]) + ":" + m.groupValues[2] +
                    "-" + two(m.groupValues[3]) + ":" + m.groupValues[4]
        }
        SINGLE_TIME.find(text)?.let { m ->
            val h = m.groupValues[1].padStart(2, '0')
            return "$h:${m.groupValues[2]}"
        }
        return ""
    }

    private fun looksLikeRoom(s: String): Boolean {
        if (s.contains("教师") || s.contains("老师") || s.contains("场次")) return false
        return s.contains("楼") || s.contains("室") || s.contains("馆") ||
                s.contains("场") || s.contains("教")
    }
}
