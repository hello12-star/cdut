package com.yh.schedule.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yh.schedule.core.WebConst
import org.json.JSONObject
import org.json.JSONTokener

/** 抓到的页面表格（行 × 单元格文本） */
private data class DataTable(val rows: List<List<String>>)

/** 抓到的页面内容：标题 + 表格 + 纯文本兜底 */
private data class DataContent(
    val title: String,
    val tables: List<DataTable>,
    val text: String,
    /** 页面确认过「这条查询没有数据」，用于成绩/考试的空态 */
    val empty: Boolean = false,
)

/**
 * 教务详情页的原生展示：隐藏 WebView 抓结构 → 用可读字号的原生表格渲染。
 *
 * 为什么不是直接放网页：强智页面为了塞进 PC 屏，字号极小，手机上几乎看不清；
 * 这里把页面里的 table 抽成结构化行，按列数决定用「键值列表」还是「横向滚动表格」。
 *
 * 覆盖：成绩查询、考试安排、培养方案、选课结果、学籍信息、评教、空教室、公告等。
 * 若某页结构特殊，右上角仍可一键切回原始网页。
 */
@Composable
fun DataPageScreen(
    url: String,
    title: String,
    kind: String = "generic",
    onBack: () -> Unit,
    onOpenRaw: (String, String) -> Unit,
) {
    var content by remember { mutableStateOf<DataContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var tail by remember { mutableStateOf("") }

    // 强智页面经常先出「正在加载」遮罩，再异步填表；抓取失败时重来一轮
    LaunchedEffect(tick, url) {
        content = null
        error = null
        loading = true
    }

    key(tick) {
        DataScraper(
            url = url,
            kind = kind,
            onJson = { raw ->
                val parsed = runCatching { parseContent(raw) }.getOrNull()
                if (parsed == null ||
                    (parsed.tables.isEmpty() && parsed.text.isBlank() && !parsed.empty)
                ) {
                    // 空页：多数是还在渲染，继续等下一轮
                } else {
                    content = parsed
                    loading = false
                }
            },
            onTail = { tail = it },
            onFail = { msg ->
                // WebView 内部已重试过；这里只负责展示原因
                error = msg
                loading = false
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
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
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { onOpenRaw(url, title) }) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = "打开原始网页")
            }
            IconButton(onClick = { tick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "重新读取")
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(30.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "正在读取教务页面…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("暂时读不到这个页面", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { tick++ }) { Text("重试") }
                }
            }
            content != null -> DataContentList(content!!, kind)
        }
    }
}

@Composable
private fun DataContentList(content: DataContent, kind: String) {
    if (content.tables.isEmpty()) {
        when {
            kind == "grade" || kind == "exam" -> EmptyDataCard(
                if (kind == "grade") "本学期暂无成绩数据" else "暂无考试安排",
                "教务系统返回的就是空列表；切换学期后可在原始网页里再查。",
            )
            content.text.isNotBlank() -> SelectionContainer {
                Text(
                    content.text,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            else -> EmptyDataCard("页面没有可展示的内容")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content.tables.forEachIndexed { ti, table ->
            val cols = table.rows.maxOfOrNull { it.size } ?: 0
            if (cols <= 2) {
                item(key = "kv-$ti") { KvCard(table) }
            } else {
                val (labels, rows) = splitHeader(table)
                if (rows.isEmpty()) {
                    item(key = "empty-$ti") { EmptyDataCard("没有查到数据") }
                } else {
                    itemsIndexed(rows, key = { ri, _ -> "row-$ti-$ri" }) { ri, row ->
                        RowCard(labels, row)
                    }
                }
            }
        }
        item {
            Text(
                "以上内容来自教务网页，以学校系统为准。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 宽表格按「一行一张卡片」排：列名做标签、单元格做值。
 * 手机上比横向滚动表格好读，也不会因为文字换行导致列错位。
 */
@Composable
private fun RowCard(labels: List<String>, row: List<String>) {
    val colCount = maxOf(labels.size, row.size)
    val hasValue = (0 until colCount).any { row.getOrNull(it)?.isNotBlank() == true }
    if (!hasValue) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            var first = true
            for (c in 0 until colCount) {
                val value = row.getOrNull(c)?.trim().orEmpty()
                if (value.isEmpty()) continue
                if (!first) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                }
                first = false
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                ) {
                    val label = labels.getOrNull(c)?.trim().orEmpty()
                    if (label.isNotEmpty()) {
                        Text(
                            label,
                            Modifier.weight(0.32f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            Modifier.weight(0.68f),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                        )
                    } else {
                        Text(
                            value,
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
}

/** 首行是不是表头：有至少两个非空、且不含纯数字 */
private fun splitHeader(table: DataTable): Pair<List<String>, List<List<String>>> {
    val first = table.rows.firstOrNull().orEmpty()
    val looksHeader = first.size >= 2 &&
            first.count { it.isNotBlank() } >= 2 &&
            first.none { it.trim().matches(Regex("^\\d+$")) }
    return if (looksHeader) first to table.rows.drop(1) else emptyList<String>() to table.rows
}

@Composable
private fun EmptyDataCard(title: String, subtitle: String = "") {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 两列表格通常是「字段名 / 值」，按键值列表排版最舒服 */
@Composable
private fun KvCard(table: DataTable) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            table.rows.forEachIndexed { i, row ->
                if (i > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                ) {
                    Text(
                        row.getOrNull(0).orEmpty(),
                        Modifier.weight(0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        row.getOrNull(1).orEmpty(),
                        Modifier.weight(0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DataScraper(
    url: String,
    kind: String,
    onJson: (String) -> Unit,
    onTail: (String) -> Unit,
    onFail: (String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode =
                    android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                var done = false
                var attempts = 0
                var clickedQuery = false

                fun attempt(view: WebView) {
                    if (done) return
                    attempts++
                    if (attempts > 8) {
                        done = true
                        onFail("页面长期没有内容，可能该入口暂时没开放或加载太慢")
                        return
                    }
                    onTail("正在解析（第 $attempts 次）")
                    view.evaluateJavascript(buildExtractJs(kind)) { raw ->
                        if (done) return@evaluateJavascript
                        val json = decodeJs(raw)
                        if (json.isBlank() || json == "null") {
                            view.postDelayed({ attempt(view) }, 1500L)
                            return@evaluateJavascript
                        }
                        val obj = runCatching { JSONObject(json) }.getOrNull()
                        val text = obj?.optString("text").orEmpty()
                        val tableCount = obj?.optJSONArray("tables")?.length() ?: 0
                        val emptyFlag = obj?.optBoolean("empty") ?: false
                        when {
                            text.contains("您尚未登录") ||
                                    text.contains("\"flag1\":2") ||
                                    text.contains("msgContent") -> {
                                done = true
                                onFail("登录已过期，请到选项页重新登录后再试")
                            }
                            tableCount == 0 && text.isBlank() && !emptyFlag ->
                                view.postDelayed({ attempt(view) }, 1500L)
                            else -> {
                                done = true
                                onJson(json)
                            }
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (view == null || done) return
                        view.postDelayed({
                            if (done) return@postDelayed
                            if (!clickedQuery) {
                                // 很多强智页面首屏只是筛选表单，要点「查询」才出结果
                                clickedQuery = true
                                view.evaluateJavascript(AUTO_QUERY_JS) { raw ->
                                    android.util.Log.i("DataScrape", "autoQuery: ${decodeJs(raw)}")
                                    view.postDelayed({ attempt(view) }, 2200L)
                                }
                            } else {
                                attempt(view)
                            }
                        }, 1200L)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true && !done) {
                            done = true
                            onFail("页面加载失败：${error?.description ?: "网络错误"}")
                        }
                    }
                }
                loadUrl(WebConst.proxify(url))
            }
        },
        onRelease = { web ->
            // 详情页每打开一次都会建一个隐藏 WebView；离开时必须销毁，
            // 否则反复查成绩/公告会积攒一堆 WebView 进程
            runCatching {
                web.stopLoading()
                web.destroy()
            }
        },
    )
}

/** evaluateJavascript 的返回值是 JSON 字符串字面量 */
private fun decodeJs(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        (JSONTokener(raw).nextValue() as? String).orEmpty()
    }.getOrDefault("")
}

private fun parseContent(raw: String): DataContent? {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val tables = mutableListOf<DataTable>()
    val arr = obj.optJSONArray("tables")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val rowsArr = arr.optJSONArray(i) ?: continue
            val rows = mutableListOf<List<String>>()
            for (r in 0 until rowsArr.length()) {
                val rowArr = rowsArr.optJSONArray(r) ?: continue
                val cells = mutableListOf<String>()
                for (c in 0 until rowArr.length()) {
                    cells.add(rowArr.optString(c).trim())
                }
                if (cells.isNotEmpty()) rows.add(cells)
            }
            if (rows.size >= 2) tables.add(DataTable(rows))
        }
    }
    return DataContent(
        title = obj.optString("title"),
        tables = tables,
        text = obj.optString("text").trim(),
        empty = obj.optBoolean("empty"),
    )
}

/** 首屏只有筛选表单时，自动点一次「查询 / 搜索」 */
private const val AUTO_QUERY_JS = """
(function() {
  function norm(s) { return (s || '').replace(/\s+/g, ''); }
  function tryDoc(d) {
    if (!d) return null;
    var els = d.querySelectorAll('button, input[type=button], input[type=submit], a');
    for (var i = 0; i < els.length; i++) {
      var t = norm((els[i].innerText || '') + ' ' + (els[i].value || ''));
      if ((t.indexOf('查询') >= 0 || t.indexOf('搜索') >= 0) && t.length <= 8) {
        els[i].click();
        return 'CLICKED:' + t;
      }
    }
    return null;
  }
  var r = tryDoc(document); if (r) return r;
  var fs = document.querySelectorAll('iframe, frame');
  for (var i = 0; i < fs.length; i++) {
    try { var rr = tryDoc(fs[i].contentDocument); if (rr) return rr; } catch (e) {}
  }
  return 'NO_BUTTON';
})()
"""

private fun buildExtractJs(kind: String): String {
    val safeKind = if (kind == "grade" || kind == "exam") kind else "generic"
    return EXTRACT_JS_TEMPLATE.replace("__KIND__", safeKind)
}

/**
 * 在 document + 两层 iframe/frame 中选内容最多的那个文档，
 * 再把 table 抽成二维文本。强智的 frameset 页面（如成绩查询 cjcx_frm）
 * 正文在子 frame 里，主文档本身是空的。
 *
 * 成绩/考试页会先找带表头的目标表：只剩表头没有数据时返回 `empty=true`，
 * 避免把整页筛选表单的文字当成内容糊在界面上。
 */
private const val EXTRACT_JS_TEMPLATE = """
(function() {
  var KIND = '__KIND__';
  function textOf(d) {
    try { return d && d.body ? (d.body.innerText || '') : ''; } catch (e) { return ''; }
  }
  function score(d) {
    try {
      var n = textOf(d).length;
      var ts = d.querySelectorAll('table');
      for (var i = 0; i < ts.length && i < 20; i++) {
        n += ts[i].querySelectorAll('td, th').length * 2;
      }
      return n;
    } catch (e) { return 0; }
  }
  var best = document, bestScore = score(document);
  var frames = document.querySelectorAll('iframe, frame');
  for (var f = 0; f < frames.length; f++) {
    try {
      var d = frames[f].contentDocument;
      if (!d) continue;
      var s = score(d);
      if (s > bestScore) { bestScore = s; best = d; }
      // 再往下一层，兼容 iframe 套 iframe
      var inner = d.querySelectorAll('iframe, frame');
      for (var g = 0; g < inner.length; g++) {
        try {
          var dd = inner[g].contentDocument;
          if (!dd) continue;
          var ss = score(dd);
          if (ss > bestScore) { bestScore = ss; best = dd; }
        } catch (e) {}
      }
    } catch (e) {}
  }
  function clean(s) {
    return (s || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
  }
  function rowsOf(t) {
    var rows = t.querySelectorAll('tr');
    var out = [], maxCols = 0;
    for (var r = 0; r < rows.length && r < 200; r++) {
      var cells = rows[r].querySelectorAll('td, th');
      var row = [];
      for (var c = 0; c < cells.length && c < 12; c++) row.push(clean(cells[c].innerText));
      if (row.length === 0) continue;
      if (row.join('').replace(/\s/g, '').length === 0) continue;
      if (row.length > maxCols) maxCols = row.length;
      out.push(row);
    }
    return { rows: out, cols: maxCols };
  }
  function findTable(required, minCols) {
    var ts = best.querySelectorAll('table');
    for (var i = 0; i < ts.length; i++) {
      var txt = clean(ts[i].innerText);
      var ok = true;
      for (var k = 0; k < required.length; k++) {
        if (txt.indexOf(required[k]) < 0) { ok = false; break; }
      }
      if (!ok) continue;
      var data = rowsOf(ts[i]);
      if (data.cols >= minCols && data.rows.length >= 1) return data;
    }
    return null;
  }
  var tables = [], empty = false;
  if (KIND === 'grade' || KIND === 'exam') {
    var found = KIND === 'grade'
      ? findTable(['课程名称', '成绩'], 5)
      : findTable(['考场'], 4);
    if (found != null) {
      // 有表头没数据 → 明确空态，不要把筛选表单当正文
      if (found.rows.length > 1) tables.push(found.rows);
      else empty = true;
    }
  }
  if (tables.length === 0 && !empty) {
    var signatures = [];
    var ts = best.querySelectorAll('table');
    for (var i = 0; i < ts.length && tables.length < 6; i++) {
      var data = rowsOf(ts[i]);
      if (data.rows.length < 2 || data.cols < 2) continue;
      var sig = data.rows.length + '|' + data.rows[0].join('|').slice(0, 120);
      var dup = false;
      for (var k = 0; k < signatures.length; k++) if (signatures[k] === sig) dup = true;
      if (dup) continue;
      signatures.push(sig);
      tables.push(data.rows);
    }
  }
  // 纯文本兜底：按文本节点收集，跳过 select/option/button 等表单控件，
  // 避免把整页下拉选项糊成一行
  var BLOCK = {TR:1, TD:1, TH:1, DIV:1, P:1, BR:1, LI:1, H1:1, H2:1, H3:1, H4:1, TABLE:1, SECTION:1, UL:1, OL:1};
  function visibleText(d) {
    var out = [];
    function walk(n) {
      if (n.nodeType === 3) { out.push(n.nodeValue || ''); return; }
      if (n.nodeType !== 1) return;
      var tag = (n.tagName || '').toUpperCase();
      if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'SELECT' || tag === 'OPTION' ||
          tag === 'BUTTON' || tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'LINK') return;
      var cs = null;
      try { cs = d.defaultView.getComputedStyle(n); } catch (e) {}
      if (cs && (cs.display === 'none' || cs.visibility === 'hidden')) return;
      var block = BLOCK[tag] === 1;
      if (block) out.push('\n');
      for (var i = 0; i < n.childNodes.length; i++) walk(n.childNodes[i]);
      if (block) out.push('\n');
    }
    walk(d.body || d.documentElement);
    return out.join('');
  }
  function tidy(s) {
    var lines = s.split('\n'), out = [];
    for (var i = 0; i < lines.length; i++) {
      var line = clean(lines[i]);
      if (line) out.push(line);
      if (out.length >= 120) break;
    }
    return out.join('\n').slice(0, 6000);
  }
  var text = '';
  if (tables.length === 0 && !empty) {
    text = tidy(visibleText(best));
  }
  return JSON.stringify({
    title: (best.title || document.title || ''),
    tables: tables,
    text: text,
    empty: empty
  });
})()
"""
