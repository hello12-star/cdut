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
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
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
import androidx.compose.runtime.collectAsState
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
    // 选项页的偏好：开启后无论本周有没有周末课都按 7 天显示
    var preferWeekend by remember { mutableStateOf(false) }
    val courseAlpha by repo.courseAlpha.collectAsState(initial = 1f)
    val courseTextScale by repo.courseTextScale.collectAsState(initial = 1f)
    val courseLineScale by repo.courseLineScale.collectAsState(initial = 1f)
    val courseColors by repo.courseColors.collectAsState(initial = emptyMap())
    val colorfulCourses by repo.colorfulCourses.collectAsState(initial = true)
    var demo by remember { mutableStateOf(true) }
    // 是否存在真实课表缓存：用于区分「抓取还没成功」和「这周确实没排课」
    var hasCache by remember { mutableStateOf(false) }
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
        hasCache = repo.hasCachedTimetable()
        preferWeekend = repo.showWeekendDefault.first()
    }

    // 周次变化、或外部增删日程后刷新
    LaunchedEffect(week, refreshKey) {
        items = repo.timetableItems(week)
        // 标记"本周数据已就绪"：切换 tab 时本页会被重建，items 会短暂回到空列表，
        // 若不拦住，会先闪一下浅色空态卡片再消失，看起来就是背景变了两种颜色
        loaded = true
    }

    // 本周周末（周六/周日）有没有课程或自建日程：有 → 排 7 天，没有 → 只排周一到周五，
    // 让每列更宽（对齐源码 showAll 的 7/5 两天数切换）。
    // 选项页开了「默认展开周末」就无视本周排课，始终 7 天。
    LaunchedEffect(items, preferWeekend) {
        showWeekend = preferWeekend || items.any { it.dayOfWeek == 6 || it.dayOfWeek == 7 }
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
                        if (!hasCache) {
                            // 还没抓到过课表 ≠ 本周没课：否则会让人以为教务真的没排课
                            Text(
                                "暂无课表数据",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "还没有成功抓到过课表。请到「聚焦」页下拉刷新完成一次教务抓取，" +
                                        "成功后回到这里即可查看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
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
            }

            TimetableAxis(
                items = items,
                week = week,
                today = LocalDate.now(),
                modifier = Modifier.fillMaxSize(),
                dayCount = if (showWeekend) 7 else 5,
                courseAlpha = courseAlpha,
                courseTextScale = courseTextScale,
                courseLineScale = courseLineScale,
                courseColors = courseColors,
                colorfulCourses = colorfulCourses,
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
                            // 对角（放大/缩小）箭头：向外扩 = 展开周末，向内收 = 收起周末
                            if (showWeekend) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
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
    // 离开组合（抓取结束、切换 Tab、重试重建）时统一置位，
    // 让已经排进消息队列的 postDelayed 回调立刻失效，避免 WebView 销毁后还在后台抓取。
    val session = remember(tick) { ScrapeSession() }

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
                // 走 VPN 代理后，教务会话 cookie 需要在代理域上生效，同样属于第三方 cookie
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                var retry = 0
                var warmedUp = false
                var grabbed = false
                // 成功或失败都算「已结束」：结束后所有轮询/回跳都必须停下，
                // 否则抓取失败后 poll 仍会每 5 秒重新加载一次教务，耗电且反复打教务。
                var finished = false
                fun fail(msg: String) {
                    if (finished || grabbed || session.released) return
                    finished = true
                    onFail(msg)
                }
                // 门户点击「教务管理系统」的次数。点击后若一直停在门户（页面用 window.open 或
                // SPA 拦截），原逻辑会重载门户 → onPageFinished → 再点，形成无限循环。
                // 这里限制次数，超过就带诊断信息失败，不再空转。
                var portalClicks = 0
                // 记录访问过的 URL 轨迹：失败时一并回传给界面，
                // 这样不用连电脑抓日志也能看出卡在哪一跳
                val urlTrail = mutableListOf<String>()
                fun trail(u: String) {
                    val short = u.substringAfter("://").take(70)
                    if (urlTrail.lastOrNull() != short) {
                        if (urlTrail.size >= 6) urlTrail.removeAt(0)
                        urlTrail.add(short)
                    }
                }
                fun trailText(): String = urlTrail.joinToString(" → ")

                fun grab(view: WebView, attempt: Int = 0) {
                    if (grabbed || finished || session.released) return
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
                                fail("登录已过期（教务返回未登录）")
                            payload == "NOT_READY" && attempt < 20 ->
                                view.postDelayed({ grab(view, attempt + 1) }, 2000)
                            payload == "NOT_READY" -> fail("页面长时间未渲染出课表，请重试")
                            payload == "EMPTY_TABLE" -> fail("课表页已打开但本学期暂无课程安排")
                            payload.isBlank() -> fail("页面内容为空，请重试")
                            payload.contains("flag1") && payload.contains("msgContent") ->
                                fail("教务会话已失效（接口返回未登录）")
                            // 新格式 {"w":周次,"t":[课程行]}；旧格式是纯数组 [...]。两种都要认，
                            // 只判 "[" 会让新格式全部落到下面的 onFail，表现为"登录成功但抓不到课表"
                            payload.startsWith("[") || payload.startsWith("{") -> {
                                if (finished || session.released) return@evaluateJavascript
                                grabbed = true
                                finished = true
                                onJson(payload)
                            }
                            else -> fail("未能读取课表数据，请重试")
                        }
                    }
                }

                /** 失败诊断：把当前地址、正文片段、候选入口（含 onclick）一并带出来 */
                fun dumpPortal(view: WebView, prefix: String) {
                    view.evaluateJavascript(
                        "(function(){" +
                                "var t=(document.body?document.body.innerText:'').replace(/\\s+/g,' ').slice(0,300);" +
                                "var a=[];var as=document.querySelectorAll('a');" +
                                "for(var i=0;i<as.length;i++){" +
                                "  a.push(((as[i].innerText||as[i].title||'').replace(/\\s+/g,' ').slice(0,30))" +
                                "    +'|'+((as[i].href||'').slice(0,80)));" +
                                "}" +
                                "var ds=[];var cs=document.querySelectorAll('div[onclick],li[onclick],span[onclick]');" +
                                "for(var d=0;d<cs.length;d++){" +
                                "  var et=(cs[d].innerText||'').replace(/\\s+/g,' ').trim().slice(0,20);" +
                                "  if(et.indexOf('教务')<0) continue;" +
                                "  ds.push(et+'|'+(cs[d].getAttribute('onclick')||'').slice(0,150));" +
                                "}" +
                                "return location.href+' || '+t+' || OPEN: '+(window.__yhOpen||'')" +
                                "  +' || LINKS: '+a.slice(0,20).join(' ; ')" +
                                "  +' || CLICK: '+ds.slice(0,10).join(' ; ');" +
                                "})()"
                    ) { info ->
                        val msg = prefix + "｜" + decodeJsString(info) + "｜轨迹: " + trailText()
                        android.util.Log.i("VPNScrape", msg)
                        fail(msg)
                    }
                }

                /**
                 * 智慧校园门户：找「教务管理系统」入口并点进去。
                 *
                 * 为什么要这一步：教务要求从门户走 SSO 单点进入，
                 * 直接访问 jw 代理域会因为拿不到 SSO 票据报「未取到会话凭据」。
                 * 门户页面可能是 SPA / 含 iframe，所以 document 和 iframe 都找一遍。
                 *
                 * 入口常见两种实现，抓取 WebView 都没法自然处理：
                 * 1. `window.open(url)`：没有 onCreateWindow 就被直接吞掉；
                 * 2. `target="_blank"`：同样不导航。
                 * 所以这里先钩住 window.open 记录目标地址，再把空白页链接改回当前窗口。
                 */
                fun clickJwLink(view: WebView, attempt: Int = 0) {
                    val js = "(function(){" +
                            "function abs(u){try{return new URL(u,location.href).href;}catch(e){return u?(''+u):'';}}" +
                            "if(!window.__yhOpenHooked){" +
                            "  window.__yhOpenHooked=1;window.__yhOpen='';" +
                            "  window.open=function(u){window.__yhOpen=abs((u||'')+'');return null;};" +
                            "}" +
                            "window.__yhOpen='';" +
                            "var bs=document.querySelectorAll('a[target=_blank]');" +
                            "for(var bi=0;bi<bs.length;bi++) bs[bi].target='_self';" +
                            "function tryDoc(d){" +
                            "  if(!d) return null;" +
                            "  var as=d.querySelectorAll('a');" +
                            "  for(var i=0;i<as.length;i++){" +
                            "    var t=(as[i].innerText||'')+(as[i].title||'');" +
                            "    if(t.indexOf('教务')>=0){" +
                            // 优先直接改 location，绕开新窗口
                            "      var h=as[i].href||'';" +
                            "      if(h && h.indexOf('javascript')!==0 && h!=='#' && h.indexOf('void')<0){" +
                            "        location.href=h; return 'NAV:'+h.slice(0,120);" +
                            "      }" +
                            "      as[i].target='_self';" +
                            "      if(as[i].click) as[i].click();" +
                            "      return 'CLICKED_A:'+((as[i].innerText||'').replace(/\\s+/g,' ').trim().slice(0,30));" +
                            "    }" +
                            "  }" +
                            "  var cards=d.querySelectorAll('div[onclick],li[onclick],span[onclick]');" +
                            "  for(var j=0;j<cards.length;j++){" +
                            "    if((cards[j].innerText||'').indexOf('教务')>=0){" +
                            // 带上 onclick 源码：失败时能看出入口到底怎么跳
                            "      var oc=cards[j].getAttribute('onclick')||'';" +
                            "      cards[j].click();" +
                            "      return 'CLICKED_DIV|'+oc.slice(0,200);" +
                            "    }" +
                            "  }" +
                            // 兜底：门户入口有时是普通 div/span（无 onclick/href），
                            // 只靠文本判断，点击后靠事件冒泡到父级卡片。
                            "  var all=d.querySelectorAll('*');" +
                            "  for(var m=0;m<all.length;m++){" +
                            "    var el=all[m];" +
                            "    var et=(el.innerText||'').replace(/\\s+/g,' ').trim();" +
                            "    if(et.length>0 && et.length<30 && et.indexOf('教务')>=0 && el.click){" +
                            "      el.click(); return 'CLICKED_TEXT:'+et;" +
                            "    }" +
                            "  }" +
                            "  return null;" +
                            "}" +
                            "var r=tryDoc(document); if(r) return r;" +
                            "var ifr=document.querySelectorAll('iframe');" +
                            "for(var k=0;k<ifr.length;k++){" +
                            "  try{var r2=tryDoc(ifr[k].contentDocument); if(r2) return r2;}catch(e){}" +
                            "}" +
                            "return 'NO_LINK';})()"
                    view.evaluateJavascript(js) { raw ->
                        val clickResult = decodeJsString(raw)
                        android.util.Log.i("VPNScrape", "clickJwLink: ${clickResult.substringBefore('?')}")
                        when {
                            clickResult.startsWith("NAV:") ->
                                view.loadUrl(clickResult.removePrefix("NAV:"))
                            clickResult.startsWith("CLICKED") -> {
                                // 点击后 SSO 可能异步跳转，等 5 秒再看落点和 window.open 目标
                                view.postDelayed({
                                    view.evaluateJavascript(
                                        "(function(){return (window.__yhOpen||'')+'|'+location.href;})()"
                                    ) { r2 ->
                                        val info = decodeJsString(r2)
                                        val opened = info.substringBefore('|')
                                        val u2 = info.substringAfter('|')
                                        android.util.Log.i(
                                            "VPNScrape",
                                            "after click: open=${opened.substringBefore('?')} " +
                                                    "now=${u2.substringBefore('?')}",
                                        )
                                        when {
                                            // 钩到的 window.open 目标：直接在本 WebView 打开。
                                            // 门户给的是直连 jw 地址，必须改成 VPN 代理域，
                                            // 否则会落到没有 TGC 的直连 CAS 登录页。
                                            opened.startsWith("http") -> {
                                                portalClicks = 0
                                                val target =
                                                    com.yh.schedule.core.WebConst.proxify(opened)
                                                android.util.Log.i(
                                                    "VPNScrape",
                                                    "open proxified: ${target.substringBefore('?')}",
                                                )
                                                view.loadUrl(target)
                                            }
                                            u2.contains("xskb/xskb_list") -> grab(view)
                                            u2.contains(com.yh.schedule.core.WebConst.VPN_PROXY_HOST) -> {
                                                portalClicks = 0
                                                view.loadUrl(com.yh.schedule.core.WebConst.JW_TIMETABLE_URL)
                                            }
                                            portalClicks < 3 -> {
                                                portalClicks++
                                                view.loadUrl(com.yh.schedule.core.WebConst.PORTAL_URL)
                                            }
                                            else -> dumpPortal(
                                                view,
                                                "点了教务入口但页面没跳转｜$clickResult",
                                            )
                                        }
                                    }
                                }, 5000)
                            }
                            attempt < 3 -> view.postDelayed({ clickJwLink(view, attempt + 1) }, 3000)
                            else -> dumpPortal(view, "门户里没找到教务入口")
                        }
                    }
                }

                fun inspect(view: WebView) {
                    if (finished || session.released) return
                    view.evaluateJavascript("location.href") { raw ->
                        if (finished || session.released) return@evaluateJavascript
                        val u = decodeJsString(raw)
                        // 只记录 host+path：SSO 跳转地址可能带 ticket，完整 URL 不应进日志
                        android.util.Log.i("VPNScrape", "inspect: ${u.substringBefore('?')}")
                        view.evaluateJavascript(
                            "(function(){return (document.title||'')+'|'+document.readyState;})()"
                        ) { t ->
                            android.util.Log.i("VPNScrape", "title: " + decodeJsString(t))
                        }
                        trail(u)
                        when {
                            // VPN 认证页：TGC 有效时应自动带 ticket 跳回教务；
                            // 停在这里说明 VPN 会话过期，只能让用户重新登录
                            u.contains("cas-paas-cdut-edu-cn-s.vpn.cdut.edu.cn") -> {
                                if (retry < 2) {
                                    retry++
                                    view.postDelayed({ inspect(view) }, 4000)
                                } else {
                                    fail("登录已过期（VPN 认证页未自动通过），请到「服务」页重新登录")
                                }
                            }
                            // 直连 CAS：VPN 模式下它没有 TGC，改走代理域的教务 SSO
                            u.contains("cas.paas.cdut.edu.cn") -> {
                                if (com.yh.schedule.core.WebConst.USE_VPN) {
                                    view.loadUrl(
                                        com.yh.schedule.core.WebConst.proxify(
                                            "https://jw.cdut.edu.cn/sso/login.jsp"
                                        )
                                    )
                                } else if (retry < 3) {
                                    retry++
                                    view.postDelayed({ inspect(view) }, 4000)
                                } else {
                                    fail("登录已过期（仍停留在认证页）")
                                }
                            }
                            u.contains("sso/login") -> {
                                if (retry < 3) {
                                    retry++
                                    view.postDelayed({ inspect(view) }, 4000)
                                } else {
                                    fail("登录已过期（仍停留在认证页）")
                                }
                            }
                            u.contains("xskb/xskb_list") -> grab(view)
                            // 门户：从「推荐系统」点「教务管理系统」进去
                            u.contains("bsdt-") || u.contains("homePage") -> clickJwLink(view)
                            // 已在教务域但不在课表页 → 直接去课表。
                            // 直连（jw.cdut.edu.cn）和 VPN 代理域都要认：只认代理域的话，
                            // 校园网直连会落到 else 反复回首页，最终报"未能进入课表页"。
                            u.contains(com.yh.schedule.core.WebConst.VPN_PROXY_HOST) ||
                                    u.contains("jw.cdut.edu.cn") -> {
                                if (retry < 3) {
                                    retry++
                                    view.postDelayed(
                                        { view.loadUrl(com.yh.schedule.core.WebConst.JW_TIMETABLE_URL) },
                                        2500,
                                    )
                                } else {
                                    fail("已进入教务但打不开课表页，请重试")
                                }
                            }
                            u.isBlank() -> fail("无法读取页面地址，请重试")
                            else -> {
                                if (retry < 3) {
                                    retry++
                                    view.postDelayed(
                                        { view.loadUrl(com.yh.schedule.core.WebConst.SCRAPE_ENTRY_URL) },
                                        3000,
                                    )
                                } else {
                                    fail("未能进入课表页｜轨迹: " + trailText())
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
                            if (com.yh.schedule.core.WebConst.USE_VPN) {
                                // VPN：首屏是门户，交给 inspect 去点「教务管理系统」
                                view.postDelayed({ inspect(view) }, 4500)
                            } else {
                                // 直连：首屏是教务首页，直接去课表
                                view.postDelayed(
                                    { view.loadUrl(com.yh.schedule.core.WebConst.JW_TIMETABLE_URL) },
                                    3000,
                                )
                            }
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
                            fail("页面加载失败：${error?.description ?: "网络错误"}")
                        }
                    }
                }
                // 起始页由开关决定：VPN 模式先进门户，直连模式直接进教务
                loadUrl(com.yh.schedule.core.WebConst.SCRAPE_ENTRY_URL)

                // 兜底轮询：教务 SSO 跳转链有时页面加载完了也不回调 onPageFinished
                // （表现为永远停在「正在检查教务系统登陆状态」）。每 5 秒看一次落点；
                // 成功、失败、离开页面都会立即停止，并且总时长封顶，避免无限打教务。
                val wv = this
                fun poll(remaining: Int) {
                    if (grabbed || finished || session.released) return
                    if (remaining <= 0) {
                        fail("抓取超时（约 2 分钟），请重试")
                        return
                    }
                    inspect(wv)
                    if (!grabbed && !finished && !session.released) {
                        wv.postDelayed({ poll(remaining - 1) }, 5000)
                    }
                }
                wv.postDelayed({ poll(23) }, 6000)
            }
        },
        onRelease = { view ->
            // 隐藏 WebView 不会自动销毁：不销毁会常驻内存，内部定时器也会继续跑
            session.released = true
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
        },
    )
    }
}

/** 一次抓取会话的生命周期标记：onRelease 之后所有排队回调都应立即短路 */
private class ScrapeSession {
    @Volatile var released: Boolean = false
}

/** evaluateJavascript 回调是 JSON 字符串字面量，需还原 */
private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        org.json.JSONTokener(raw).nextValue() as String
    }.getOrDefault("")
}
