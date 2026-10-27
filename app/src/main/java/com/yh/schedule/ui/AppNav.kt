package com.yh.schedule.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.yh.schedule.CdutApp
import com.yh.schedule.core.Course
import com.yh.schedule.core.CustomEvent
import com.yh.schedule.core.SessionStore
import com.yh.schedule.data.Repository
import com.yh.schedule.ui.screens.AddEventScreen
import com.yh.schedule.ui.screens.CourseDetailScreen
import com.yh.schedule.ui.screens.FocusScreen
import com.yh.schedule.ui.screens.GradeScreen
import com.yh.schedule.ui.screens.LoginScreen
import com.yh.schedule.ui.screens.ServicesScreen
import com.yh.schedule.ui.screens.TimetableScreen
import com.yh.schedule.ui.screens.WebContainerScreen

private data class Tab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/** 全屏覆盖层的三种内容，作为 AnimatedContent 的 targetState 使用 */
private sealed interface Overlay {
    data class Add(val date: java.time.LocalDate, val start: String, val end: String) : Overlay
    data class Edit(val event: CustomEvent) : Overlay
    // 注意：子类别叫 Course，否则会遮蔽 com.yh.schedule.core.Course
    data class CourseDetail(val courses: List<Course>) : Overlay
}

@Composable
fun AppRoot(app: CdutApp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SessionStore(ctx.applicationContext) }
    val repo = remember { Repository(ctx.applicationContext, app, store) }

    // 用已持久化的学期第一周周一覆盖硬编码默认值（首次启动没有记录就用默认）
    // 同时读一次凭据：老版本存的是明文密码，读取时会静默重写为 Keystore 加密后的密文
    LaunchedEffect(Unit) {
        repo.loadTermStartMonday()
        repo.savedCredentials()
    }

    val loggedIn by androidx.compose.runtime.produceState<Boolean?>(initialValue = null) {
        store.loggedIn.collect { value = it }
    }
    var showCasLogin by remember { androidx.compose.runtime.mutableStateOf(false) }
    val rootScope = androidx.compose.runtime.rememberCoroutineScope()

    // 登录成功（含「刷新登录状态 / 切换账号」）后：清掉上一份课表缓存，并允许下次重新抓取，
    // 否则换了账号还会看到上一个人的课表
    fun onLoginDone() {
        showCasLogin = false
        rootScope.launch {
            repo.cacheTimetable(emptyList())
            repo.resetScrapeGuard()
        }
    }

    when (loggedIn) {
        null -> { /* DataStore 首次读取中 */ }
        false -> if (showCasLogin) {
            com.yh.schedule.ui.screens.CasLoginScreen(
                repo = repo,
                onSuccess = { onLoginDone() },
                onBack = { showCasLogin = false },
            )
        } else {
            LoginScreen(repo = repo, onCasLogin = { showCasLogin = true })
        }
        true -> MainNav(repo = repo)
    }
}

@Composable
fun MainNav(repo: Repository) {
    val nav = rememberNavController()
    var showRelogin by remember { androidx.compose.runtime.mutableStateOf(false) }
    val reloginScope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "main") {
            composable("main") { MainScaffold(repo = repo, openWeb = { url, title ->
                nav.navigate("web?url=${android.net.Uri.encode(url)}&title=${android.net.Uri.encode(title)}")
            }, onRelogin = { showRelogin = true }) }
            composable(
                route = "web?url={url}&title={title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                ),
            ) { entry ->
                val url = entry.arguments?.getString("url").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                WebContainerScreen(url = url, title = title, onBack = { nav.popBackStack() })
            }
        }

        // 会话失效时，从主界面直接拉起登录页（带账号密码自动填充）
        if (showRelogin) {
            androidx.compose.material3.Surface(
                Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
            ) {
                com.yh.schedule.ui.screens.CasLoginScreen(
                    repo = repo,
                    onSuccess = {
                        showRelogin = false
                        // 刷新/切换账号后清掉旧课表，并允许聚焦页重新抓取
                        reloginScope.launch {
                            repo.cacheTimetable(emptyList())
                            repo.resetScrapeGuard()
                        }
                    },
                    onBack = { showRelogin = false },
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    repo: Repository,
    openWeb: (String, String) -> Unit,
    onRelogin: () -> Unit = {},
) {
    val tabs = listOf(
        Tab("课程表", Icons.Filled.CalendarMonth),
        Tab("聚焦", Icons.Filled.WbSunny),
        Tab("成绩", Icons.Filled.BarChart),
        Tab("服务", Icons.Filled.Apps),
    )
    var selected by remember { mutableIntStateOf(0) }
    val demo by repo.demoMode.collectAsState(initial = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 课表页只读缓存，刚登录时缓存是空的。此时自动停在「聚焦」页，
    // 抓取流程在那里发起，用户能看到进度；抓到后切回课表就有内容了。
    // 用 rememberSaveable 做一次性标记：从 WebView 返回会重建 MainScaffold，
    // 若不加标记会再次把用户弹到聚焦页。
    var bootRedirectDone by rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    LaunchedEffect(Unit) {
        if (!bootRedirectDone) {
            bootRedirectDone = true
            if (!repo.hasCachedTimetable()) selected = 1
        }
    }

    // 全屏覆盖层的内容（null = 关闭）。作为 AnimatedContent 的 targetState，
    // 这样关闭时旧内容会保留到退出动画播完，不会中途闪白。
    var overlay by remember { androidx.compose.runtime.mutableStateOf<Overlay?>(null) }
    // 增删改日程后自增，让课表重排（日程数据在 Room，课表页只按周查一次）
    var ttRefresh by remember { mutableIntStateOf(0) }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                // 注意：导航栏保持常驻、不去动它。一旦收起/展开，Scaffold 的 content padding 会变，
                // 底下整个页面跟着重排，观感上就是闪一下。覆盖层直接盖在 Scaffold 之上即可。
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = selected == i,
                            onClick = { selected = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
                when (selected) {
                    0 -> TimetableScreen(
                        repo,
                        onAddEvent = { date, start, end -> overlay = Overlay.Add(date, start, end) },
                        onEditEvent = { overlay = Overlay.Edit(it) },
                        onCourseDetail = { overlay = Overlay.CourseDetail(it) },
                        refreshKey = ttRefresh,
                    )
                    1 -> FocusScreen(
                        repo = repo,
                        openWeb = openWeb,
                        onAddEvent = {
                            overlay = Overlay.Add(java.time.LocalDate.now(), "08:10", "09:45")
                        },
                        onEditEvent = { overlay = Overlay.Edit(it) },
                    )
                    2 -> GradeScreen(repo)
                    3 -> ServicesScreen(
                        repo = repo,
                        openWeb = openWeb,
                        isRealLogin = !demo,
                        onRelogin = onRelogin,
                    )
                }
            }
        }

        // ---- 全屏覆盖层：盖在 Scaffold（含底部导航栏）之上 ----
        androidx.compose.animation.AnimatedContent(
            targetState = overlay,
            transitionSpec = {
                // 进入：淡入 + 轻微上滑；退出：淡出 + 轻微下滑。位移很小，只做"浮起"感，不做整屏滑动
                (androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(180)
                ) + androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(220),
                    initialOffsetY = { it / 28 },
                )).togetherWith(
                    androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(140)
                    ) + androidx.compose.animation.slideOutVertically(
                        animationSpec = androidx.compose.animation.core.tween(160),
                        targetOffsetY = { it / 40 },
                    )
                )
            },
            label = "overlay",
        ) { target ->
            when (target) {
                null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
                is Overlay.Add -> AddEventScreen(
                    initialDate = target.date,
                    initialStart = target.start,
                    initialEnd = target.end,
                    onDismiss = { overlay = null },
                    onSave = { event ->
                        scope.launch {
                            repo.addEvent(event)
                            ttRefresh++
                            overlay = null
                        }
                    },
                )
                is Overlay.Edit -> AddEventScreen(
                    initialDate = target.event.date,
                    initialStart = target.event.startTime,
                    initialEnd = target.event.endTime,
                    editing = target.event,
                    onDismiss = { overlay = null },
                    onDelete = {
                        scope.launch {
                            repo.removeEvent(target.event.id)
                            ttRefresh++
                            overlay = null
                        }
                    },
                    onSave = { event ->
                        scope.launch {
                            repo.updateEvent(event)
                            ttRefresh++
                            overlay = null
                        }
                    },
                )
                is Overlay.CourseDetail -> CourseDetailScreen(
                    courses = target.courses,
                    onDismiss = { overlay = null },
                )
            }
        }
    }

    // 返回键关闭覆盖层（放在最后，优先级高于页面自身的 BackHandler）
    androidx.activity.compose.BackHandler(enabled = overlay != null) { overlay = null }
}
