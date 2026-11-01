package com.yh.schedule.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yh.schedule.data.Repository
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** 查询中心的一个入口。Web 类打开官方页面，本地类留在应用内 */
private sealed interface QueryAction {
    data class Web(val url: String) : QueryAction

    /** 本机聚合的成绩统计页（真实数据尚未接入时为空态） */
    data object LocalGrades : QueryAction
}

private data class QueryEntry(
    val title: String,
    val desc: String,
    /** 额外的检索词，让「学分」「GPA」这类词也能搜到 */
    val keywords: String,
    val icon: ImageVector,
    val action: QueryAction,
)

private const val JW = "https://jw.cdut.edu.cn/jsxsd"

/**
 * 查询中心的入口清单。
 *
 * 只收录**已在真机/模拟器上验证能打开**的成理页面；参考源码（聚在工大）里
 * 那些本校没有的服务（慧新易校、洗衣机、乐跑、校车等）不在此列，
 * 避免点开是 404 或空白页。
 */
private val QUERIES = listOf(
    QueryEntry(
        "成绩查询", "教务 · 官方成绩单", "成绩 分数 学分 绩点 GPA 考试通过",
        Icons.Filled.Assignment, QueryAction.Web("$JW/kscj/cjcx_frm"),
    ),
    QueryEntry(
        "成绩统计", "加权平均 · 平均绩点", "统计 加权 绩点 GPA 均分",
        Icons.Filled.BarChart, QueryAction.LocalGrades,
    ),
    QueryEntry(
        "考试安排", "考场 · 时间", "考试 考场 补考 监考",
        Icons.Filled.Event, QueryAction.Web("$JW/xsks/xsksap_query"),
    ),
    QueryEntry(
        "我的课表", "教务 · 学期理论课表", "课表 课程 上课 教室",
        Icons.Filled.School, QueryAction.Web("$JW/xskb/xskb_list.do"),
    ),
    QueryEntry(
        "培养方案", "学分完成情况", "培养 方案 学分 毕业要求",
        Icons.Filled.MenuBook, QueryAction.Web("$JW/pyfa/topyfamx"),
    ),
    QueryEntry(
        "选课结果", "本学期选课", "选课 已选 课程 退课",
        Icons.Filled.Checklist, QueryAction.Web("$JW/xkgl/xsxkjgcx"),
    ),
    QueryEntry(
        "缓考申请", "缓考 · 免修", "缓考 免修 申请",
        Icons.Filled.HourglassEmpty, QueryAction.Web("$JW/kscj/hksq_query"),
    ),
    QueryEntry(
        "学籍信息", "个人信息 · 学籍状态", "学籍 信息 专业 班级 学号",
        Icons.Filled.Badge, QueryAction.Web("$JW/xsxj/xjxxgl.do"),
    ),
    QueryEntry(
        "学生评教", "教师评价 · 教学反馈", "评教 教师 评价 教学 课程",
        Icons.Filled.RateReview, QueryAction.Web("$JW/xspj/xspj_find.do?Ves632DSdyV=NEW_XSD_JXPJ"),
    ),
    QueryEntry(
        "空教室", "空闲教室查询", "空教室 自习 教室 空闲 查询",
        Icons.Filled.MeetingRoom, QueryAction.Web("$JW/kbcx/kbxx_query"),
    ),
    QueryEntry(
        "补考报名", "补考 · 重修", "补考 重修 报名 考试",
        Icons.Filled.Replay, QueryAction.Web("$JW/kscj/bkjc_query"),
    ),
    QueryEntry(
        "已收公告", "教务通知", "公告 通知 消息 教务",
        Icons.Filled.Campaign, QueryAction.Web("$JW/ggly/ysgg_query"),
    ),
    QueryEntry(
        "图书馆", "馆藏检索 · 研修室预约", "图书馆 借书 馆藏 研修室 自习",
        Icons.Filled.LocalLibrary, QueryAction.Web("https://www.lib.cdut.edu.cn/"),
    ),
    // ---- 以下是原来「服务」页的办事入口，按参考源码的做法一并收进查询中心 ----
    QueryEntry(
        "砚湖易办", "一站式办事 · 可信凭证", "办事 易办 审批 证明 流程",
        Icons.Filled.AccountBalance, QueryAction.Web("https://bsdt.cdut.edu.cn/EIP/nonlogin/homePage.htm"),
    ),
    QueryEntry(
        "智慧后勤", "水电缴费 · 网上报修", "后勤 报修 水电 维修 宿舍",
        Icons.Filled.Build, QueryAction.Web("https://clhq.cdut.edu.cn/index.htm"),
    ),
    QueryEntry(
        "缴费平台", "学杂费线上缴纳", "缴费 学费 学杂费 费用 交费",
        Icons.Filled.Payments, QueryAction.Web("https://wsjf.cdut.edu.cn/payment/"),
    ),
    QueryEntry(
        "学生邮箱", "edu 邮箱（首次需激活）", "邮箱 邮件 edu mail",
        Icons.Filled.Email, QueryAction.Web("http://mail.stu.cdut.edu.cn"),
    ),
)

/**
 * 按持久化的顺序重排入口。
 * 顺序串里可能残留已删除的入口 id；未知 id 直接跳过，新入口追加在末尾。
 */
private fun reorderQueries(all: List<QueryEntry>, order: String): List<QueryEntry> {
    if (order.isBlank()) return all
    val ids = order.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val byTitle = all.associateBy { it.title }
    val ordered = ids.mapNotNull { byTitle[it] }.distinct()
    val rest = all.filter { it.title !in ids }
    return ordered + rest
}

/**
 * 查询中心：参考源码 SearchScreen 的框架 —— 顶部搜索框 + 双列卡片网格。
 * 输入即过滤，匹配标题 / 描述 / 关键词。
 */
@Composable
fun QueryCenterScreen(
    repo: Repository,
    openWeb: (String, String) -> Unit,
    /** 打开本机成绩统计页（由调用方以全屏覆盖层承载） */
    onOpenGradeStats: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val savedOrder by repo.queryOrder.collectAsState(initial = "")
    var entries by remember(savedOrder) {
        mutableStateOf(reorderQueries(QUERIES, savedOrder))
    }
    val scope = rememberCoroutineScope()

    val filtered = entries.filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.desc.contains(query, ignoreCase = true) ||
            it.keywords.contains(query, ignoreCase = true)
    }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        if (query.isBlank()) {
            entries = entries.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "查询中心",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        Text(
            "学业信息一站式查询，输入关键词可过滤；清空搜索后长按卡片可拖动排序。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("搜索功能") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清空")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )

        if (filtered.isEmpty()) {
            Text(
                "没有匹配的查询项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.title }) { entry ->
                ReorderableItem(reorderState, key = entry.title) { isDragging ->
                    val interactionSource = remember { MutableInteractionSource() }
                    QueryCard(
                        entry = entry,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = if (isDragging) 0.96f else 1f
                                scaleY = if (isDragging) 0.96f else 1f
                            }
                            .longPressDraggableHandle(
                                enabled = query.isBlank(),
                                interactionSource = interactionSource,
                                onDragStopped = {
                                    scope.launch {
                                        repo.setQueryOrder(entries.joinToString(",") { it.title })
                                    }
                                },
                            ),
                    ) {
                        when (val a = entry.action) {
                            is QueryAction.Web -> openWeb(a.url, entry.title)
                            QueryAction.LocalGrades -> onOpenGradeStats()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryCard(
    entry: QueryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .padding(14.dp)
                .fillMaxSize(),
        ) {
            Icon(
                entry.icon,
                contentDescription = entry.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                entry.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** 本机成绩统计页：加一层标题与返回，供查询中心以全屏覆盖层打开 */
@Composable
fun GradeStatsScreen(repo: Repository, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "成绩统计",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            GradeScreen(repo)
        }
    }
}
