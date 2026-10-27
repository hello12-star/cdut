package com.yh.schedule.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 强智课表「页面内 JS 结构化」结果解析器。
 *
 * 实测 DOM 要点：
 * 1. 一个 td 内所有 `<font>` 才是完整信息，教师/教学楼等在 `div.kbcontent1` 之外；
 * 2. `<font title="周次(节次)">2-5,9-19(周)</font>`、`title="教室"`、`title="教师"`；
 * 3. 同一门课会有两组 font（简略组 + 详情组），一组 td 也可能含多门课；
 * 4. 周次格式多样：`2-5,9-19(周)`、`8-(16)周`、`2-5,9,14-15周`、`2-4,6-13周`。
 *
 * 解析策略：按「每遇到周次字段即完成一门课」分组；同门课的多段周次在此合并。
 */
object QzJsTimetableParser {

    private val SECTION_MAP: List<Pair<String, Int>> = listOf(
        "第一二节" to 1,
        "第三四节" to 3,
        "中午" to 5,
        "第五六节" to 5,
        "第七八节" to 7,
        "第九十十一节" to 9,
        "第九十节" to 9,
        "第十一节" to 11,
    )

    private val SEC_REGEX = Regex("\\[?\\d{1,2}\\s*-\\s*\\d{1,2}\\s*节\\]?")

    fun parse(json: String): List<Course> = parseWithWeek(json).first

    /**
     * 同时返回「当前第几周」（0 表示页面没给出），供 [QzTermParser] 反推学期第一周周一。
     *
     * 兼容两种回传格式：
     * - 新：`{"w":8,"t":[...]}`（w = 周次，t = 课程行）
     * - 旧：`[...]`（纯数组，无周次）
     */
    fun parseWithWeek(json: String): Pair<List<Course>, Int> {
        if (json.isBlank()) return emptyList<Course>() to 0
        val out = mutableListOf<Course>()
        val trimmed = json.trimStart()
        var week = 0
        val arr: JSONArray = if (trimmed.startsWith("{")) {
            val o = runCatching { JSONObject(trimmed) }.getOrNull()
                ?: return emptyList<Course>() to 0
            week = o.optInt("w", 0)
            o.optJSONArray("t") ?: return emptyList<Course>() to 0
        } else {
            runCatching { JSONArray(trimmed) }.getOrNull()
                ?: return emptyList<Course>() to 0
        }

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val row = o.optString("row")
            val day = o.optInt("day")
            if (day !in 1..7) continue

            val fields = mutableListOf<Pair<String, String>>()
            val fa = o.optJSONArray("fonts")
            if (fa != null) {
                for (k in 0 until fa.length()) {
                    val fo = fa.optJSONObject(k)
                    if (fo != null) {
                        fields.add(fo.optString("t") to fo.optString("v").trim())
                    } else {
                        fields.add("" to fa.optString(k).trim())
                    }
                }
            } else {
                o.optString("text").replace(Regex("\\s+"), " ").trim()
                    .split(" ").filter { it.isNotBlank() }
                    .forEach { fields.add("" to it) }
            }
            val valid = fields.filter { it.second.isNotBlank() }
            if (valid.isEmpty()) continue

            val startSec = SECTION_MAP.firstOrNull {
                row.replace(" ", "").contains(it.first)
            }?.second ?: 1

            out.addAll(splitCourses(valid, day, startSec))
        }

        // 同门课可能被拆成多段周次 / 简略与详情两套信息 → 按 名称+星期+起始节 合并
        val merged = out.groupBy { "${it.name}|${it.dayOfWeek}|${it.startSection}" }
            .map { (_, list) ->
                val base = list.first()
                val weeks = list.flatMap { it.weeks }.distinct().sorted()
                base.copy(
                    teacher = list.firstNotNullOfOrNull { c ->
                        c.teacher.takeIf { t -> t.isNotBlank() }
                    }.orEmpty(),
                    room = list.firstNotNullOfOrNull { c ->
                        c.room.takeIf { r -> r.isNotBlank() }
                    }.orEmpty(),
                    weeks = weeks,
                    startWeek = weeks.minOrNull() ?: base.startWeek,
                    endWeek = weeks.maxOrNull() ?: base.endWeek,
                )
            }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
        return merged to week
    }

    /** 按「遇到周次即完成一门」切分格子内的多门课 */
    private fun splitCourses(
        fields: List<Pair<String, String>>,
        day: Int,
        defaultStart: Int,
    ): List<Course> {
        val result = mutableListOf<Course>()
        var name = ""
        var week = ""
        var room = ""
        var teacher = ""
        var sec = ""

        fun flush() {
            if (name.isNotBlank() && week.isNotBlank()) {
                buildCourse(name, week, room, teacher, sec, day, defaultStart)
                    ?.let { result.add(it) }
            }
            name = ""; week = ""; room = ""; teacher = ""; sec = ""
        }

        for ((title, value) in fields) {
            when {
                title.contains("周") -> week = value
                title.contains("教室") -> room = value
                title.contains("教师") -> teacher = value
                title.contains("节") -> sec = value
                SEC_REGEX.containsMatchIn(value) && value.length < 16 -> sec = value
                // 兜底：必须是「数字开头」的周次（避免「大学英语I(周一34-02)」误判）
                Regex("^[0-9]+(?:-[0-9]+)?(?:,[0-9]+(?:-[0-9]+)?)*").containsMatchIn(value) &&
                        value.contains("周") -> week = value
                else -> {
                    val clean = stripSuffix(value)
                    if (isValidName(clean)) {
                        if (name.isNotBlank() && week.isNotBlank()) flush()
                        if (name.isBlank()) name = clean
                        else if (week.isNotBlank()) { flush(); name = clean }
                    }
                }
            }
        }
        flush()
        return result
    }

    /** 去掉「(周一34-02)」这类附加后缀 */
    private fun stripSuffix(raw: String): String =
        raw.replace(Regex("[（(]\\s*周[一二三四五六日天].*?[）)]"), "")
            .replace(Regex("[（(]\\s*\\d{1,2}\\s*-\\s*\\d{1,2}\\s*[）)]$"), "")
            .trim()

    /** 是否为合法课程名（排除通知单/班级/备注等） */
    private fun isValidName(s: String): Boolean =
        s.length in 2..40 &&
                !s.contains("：") && !s.contains(":") &&
                !s.startsWith("备注") && !s.startsWith("班级") &&
                !s.startsWith("通知单") && !s.startsWith("【") &&
                !s.startsWith("(") && !s.contains("理论:")

    private fun buildCourse(
        rawName: String,
        weekToken: String,
        room: String,
        teacher: String,
        secToken: String,
        day: Int,
        defaultStart: Int,
    ): Course? {
        val name = stripSuffix(rawName)
        if (!isValidName(name)) return null

        val weeks = weeksOf(weekToken)
        if (weeks.isEmpty()) return null

        // 节次
        var startSec = defaultStart
        var count = 2
        Regex("(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*节").find(secToken)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && b >= a) {
                startSec = a
                count = b - a + 1
            }
        }

        // 单双周（若文本里带「单/双」）
        val clean = weekToken.replace(Regex("[（(].*?[）)]"), "")
        val parity = when {
            clean.contains("单") -> 1
            clean.contains("双") -> 2
            else -> 0
        }
        val filtered = if (parity == 0) weeks else weeks.filter {
            if (parity == 1) it % 2 == 1 else it % 2 == 0
        }

        return Course(
            name = name,
            room = room,
            teacher = teacher,
            dayOfWeek = day,
            startSection = startSec,
            sectionCount = count,
            startWeek = filtered.minOrNull() ?: 1,
            endWeek = filtered.maxOrNull() ?: 16,
            weekParity = parity,
            startTime = QzHtmlParser.sectionTimeStart(startSec),
            endTime = QzHtmlParser.sectionTimeEnd(startSec + count - 1),
            weeks = filtered,
        )
    }

    /** "2-5,9-16(周)[03-04节]" → [2,3,4,5,9,...,16] */
    private fun weeksOf(token: String): List<Int> {
        val clean = token
            .replace("（", "").replace("）", "")
            .replace("(", "").replace(")", "")
        val spec = Regex("[0-9]+(?:-[0-9]+)?(?:,[0-9]+(?:-[0-9]+)?)*")
            .find(clean)?.value.orEmpty()
        return expandWeeks(spec)
    }

    private fun expandWeeks(spec: String): List<Int> {
        if (spec.isBlank()) return emptyList()
        val out = mutableSetOf<Int>()
        spec.split(",").forEach { seg ->
            val s = seg.trim()
            val range = Regex("^(\\d+)-(\\d+)$").find(s)
            if (range != null) {
                val a = range.groupValues[1].toIntOrNull() ?: return@forEach
                val b = range.groupValues[2].toIntOrNull() ?: return@forEach
                if (a <= b && b <= 30) (a..b).forEach { out.add(it) }
            } else {
                s.toIntOrNull()?.let { if (it in 1..30) out.add(it) }
            }
        }
        return out.sorted()
    }
}
