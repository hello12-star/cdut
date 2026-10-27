package com.yh.schedule.core

import com.yh.schedule.CdutApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 强智（jsxsd）教务适配器 —— 真实模式骨架。
 *
 * 成理教务：jw.cdut.edu.cn（强智科技"本研一体化"平台）。
 * 注意：路径按**经典强智 jsxsd 形态**编写；成理 2021 年新版的具体路径
 * 可能不同（如 /kbcx/xskbcx_cxXsgrkbList.html 风格）。所有路径集中在
 * [QzPaths]，联调时只改这一处 —— 这正是 campus 适配层的价值。
 */
class QzClient(private val app: CdutApp) {

    object QzPaths {
        const val JW_BASE = "https://jw.cdut.edu.cn"
        /** 经典强智登录（待联调确认；成理登录页另有 /sso/login.jsp 入口） */
        const val LOGIN_POST = "$JW_BASE/jsxsd/xk/LoginToXk"
        /** 个人课表（HTML 表格 #table1） */
        const val TIMETABLE = "$JW_BASE/jsxsd/xskbcx/CourseTableAction_personalDkbx.do"
        /** 成绩列表（HTML 表格） */
        const val GRADE = "$JW_BASE/jsxsd/kscj/cjcx_list"
    }

    private val cookieJar = object : okhttp3.CookieJar {
        private val store = linkedMapOf<String, MutableList<okhttp3.Cookie>>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            store[url.host] = (store[url.host] ?: mutableListOf()).apply { addAll(cookies) }
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
            store[url.host] ?: emptyList()

        fun hasSession(): Boolean = store.any { (_, list) ->
            list.any { it.name.equals("JSESSIONID", ignoreCase = true) }
        }
    }

    private val client = app.http.newBuilder().cookieJar(cookieJar).build()

    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    /** 强智经典登录：POST userAccount/userPassword/checked */
    suspend fun login(account: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder()
                    .add("userAccount", account)
                    .add("userPassword", password)
                    .add("checked", "0")
                    .build()
                val req = Request.Builder().url(QzPaths.LOGIN_POST).header("User-Agent", UA)
                    .post(form).build()
                client.newCall(req).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    if (html.contains("密码错误") || html.contains("用户名或密码不正确"))
                        error("教务账号或密码错误")
                    if (!cookieJar.hasSession()) error("未取得教务会话（登录路径待校准）")
                }
            }
        }

    /** 个人课表：需要学期代码 xnxqdm（形如 2026-2027-1），联调时从学期下拉接口获取 */
    suspend fun timetable(xnxqdm: String): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder().add("xnxqdm", xnxqdm).build()
                val req = Request.Builder().url(QzPaths.TIMETABLE).header("User-Agent", UA)
                    .post(form).build()
                client.newCall(req).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    parseTimetable(html)
                }
            }
        }

    suspend fun grades(): Result<List<GradeItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(QzPaths.GRADE).header("User-Agent", UA).build()
                client.newCall(req).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    parseGrades(html)
                }
            }
        }

    // ---------- HTML 解析（强智通用形态，字段名以联调为准） ----------

    /** 解析强智个人课表表格：行=节次，列=星期；单元格含 课程名/周次/教室/教师 */
    fun parseTimetable(html: String): List<Course> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("#table1") ?: return emptyList()
        val rows = table.select("tr").drop(1) // 跳过表头
        val out = mutableListOf<Course>()
        rows.forEachIndexed { idx, tr ->
            val cells = tr.select("td")
            cells.forEachIndexed { colIdx, td ->
                val text = td.text().trim()
                if (text.isNotEmpty() && text.length > 3) {
                    parseCell(text, colIdx + 1, idx + 1)?.let { out.add(it) }
                }
            }
        }
        return out
    }

    /**
     * 强智单元格常见形态：
     * "数字逻辑 第1-16周(1,2节) 翠七教304 张三"
     * 或分行。此处做宽容解析，未识别的字段置默认值。
     */
    private fun parseCell(text: String, dayCol: Int, sectionRow: Int): Course? {
        val lines = text.split(Regex("\\s{1,}")).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val name = lines.firstOrNull { !it.contains("周") } ?: return null
        val weekTxt = lines.firstOrNull { it.contains("周") } ?: ""
        val weekRegex = Regex("第(\\d+)[-~](\\d+)周")
        val wm = weekRegex.find(weekTxt)
        val startWeek = wm?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val endWeek = wm?.groupValues?.get(2)?.toIntOrNull() ?: 16
        val parity = when {
            weekTxt.contains("单周") -> 1
            weekTxt.contains("双周") -> 2
            else -> 0
        }
        val room = lines.firstOrNull { it.contains("教") || it.contains("楼") } ?: ""
        val teacher = lines.lastOrNull { it != name && it != room && !it.contains("周") } ?: ""
        // 节次：优先取单元格文本中的 (x,y节)，否则按行列推
        val secRegex = Regex("\\((\\d+),(\\d+)节\\)")
        val sm = secRegex.find(text)
        val startSection = sm?.groupValues?.get(1)?.toIntOrNull() ?: (sectionRow * 2 - 1)
        val count = sm?.groupValues?.get(2)?.toIntOrNull()?.let { it - startSection + 1 }
            ?: 2
        return Course(
            name = name, room = room, teacher = teacher,
            dayOfWeek = dayCol, startSection = startSection, sectionCount = count,
            startWeek = startWeek, endWeek = endWeek, weekParity = parity,
            startTime = defaultSectionStart(startSection),
            endTime = defaultSectionEnd(startSection + count - 1),
        )
    }

    fun parseGrades(html: String): List<GradeItem> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("table tr")
        val out = mutableListOf<GradeItem>()
        var term = "本学期"
        rows.forEach { tr ->
            val tds = tr.select("td").map { it.text().trim() }
            if (tds.size >= 5) {
                // 列序（强智常见）：序号/学期/课程/学分/成绩/绩点... 以联调为准
                val score = tds.getOrNull(4)?.toDoubleOrNull()
                val credit = tds.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                val courseName = tds.getOrNull(2) ?: return@forEach
                if (score != null) {
                    out += GradeItem(term, courseName, credit, score, gradePointOf(score))
                }
            }
        }
        return out
    }

    private fun gradePointOf(score: Double): Double? = when {
        score >= 90 -> 4.0
        score >= 80 -> 3.0
        score >= 70 -> 2.0
        score >= 60 -> 1.0
        else -> 0.0
    }

    // 作息时间统一由 QzHtmlParser 提供，避免这里留第二份容易走样的副本
    private fun defaultSectionStart(s: Int): String = QzHtmlParser.sectionTimeStart(s)

    private fun defaultSectionEnd(s: Int): String = QzHtmlParser.sectionTimeEnd(s)
}
