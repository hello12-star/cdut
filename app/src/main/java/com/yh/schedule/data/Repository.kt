package com.yh.schedule.data

import android.content.Context
import com.yh.schedule.CdutApp
import com.yh.schedule.core.Course
import com.yh.schedule.core.Exam
import com.yh.schedule.core.GradeItem
import com.yh.schedule.core.QzClient
import com.yh.schedule.core.QzHtmlParser
import com.yh.schedule.core.QzTermParser
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.SessionStore
import com.yh.schedule.core.TimeTableItem
import com.yh.schedule.core.TimeTableType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 统一仓库：按 SessionStore 的 demo 开关路由 Mock / 真实数据源。
 * 真实数据源当前为骨架（成理接口路径待联调校准），失败时回落 Mock 并置标记。
 */
class Repository(
    context: Context,
    private val app: CdutApp,
    private val store: SessionStore,
) {
    private val db = AppDb.get(context)
    private val qz = QzClient(app)
    private val eventDao = db.customEventDao()

    val demoMode: Flow<Boolean> = store.demoMode
    val loggedIn: Flow<Boolean> = store.loggedIn

    /** 登录：demo=true 直接进入演示模式；真实模式走 WebView 统一认证（见 CasLoginScreen） */
    suspend fun login(account: String, password: String, demo: Boolean): Result<Unit> {
        if (demo) {
            store.markLoggedIn(account.ifBlank { "演示用户" }, null, demo = true)
            return Result.success(Unit)
        }
        // 真实模式不在代码中处理密码（瑞数 WAF + RSA 加密 + 安全边界），
        // 统一改为 WebView 登录：CasLoginScreen 完成后调用 onWebLoginSuccess。
        return Result.failure(IllegalStateException("请使用统一认证 WebView 登录"))
    }

    /**
     * WebView 统一认证成功后：保存会话 Cookie 与账号密码，并置登录态。
     * account/password 为用户在原生输入框填写的值（用于下次自动填充，对齐原版 saveInput）。
     */
    suspend fun onWebLoginSuccess(
        casCookie: String,
        account: String = "",
        password: String = "",
        remember: Boolean = true,
    ) {
        // 必须在写入新账号之前比较：写完之后读到的永远都是刚保存的新账号
        val prevAccount = store.account()
        val switched = prevAccount != null && account.isNotBlank() && prevAccount != account

        // 换账号：旧课表不得残留（否则会看到上一个人的课表），
        // 同时清掉抓取节流标记，让聚焦页尽快抓新账号的课表。
        // 同账号重登 / 「刷新登录状态」：一律保留上次抓到的课表 ——
        // 课表本身很稳定，重登成功但本次抓取失败时，清缓存只会让课表页平白变空。
        if (switched) {
            store.clearTimetable()
            resetScrapeGuard()
        }

        store.saveCasCookie(casCookie)
        if (account.isNotBlank()) {
            if (remember) store.saveCredentials(account, password)
            else store.clearCredentials()
        }
        store.markLoggedIn(
            account.ifBlank { "统一认证用户" },
            null,
            demo = false,
        )
        // 课表/成绩的真实数据均由 WebView 链路直接携带会话；
        // 此处保存的 Cookie 留作后续接入 OkHttp 数据源（如成绩查询）时复用。
    }

    /** 已保存的账号密码（用于登录页自动填充） */
    suspend fun savedCredentials(): Pair<String, String>? = store.credentials()

    suspend fun logout() = store.logout()

    /**
     * 课表：
     * - 演示模式 → Mock
     * - 真实模式 → 返回本地缓存（无缓存返回空列表，由 UI 触发 WebView 抓取）
     * 注意：真实数据由 TimetableScreen 内的隐藏 WebView 抓取后经 [cacheTimetable] 落盘，
     * 因为强智部署了瑞数防护，普通 HTTP 客户端直连会被 403。
     */
    suspend fun timetable(week: Int): List<Course> = withContext(Dispatchers.IO) {
        if (store.demoMode.first()) return@withContext MockData.timetable
        val cached = store.timetableJson()
        if (cached.isNullOrBlank()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<Course>>(cached) }
            .getOrDefault(emptyList())
            .map { it.withSectionTime() }
    }

    /**
     * 作息时间以「节次」为准。
     *
     * 缓存里连 startTime/endTime 一起存了，作息表一改，旧缓存的时间就会过期；
     * 这里按 [QzHtmlParser.sectionTimeStart] 统一重算，改作息不必清缓存或重新登录。
     */
    /**
     * 晚课里**实际只上 2 节**的课程（教务对所有晚课都标「09-10节」，分不出节数，只能按名字认）。
     * 名单外的晚课一律按 3 节（21:35 下课）。以后发现别的 2 节晚课，往这里加名字即可。
     */
    private val EVENING_TWO_SECTION_COURSES = listOf("军事理论")

    private fun Course.withSectionTime(): Course {
        val start = QzHtmlParser.sectionTimeStart(startSection)
        val defaultEnd = QzHtmlParser.sectionTimeEnd(startSection + sectionCount - 1)
        val isEvening = startSection >= 9
        val twoSection = isEvening && EVENING_TWO_SECTION_COURSES.any { name.contains(it) }
        return copy(
            startTime = start,
            endTime = if (twoSection) "20:45" else defaultEnd,
        )
    }

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 上次尝试抓课表的时间戳（ms），0 表示从未抓过 */
    suspend fun lastScrapeAt(): Long = store.lastScrapeAt()

    /**
     * 标记"刚尝试过抓取"。
     * 失败也算——避免用户每次切到聚焦页都重抓，把本来就慢的教务拖垮。
     */
    suspend fun markScrapeAttempted() = store.markScrapeAt(System.currentTimeMillis())

    /** 清除抓取标记，让下次进聚焦页重新自动抓（换账号后调用） */
    suspend fun resetScrapeGuard() = store.markScrapeAt(0L)

    /**
     * 保存 WebView 抓取并解析后的真实课表。
     *
     * @param weekOfTerm 教务页面探测到的「当前第几周」，0 表示没探测到。
     *   非 0 时用它反推学期第一周周一并持久化（见 [QzTermParser]），
     *   这样换学期不必再手改代码里的日期。
     */
    suspend fun cacheTimetable(courses: List<Course>, weekOfTerm: Int = 0) {
        store.saveTimetableJson(json.encodeToString(courses))

        val derived = QzTermParser.deriveTermStartMonday(weekOfTerm) ?: return
        val current = SchoolCalendar.termStartMonday
        // 当前**不自动覆盖**，只记录，等真机确认周次探测可靠后再启用。
        // 原因：页面上周次很容易探测错，而 termStartMonday 一旦被改坏，
        // 所有课都会被算到错误的周次上，课表直接变空 —— 影响面太大，不能赌。
        android.util.Log.i(
            "QzTerm",
            "探测到周次=$weekOfTerm → 推出学期起始日=$derived，当前使用=$current（未覆盖）",
        )
        if (QzTermParser.isFarApart(current, derived, maxDays = 21)) {
            android.util.Log.w("QzTerm", "两者相差超过 21 天，周次探测大概率有误")
        }
        // 确认可靠后把下面两行打开即可启用自动校准：
        // store.saveTermStartMonday(derived.toString())
        // SchoolCalendar.termStartMonday = derived
    }

    /** 读出已持久化的学期第一周周一（启动时调用，覆盖硬编码默认值） */
    suspend fun loadTermStartMonday(): java.time.LocalDate? {
        val saved = store.termStartMonday()
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: return null

        // 已存的值也要校验：上一版可能被探测错的周次写坏过。
        // 相差超过 45 天基本可以确定是坏值（同校同一学期内的校历调整不会这么大），
        // 直接回退到默认并纠正存储，否则课表会一直空着。
        val fallback = java.time.LocalDate.of(2026, 8, 31)
        if (QzTermParser.isFarApart(fallback, saved, maxDays = 45)) {
            android.util.Log.w("QzTerm", "已存的学期起始日 $saved 明显异常，回退到默认 $fallback")
            store.saveTermStartMonday(fallback.toString())
            SchoolCalendar.termStartMonday = fallback
            return fallback
        }
        SchoolCalendar.termStartMonday = saved
        return saved
    }

    /** 是否有真实课表缓存 */
    suspend fun hasCachedTimetable(): Boolean = !store.timetableJson().isNullOrBlank()

    /**
     * 某周课表项：课程 + 自定义日程，合并为一组 [TimeTableItem]
     * （对齐参考源码 allToTimeTableData() 的叠加顺序：课程 + FOCUS + EXAM）。
     *
     * 注意：必须在这里按周过滤，否则每周都会画出全部课程。
     */
    suspend fun timetableItems(week: Int): List<TimeTableItem> = withContext(Dispatchers.IO) {
        val courses = timetable(week)
            .filter { SchoolCalendar.courseInWeek(it, week) }
            .map { it.toTimeTableItem() }
        courses + eventsInWeek(week)
    }

    /** 本周的自定义日程 → 课表项（**跨天日程按天拆分**，同源码 focusToTimeTableData 逻辑） */
    private suspend fun eventsInWeek(week: Int): List<TimeTableItem> {
        val monday = SchoolCalendar.termStartMonday.plusWeeks((week - 1).toLong())
        val sunday = monday.plusDays(6)
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val rows = runCatching {
            eventDao.range(fmt.format(monday), fmt.format(sunday)).first()
        }.getOrDefault(emptyList())

        val out = mutableListOf<TimeTableItem>()
        for (e in rows) {
            val start = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: continue
            val end = runCatching { LocalDate.parse(e.endDate) }.getOrNull() ?: start

            var d = if (start.isBefore(monday)) monday else start
            val last = if (end.isAfter(sunday)) sunday else end
            if (last.isBefore(monday) || d.isAfter(sunday)) continue

            while (!d.isAfter(last)) {
                val isFirst = d == start
                val isLast = d == end
                out.add(
                    TimeTableItem(
                        type = TimeTableType.FOCUS,
                        name = e.title,
                        dayOfWeek = d.dayOfWeek.value,
                        // 跨天：非首日从 08:00 起，非末日到 22:00 止
                        startTime = if (isFirst) e.startTime else "08:00",
                        endTime = if (isLast) e.endTime else "22:00",
                        place = e.note.ifBlank { null },
                        teacher = null,
                        eventId = e.id,
                    )
                )
                d = d.plusDays(1)
            }
        }
        return out
    }

    private fun Course.toTimeTableItem() = TimeTableItem(
        type = TimeTableType.COURSE,
        name = name,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        place = room.ifBlank { null },
        teacher = teacher.ifBlank { null },
    )

    suspend fun exams(): List<Exam> = withContext(Dispatchers.IO) {
        if (store.demoMode.first()) return@withContext MockData.exams
        // 真实模式暂无考试数据源：返回空而不是演示数据（避免假数据冒充真实）
        emptyList()
    }

    suspend fun grades(): List<GradeItem> = withContext(Dispatchers.IO) {
        if (store.demoMode.first()) return@withContext MockData.grades
        // 真实模式：会话只存在于 WebView，OkHttp 直连教务必然拿不到成绩；
        // 失败时返回空列表，绝不回退到演示成绩冒充真实数据（成绩页会显示空态提示）。
        qz.grades().getOrDefault(emptyList())
    }

    // ---------- 自定义日程（本地 Room，两种模式一致） ----------

    fun customEvents(): Flow<List<com.yh.schedule.core.CustomEvent>> =
        eventDao.all().map { list -> list.map { it.toModel() } }

    suspend fun eventsOfDate(date: LocalDate): Flow<List<com.yh.schedule.core.CustomEvent>> {
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return eventDao.range(key, key).map { list -> list.map { it.toModel() } }
    }

    /** 添加/更新日程（字段对齐原版 AddEventUI）；返回新行 id */
    suspend fun addEvent(e: com.yh.schedule.core.CustomEvent): Long =
        eventDao.insert(
            CustomEventEntity(
                title = e.title,
                date = e.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = e.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                startTime = e.startTime,
                endTime = e.endTime,
                note = e.note,
                type = e.type.name,
                remark = e.remark,
            )
        )

    /** 更新日程（编辑，对齐原版 AddEventDestination 的编辑模式） */
    suspend fun updateEvent(e: com.yh.schedule.core.CustomEvent) {
        if (e.id <= 0) return
        eventDao.update(
            CustomEventEntity(
                id = e.id,
                title = e.title,
                date = e.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = e.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                startTime = e.startTime,
                endTime = e.endTime,
                note = e.note,
                type = e.type.name,
                remark = e.remark,
            )
        )
    }

    suspend fun removeEvent(id: Long) = eventDao.deleteById(id)

    private fun CustomEventEntity.toModel() = com.yh.schedule.core.CustomEvent(
        id = id,
        title = title,
        date = LocalDate.parse(date),
        endDate = runCatching { LocalDate.parse(endDate) }
            .getOrDefault(LocalDate.parse(date)),
        startTime = startTime,
        endTime = endTime,
        note = note,
        type = runCatching { com.yh.schedule.core.EventType.valueOf(type) }
            .getOrDefault(com.yh.schedule.core.EventType.SCHEDULE),
        remark = remark,
    )
}
