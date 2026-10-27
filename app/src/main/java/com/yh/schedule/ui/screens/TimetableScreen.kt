package com.yh.schedule.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yh.schedule.core.Course
import com.yh.schedule.core.CustomEvent
import com.yh.schedule.core.QzJsTimetableParser
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.TimeTableItem
import com.yh.schedule.core.TimeTableType
import com.yh.schedule.data.Repository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val SWIPE_THRESHOLD = 90f

/**
 * 课表页（对齐参考源码 HFUT-Schedule 的日历主页）：
 * - 时间轴渲染（左侧完整时间刻度）
 * - 顶部只有日期数字（今天下划线）
 * - 左右滑动 / 右下角胶囊切周
 * - 双击空白添加日程，保存后立即出现在课表中
 * - 真实数据：隐藏 WebView 抓取强智课表页 → 页面内 JS 结构化 → 解析缓存
 */
@Composable
fun TimetableScreen(
    repo: Repository,
    /** 双击空白：由 MainScaffold 打开全屏添加页 */
    onAddEvent: (java.time.LocalDate, String, String) -> Unit = { _, _, _ -> },
    /** 点自建日程：由 MainScaffold 打开全屏编辑页 */
    onEditEvent: (CustomEvent) -> Unit = {},
    /** 点课程：由 MainScaffold 打开全屏课程详情 */
    onCourseDetail: (List<Course>) -> Unit = {},
    /** 外部增删改日程后自增，触发本周重排 */
    refreshKey: Int = 0,
) {
    var week by remember { mutableIntStateOf(SchoolCalendar.currentWeek()) }
    var items by remember { mutableStateOf<List<TimeTableItem>>(emptyList()) }
    var showWeekend by remember { mutableStateOf(false) }
    var demo by remember { mutableStateOf(true) }
    // 本周数据是否已加载过一次（用于屏蔽重建瞬间的空态闪烁）
    var loaded by remember { mutableStateOf(false) }
    var swipeX by remember { mutableFloatStateOf(0f) }

    var showWeekPicker by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<TimeTableItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // 课表页只读本地缓存：不抓取、不显示进度、不弹横幅——这样从任何 tab 切回来
    // 都是同一帧同一布局，不会有内容或底色跳变。抓取统一在「聚焦」页做，成功后写缓存。
    LaunchedEffect(Unit) {
        demo = repo.demoMode.first()
    }

    // 周次变化、或外部增删日程后刷新
    LaunchedEffect(week, refreshKey) {
        items = repo.timetableItems(week)
        // 标记"本周数据已就绪"：切换 tab 时本页会被重建，items 会短暂回到空列表，
        // 若不拦住，会先闪一下浅色空态卡片再消失，看起来就是背景变了两种颜色
        loaded = true
    }

    // 本周周末（周六/周日）有没有课程或自建日程：有 → 排 7 天，没有 → 只排周一到周五，
    // 让每列更宽（对齐源码 showAll 的 7/5 两天数切换）
    LaunchedEffect(items) {
        showWeekend = items.any { it.dayOfWeek == 6 || it.dayOfWeek == 7 }
    }

    // 抓取逻辑已整体移到「聚焦」页（见 FocusScreen 的教务检查卡），课表页不再联网。

    // 课表页不再显示刷新结果——成功/失败都在聚焦页反馈

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(week) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            swipeX > SWIPE_THRESHOLD && week > 1 -> week--
                            swipeX < -SWIPE_THRESHOLD && week < 25 -> week++
                        }
                        swipeX = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        swipeX += amount
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {

            if (!demo && loaded && items.isEmpty()) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "本周没有课程安排",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "第 $week 周没有排课（军训、停课或集中实践周常见）。\n" +
                                    "点右下角切到其他周次即可查看。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            TimetableAxis(
                items = items,
                week = week,
                today = LocalDate.now(),
                modifier = Modifier.fillMaxSize(),
                dayCount = if (showWeekend) 7 else 5,
                onItemClick = { item ->
                    val eid = item.eventId
                    when {
                        // 自建日程 → 编辑页（右上角可删除 / 复制）
                        item.type == TimeTableType.FOCUS && eid != null -> scope.launch {
                            val target = repo.customEvents().first().firstOrNull { it.id == eid }
                            if (target != null) onEditEvent(target) else detailItem = item
                        }
                        // 课程 → 课程详情页（同名多条一起传，用于拼「上课安排」）
                        item.type == TimeTableType.COURSE -> scope.launch {
                            val all = repo.timetable(week).filter { it.name == item.name }
                            all.ifEmpty { null }?.let { onCourseDetail(it) }
                        }
                        else -> detailItem = item
                    }
                },
                onBlankDoubleTap = { day, hour ->
                    val monday = SchoolCalendar.termStartMonday.plusWeeks((week - 1).toLong())
                    val date = monday.plusDays((day - 1).toLong())
                    val start = hour.coerceIn(0f, 23f).let { com.yh.schedule.ui.screens.nearestHalf(it) }
                    val end = (hour + 1f).coerceIn(0f, 23f)
                        .let { com.yh.schedule.ui.screens.nearestHalf(it) }
                    onAddEvent(date, start, end)
                },
                onWeekTitleClick = { showWeekPicker = true },
                trailing = {
                    // 只保留「5 天 / 7 天」缩放开关；刷新按钮已移除——课表页不联网，
                    // 抓取与进度统一在「聚焦」页展示
                    IconButton(
                        onClick = { showWeekend = !showWeekend },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            if (showWeekend) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                            contentDescription = if (showWeekend) "收起周末" else "展开周末",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        WeekFloatingButton(
            week = week,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            onPrevious = { if (week > 1) week-- },
            onNext = { if (week < 25) week++ },
            onBackToCurrent = { week = SchoolCalendar.currentWeek() },
        )

        toast?.let {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                action = {
                    TextButton(onClick = { toast = null }) { Text("关闭") }
                },
            ) { Text(it) }
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(2500)
                toast = null
            }
        }
    }

    detailItem?.let { item ->
        CourseDetailDialog(item = item, onDismiss = { detailItem = null })
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            currentWeek = week,
            onSelect = { w ->
                week = w
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false },
        )
    }
}

/** 日期 → 第几周 */
private fun weekOf(date: LocalDate): Int {
    val weeks = java.time.temporal.ChronoUnit.WEEKS
        .between(SchoolCalendar.termStartMonday, date).toInt() + 1
    return weeks.coerceIn(1, 25)
}

/** 小时浮点 → HH:mm（取整到半小时） */
private fun nearestHalf(hour: Float): String {
    val totalMinutes = (hour * 60).toInt()
    val half = (totalMinutes / 30) * 30
    val h = (half / 60).coerceIn(0, 23)
    val m = half % 60
    return "%02d:%02d".format(h, m)
}

/**
 * 隐藏 WebView：加载教务课表页，在页面内把 table#timetable 结构化返回 JSON。
 * 状态机对齐 SSO 多跳时序（详见 README 的"五个必须遵守的约束"）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun TimetableScraper(
    tick: Int,
    onJson: (String) -> Unit,
    onFail: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    // 关键：用 key(tick) 让每次重试都重建 WebView。
    // AndroidView 的 factory 只在首次组合时执行，grabbed / retry / warmedUp 都是 factory 内部变量；
    // 不重建的话第二次抓取会被 grab() 里的 if (grabbed) return 直接挡掉，表现为"点了重试没反应"
    androidx.compose.runtime.key(tick) {
    AndroidView(
        // 不能 0dp：零尺寸会跳过布局/渲染，DOM 不生成
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // VPN 代理走 http，页面里可能夹带 https 资源，默认策略会拦截导致白屏
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                CookieManager.getInstance().setAcceptCookie(true)

                var retry = 0
                var warmedUp = false
                var grabbed = false

                fun grab(view: WebView, attempt: Int = 0) {
                    if (grabbed) return
                    view.evaluateJavascript(
                        "(function(){" +
                                "var t=document.getElementById('timetable');" +
                                "if(!t||t.rows.length<2){" +
                                "  var b=(document.body?document.body.innerText:'').slice(0,200);" +
                                "  if(b.indexOf('flag1')>=0||b.indexOf('msgContent')>=0) return 'NOT_LOGIN';" +
                                "  return 'NOT_READY';" +
                                "}" +
                                "var out=[];" +
                                "for(var r=1;r<t.rows.length;r++){" +
                                "  var cells=t.rows[r].cells;" +
                                "  if(cells.length<8) continue;" +
                                "  var label=(cells[0].innerText||'').replace(/\\s+/g,' ').trim();" +
                                "  for(var c=1;c<cells.length&&c<=7;c++){" +
                                // 抓整格所有 font（教师/教学楼等在 div.kbcontent1 之外）
                                "    var fs=[];var fonts=cells[c].querySelectorAll('font');" +
                                "    for(var f=0;f<fonts.length;f++){" +
                                "      var s=(fonts[f].textContent||'').replace(/\\s+/g,' ').trim();" +
                                "      if(s) fs.push({t:(fonts[f].getAttribute('title')||''),v:s});" +
                                "    }" +
                                "    if(fs.length>0) out.push({row:label,day:c,fonts:fs});" +
                                "  }" +
                                "}" +
                                "if(out.length===0) return 'EMPTY_TABLE';" +
                                // 探测「当前第几周」：三级回退，任一命中即可（见 QzTermParser 的说明）
                                // ① 地址栏参数 zs / weekNo / zc（强智课表页标准，最可靠）
                                // ② 页面正文里的「第N周」
                                // ③ 页面上任意一个值为 1~2 位数字的 select（周次下拉框）
                                "var wk=(function(){" +
                                "  try{var m=(location.search||'').match(/[?&](?:zs|weekNo|zc)=(\\d{1,2})/);if(m)return parseInt(m[1],10);}catch(e){}" +
                                "  try{var b2=(document.body?document.body.innerText:'').slice(0,4000);var m2=b2.match(/第\\s*(\\d{1,2})\\s*周/);if(m2)return parseInt(m2[1],10);}catch(e){}" +
                                "  try{var ss=document.querySelectorAll('select');for(var i=0;i<ss.length;i++){var v=ss[i].value;if(v&&/^\\d{1,2}$/.test(v))return parseInt(v,10);}}catch(e){}" +
                                "  return 0;" +
                                "})();" +
                                "return JSON.stringify({w:wk,t:out});})()"
                    ) { raw ->
                        val payload = decodeJsString(raw)
                        when {
                            // 会话过期：教务返回未登录 JSON，立即提示而不是干等
                            payload == "NOT_LOGIN" ->
                                onFail("登录已过期（教务返回未登录）")
                            payload == "NOT_READY" && attempt < 20 ->
                                view.postDelayed({ grab(view, attempt + 1) }, 2000)
                            payload == "NOT_READY" -> onFail("页面长时间未渲染出课表，请重试")
                            payload == "EMPTY_TABLE" -> onFail("课表页已打开但本学期暂无课程安排")
                            payload.isBlank() -> onFail("页面内容为空，请重试")
                            payload.contains("flag1") && payload.contains("msgContent") ->
                                onFail("教务会话已失效（接口返回未登录）")
                            // 新格式 {"w":周次,"t":[课程行]}；旧格式是纯数组 [...]。两种都要认，
                            // 只判 "[" 会让新格式全部落到下面的 onFail，表现为"登录成功但抓不到课表"
                            payload.startsWith("[") || payload.startsWith("{") -> {
                                grabbed = true
                                onJson(payload)
                            }
                            else -> onFail("未能读取课表数据，请重试")
                        }
                    }
                }

                fun inspect(view: WebView) {
                    view.evaluateJavascript("location.href") { raw ->
                        val u = decodeJsString(raw)
                        when {
                            u.contains("cas.paas.cdut.edu.cn") || u.contains("sso/login") -> {
                                if (retry < 4) {
                                    retry++
                                    view.postDelayed({ inspect(view) }, 4000)
                                } else {
                                    onFail("登录已过期（仍停留在认证页）")
                                }
                            }
                            u.contains("xskb/xskb_list") -> grab(view)
                            u.isBlank() -> onFail("无法读取页面地址，请重试")
                            else -> {
                                if (retry < 4) {
                                    retry++
                                    view.postDelayed({ view.loadUrl(com.yh.schedule.core.WebConst.JW_TIMETABLE_URL) }, 3000)
                                } else {
                                    onFail("未能进入课表页，请重试")
                                }
                            }
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (view == null) return
                        if (!warmedUp) {
                            warmedUp = true
                            view.postDelayed(
                                { view.loadUrl(com.yh.schedule.core.WebConst.JW_TIMETABLE_URL) },
                                3000,
                            )
                            return
                        }
                        view.postDelayed({ inspect(view) }, 4500)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            onFail("页面加载失败：${error?.description ?: "网络错误"}")
                        }
                    }
                }
                webView = this
                // 先访问教务首页触发 SSO，再抓课表（关键：直接抓会被踢回登录页）
                loadUrl(com.yh.schedule.core.WebConst.JW_HOME_URL)
            }
        },
    )
    }
}

/** evaluateJavascript 回调是 JSON 字符串字面量，需还原 */
private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        org.json.JSONTokener(raw).nextValue() as String
    }.getOrDefault("")
}
