package com.yh.schedule.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yh.schedule.core.CustomEvent
import com.yh.schedule.core.EventType
import com.yh.schedule.core.SchoolCalendar
import java.time.LocalDate

/** 页面左右留白，对齐参考源码 APP_HORIZONTAL_DP */
private val APP_HORIZONTAL_DP = 16.dp

/** 卡片间距 */
private val CARD_GAP = 12.dp

/** 距开学第几周（1 起），与课表周次同一口径 */
private fun weekOfTerm(d: LocalDate): Int =
    (java.time.temporal.ChronoUnit.WEEKS.between(SchoolCalendar.termStartMonday, d).toInt() + 1)
        .coerceAtLeast(1)

/**
 * 添加 / 修改日程页（全屏）。
 *
 * 结构对齐参考源码 `home/focus/funiction/AddEvent.kt` 的 `AddEventUI`：
 * 1. 日程 / DeadLine 分段选择（单选，选中带勾）
 * 2. 类型说明卡（图标 + 解释文案，点卡片可切换类型）
 * 3. 虚线分隔
 * 4. 标题
 * 5. 备注（可为空）
 * 6. 起止时间卡 →「选择日期范围」「选择时间范围」
 * 7. 时间显示文案
 * 8. 底部：预览卡 + 添加/更新按钮
 *
 * 预览卡的排布刻意对齐课程块三要素：remark(时间) / title(课程名位置) / note(地点位置)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    initialDate: LocalDate = LocalDate.now(),
    initialStart: String = "08:10",
    initialEnd: String = "09:45",
    editing: CustomEvent? = null,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (CustomEvent) -> Unit,
    /** 由调用方传入共享元素修饰符（sharedBounds），实现 FAB → 整页的容器转换动画 */
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AddEventContent(
            initialDate = initialDate,
            initialStart = initialStart,
            initialEnd = initialEnd,
            editing = editing,
            onDismiss = onDismiss,
            onDelete = onDelete,
            onSave = onSave,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventContent(
    initialDate: LocalDate,
    initialStart: String,
    initialEnd: String,
    editing: CustomEvent?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (CustomEvent) -> Unit,
) {
    var isScheduleType by remember { mutableStateOf(editing?.type != EventType.DEADLINE) }
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }

    var startDate by remember { mutableStateOf(editing?.date ?: initialDate) }
    var endDate by remember { mutableStateOf(editing?.endDate ?: initialDate) }
    var startTime by remember { mutableStateOf(editing?.startTime ?: initialStart) }
    var endTime by remember { mutableStateOf(editing?.endTime ?: initialEnd) }

    // 时间显示文案：日期/时间一变就重算（对齐源码 LaunchedEffect(date, time)）
    var remark by remember { mutableStateOf(editing?.remark ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var copyMode by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun md(d: LocalDate): String = "%02d-%02d".format(d.monthValue, d.dayOfMonth)

    LaunchedEffect(startDate, endDate, startTime, endTime) {
        remark = if (isScheduleType) {
            if (startDate == endDate) {
                "${md(startDate)} " +
                        if (startTime == endTime) startTime else "$startTime ~ $endTime"
            } else {
                "${md(startDate)} $startTime ~ ${md(endDate)} $endTime"
            }
        } else {
            "${md(endDate)} $endTime 截止"
        }
    }

    // 切换类型时清掉已选时间，避免日程/DDL 两种口径互相污染（对齐源码）
    fun switchType(toSchedule: Boolean) {
        if (toSchedule == isScheduleType) return
        isScheduleType = toSchedule
        err = null
    }

    val timeValid = AxisTime.toFloat(endTime) > AxisTime.toFloat(startTime) ||
            endDate.isAfter(startDate)
    val canSubmit = title.isNotBlank() && endDate >= startDate &&
            (endDate != startDate || timeValid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editing == null && isScheduleType -> "添加日程"
                            editing == null -> "添加 DeadLine"
                            copyMode -> "复制日程"
                            else -> "修改日程"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    // 对齐源码：编辑已有日程时 actions 里是「复制 + 删除」两个图标；
                    // 复制模式（enableCopy）下两个都不显示
                    if (editing != null && !copyMode) {
                        IconButton(onClick = { copyMode = true }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "复制",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (onDelete != null) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    // Dialog 里拿不到导航栏 inset（navigationBarsPadding 在这里是 0），
                    // 底部固定留一段安全距离，避免按钮被手势条压住
                    modifier = Modifier.padding(
                        start = APP_HORIZONTAL_DP,
                        end = APP_HORIZONTAL_DP,
                        top = CARD_GAP,
                        bottom = 28.dp,
                    ),
                ) {
                    // 预览卡：时间 / 标题 / 备注，对应课程块的三要素排布
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            leadingContent = {
                                Icon(
                                    if (isScheduleType) Icons.Filled.CalendarMonth else Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            overlineContent = {
                                Text(
                                    remark,
                                    maxLines = 1,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            headlineContent = {
                                Text(
                                    title,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            supportingContent = if (note.isNotBlank()) {
                                {
                                    Text(
                                        note,
                                        maxLines = 1,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else null,
                        )
                    }

                    Spacer(Modifier.height(CARD_GAP))

                    Button(
                        onClick = {
                            when {
                                title.isBlank() -> err = "请填写标题"
                                endDate < startDate -> err = "结束日期不能早于开始日期"
                                endDate == startDate &&
                                        AxisTime.toFloat(endTime) <= AxisTime.toFloat(startTime) ->
                                    err = if (isScheduleType) "结束时间需晚于开始时间" else "截止时间需晚于开始时间"

                                else -> onSave(
                                    CustomEvent(
                                        // 复制模式下强制当新增处理（对齐源码 enableCopy 走 insert）
                                        id = if (copyMode) 0 else (editing?.id ?: 0),
                                        title = title.trim(),
                                        date = startDate,
                                        endDate = endDate,
                                        startTime = startTime,
                                        endTime = endTime,
                                        note = note.trim(),
                                        type = if (isScheduleType) EventType.SCHEDULE else EventType.DEADLINE,
                                        remark = remark.trim(),
                                    ),
                                )
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (editing == null || copyMode) "添加" else "更新")
                    }
                }
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(CARD_GAP))

            // ---- 类型分段 ----
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = APP_HORIZONTAL_DP),
            ) {
                listOf(true, false).forEachIndexed { index, isSchedule ->
                    SegmentedButton(
                        selected = isScheduleType == isSchedule,
                        onClick = { switchType(isSchedule) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        icon = { SegmentedButtonDefaults.Icon(isScheduleType == isSchedule) },
                        label = { Text(if (isSchedule) "日程" else "DeadLine") },
                    )
                }
            }

            Spacer(Modifier.height(CARD_GAP))

            // ---- 类型说明卡 ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = APP_HORIZONTAL_DP),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(APP_HORIZONTAL_DP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { switchType(!isScheduleType) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (isScheduleType) Icons.Filled.CalendarMonth else Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(CARD_GAP))
                    Text(
                        if (isScheduleType) {
                            "日程类型旨在用户自行添加额外的课程、实训、实验、班会等，强调线下活动、" +
                                    "有始有终；添加后，将同时显示在课程表方格中，在进行期间会显示为重要事项，否则在其他事项中"
                        } else {
                            "DDL类型旨在用户自行添加需要在截止日期之前的作业、实验报告等，强调线上活动，" +
                                    "相比日程类型只需注意结束时间；添加后，当剩余72h内时将会显示为重要事项，否则在其他事项中"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(CARD_GAP))
            DashedDivider(Modifier.padding(horizontal = APP_HORIZONTAL_DP))
            Spacer(Modifier.height(CARD_GAP))

            // ---- 标题 ----
            YhTextField(
                value = title,
                onValueChange = { title = it; err = null },
                label = "标题",
                modifier = Modifier.padding(horizontal = APP_HORIZONTAL_DP),
            )

            Spacer(Modifier.height(CARD_GAP))

            // ---- 备注 ----
            YhTextField(
                value = note,
                onValueChange = { note = it },
                label = "备注(可为空)",
                modifier = Modifier.padding(horizontal = APP_HORIZONTAL_DP),
            )

            Spacer(Modifier.height(CARD_GAP))

            // ---- 起止时间 ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = APP_HORIZONTAL_DP),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(APP_HORIZONTAL_DP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(CARD_GAP))
                        Column {
                            if (isScheduleType) {
                                Text(
                                    "开始 $startDate (第${weekOfTerm(startDate)}周) $startTime",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                "${if (isScheduleType) "结束" else "截止"} $endDate " +
                                        "(第${weekOfTerm(endDate)}周) $endTime",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = APP_HORIZONTAL_DP, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(
                                if (isScheduleType) "选择日期范围" else "选择截止日期",
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(
                                if (isScheduleType) "选择时间范围" else "选择截止时间",
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(CARD_GAP))

            // ---- 时间显示文案 ----
            YhTextField(
                value = remark,
                onValueChange = { remark = it },
                label = "时间显示文案",
                modifier = Modifier.padding(horizontal = APP_HORIZONTAL_DP),
            )

            err?.let {
                Spacer(Modifier.height(CARD_GAP))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = APP_HORIZONTAL_DP),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        YhDateRangePickerDialog(
            initialStart = startDate,
            initialEnd = endDate,
            onSelected = { (s, e) ->
                startDate = s
                endDate = e
                err = null
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        YhTimeRangePickerDialog(
            isSchedule = isScheduleType,
            defaultValue = startTime to endTime,
            onSelected = { (s, e) ->
                startTime = s
                endTime = e
                err = null
            },
            onDismiss = { showTimePicker = false },
        )
    }

    // 删除确认（源码 LittleDialog 的「要删除此项吗」）
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            text = { Text("要删除此项吗") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 输入框：圆角填充、无描边，对齐原版的浅色块样式 */
@Composable
private fun YhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        singleLine = false,
        minLines = 1,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** 虚线分隔（Compose 无内置，手绘） */
@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
        )
    }
}
