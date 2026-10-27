package com.yh.schedule.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 成都理工强智教务课表 HTML 解析器（基于实测页面结构，2026-10-18）。
 *
 * 结构（/jsxsd/xskb/xskb_list.do 与首页 mainV_index_loadkb.htmlx 同模板）：
 * ```
 * <table id="timetable">
 *   <tbody>
 *     <tr><td>第一二节</td><td>(周一)</td>...<td>(周日)</td></tr>
 *     <tr><td>第三四节</td>...</tr>
 *     <tr><td>中午</td>...</tr>
 *     ...
 *   </tbody>
 * </table>
 * ```
 * 单元格文本形态：
 * ```
 * 高等数学 B(1)
 * 1-5周(单)
 * 01-02节
 * (翠) B210
 * ```
 * 一个单元格可能含多门课（多个 div）。
 */
object QzHtmlParser {

    /** 行标签 → (起始节, 节数) */
    private val SECTION_MAP: List<Pair<String, Pair<Int, Int>>> = listOf(
        "第一二节" to (1 to 2),
        "第二一二节" to (1 to 2),
        "第三四节" to (3 to 2),
        "第五六节" to (5 to 2),
        "第七八节" to (7 to 2),
        "第九十节" to (9 to 2),
        "第十一节" to (11 to 1),
        // 「中午」是午休行，不排课。这里故意不映射，让 parseTimetable 里的
        // firstOrNull 匹配不到而跳过该行（映射成 5-6 节会凭空造出一节下午课）
    )

    fun parseTimetable(html: String): List<Course> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("timetable")
            ?: doc.selectFirst("table")
            ?: return emptyList()

        val out = mutableListOf<Course>()
        // 兼容两种结构：有/无 <tbody>；表头行（th 或含"星期"）会被下面的 td 数量过滤掉
        table.select("tr").forEach { tr ->
            val tds = tr.select("td")
            if (tds.size < 3) return@forEach
            val label = tds[0].text().trim()
            val sec = SECTION_MAP.firstOrNull { label.contains(it.first) }?.second ?: return@forEach

            for (i in 1 until tds.size) {
                val day = i
                if (day > 7) break
                val cell = tds[i]
                // 单元格内每个课程块：优先 div，其次整体
                val divs = cell.select("div")
                val blocks: List<Element> = if (divs.isNotEmpty()) divs else listOf(cell)
                blocks.forEach { b ->
                    val lines = b.html()
                        .replace("<br>", "\n").replace("<br/>", "\n")
                        .replace("<br />", "\n")
                        .let { Jsoup.parse(it).text() }
                        .split(Regex("\\s{1,}"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    parseCourse(lines, day, sec.first, sec.second)?.let { out.add(it) }
                }
            }
        }
        return out.distinctBy { "${it.name}|${it.dayOfWeek}|${it.startSection}|${it.startWeek}" }
    }

    private fun parseCourse(
        lines: List<String>,
        day: Int,
        defaultStart: Int,
        defaultCount: Int,
    ): Course? {
        if (lines.isEmpty()) return null

        val weekLine = lines.firstOrNull { it.contains("周") && it.any { ch -> ch.isDigit() } }
        val secLine = lines.firstOrNull { Regex("\\d+\\s*-\\s*\\d+\\s*节").containsMatchIn(it) }
        val roomLine = lines.lastOrNull {
            it != weekLine && it != secLine &&
                    (it.contains("(") || it.contains("（") || it.contains("楼") ||
                            it.contains("教") || it.contains("操场") || it.contains("馆"))
        }
        val name = lines.firstOrNull { it != weekLine && it != secLine && it != roomLine }
            ?: return null
        if (name.length < 2) return null

        var startWeek = 1
        var endWeek = 16
        var parity = 0
        weekLine?.let { wl ->
            val range = Regex("(\\d+)\\s*-\\s*(\\d+)\\s*周").find(wl)
            val single = Regex("(\\d+)\\s*周").find(wl)
            when {
                range != null -> {
                    startWeek = range.groupValues[1].toIntOrNull() ?: 1
                    endWeek = range.groupValues[2].toIntOrNull() ?: 16
                }
                single != null -> {
                    startWeek = single.groupValues[1].toIntOrNull() ?: 1
                    endWeek = startWeek
                }
            }
            if (wl.contains("单")) parity = 1
            if (wl.contains("双")) parity = 2
        }

        var startSec = defaultStart
        var count = defaultCount
        secLine?.let { sl ->
            val m = Regex("(\\d+)\\s*-\\s*(\\d+)\\s*节").find(sl)
            if (m != null) {
                val a = m.groupValues[1].toIntOrNull()
                val b = m.groupValues[2].toIntOrNull()
                if (a != null && b != null && b >= a) {
                    startSec = a
                    count = b - a + 1
                }
            }
        }

        val room = roomLine ?: ""

        return Course(
            name = name,
            room = room,
            teacher = "",
            dayOfWeek = day,
            startSection = startSec,
            sectionCount = count,
            startWeek = startWeek,
            endWeek = endWeek,
            weekParity = parity,
            startTime = sectionTimeStart(startSec),
            endTime = sectionTimeEnd(startSec + count - 1),
        )
    }

    /**
     * 小节起止时间（成都理工大学作息，2026-10-24 用户确认）。
     * 每小节 45 分钟，同一大节内小节之间休息 5 分钟：
     * 1-2 节 08:10–09:45、3-4 节 10:15–11:50、5-6 节 14:30–16:05、
     * 7-8 节 16:25–18:00。
     *
     * 晚课（9 节起）**统一按 19:10–21:35（3 节）**：
     * 教务对所有晚课一律标「09-10节」，从 DOM 里分辨不出实际是 2 节还是 3 节。
     * 实测绝大多数晚课都是 3 节（21:35 下课），只有个别课（如军事理论）是 2 节 20:45，
     * 那种例外在 [com.yh.schedule.data.Repository.withSectionTime] 里按课程名特判。
     */
    private val SECTION_START = listOf(
        "08:10", "09:00",   // 1-2
        "10:15", "11:05",   // 3-4
        "14:30", "15:20",   // 5-6
        "16:25", "17:15",   // 7-8
    )

    private val SECTION_END = listOf(
        "08:55", "09:45",
        "11:00", "11:50",
        "15:15", "16:05",
        "17:10", "18:00",
    )

    /** 晚课时段（9-11 节），按 3 节算 */
    const val EVENING_START = "19:10"
    const val EVENING_END = "21:35"

    fun sectionTimeStart(s: Int): String = when {
        s <= SECTION_START.size -> SECTION_START[(s - 1).coerceIn(0, SECTION_START.lastIndex)]
        else -> EVENING_START
    }

    fun sectionTimeEnd(s: Int): String = when {
        s <= SECTION_END.size -> SECTION_END[(s - 1).coerceIn(0, SECTION_END.lastIndex)]
        else -> EVENING_END
    }
}
