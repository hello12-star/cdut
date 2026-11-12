package com.yh.schedule.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yh.schedule.core.Exam
import com.yh.schedule.core.NetworkAccess
import com.yh.schedule.core.QzExamParser
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.WebConst
import com.yh.schedule.data.Repository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 考试安排（0.2.0 结构化版）。
 *
 * 数据来源只有一条：隐藏 WebView 打开教务的考试查询页 → 页面内把结果表抽成二维文本
 * → [QzExamParser] 按表头映射成课程/日期/时间/考场 → 落 Room → 这里按日期分组展示。
 * 教务没排考试时页面只有表头，这时给空态，不把筛选表单当成内容。
 */
@Composable
fun ExamScreen(
    repo: Repository,
    onBack: () -> Unit = {},
    /** 打开原始教务页（排查用，也方便看教务原表） */
    onOpenWeb: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    suspend fun loadLocal() {
        exams = repo.exams()
        loaded = true
    }

    LaunchedEffect(Unit) {
        loadLocal()
        // 没缓存且已登录 → 自动抓一次，省得用户还要手动点刷新
        if (exams.isEmpty() && repo.loggedIn.first()) {
            NetworkAccess.ready.first { it }
            checking = true
            tick++
        }
    }

    if (checking) {
        ExamScraper(
            tick = tick,
            onRows = { rows ->
                checking = false
                val parsed = QzExamParser.parse(rows)
                // 强智没排考时会在结果表里塞一行「未查询到数据」，这不算解析失败
                val dataRows = rows.drop(1).filterNot { r ->
                    val t = r.joinToString("").replace(Regex("\\s"), "")
                    t.contains("未查询到") || t.contains("暂无") || t.contains("没有数据")
                }
                if (parsed.isEmpty()) {
                    android.util.Log.i(
                        "ExamScrape",
                        "unparsed dataRows=" + dataRows.joinToString(" | ") { it.joinToString("/") },
                    )
                    error = if (dataRows.isEmpty()) null
                    else "教务返回了 ${dataRows.size} 行，但没能识别出日期/考场，请点外链查看原表"
                } else {
                    error = null
                    scope.launch {
                        repo.cacheExams(parsed)
                        loadLocal()
                    }
                }
            },
            onFail = { msg ->
                checking = false
                error = msg
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("考试安排", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenWeb) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "打开教务原页面")
            }
            IconButton(
                onClick = {
                    error = null
                    checking = true
                    tick++
                },
                enabled = !checking,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }

        if (checking) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "正在读取教务考试安排…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        when {
            error != null -> Card(
                Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("没读到考试安排", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        error = null
                        checking = true
                        tick++
                    }) { Text("重试") }
                }
            }

            loaded && exams.isEmpty() && !checking -> Card(
                Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("本学期暂无考试安排", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "教务系统返回的就是空表；排考公布后点右上角刷新即可。" +
                                "也可以点右上角的外链按钮直接看教务原页面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val grouped = exams.groupBy { it.date }.toSortedMap()
                grouped.forEach { (date, list) ->
                    item(key = "h-$date") {
                        Text(
                            "${SchoolCalendar.chineseDay(date.dayOfWeek)} · ${date.monthValue}月${date.dayOfMonth}日" +
                                    if (date.isBefore(LocalDate.now())) "（已结束）" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(list, key = { it.course + it.timeRange + it.date.toString() + it.room }) { exam ->
                        ExamRow(exam)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamRow(exam: Exam) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(exam.course, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        exam.timeRange.takeIf { it.isNotBlank() },
                        exam.room.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { "时间/考场待教务公布" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 隐藏 WebView：打开教务考试页，把结果表抽成二维文本数组。
 * 只认同时含「考场」或「考试时间」的表格，避免抓到筛选表单。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ExamScraper(
    tick: Int,
    onRows: (List<List<String>>) -> Unit,
    onFail: (String) -> Unit,
) {
    androidx.compose.runtime.key(tick) {
        AndroidView(
            modifier = Modifier.size(1.dp),
            factory = { ctx ->
                val view = android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(this, true)

                    var finished = false
                    var attempts = 0
                    fun fail(msg: String) {
                        if (finished) return
                        finished = true
                        onFail(msg)
                    }

                    fun grab() {
                        if (finished) return
                        evaluateJavascript(EXAM_JS) { raw ->
                            if (finished) return@evaluateJavascript
                            val payload = decodeJsString(raw)
                            val json = runCatching { org.json.JSONObject(payload) }.getOrNull()
                            if (json == null) {
                                fail("页面内容为空，请重试")
                                return@evaluateJavascript
                            }
                            if (json.optBoolean("notLogin")) {
                                fail("登录已过期（教务返回未登录）")
                                return@evaluateJavascript
                            }
                            if (json.optBoolean("ok")) {
                                val arr = json.optJSONArray("rows")
                                val rows = mutableListOf<List<String>>()
                                if (arr != null) {
                                    for (i in 0 until arr.length()) {
                                        val r = arr.optJSONArray(i) ?: continue
                                        rows += (0 until r.length()).map { r.optString(it) }
                                    }
                                }
                                finished = true
                                onRows(rows)
                            } else if (attempts++ < 15) {
                                android.util.Log.i(
                                    "ExamScrape",
                                    "attempt=$attempts body=" + json.optString("body"),
                                )
                                // 页面还在跑查询/渲染，1.5 秒后再看
                                postDelayed({ grab() }, 1500)
                            } else {
                                fail("页面长时间未出现考试表，请重试")
                            }
                        }
                    }

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(v: android.webkit.WebView?, url: String?) {
                            postDelayed({ grab() }, 2500)
                        }
                    }
                    loadUrl(WebConst.JW_EXAM_URL)
                }
                view
            },
            onRelease = { v ->
                runCatching {
                    v.stopLoading()
                    v.destroy()
                }
            },
        )
    }
}

private const val EXAM_JS = """
(function(){
  function clean(s){ return (s||'').replace(/\u00a0/g,' ').replace(/\s+/g,' ').trim(); }
  // 强智的考试结果在 frameset/iframe 里，最多往下找两层，逐个文档扫描
  function docs(){
    var out = [document];
    var fs = document.querySelectorAll('iframe, frame');
    for (var i = 0; i < fs.length; i++) {
      try {
        var d = fs[i].contentDocument; if (!d) continue;
        out.push(d);
        var inner = d.querySelectorAll('iframe, frame');
        for (var j = 0; j < inner.length; j++) {
          try { var dd = inner[j].contentDocument; if (dd) out.push(dd); } catch(e) {}
        }
      } catch(e) {}
    }
    return out;
  }
  var all = docs(), loginText = '';
  for (var k = 0; k < all.length; k++) {
    try { loginText += all[k].body ? (all[k].body.innerText || '') : ''; } catch(e) {}
    var tables = all[k].querySelectorAll('table');
    for (var i = 0; i < tables.length; i++) {
      var t = tables[i];
      var probe = clean(t.innerText);
      if (probe.indexOf('考场') < 0 && probe.indexOf('考试时间') < 0 && probe.indexOf('考试地点') < 0) continue;
      var trs = t.querySelectorAll('tr');
      var rows = [];
      for (var r = 0; r < trs.length && r < 100; r++) {
        var cells = trs[r].querySelectorAll('td, th');
        var row = [];
        for (var c = 0; c < cells.length && c < 10; c++) row.push(clean(cells[c].innerText));
        if (row.join('').length === 0) continue;
        rows.push(row);
      }
      if (rows.length >= 1) return JSON.stringify({ok:true, rows:rows});
    }
  }
  if (loginText.indexOf('flag1') >= 0 || loginText.indexOf('msgContent') >= 0) {
    return JSON.stringify({ok:false, notLogin:true, rows:[]});
  }
  return JSON.stringify({ok:false, rows:[], body:clean(loginText.slice(0,120))});
})()
"""

/** evaluateJavascript 回调是 JSON 字符串字面量，需要还原成原始文本 */
private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching { org.json.JSONTokener(raw).nextValue() as String }.getOrDefault("")
}
