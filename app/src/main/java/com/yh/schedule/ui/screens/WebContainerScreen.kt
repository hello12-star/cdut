package com.yh.schedule.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONTokener

/**
 * WebView 容器（对应原 App 的 H5 聚合功能面）。
 *
 * 关键配置（联调实测结论，2026-10-18）：
 * - **统一使用系统默认 UA**：与登录 WebView 保持一致，避免瑞数把登录与抓取判成两个客户端。
 *   成理强智菜单页（jsxsd/assets_v1）在移动 UA 下会因页面自身 JS bug 白屏，
 *   但各功能页是独立 HTML，可正常渲染；不要再为此单独设置 PC UA。
 * - useWideViewPort + loadWithOverviewMode：PC 页面整体缩放显示。
 * - 与统一认证共享 CookieManager，登录后容器自动携带会话。
 * - 深色模式下可按偏好强制网页深色（仅当用户打开开关且应用处于深色主题时）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebContainerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    /** 应用当前是否处于深色主题 */
    darkTheme: Boolean = false,
    /** 用户是否在选项页打开了「网页强制深色」 */
    forceWebViewDark: Boolean = false,
    /** 会话失效时拉起统一认证登录 */
    onRelogin: () -> Unit = {},
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var sessionExpired by remember { mutableStateOf(false) }
    var blankPage by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var pageToken by remember { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        // 不设置 UA：与登录 WebView 保持一致（瑞数指纹）
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.applyForceDark(darkTheme && forceWebViewDark)
                        CookieManager.getInstance().setAcceptCookie(true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = false // 容器内导航

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                pageToken += 1
                                sessionExpired = false
                                blankPage = false
                                loadError = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view ?: return
                                val token = pageToken

                                // 0) 网页强制深色：系统 API 在部分 WebView 版本上不生效，
                                //    因此按 HFUT 源码的做法注入一段深色 CSS。
                                if (darkTheme && forceWebViewDark) {
                                    view.evaluateJavascript(FORCE_DARK_JS, null)
                                }

                                // 1) 先判断是否是未登录 JSON（这一步与正文渲染无关）
                                view.evaluateJavascript(SESSION_CHECK_JS) { raw ->
                                    val text = decodeJsResult(raw).trim()
                                    if (text.contains("您尚未登录") ||
                                        text.contains("\"flag\":\"2\"") ||
                                        text.contains("\"flag1\":2")
                                    ) {
                                        sessionExpired = true
                                    }
                                }

                                // 2) 空白页检测要延迟重试：强智部分页面在 onPageFinished
                                //    之后才由 JS 填表，立刻读 innerText 会误判成白屏。
                                fun checkBlank(attempt: Int) {
                                    view.postDelayed({
                                        if (token != pageToken || loadError) return@postDelayed
                                        view.evaluateJavascript(SESSION_CHECK_JS) { raw ->
                                            val text = decodeJsResult(raw).trim()
                                            when {
                                                text.isNotBlank() -> blankPage = false
                                                attempt >= 2 -> blankPage = true
                                                else -> checkBlank(attempt + 1)
                                            }
                                        }
                                    }, 1500L)
                                }
                                checkBlank(0)
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?,
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (request?.isForMainFrame == true) loadError = true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) loadError = true
                            }
                        }
                        loadUrl(url)
                    }
                },
            )

            if ((blankPage || loadError) && !sessionExpired) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "该入口暂未开放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            "页面为空或加载失败，功能待后续版本补齐。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
                        )
                    }
                }
            }
        }
    }

    if (sessionExpired) {
        AlertDialog(
            onDismissRequest = { sessionExpired = false },
            title = { Text("登录已过期") },
            text = { Text("教务会话已失效，请重新登录后再试。") },
            confirmButton = {
                TextButton(onClick = {
                    sessionExpired = false
                    onRelogin()
                }) { Text("重新登录") }
            },
            dismissButton = {
                TextButton(onClick = {
                    sessionExpired = false
                    onBack()
                }) { Text("返回") }
            },
        )
    }
}

/**
 * 网页强制深色 CSS（对齐 HFUT `WebViewFactory.FORCE_DARK_CSS`）。
 * 系统级 forceDark/算法深色在部分页面不生效，注入 CSS 更可靠。
 */
private const val FORCE_DARK_JS = """
(function() {
  var css = 'html, body { background-color: #121212 !important; color: #eeeeee !important; }' +
            '* { background-color: transparent !important; color: #eeeeee !important; border-color: #333333 !important; }' +
            'a { color: #8ab4f8 !important; }';
  var style = document.createElement('style');
  style.type = 'text/css';
  style.appendChild(document.createTextNode(css));
  document.head.appendChild(style);
})();
"""

/** 读取页面文本，用于识别未登录 JSON 与空白页 */
private const val SESSION_CHECK_JS = """
(function() {
  try {
    return document.body ? (document.body.innerText || '') : '';
  } catch (e) { return ''; }
})()
"""

/** evaluateJavascript 的回调是 JSON 字符串字面量，先反序列化再比较 */
private fun decodeJsResult(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        (JSONTokener(raw).nextValue() as? String) ?: raw
    }.getOrDefault(raw)
}

/** 强制网页深色：API 33+ 用算法深色，29–32 用旧的 FORCE_DARK */
private fun WebSettings.applyForceDark(enabled: Boolean) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            setAlgorithmicDarkeningAllowed(enabled)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            @Suppress("DEPRECATION")
            forceDark = if (enabled) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
    }
}
