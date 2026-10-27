package com.yh.schedule.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 右下角周次胶囊（对齐参考源码 `calendar/common/DraggableWeekButton.kt`）。
 *
 * 交互：
 * - 点击 → 回到当前周
 * - 左右拖动/点箭头 → 切周（拖动时按钮跟手偏移，松手回弹）
 * - 文字切换垂直滑动动画
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekFloatingButton(
    week: Int,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackToCurrent: () -> Unit,
) {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val dragThreshold = 10f
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    when {
                        totalDragX > dragThreshold -> onPrevious()
                        totalDragX < -dragThreshold -> onNext()
                    }
                    totalDragX = 0f
                    scope.launch {
                        offset.animateTo(
                            Offset.Zero,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount.x
                    scope.launch {
                        offset.snapTo(
                            Offset(
                                x = (offset.value.x + dragAmount.x / 2f).coerceIn(-40f, 40f),
                                y = 0f,
                            )
                        )
                    }
                },
            )
        },
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), 0) }
                .height(46.dp)
                .combinedClickable(onClick = onBackToCurrent, onLongClick = null),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "上一周",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onPrevious() },
                )
                Spacer(Modifier.width(10.dp))
                AnimatedContent(
                    targetState = week,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInVertically { h -> h } + fadeIn() togetherWith
                                    slideOutVertically { h -> -h } + fadeOut()
                        } else {
                            slideInVertically { h -> -h } + fadeIn() togetherWith
                                    slideOutVertically { h -> h } + fadeOut()
                        }
                    },
                    label = "weekChange",
                ) { w ->
                    Text("第 $w 周", fontSize = 14.sp)
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "下一周",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onNext() },
                )
            }
        }
    }
}
