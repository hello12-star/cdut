package com.yh.schedule.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 可见行数（上中下三行，中间为选中项） */
private const val WHEEL_VISIBLE_COUNT = 3

/** 滚轮总高度 */
private const val WHEEL_HEIGHT_DP = 120

/** 无限滚动时把列表撑长的倍数（伪造无限，实际是 size * N 个循环项） */
private const val WHEEL_LOOP_FACTOR = 10000

/**
 * 滚轮选择器。
 *
 * 移植自参考源码 `ui/component/input/WheelPicker.kt`：
 * - LazyColumn + snap 吸附，中间项高亮为胶囊底、上下项按距离缩放并绕 X 轴翻转（3D 滚筒感）
 * - 无限滚动靠「循环填充 + floorMod 取余」实现，滑到头不会停
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> WheelPicker(
    data: List<T>,
    modifier: Modifier = Modifier,
    initialSelectedIndex: Int = 0,
    selectedColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    selectedShape: Shape = MaterialTheme.shapes.medium,
    enableInfiniteScroll: Boolean = true,
    onSelect: (index: Int, item: T) -> Unit,
    content: @Composable (item: T) -> Unit,
) {
    if (data.isEmpty()) return

    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.height(WHEEL_HEIGHT_DP.dp), propagateMinConstraints = true) {
        val density = LocalDensity.current
        val size = data.size
        val paddingCount = WHEEL_VISIBLE_COUNT / 2
        val count = if (enableInfiniteScroll) size * WHEEL_LOOP_FACTOR else size + paddingCount * 2

        val pickerHeight = maxHeight
        val pickerHeightPx = with(density) { pickerHeight.toPx() }
        val pickerCenterLinePx = pickerHeightPx / 2
        val itemHeight = pickerHeight / WHEEL_VISIBLE_COUNT
        val itemHeightPx = pickerHeightPx / WHEEL_VISIBLE_COUNT
        val startIndex = count / 2

        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = if (enableInfiniteScroll) {
                startIndex - startIndex.floorMod(size) + initialSelectedIndex
            } else {
                initialSelectedIndex
            },
            initialFirstVisibleItemScrollOffset = ((itemHeightPx - pickerHeightPx) / 2).roundToInt(),
        )
        val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }

        // 非无限模式：滚到边界时把剩余手势吃掉，避免传递给外层 ScrollView
        val nestedScrollConnection = remember(listState, enableInfiniteScroll) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (enableInfiniteScroll) return Offset.Zero
                    val delta = available.y
                    val isAtTop = !listState.canScrollBackward
                    val isAtBottom = !listState.canScrollForward
                    val shouldBlock = (delta > 0 && isAtTop) || (delta < 0 && isAtBottom)
                    return if (shouldBlock) Offset(0f, available.y) else Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (enableInfiniteScroll) return Velocity.Zero
                    val velocityY = available.y
                    val isAtTop = !listState.canScrollBackward
                    val isAtBottom = !listState.canScrollForward
                    val shouldBlock = (velocityY > 0 && isAtTop) || (velocityY < 0 && isAtBottom)
                    return if (shouldBlock) Velocity(0f, velocityY) else Velocity.Zero
                }
            }
        }

        LazyColumn(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            items(count) { index ->
                val currIndex = if (enableInfiniteScroll) {
                    (index - startIndex).floorMod(size)
                } else {
                    index - paddingCount
                }
                val isPaddingItem = !enableInfiniteScroll &&
                        (index < paddingCount || index >= paddingCount + size)

                val item = layoutInfo.visibleItemsInfo.find { it.index == index }

                var currentsAdjust = 1f
                var itemCenterY = 0f

                if (item != null) {
                    val viewportStart = layoutInfo.viewportStartOffset
                    itemCenterY = item.offset - viewportStart + item.size / 2f

                    val distance = abs(itemCenterY - pickerCenterLinePx)
                    val fraction = 1f - (distance / pickerCenterLinePx).coerceIn(0f, 1f)
                    currentsAdjust = 0.6f + 0.4f * fraction

                    // 停止滚动后，把压在中心线上的那一项报出去
                    if (!listState.isScrollInProgress &&
                        !isPaddingItem &&
                        item.offset < pickerCenterLinePx &&
                        item.offset + item.size > pickerCenterLinePx &&
                        currIndex in data.indices
                    ) {
                        onSelect(currIndex, data[currIndex])
                    }
                }

                val selected = abs(itemCenterY - pickerCenterLinePx) < itemHeightPx / 2
                val colorAlpha = if (selected && !isPaddingItem) 1f else 0f

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .background(selectedColor.copy(colorAlpha), shape = selectedShape)
                        .clickable(
                            enabled = !isPaddingItem && !selected,
                            interactionSource = null,
                            indication = null,
                        ) {
                            scope.launch {
                                listState.animateScrollToItem(
                                    index = index,
                                    scrollOffset = ((itemHeightPx - pickerHeightPx) / 2).roundToInt(),
                                )
                            }
                        }
                        .graphicsLayer {
                            alpha = if (isPaddingItem) 0f else currentsAdjust
                            scaleX = currentsAdjust
                            scaleY = currentsAdjust
                            rotationX = when {
                                itemCenterY < pickerCenterLinePx -> (1 - currentsAdjust) * 90f
                                itemCenterY > pickerCenterLinePx -> -(1 - currentsAdjust) * 90f
                                else -> 0f
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isPaddingItem && currIndex in data.indices) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            content(data[currIndex])
                        }
                    }
                }
            }
        }
    }
}

private fun Int.floorMod(other: Int): Int = when (other) {
    0 -> this
    else -> this - floorDiv(other) * other
}
