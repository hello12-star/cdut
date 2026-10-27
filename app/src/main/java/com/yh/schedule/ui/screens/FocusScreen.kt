package com.yh.schedule.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yh.schedule.core.CustomEvent
import com.yh.schedule.core.QzJsTimetableParser
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.TimeTableItem
import com.yh.schedule.core.TimeTableType
import com.yh.schedule.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class CourseState { NOT_STARTED, ONGOING, ENDED }

private fun stateOf(item: TimeTableItem, now: Double): CourseState {
    val s = AxisTime.toFloat(item.startTime)
    val e = AxisTime.toFloat(item.endTime)
    return when {
        now < s -> CourseState.NOT_STARTED
        now <= e -> CourseState.ONGOING
        else -> CourseState.ENDED
    }
}

private fun nowHourFloat(): Double = LocalTime.now().let { it.hour + it.minute / 60.0 }

private fun nowHhMm(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun weekOf(date: LocalDate): Int =
    (ChronoUnit.WEEKS.between(SchoolCalendar.termStartMonday, date).toInt() + 1).coerceIn(1, 25)

/** 日程是否已过期（按 endDate + endTime 判定） */
private fun isOverdue(e: CustomEvent, now: LocalDateTime): Boolean {
    val end = runCatching {
        LocalDateTime.of(e.endDate, LocalTime.parse(e.endTime))
    }.getOrNull() ?: return e.date.isBefore(now.toLocalDate())
    return now.isAfter(end)
}

/**
 * 聚焦页（严格对齐原版 `FocusUI.TodayScreen` + `FocusItems`）：
 *
 * 两个页签：**重要安排 / 其他事项**（原版 TAB_LEFT / TAB_RIGHT），左右滑动亦可切换。
 *
 * 重要安排（左页）：
 * - 今日课程：只显示**未上完**的（已下课直接消失，对齐 `if(state != ENDED) itemUI()`）
 * - 今天所有课都上完后，改为显示**明天的课**（对齐 `compareTime(lastTime) != NOT_STARTED`）
 * - 明日课程：左侧「+1」徽标 + 右侧「明日」（对齐 `JxglstuTomorrowCourseItem`）
 * - 日程：今天的（未过期）+ 今天课上完时追加**明天的日程**（对齐 `isTomorrow && showTomorrow`）
 *
 * 其他事项（右页）：
 * - 今天还没上完时，明天课程放这里（对齐源码 right 页的"第二天课表"）
 * - 其余日程（未来 / 已过期，过期默认折叠）
 */
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
@Composable
fun FocusScreen(
    repo: Repository,
    openWeb: (String, String) -> Unit = { _, _ -> },
    /** 点右下角 +：由 MainScaffold 打开全屏添加页 */
    onAddEvent: () -> Unit = {},
    /** 点日程条目：由 MainScaffold 打开全屏编辑页 */
    onEditEvent: (CustomEvent) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })

    var todayCourses by remember { mutableStateOf<List<TimeTableItem>>(emptyList()) }
    var tomorrowCourses by remember { mutableStateOf<List<TimeTableItem>>(emptyList()) }
    var events by remember { mutableStateOf<List<CustomEvent>>(emptyList()) }
    var timeNow by remember { mutableStateOf(nowHourFloat()) }
    var clock by remember { mutableStateOf(nowHhMm()) }
    var detailItem by remember { mutableStateOf<TimeTableItem?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomEvent?>(null) }
    var showOverdue by remember { mutableStateOf(false) }
    // 教务系统检查（抓取课表）状态：只在聚焦页展示，课表页不参与
    var checking by remember { mutableStateOf(false) }
    // 存具体错误文案而不是 boolean：抓不到时要能看出是"登录过期"还是"读不到课表"
    var authError by remember { mutableStateOf<String?>(null) }
    var eduTick by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var lastRefresh by remember { mutableStateOf(nowHhMm()) }

    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val nowDateTime = LocalDateTime.now()

    // today 参与 key：App 挂夜后跨天，这里会重新加载"今天/明天"的课
    LaunchedEffect(refreshTick, today) {
        val tw = weekOf(today)
        val mw = weekOf(tomorrow)
        todayCourses = repo.timetableItems(tw)
            .filter { it.dayOfWeek == today.dayOfWeek.value && it.type == TimeTableType.COURSE }
            .sortedBy { it.startTime }
        tomorrowCourses = repo.timetableItems(mw)
            .filter { it.dayOfWeek == tomorrow.dayOfWeek.value && it.type == TimeTableType.COURSE }
            .sortedBy { it.startTime }
        lastRefresh = nowHhMm()
    }

    LaunchedEffect(Unit) {
        repo.customEvents().collect { events = it }
    }

    // 进 App 后检查一次教务系统。
    // 用「上次尝试时间」判据（持久化），而不是进程级 object：
    // 后者一旦某条重置路径漏掉，就会永远不自动抓取，而且现象是静默无动作、极难排查。
    // 6 小时内不重复自动抓；换账号时会由 AppNav 清掉这个时间戳。
    LaunchedEffect(Unit) {
        val last = repo.lastScrapeAt()
        val stale = last == 0L || System.currentTimeMillis() - last > 6 * 60 * 60 * 1000L
        if (stale) {
            repo.markScrapeAttempted()
            checking = true
            eduTick++
        }
    }

    // 失败提示不自动消失：抓不到课表时用户需要看清原因（点卡片可重试，成功后自然消失）
    if (checking) {
        TimetableScraper(
            tick = eduTick,
            onJson = { json ->
                val (parsed, weekOfTerm) = QzJsTimetableParser.parseWithWeek(json)
                checking = false
                if (parsed.isEmpty()) {
                    authError = "抓到页面但解析出 0 门课（可能登录已过期或本学期无课）"
                } else {
                    authError = null
                    scope.launch {
                        repo.cacheTimetable(parsed, weekOfTerm)
                        refreshTick++
                    }
                }
            },
            onFail = { msg ->
                checking = false
                authError = msg
            },
        )
    }

    // 每 30s 推进时间：已下课的课会自动消失（对齐原版每分钟更新时间）
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            timeNow = nowHourFloat()
            clock = nowHhMm()
        }
    }

    // 今天课是否全部上完（严格对齐原版 compareTime(lastTime) != NOT_STARTED）
    // lastTime = 今天最后一节课的结束时间；今天没课时为 "00:00"，
    // 此时 now > 00:00 恒成立 → showTomorrow = true，明天的日程/课程会进入「重要安排」
    val lastEndTime = todayCourses.lastOrNull()?.endTime ?: "00:00"
    val showTomorrow = AxisTime.toFloat(nowHhMm()) >= AxisTime.toFloat(lastEndTime)

    // ---- 重要安排 ----
    val importantCourses = if (showTomorrow) tomorrowCourses
    else todayCourses.filter { stateOf(it, timeNow) != CourseState.ENDED }

    val todayEvents = events.filter { it.date == today && !isOverdue(it, nowDateTime) }
    val tomorrowEvents = events.filter { it.date == tomorrow }
    val importantEvents = if (showTomorrow) todayEvents + tomorrowEvents else todayEvents

    // ---- 其他事项 ----
    val otherCourses = if (showTomorrow) emptyList() else tomorrowCourses
    val importantIds = importantEvents.map { it.id }.toSet()
    val otherEvents = events
        .filter { it.id !in importantIds }
        .sortedBy { it.date.toString() + it.startTime }
    val overdueEvents = otherEvents.filter { isOverdue(it, nowDateTime) }
    val futureEvents = otherEvents.filterNot { isOverdue(it, nowDateTime) }

    // 全屏页由 MainScaffold 统一承载（盖在 Scaffold 之上），这里只负责发起
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- 顶部日期（对齐截图：10-22 第7周 周四，蓝色大字）----
            Text(
                today.format(DateTimeFormatter.ofPattern("MM-dd")) +
                        " 第${weekOf(today)}周 " +
                        SchoolCalendar.chineseDay(today.dayOfWeek),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )

            // ---- 两个页签：重要安排 / 其他事项 ----
            TabRow(
                selectedTabIndex = pager.currentPage,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {},
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Tab(
                    selected = pager.currentPage == 0,
                    onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .background(
                            color = if (pager.currentPage == 0)
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    text = {
                        Text(
                            "重要安排",
                            fontSize = 14.sp,
                            fontWeight = if (pager.currentPage == 0) FontWeight.Bold
                            else FontWeight.Normal,
                            color = if (pager.currentPage == 0)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    },
                )
                Tab(
                    selected = pager.currentPage == 1,
                    onClick = { scope.launch { pager.animateScrollToPage(1) } },
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .background(
                            color = if (pager.currentPage == 1)
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    text = {
                        Text(
                            "其他事项",
                            fontSize = 14.sp,
                            fontWeight = if (pager.currentPage == 1) FontWeight.Bold
                            else FontWeight.Normal,
                            color = if (pager.currentPage == 1)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    },
                )
            }

            // 下拉刷新：重新抓一次教务系统（与顶部检查卡共用同一套状态）
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = checking,
                onRefresh = {
                    authError = null
                    checking = true
                    eduTick++
                },
                // 不再画默认的圆形指示器：顶部「正在检查教务系统登陆状态」卡片就是进度反馈，
                // 两个转圈叠在一起会压住文字
                indicator = {},
                // 统一底色：下拉刷新时 Compose 会在内容边缘叠加一层下拉效果，
                // 若这里透明，就会透出下层不同的底色，看起来像"闪一下变了颜色"。
                // 铺上和页面一致的 background，任何过渡都落在同一底色上。
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (page == 0) {
                        // 检查卡：用高度动画展开/收起，避免瞬间插入把下面内容顶得跳一下
                        item {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = checking || authError != null,
                                enter = androidx.compose.animation.expandVertically(
                                    androidx.compose.animation.core.tween(180)
                                ) + androidx.compose.animation.fadeIn(
                                    androidx.compose.animation.core.tween(150)
                                ),
                                exit = androidx.compose.animation.shrinkVertically(
                                    androidx.compose.animation.core.tween(200)
                                ) + androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(120)
                                ),
                            ) {
                                EduCheckCard(
                                    checking = checking,
                                    error = authError,
                                    onRetry = {
                                        authError = null
                                        checking = true
                                        eduTick++
                                    },
                                )
                            }
                        }

                        // 明日概况（FocusCard 中 TodayUI 的等价物）
                        item { TomorrowSummaryCard(tomorrowCourses) }

                        // 课程：未上的今日课，或（今日课后）明日课
                        if (importantCourses.isNotEmpty()) {
                            items(importantCourses, key = { "${it.name}-${it.startTime}-${showTomorrow}" }) { c ->
                                CourseCard(
                                    item = c,
                                    state = if (showTomorrow) null else stateOf(c, timeNow),
                                    nowHour = timeNow,
                                    isTomorrow = showTomorrow,
                                    onClick = { detailItem = c },
                                )
                            }
                        } else if (!showTomorrow) {
                            item { EmptyCard("今天没有课") }
                        }

                        // 日程：今天的 + （课上完后）明天的
                        items(importantEvents, key = { it.id }) { e ->
                            EventCard(
                                event = e,
                                overdue = false,
                                isTomorrow = e.date == tomorrow,
                                onClick = { onEditEvent(e) },
                                onLongPress = { pendingDelete = e },
                                onDelete = { pendingDelete = e },
                            )
                        }
                        if (importantCourses.isEmpty() && importantEvents.isEmpty()) {
                            item { EmptyCard("今天没有待办安排，点右下角 + 添加") }
                        }
                    } else {
                        // 明日课程（今天还没上完时才放这里）
                        if (otherCourses.isNotEmpty()) {
                            items(otherCourses, key = { "tm-${it.name}-${it.startTime}" }) { c ->
                                CourseCard(
                                    item = c,
                                    state = null,
                                    nowHour = timeNow,
                                    isTomorrow = true,
                                    onClick = { detailItem = c },
                                )
                            }
                        }

                        if (overdueEvents.isNotEmpty()) {
                            item {
                                Card(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { showOverdue = !showOverdue },
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            (if (showOverdue) "收起" else "展开") +
                                                    "已过期日程（${overdueEvents.size}）",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            if (showOverdue) {
                                items(overdueEvents, key = { "od-${it.id}" }) { e ->
                                    EventCard(
                                        event = e,
                                        overdue = true,
                                        isTomorrow = false,
                                        onClick = { onEditEvent(e) },
                                        onLongPress = { pendingDelete = e },
                                        onDelete = { pendingDelete = e },
                                    )
                                }
                            }
                        }

                        items(futureEvents, key = { "ft-${it.id}" }) { e ->
                            EventCard(
                                event = e,
                                overdue = false,
                                isTomorrow = e.date == tomorrow,
                                onClick = { onEditEvent(e) },
                                onLongPress = { pendingDelete = e },
                                onDelete = { pendingDelete = e },
                            )
                        }

                        if (otherCourses.isEmpty() && otherEvents.isEmpty()) {
                            item { EmptyCard("没有其他事项") }
                        }

                        item {
                            Text(
                                "数据更新于 $lastRefresh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                            )
                        }
                    }
                }
            }
            }
        }

        FloatingActionButton(
            onClick = onAddEvent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "添加日程") }
    }

    detailItem?.let { item ->
        CourseDetailDialog(item = item, onDismiss = { detailItem = null })
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除日程") },
            text = { Text("要删除「${target.title}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.removeEvent(target.id)
                        pendingDelete = null
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    // 添加/编辑日程已上移到上面的 AnimatedContent 分支里（参与容器转换动画）
}

/** 明日概况：无课 / 有早八 / 有早九 / 有早十 / 睡懒觉（对齐 TodayUI） */
@Composable
private fun TomorrowSummaryCard(courses: List<TimeTableItem>) {
    val first = courses.minByOrNull { AxisTime.toFloat(it.startTime) }
    val (text, emoji) = when {
        first == null -> "无课" to "😄"
        first.startTime.startsWith("08") -> "有早八" to "😫"
        first.startTime.startsWith("09") -> "有早九" to "😫"
        first.startTime.startsWith("10") || first.startTime.startsWith("11") -> "有早十" to "😐"
        AxisTime.toFloat(first.startTime) >= 14f -> "睡懒觉" to "😄"
        else -> "有课" to "🙂"
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "明天",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f),
                )
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (first != null) {
                Text(
                    "${first.startTime} 起",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f),
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            text,
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 课程卡片（对齐原版）：
 * - 今日课：leading 为状态图标（未开始/上课中进度/已下课），trailing 为状态文字
 * - 明日课：leading 为「+1」徽标，overline 时间段，headline 课名，supporting 地点，trailing「明日」
 */
@Composable
private fun CourseCard(
    item: TimeTableItem,
    state: CourseState?,
    nowHour: Double,
    isTomorrow: Boolean,
    onClick: () -> Unit,
) {
    val progress = run {
        val s = AxisTime.toFloat(item.startTime)
        val e = AxisTime.toFloat(item.endTime)
        if (e > s) ((nowHour - s) / (e - s)).toFloat().coerceIn(0f, 1f) else 0f
    }

    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // leading：明日课显示 +1，今日课显示状态图标
            if (isTomorrow) {
                Text(
                    "+1",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(26.dp),
                )
            } else {
                when (state) {
                    CourseState.ONGOING -> CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                    CourseState.ENDED -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    else -> Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${item.startTime}-${item.endTime}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                item.place?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val label = if (isTomorrow) "明日" else when (state) {
                CourseState.NOT_STARTED -> "未开始"
                CourseState.ONGOING -> "上课中"
                CourseState.ENDED -> "已下课"
                null -> ""
            }
            Text(
                label,
                fontSize = 12.sp,
                color = if (state == CourseState.ONGOING) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 日程卡片（对齐 CustomItemUI）：
 * 标题 / 时间段（remark）+ 描述；过期加删除线；明日显示 +1 与「明日」；长按删除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventCard(
    event: CustomEvent,
    overdue: Boolean,
    isTomorrow: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val strike = if (overdue) TextDecoration.LineThrough else null
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // leading：明日事项显示「+1」（对齐截图），否则显示日历图标
            if (isTomorrow) {
                Text(
                    "+1",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(26.dp),
                )
            } else {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    tint = if (overdue) MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (event.remark.isNotBlank()) "${event.remark}  " else "") +
                            "${event.startTime}-${event.endTime}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = strike,
                )
                Text(
                    event.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = strike,
                )
                if (event.note.isNotBlank()) {
                    Text(
                        event.note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = strike,
                    )
                }
            }
            if (overdue) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (isTomorrow) {
                Text(
                    "明日",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
