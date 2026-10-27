package com.yh.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.TimeTableItem
import com.yh.schedule.core.TimeTableType
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 时间轴工具：对齐参考源码
 * `ui/screen/home/calendar/timetable/logic/Util.kt`
 *
 * 时间 → Y 坐标；午休（11:50–14:30）压缩到 10% 高度，避免大片空白。
 */
object AxisTime {
    const val START_TIME = "08:00"
    const val END_TIME = "22:00"
    const val LUNCH_START = 11f + 50f / 60f   // 11:50，上午最后一节下课
    const val LUNCH_END = 14f + 30f / 60f     // 14:30，下午第一节上课
    const val LUNCH_FACTOR = 0.4f

    /** 每小时高度（dp） */
    const val HOUR_HEIGHT = 56

    fun toFloat(time: String): Float {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h + m / 60f
    }

    fun hourLabel(hour: Int): String = "%02d:00".format(hour)

    fun toY(hour: Float, hourPx: Float, start: Float = toFloat(START_TIME)): Float {
        return when {
            hour <= LUNCH_START -> (hour - start) * hourPx
            hour <= LUNCH_END ->
                (LUNCH_START - start) * hourPx +
                        (hour - LUNCH_START) * hourPx * LUNCH_FACTOR
            else ->
                (LUNCH_START - start) * hourPx +
                        (LUNCH_END - LUNCH_START) * hourPx * LUNCH_FACTOR +
                        (hour - LUNCH_END) * hourPx
        }
    }
}

private data class PositionedItem(
    val item: TimeTableItem,
    val start: Float,
    val end: Float,
    val columnIndex: Int,
    val totalColumns: Int,
)

/**
 * 同一天内的布局，对齐源码 `layoutSquaresForDay`：
 * 1. 按开始时间排序；
 * 2. **把时间重叠的课合并成一个 group**（group 的区间取并集）；
 * 3. group 内均分列宽（源码是在 group 区域内纵向排布，时间轴布局下改为横向均分，
 *    这样每门课的高度仍按自身时间精确，且不会互相压盖）。
 */
private fun layoutDay(items: List<TimeTableItem>): List<PositionedItem> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedBy { AxisTime.toFloat(it.startTime) }

    // 1. 合并重叠区间
    val groups = mutableListOf<MutableList<TimeTableItem>>()
    var current = mutableListOf<TimeTableItem>()
    var currentEnd = -1f

    for (item in sorted) {
        val start = AxisTime.toFloat(item.startTime)
        val end = AxisTime.toFloat(item.endTime).coerceAtLeast(start + 0.5f)
        when {
            current.isEmpty() -> {
                current.add(item); currentEnd = end
            }
            start < currentEnd -> {
                current.add(item); if (end > currentEnd) currentEnd = end
            }
            else -> {
                groups.add(current)
                current = mutableListOf(item)
                currentEnd = end
            }
        }
    }
    if (current.isNotEmpty()) groups.add(current)

    // 2. 组内均分列
    return groups.flatMap { group ->
        val n = group.size
        group.mapIndexed { index, item ->
            val start = AxisTime.toFloat(item.startTime)
            val end = AxisTime.toFloat(item.endTime).coerceAtLeast(start + 0.5f)
            PositionedItem(item, start, end, index, n)
        }
    }
}

private fun blockColors(type: TimeTableType): Pair<Color, Color> = when (type) {
    TimeTableType.COURSE -> Color(0xFFDCE7FB) to Color(0xFF1B3B6F)
    TimeTableType.FOCUS -> Color(0xFFD7F0DC) to Color(0xFF1B5E20)
    TimeTableType.EXAM -> Color(0xFFFBDADA) to Color(0xFF7F1D1D)
}

/**
 * 课表主体：左侧完整时间标尺 + 7 列日期 + 时间轴课程块。
 *
 * 对齐参考源码：
 * - `TimetableSingleSquare`：绝对定位、时间→Y、重叠分列
 * - `ScheduleTopDate`：表头只有日期数字，今天下划线
 * - 课程块四要素：开始时间 / 课程名@教师 / 地点 / 结束时间
 */
@Composable
fun TimetableAxis(
    items: List<TimeTableItem>,
    week: Int,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
    rulerWidth: Dp = 42.dp,
    /** 显示几天：5 = 只下周一到周五（周末无内容时，列更宽）；7 = 完整整周 */
    dayCount: Int = 7,
    onItemClick: (TimeTableItem) -> Unit = {},
    onBlankDoubleTap: (dayOfWeek: Int, hour: Float) -> Unit = { _, _ -> },
    onWeekTitleClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val hourDp = AxisTime.HOUR_HEIGHT.dp
    val density = LocalDensity.current
    val hourPx = with(density) { hourDp.toPx() }
    val startF = AxisTime.toFloat(AxisTime.START_TIME)
    val endF = AxisTime.toFloat(AxisTime.END_TIME)
    val gridHeight: Dp = with(density) { AxisTime.toY(endF, hourPx, startF).toDp() }

    val divColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier.fillMaxSize()) {
        // ---- 顶部：日期范围 + 第几周（点击可切换周次）----
        val monday = SchoolCalendar.termStartMonday.plusWeeks((week - 1).toLong())
        val sunday = monday.plusDays(6)
        Row(
            Modifier
                .fillMaxWidth()
                .let {
                    if (onWeekTitleClick != null) it.clickable { onWeekTitleClick() } else it
                }
                .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${monday.monthValue}月${monday.dayOfMonth}日 - " +
                        "${sunday.monthValue}月${sunday.dayOfMonth}日",
                fontSize = 12.sp,
                color = labelColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "第 $week 周",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            trailing?.invoke()
        }

        // ---- 日期表头（原版：仅日数字，今天下划线）----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(rulerWidth))
            (1..dayCount).forEach { d ->
                val date = SchoolCalendar.termStartMonday.plusDays((week - 1) * 7L + d - 1)
                val isToday = date == today
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${date.dayOfMonth}",
                        // 5 天模式列更宽，日期数字跟着放大（对齐源码 12f→14f 的做法）
                        fontSize = if (dayCount <= 5) 15.sp else 13.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else labelColor,
                        textDecoration = if (isToday) TextDecoration.Underline else null,
                    )
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            val cellW = (maxWidth - rulerWidth) / dayCount
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        val rulerPx = rulerWidth.toPx()
                        // 每小时横线（午休压缩区内（13:00）不画，避免与 12:00 重叠）
                        var hour = AxisTime.START_TIME.substringBefore(":").toInt()
                        while (hour <= 22) {
                            if (hour !in 13..13) {
                                val y = AxisTime.toY(hour.toFloat(), hourPx, startF)
                                if (y in 0f..h) {
                                    drawLine(
                                        color = divColor.copy(alpha = 0.6f),
                                        strokeWidth = 1f,
                                        start = Offset(rulerPx, y),
                                        end = Offset(w, y),
                                    )
                                }
                            }
                            hour++
                        }
                        // 列边界虚线
                        for (i in 0..dayCount) {
                            val x = rulerPx + (w - rulerPx) * i / dayCount.toFloat()
                            drawLine(
                                color = divColor.copy(alpha = 0.8f),
                                strokeWidth = 1f,
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                            )
                        }
                    }
                    .pointerInput(week) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val rulerPx = rulerWidth.toPx()
                                val dayIndex = ((offset.x - rulerPx) / cellW.toPx()).toInt()
                                if (dayIndex in 0 until dayCount) {
                                    // 反解 Y → 小时（忽略午休压缩段，取近似）
                                    val hour = yToHour(offset.y, hourPx, startF)
                                    onBlankDoubleTap(dayIndex + 1, hour)
                                }
                            },
                        )
                    },
            ) {
                // ---- 左侧完整时间标尺（每小时；13:00 位于午休压缩区，省略避免重叠）----
                var hour = AxisTime.START_TIME.substringBefore(":").toInt()
                while (hour <= 22) {
                    if (hour != 13) {
                        val y = AxisTime.toY(hour.toFloat(), hourPx, startF)
                        Text(
                            text = AxisTime.hourLabel(hour),
                            fontSize = 10.sp,
                            color = labelColor,
                            modifier = Modifier
                                .offset(y = with(density) { y.toDp() } - 7.dp)
                                .width(rulerWidth)
                                .padding(end = 4.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                    hour++
                }

                // ---- 课程 / 日程块 ----
                val byDay = items.groupBy { it.dayOfWeek }
                byDay.forEach { (day, dayItems) ->
                    if (day !in 1..dayCount) return@forEach
                    val positioned = layoutDay(dayItems)
                    positioned.forEach { p ->
                        val colW = cellW - 4.dp
                        val gap = 2.dp
                        val innerW = (colW - gap * (p.totalColumns - 1)) / p.totalColumns
                        val x = rulerWidth + cellW * (day - 1) + 2.dp +
                                (innerW + gap) * p.columnIndex
                        val yStart = AxisTime.toY(p.start, hourPx, startF)
                        val yEnd = AxisTime.toY(p.end, hourPx, startF)
                        val h = (yEnd - yStart).coerceAtLeast(hourPx * 0.4f)

                        val (bg, fg) = blockColors(p.item.type)
                        // 上下各延伸 5dp 让块更饱满
                        val extend = 5.dp
                        val extendPx = with(density) { extend.toPx() }
                        val yTop = (yStart - extendPx).coerceAtLeast(0f)
                        val blockH = h + extendPx * 2
                        Box(
                            Modifier
                                .offset(x = x, y = with(density) { yTop.toDp() })
                                .width(innerW)
                                .height(with(density) { blockH.toDp() })
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .clickable { onItemClick(p.item) }
                                .padding(horizontal = 2.dp),
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 并排/重叠导致变窄时不再整块丢弃文字：缩字号、允许逐字换行，
                                // 只要宽度还放得下一个字就照常渲染（对齐原版半框也显示信息的效果）
                                val showText = innerW >= 16.dp
                                val narrow = innerW < 30.dp
                                val wide = dayCount <= 5   // 5 天模式列更宽，字号跟着放大
                                val timeSize = if (narrow) 7.5.sp else if (wide) 10.5.sp else 9.5.sp
                                val nameSize = if (narrow) 8.sp else if (wide) 12.5.sp else 11.sp
                                val nameLine = if (narrow) 9.sp else if (wide) 14.sp else 12.sp
                                val nameMax = if (narrow) 3 else 4
                                val placeSize = if (narrow) 7.5.sp else if (wide) 11.sp else 10.sp
                                val placeLine = if (narrow) 8.5.sp else if (wide) 12.sp else 11.sp

                                if (showText) {
                                    // 开始时间：固定贴顶
                                    Text(
                                        p.item.startTime,
                                        fontSize = timeSize,
                                        color = fg.copy(alpha = 0.8f),
                                        maxLines = 1,
                                    )
                                    // 中间：课程名 + 地点。两者按 weight 平分剩余高度，
                                    // 保证地点不被长课程名挤掉（名字可以截断，地点必须留）
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clipToBounds(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxHeight(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            // 课程名吃「剩余高度」（可截断），地点按内容优先占位：
                                            // 这样长课名挤掉的是自己的字数，而不是把地点顶没
                                            Text(
                                                p.item.name,
                                                modifier = Modifier.weight(1f, fill = false),
                                                fontSize = nameSize,
                                                lineHeight = nameLine,
                                                fontWeight = FontWeight.Medium,
                                                color = fg,
                                                maxLines = nameMax,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                            )
                                            p.item.place?.let {
                                                Text(
                                                    simplifyPlace(it),
                                                    fontSize = placeSize,
                                                    lineHeight = placeLine,
                                                    color = fg.copy(alpha = 0.9f),
                                                    maxLines = if (narrow) 2 else 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    }
                                    // 结束时间：固定贴底
                                    Text(
                                        p.item.endTime,
                                        fontSize = timeSize,
                                        color = fg.copy(alpha = 0.8f),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Y 坐标 → 小时（反向映射，考虑午休压缩；用于双击空白处新建日程） */
private fun yToHour(y: Float, hourPx: Float, start: Float): Float {
    val lunchStartY = (AxisTime.LUNCH_START - start) * hourPx
    val lunchHeight = (AxisTime.LUNCH_END - AxisTime.LUNCH_START) * hourPx * AxisTime.LUNCH_FACTOR
    return when {
        y <= lunchStartY -> start + y / hourPx
        y <= lunchStartY + lunchHeight ->
            AxisTime.LUNCH_START + (y - lunchStartY) / (hourPx * AxisTime.LUNCH_FACTOR)
        else -> AxisTime.LUNCH_END + (y - lunchStartY - lunchHeight) / hourPx
    }
}

/**
 * 地点简化（对齐原版 `simplifyPlace` 的意图：块内只留关键房间号）：
 * 「（宜）乙103」→「乙103」；「C103（室外篮球场）」→「C103」；
 * 「（宜）基A427（语音/计算机）」→「基A427」
 */
private fun simplifyPlace(place: String): String {
    val s = place
        .replace(Regex("^[（(][^）)]{1,4}[）)]"), "")
        .replace(Regex("\\s*[（(][^）)]*[）)]\\s*$"), "")
        .trim()
    return s.ifBlank { place }
}

/** 把小时 + 分钟取整到最近的半小时，便于新建日程默认值 */
fun Float.toNearestHalfHour(): String {
    val total = (this * 60).roundToInt()
    val half = (total / 30) * 30
    val h = (half / 60).coerceIn(0, 23)
    val m = half % 60
    return "%02d:%02d".format(h, m)
}
