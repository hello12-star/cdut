package com.yh.schedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yh.schedule.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** JS 字符串转义 */
private fun String.jsEscape(): String =
    replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

/**
 * CAS 登录页地址（带 service，登录成功后回到教务 SSO）。
 * service 的 targetUrl = base64("http://jw.cdut.edu.cn/Logon.do?method=logonSSOcdlgdx")
 */
/**
 * 浏览器（隐藏 WebView）的起始地址。
 * 具体走教务 CAS 还是 VPN 的 CAS，由 [com.yh.schedule.core.WebConst.USE_VPN] 决定。
 */
private val CAS_LOGIN_URL: String
    get() = com.yh.schedule.core.WebConst.CAS_LOGIN_URL

/**
 * 登录页（**只有这一个原生界面，官网登录页不出现**）。
 *
 * 实现方式：CAS 登录页放在 1dp 的隐藏 WebView 里后台跑。
 * 1. 用户在本页输学号/密码 → 点「登录」
 * 2. 后台注入账密 + **自动点击页面「登录」按钮**
 * 3. 监听跳转判断结果：成功收 Cookie；失败把原因显示在本页
 * 4. 只有 CAS 强制要验证码时，才把后台页面展开让用户处理
 *
 * 必须在 WebView 里跑的原因：CAS 页有 `execution` token 与瑞数防护，
 * 密码要用页面脚本 `encrypt.encrypt(pwd)` 生成 `__RSA__`，纯 HTTP 提交必失败。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CasLoginScreen(
    repo: Repository,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在准备登录环境…") }
    var error by remember { mutableStateOf<String?>(null) }
    var showWebView by remember { mutableStateOf(false) }   // 仅在需要验证码时展开
    var pageReady by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }
    // 已点过「登录」：超时把 busy 置 false 之后，WebView 仍可能稍后成功，
    // 用这个标记让成功判定不依赖 busy，避免"其实登上了但 App 不认"
    var loginSubmitted by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var retryCount = 0
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // 自动填充上次保存的账号密码（对齐原版 saveInput）：用户可以直接点登录，
    // 换账号也只需在原值上改，不用从空开始敲
    LaunchedEffect(Unit) {
        repo.savedCredentials()?.let { (a, p) ->
            if (account.isBlank()) account = a
            if (password.isBlank()) password = p
        }
    }

    // 兜底：CAS 页面有可能因为网络/跳转链变化一直不回调 onPageFinished，
    // 那登录按钮就会永远灰着、怎么点都没反应。8 秒后强制放开，让用户至少能试。
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8000L)
        if (!pageReady) {
            pageReady = true
            if (!busy) status = "环境准备较慢，可直接点「登录」重试"
        }
    }

    // 展开后台页面（需要验证码）时收起键盘，避免遮挡
    LaunchedEffect(showWebView) {
        if (showWebView) keyboard?.hide()
    }

    // 登录超时保护。
    // 走 VPN 后链路变长（vpn.cdut.edu.cn → CAS → 回跳 VPN → 建会话），
    // 25 秒经常不够，放宽到 45 秒再判失败。
    LaunchedEffect(busy) {
        if (busy) {
            delay(45_000)
            if (busy) {
                busy = false
                status = "登录未完成"   // 同步重置，避免残留在"正在登录…"
                error = "登录超时，请检查网络后重试（若反复失败，可点到「服务」页重新登录）"
            }
        }
    }

    /** 提交后仍停在登录页 → 判断失败原因 */
    fun diagnose(view: WebView) {
        val js = """
            (function(){
              var t = document.body ? (document.body.innerText || '') : '';
              var all = document.querySelectorAll('#captcha');
              var capVisible = false;
              for (var i = 0; i < all.length; i++) {
                if (all[i].type !== 'hidden' && all[i].offsetParent !== null) capVisible = true;
              }
              if (capVisible) return 'CAPTCHA';
              if (t.indexOf('密码错误') >= 0 || t.indexOf('用户名或密码') >= 0) return 'BAD';
              if (t.indexOf('锁定') >= 0 || t.indexOf('冻结') >= 0) return 'LOCKED';
              if (t.indexOf('验证码') >= 0) return 'CAPTCHA';
              return 'WAIT';
            })()
        """.trimIndent()
        view.evaluateJavascript(js) { raw ->
            when (raw?.trim()?.trim('"')) {
                "CAPTCHA" -> {
                    busy = false
                    showWebView = true
                    status = "需要输入验证码，请在下方完成后会自动继续"
                }
                "BAD" -> {
                    busy = false
                    error = "账号或密码错误"
                    status = "请修改后重试"
                }
                "LOCKED" -> {
                    busy = false
                    error = "账号被锁定或冻结，请稍后重试"
                }
                else -> { /* 还在跳转 */ }
            }
        }
    }

    /** 注入账密并自动点「登录」 */
    fun submit(view: WebView) {
        busy = true
        loginSubmitted = true
        // 重新发起时清掉上一轮的红字
        error = null
        status = "正在登录…"
        val js = """
            (function(){
              function setVal(id, v){
                var els = document.querySelectorAll('#' + id);
                for (var i = 0; i < els.length; i++) {
                  var el = els[i];
                  if (el.type === 'hidden' || el.offsetParent === null) continue;
                  el.value = v;
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true}));
                }
              }
              setVal('username', '${account.jsEscape()}');
              setVal('password', '${password.jsEscape()}');
              setTimeout(function(){
                var btns = document.querySelectorAll('button, .el-button, input[type=button]');
                for (var i = 0; i < btns.length; i++) {
                  var t = ((btns[i].innerText || btns[i].value || '') + '').replace(/\s/g, '');
                  if (t.indexOf('登录') >= 0 && t.length <= 4) { btns[i].click(); return; }
                }
              }, 400);
              return 'submitted';
            })()
        """.trimIndent()
        view.evaluateJavascript(js, null)
        // CAS 是单页应用：登录失败时页面不刷新，onPageFinished 不会触发，
        // 因此主动轮询页面状态（成功跳转会被 onPageFinished 捕获并把 busy 置 false）
        scope.launch {
            repeat(12) {
                delay(2000)
                if (!busy) return@launch
                diagnose(view)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- 顶栏 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("登录", style = MaterialTheme.typography.titleMedium)
        }

        // ---- 原生登录表单（唯一可见界面）----
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (showWebView) Modifier else Modifier.weight(1f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("砚湖日程", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "成都理工大学 · 教务账号登录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = account,
                        onValueChange = { account = it; error = null },
                        label = { Text("学号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = remember, onCheckedChange = { remember = it })
                        Text("记住账号密码", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { webRef?.let { submit(it) } },
                        enabled = !busy && pageReady &&
                                account.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("登录中…")
                        } else {
                            Text("登录")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // 只显示一条：有错误就只显示错误，否则显示进度文案。
            // 之前 status 无条件渲染 + error 再渲染一行，会出现
            // "正在登录…" 和 "登录超时" 同时挂在界面上的矛盾状态。
            Text(
                text = error ?: status,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(10.dp))
            Text(
                "说明：登录由后台完成，官网页面不会打扰你。勾选「记住账号密码」后，" +
                        "账号密码会保存在本机（应用私有目录），下次自动填充。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        // ---- 后台 WebView：平时裁成 1dp（内部仍是正常尺寸，保证网络/渲染正常）；需要验证码时展开 ----
        Box(
            Modifier.then(
                if (showWebView) Modifier
                    .weight(1f)
                    .fillMaxWidth()
                else Modifier
                    .size(1.dp)
                    .clipToBounds()
            ),
        ) {
            AndroidView(
                // requiredSize 会忽略父容器约束，保证 WebView 内部按真实尺寸布局
                // （用 size() 会被父级 1dp 约束压扁，导致 Chromium 不做网络请求）
                modifier = if (showWebView) Modifier.fillMaxSize()
                else Modifier.requiredSize(360.dp, 640.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        // VPN 登录页 / 代理页会 http↔https 混用，默认策略会拦掉子资源
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        CookieManager.getInstance().setAcceptCookie(true)
                        // 关键：登录链是跨域的（vpn.cdut.edu.cn → cas.paas.cdut.edu.cn → 回跳），
                        // CAS 的 TGC 属于第三方 cookie。WebView 默认拒绝，
                        // 表现就是"认证过了但说没有可信凭证"。必须显式打开。
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webRef = this
                        // 接管 target="_blank" / window.open：
                        // 门户里的「教务管理系统」入口常是新窗口链接，WebView 默认不处理，
                        // 点了等于没点，表现为"停着不动 / 未取到会话凭据"
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message,
                            ): Boolean {
                                val holder = WebView(view.context)
                                holder.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView,
                                        request: android.webkit.WebResourceRequest,
                                    ): Boolean {
                                        view.loadUrl(request.url.toString())
                                        return true
                                    }
                                }
                                (resultMsg.obj as WebView.WebViewTransport).webView = holder
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val u = url.orEmpty()
                                when {
                                    u.contains("cas.paas.cdut.edu.cn/cas/login") -> {
                                        if (!pageReady) {
                                            pageReady = true
                                            if (!busy) status = "输入学号密码后点「登录」"
                                        }
                                        if (busy) diagnose(view!!)
                                    }
                                    u.contains("sso/login") ||
                                            u.contains("Logon.do") ||
                                            u.contains("LoginToXk") -> {
                                        status = "正在建立教务会话…"
                                    }
                                    // 四种都算登录成功：
                                    // ① 智慧校园门户（VPN 登录后的落地页，最常见的成功标志）
                                    // ② VPN 门户 ③ 教务 VPN 代理域 ④ 教务内网直连（历史路径）
                                    u.contains("bsdt-cdut") ||
                                            u.contains("vpn.cdut.edu.cn/por") ||
                                            u.contains(com.yh.schedule.core.WebConst.VPN_PROXY_HOST) ||
                                            u.contains("jw.cdut.edu.cn") -> {
                                        if (!handled && (busy || loginSubmitted || showWebView)) {                                            handled = true
                                            loginSubmitted = false
                                            busy = false
                                            status = "登录成功，正在保存…"
                                            scope.launch {
                                                delay(2000)
                                                val cm = CookieManager.getInstance()
                                                val casCookie = cm.getCookie("https://cas.paas.cdut.edu.cn")
                                                val jwCookie = cm.getCookie("https://jw.cdut.edu.cn")
                                                // VPN 模式下会话 cookie 落在 vpn.cdut.edu.cn 和门户域上，
                                                // 之前只读 cas / jw 两个域，导致"其实登上了"被判成
                                                // "未取到会话凭据"——这是 VPN 链路一直失败的真正原因之一。
                                                val vpnCookie = cm.getCookie("https://vpn.cdut.edu.cn")
                                                val portalCookie = cm.getCookie(
                                                    com.yh.schedule.core.WebConst.PORTAL_URL
                                                )

                                                val directMerged = listOfNotNull(casCookie, jwCookie)
                                                    .joinToString("; ")
                                                val merged = if (com.yh.schedule.core.WebConst.USE_VPN) {
                                                    listOfNotNull(
                                                        directMerged.takeIf { it.isNotBlank() },
                                                        vpnCookie,
                                                        portalCookie,
                                                    ).joinToString("; ")
                                                } else {
                                                    directMerged
                                                }

                                                if (merged.isNotBlank()) {
                                                    repo.onWebLoginSuccess(
                                                        merged,
                                                        account = account,
                                                        password = password,
                                                        remember = remember,
                                                    )
                                                    onSuccess()
                                                } else {
                                                    handled = false
                                                    busy = false
                                                    error = "未取到会话凭据：cas/jw" +
                                                            (if (com.yh.schedule.core.WebConst.USE_VPN) "/vpn/门户" else "") +
                                                            " 四个域的 cookie 均为空，请重试"
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                // 只处理主文档失败；DNS 抖动很常见，重试几次（对齐抓取器的重试策略）
                                if (request?.isForMainFrame != true) return
                                // 只重试 2 次：VPN 的 ticket 是一次性的，
                                // 反复重新加载会把 ticket 消费掉，越重试越拿不到凭据
                                if (retryCount < 2) {
                                    retryCount++
                                    status = "网络不稳定，正在重试（$retryCount/2）…"
                                    view?.postDelayed({
                                        view.loadUrl(CAS_LOGIN_URL)
                                    }, 1500)
                                } else {
                                    busy = false
                                    // 重试耗尽也要放开按钮，否则页面停在「正在准备登录环境…」
                                    // 且「登录」永远是灰的，用户完全无法操作
                                    pageReady = true
                                    status = "网络异常：${error?.description ?: "加载失败"}，请返回重试"
                                }
                            }
                        }
                        loadUrl(CAS_LOGIN_URL)
                    }
                },
            )
        }
    }
}
