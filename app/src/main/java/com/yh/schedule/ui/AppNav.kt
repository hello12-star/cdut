package com.yh.schedule.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import com.yh.schedule.ui.screens.AboutScreen
import com.yh.schedule.ui.screens.CourseDetailScreen
import com.yh.schedule.ui.screens.DataPageScreen
import com.yh.schedule.ui.screens.FocusScreen
import com.yh.schedule.ui.screens.GradeStatsScreen
import com.yh.schedule.ui.screens.LoginScreen
import com.yh.schedule.ui.screens.OptionsScreen
import com.yh.schedule.ui.screens.QueryCenterScreen
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
    /** 查询中心里的「成绩统计」本地页 */
    data object Grade : Overlay
    /** 选项页里的「维护与关于」页（开源引用 / 用户协议） */
    data object About : Overlay
}

@Composable
fun AppRoot(app: CdutApp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SessionStore(ctx.applicationContext) }
    val repo = remember { Repository(ctx.applicationContext, app, store) }

    // 本地是否已有可展示的课表缓存：决定「登录不上时能不能先看课表」
    var hasCache by remember { mutableStateOf(false) }
    // 离线查看：未登录但本地有缓存时，直接进主界面看上次同步的数据
    var offlineView by remember { mutableStateOf(false) }

    // 用已持久化的学期第一周周一覆盖硬编码默认值（首次启动没有记录就用默认）
    // 同时读一次凭据：老版本存的是明文密码，读取时会静默重写为 Keystore 加密后的密文
    LaunchedEffect(Unit) {
        repo.loadTermStartMonday()
        repo.savedCredentials()
        hasCache = repo.hasCachedTimetable()
        // 每天首次启动自动备份一次（同一个文件覆盖，不堆积）；失败静默，不打扰使用
        runCatching {
            val last = repo.lastAutoBackupAt()
            if (System.currentTimeMillis() - last > 24 * 60 * 60 * 1000L) {
                val raw = repo.exportBackupJson()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.yh.schedule.core.TimetableExporter.writeToDownloads(
                        ctx, "掌上成理自动备份.json",
                    ) { out -> out.write(raw.toByteArray(Charsets.UTF_8)) }
                }
                repo.markAutoBackupAt(System.currentTimeMillis())
            }
        }
    }

    val loggedIn by androidx.compose.runtime.produceState<Boolean?>(initialValue = null) {
        store.loggedIn.collect { value = it }
    }
    var showCasLogin by remember { androidx.compose.runtime.mutableStateOf(false) }

    // 接入方式判定：校园网 WiFi 直连、蜂窝/校外 WiFi 走 VPN。
    // 登录页必须在判定完成后才渲染，避免把 CAS 页加载到错误的通道上；
    // 已登录用户则先用本地缓存直接进主页，探测在后台补，不必白等最多 2.5 秒。
    var accessReady by remember { mutableStateOf(false) }
    val rootScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        com.yh.schedule.core.NetworkAccess.refresh(ctx.applicationContext)
        accessReady = true
    }
    // 运行中切换网络（WiFi ↔ 流量）时重新判定；版本号变化会触发 MainNav 里的静默重登
    androidx.compose.runtime.DisposableEffect(Unit) {
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                rootScope.launch {
                    com.yh.schedule.core.NetworkAccess.refresh(ctx.applicationContext)
                }
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }

    // 深色模式是用户偏好（选项页可改），因此主题必须在这里包裹，不能放在 MainActivity
    val themeMode by store.themeMode.collectAsState(initial = "system")
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    // 登录成功后只关掉登录页。不再无条件清课表缓存：
    // 换账号的清理已移入 Repository.onWebLoginSuccess（按新旧账号比对），
    // 同账号重登保留上次课表，重登后抓取失败也不会让课表页变空。
    fun onLoginDone() {
        repo.setAutoRefreshFailed(false)
        showCasLogin = false
    }

    com.yh.schedule.ui.theme.YhTheme(darkTheme = darkTheme) {
        // 根容器铺主题底色并让出状态栏高度：内容仍从状态栏下方开始，
        // 但状态栏区域由这块背景填充，任何下拉/窗口动画都不会露出白底。
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            // 已登录用户不再等网络探测：课表页只读缓存，首屏应先出现；
            // 需要联网的抓取/静默重登会自己等 NetworkAccess.ready。
            if (!accessReady && loggedIn != true) {
                // 正在判定直连/VPN（WiFi 下最多等 2.5 秒探测）
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                )
            } else when (loggedIn) {
                null -> { /* DataStore 首次读取中 */ }
                false -> when {
                    // 登录不上但本地上次同步过课表：直接进主界面看缓存，
                    // 不要因为认证超时让「看课表」这件事彻底做不了
                    offlineView && hasCache -> MainNav(repo = repo, darkTheme = darkTheme)
                    showCasLogin -> com.yh.schedule.ui.screens.CasLoginScreen(
                        repo = repo,
                        onSuccess = { onLoginDone() },
                        onBack = { showCasLogin = false },
                        onUseCache = if (hasCache) {
                            {
                                showCasLogin = false
                                offlineView = true
                                android.widget.Toast.makeText(
                                    ctx,
                                    "登录未完成，当前显示上次同步的课表",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } else null,
                    )
                    else -> LoginScreen(
                        repo = repo,
                        onCasLogin = { showCasLogin = true },
                        onViewCache = if (hasCache) {
                            { offlineView = true }
                        } else null,
                    )
                }
                true -> MainNav(repo = repo, darkTheme = darkTheme)
            }
        }
    }
}

@Composable
fun MainNav(repo: Repository, darkTheme: Boolean = false) {
    val nav = rememberNavController()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showRelogin by remember { androidx.compose.runtime.mutableStateOf(false) }
    // 本地是否有课表缓存：登录失败时用来决定「能不能先回去看课表」
    var hasCache by remember { mutableStateOf(false) }
    val forceWebViewDark by repo.webViewDark.collectAsState(initial = false)

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 静默重登的两种触发：
    // ① 选项页开启「自动刷新登录」→ 冷启动后台刷新一次（D4，只认冷启动时的开关值）；
    // ② 接入方式变了（校园网 ↔ 流量/校外 WiFi）→ 两种会话不通用，必须重登一次。
    val accessVersion by com.yh.schedule.core.NetworkAccess.version.collectAsState()
    val netReady by com.yh.schedule.core.NetworkAccess.ready.collectAsState()
    var autoLoginCreds by remember { mutableStateOf<Pair<String, String>?>(null) }
    var autoLoginReason by remember { mutableStateOf<String?>(null) }   // cold / mode
    var autoLoginDone by remember { mutableStateOf(false) }
    var seenAccessVersion by remember { mutableStateOf<Int?>(null) }
    var mainUiReady by remember { mutableStateOf(false) }
    var coldRefreshPending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasCache = repo.hasCachedTimetable()
        val creds = repo.savedCredentials()
        autoLoginCreds = creds
        // 先用缓存渲染主线后，这里必须等首次接入判定完成再比较接入方式，
        // 否则会把「默认 VPN → 探测后直连」误判成网络切换，刚启动就白跑一次重登。
        com.yh.schedule.core.NetworkAccess.ready.first { it }
        val coldRefresh = repo.autoRefreshLogin.first()
        val lastMode = repo.loginAccessVpn()
        // D4 只对已有课表缓存的回访用户生效：首次登录/刚清缓存时，聚焦页自己的
        // 抓取会完成登录，再开一个后台登录 WebView 只会互相抢 Cookie。
        coldRefreshPending = creds != null && coldRefresh && repo.hasCachedTimetable()
        autoLoginReason = when {
            creds == null -> null
            coldRefresh -> null   // 等主界面画完再启动，见下面的 mainUiReady
            lastMode != null && lastMode != com.yh.schedule.core.WebConst.USE_VPN -> "mode"
            else -> null
        }
    }

    // 冷启动自动刷新必须让位给首屏：WebView 初始化会抢主线程，太早启动会把
    // 课表块推迟几秒。MainScaffold 画完第一帧并留出 4 秒后再开始后台刷新。
    LaunchedEffect(mainUiReady, coldRefreshPending, netReady) {
        if (mainUiReady && coldRefreshPending && !autoLoginDone && netReady) {
            coldRefreshPending = false
            autoLoginReason = "cold"
        }
    }

    // 首次进入只记基线；运行中网络切换导致的方式变化才触发静默重登
    LaunchedEffect(accessVersion, netReady) {
        // 首次判定完成前只记录、不比较，避免把启动时的默认值当成一次网络切换
        if (!netReady) return@LaunchedEffect
        val seen = seenAccessVersion
        if (seen == null) {
            seenAccessVersion = accessVersion
            return@LaunchedEffect
        }
        if (accessVersion > seen) {
            seenAccessVersion = accessVersion
            val creds = repo.savedCredentials()
            if (creds != null) {
                autoLoginCreds = creds
                autoLoginDone = false
                autoLoginReason = "mode"
            }
        }
    }

    // 登录覆盖层打开时，系统返回键只关掉登录页，而不是退出整个应用
    //（不拦的话系统返回会直接 finish Activity，用户从"服务→刷新登录状态"进来会被踢出 App）
    androidx.activity.compose.BackHandler(enabled = showRelogin) { showRelogin = false }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "main") {
            composable("main") { MainScaffold(
                repo = repo,
                openWeb = { url, title ->
                    nav.navigate("web?url=${android.net.Uri.encode(url)}&title=${android.net.Uri.encode(title)}")
                },
                openData = { url, title, kind ->
                    nav.navigate(
                        "data?url=${android.net.Uri.encode(url)}&title=${android.net.Uri.encode(title)}&kind=$kind"
                    )
                },
                openExam = { nav.navigate("exam") },
                openCourseSearch = { nav.navigate("courses") },
                onRelogin = { showRelogin = true },
                onUiReady = { mainUiReady = true },
            ) }
            composable(
                route = "web?url={url}&title={title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                ),
            ) { entry ->
                val url = entry.arguments?.getString("url").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                WebContainerScreen(
                    url = url,
                    title = title,
                    onBack = { nav.popBackStack() },
                    darkTheme = darkTheme,
                    forceWebViewDark = forceWebViewDark,
                    onRelogin = {
                        showRelogin = true
                        nav.popBackStack()
                    },
                )
            }
            composable(
                route = "data?url={url}&title={title}&kind={kind}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "generic" },
                ),
            ) { entry ->
                val url = entry.arguments?.getString("url").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                val kind = entry.arguments?.getString("kind").orEmpty().ifBlank { "generic" }
                DataPageScreen(
                    url = url,
                    title = title,
                    kind = kind,
                    onBack = { nav.popBackStack() },
                    onOpenRaw = { rawUrl, rawTitle ->
                        nav.navigate(
                            "web?url=${android.net.Uri.encode(rawUrl)}&title=${android.net.Uri.encode(rawTitle)}"
                        )
                    },
                )
            }

            // 考试安排：本机结构化页（WebView 抓表 → 落库 → 原生列表）
            composable("exam") {
                com.yh.schedule.ui.screens.ExamScreen(
                    repo = repo,
                    onBack = { nav.popBackStack() },
                    onOpenWeb = {
                        val url = com.yh.schedule.core.WebConst.JW_EXAM_URL
                        nav.navigate(
                            "web?url=${android.net.Uri.encode(url)}&title=${android.net.Uri.encode("考试安排")}"
                        )
                    },
                )
            }

            // 课程搜索：只在本地缓存课表里检索
            composable("courses") {
                com.yh.schedule.ui.screens.CourseSearchScreen(
                    repo = repo,
                    onBack = { nav.popBackStack() },
                )
            }
        }

        // 静默重登：冷启动刷新（D4）或网络接入方式切换时各触发一次，不阻塞界面。
        val creds = autoLoginCreds
        val reloginReason = autoLoginReason
        if (creds != null && reloginReason != null && !autoLoginDone) {
            com.yh.schedule.ui.screens.AutoLoginRefresher(
                account = creds.first,
                password = creds.second,
            ) { ok ->
                autoLoginDone = true
                autoLoginReason = null
                repo.setAutoRefreshFailed(!ok)
                if (ok) scope.launch { repo.markLoginAccessMode() }
                val modeLabel = if (com.yh.schedule.core.WebConst.USE_VPN) {
                    "校外 VPN"
                } else {
                    "校园网直连"
                }
                val msg = when {
                    ok && reloginReason == "mode" -> "已切换到$modeLabel，登录已刷新"
                    ok -> "自动刷新登录成功"
                    reloginReason == "mode" -> "已切换到$modeLabel，请重新登录"
                    else -> "自动刷新登录失败，请重新登录"
                }
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
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
                        // 与 AppRoot 一致：不清课表缓存，保留策略见 Repository.onWebLoginSuccess
                        repo.setAutoRefreshFailed(false)
                        showRelogin = false
                    },
                    onBack = { showRelogin = false },
                    // 有缓存时登录超时自动关掉登录页，回到上次同步的课表
                    onUseCache = if (hasCache) {
                        {
                            showRelogin = false
                            android.widget.Toast.makeText(
                                ctx,
                                "登录未完成，当前显示上次同步的课表",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    repo: Repository,
    openWeb: (String, String) -> Unit,
    openData: (String, String, String) -> Unit,
    openExam: () -> Unit = {},
    openCourseSearch: () -> Unit = {},
    onRelogin: () -> Unit = {},
    onUiReady: () -> Unit = {},
) {
    val tabs = listOf(
        Tab("课程表", Icons.Filled.CalendarMonth),
        Tab("聚焦", Icons.Filled.WbSunny),
        Tab("查询中心", Icons.Filled.Category),
        Tab("选项", Icons.Filled.Settings),
    )
    // 必须用 rememberSaveable：查询/网页详情是独立导航页，打开时 MainScaffold
    // 会离开组合；回到 main 时若用 remember，tab 会重置成 0（课程表），
    // 表现就是「从成绩查询返回却跳到了课表」。
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val demo by repo.demoMode.collectAsState(initial = true)
    val infiniteWheel by repo.infiniteWheel.collectAsState(initial = true)
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

    // 首屏优先：先画完第一帧，再留 4 秒给课表缓存加载，之后才允许冷启动自动刷新登录。
    // 自动登录 WebView 的初始化很重，和首屏抢主线程会让课表块晚几秒才出现。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(4000)
        onUiReady()
    }

    // 全屏覆盖层的内容（null = 关闭）。作为 AnimatedContent 的 targetState，
    // 这样关闭时旧内容会保留到退出动画播完，不会中途闪白。
    var overlay by remember { androidx.compose.runtime.mutableStateOf<Overlay?>(null) }
    // 增删改日程后自增，让课表重排（日程数据在 Room，课表页只按周查一次）
    var ttRefresh by remember { mutableIntStateOf(0) }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Scaffold(
            // 顶部状态栏高度已由 AppRoot 的根容器统一让出，Scaffold 不再重复加 inset
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                        onRelogin = onRelogin,
                    )
                    2 -> QueryCenterScreen(
                        repo = repo,
                        openWeb = openWeb,
                        openData = openData,
                        onOpenTimetable = { selected = 0 },
                        onOpenGradeStats = { overlay = Overlay.Grade },
                        onOpenExams = openExam,
                        onOpenCourseSearch = openCourseSearch,
                    )
                    3 -> OptionsScreen(
                        repo = repo,
                        demo = demo,
                        onRelogin = onRelogin,
                        onOpenLicenses = { overlay = Overlay.About },
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
                    enableInfiniteWheel = infiniteWheel,
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
                    enableInfiniteWheel = infiniteWheel,
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
                is Overlay.Grade -> GradeStatsScreen(
                    repo = repo,
                    onBack = { overlay = null },
                )
                is Overlay.About -> AboutScreen(onBack = { overlay = null })
            }
        }
    }

    // 返回键关闭覆盖层（放在最后，优先级高于页面自身的 BackHandler）
    androidx.activity.compose.BackHandler(enabled = overlay != null) { overlay = null }
}
