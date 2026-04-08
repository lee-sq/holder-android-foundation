package com.holderzone.widget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    minCellWidth: Dp = 160.dp, // 设定每个格子的最小宽度
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellWidth),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        itemsIndexed(items) { index, item ->
            itemContent(index, item)
        }
    }
}

@Composable
fun <T> AdaptiveFlowRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 12.dp,  // 主轴间距（同一行内）
    verticalSpacing: Dp = 12.dp,    // 交叉轴间距（不同行之间）
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        items.forEachIndexed { index, item ->
            itemContent(index, item)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> AdaptiveStaggeredGrid(
    items: List<T>,
    minCellWidth: Dp = 160.dp,        // 每列的最小宽度，列数自适应
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    horizontalSpacing: Dp = 12.dp,    // 列间距
    verticalSpacing: Dp = 12.dp,      // item 垂直间距
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = minCellWidth),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalItemSpacing = verticalSpacing
    ) {
        itemsIndexed(items) { index, item ->
            itemContent(index, item)  // 每个 item 可有不同高度
        }
    }
}


@Composable
fun <T> AdaptiveFlowRowResponsive(
    items: List<T>,
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 160.dp,           // 每个子项的最小宽度
    horizontalSpacing: Dp = 12.dp,       // 同一行内的间距
    verticalSpacing: Dp = 12.dp,         // 行与行的间距
    enforceEqualWidth: Boolean = true,   // 是否强制每个子项宽度相等
    keySelector: ((T) -> Any)? = null,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxPx = constraints.maxWidth
        val spacingPx = with(density) { horizontalSpacing.roundToPx() }
        val minCellPx = with(density) { minCellWidth.roundToPx() }

        val columns = ((maxPx + spacingPx) / (minCellPx + spacingPx)).coerceAtLeast(1)
        val targetItemPx = ((maxPx - spacingPx * (columns - 1)) / columns).coerceAtLeast(0)
        val targetItemDp = with(density) { (targetItemPx - 1).toDp() }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            items.forEachIndexed { index, item ->
                val childModifier = if (enforceEqualWidth) {
                    Modifier.width(targetItemDp)
                } else {
                    Modifier.widthIn(max = targetItemDp)
                }
                val content: @Composable () -> Unit = {
                    Box(childModifier) {
                        itemContent(index, item)
                    }
                }
                if (keySelector != null) {
                    key(Pair(keySelector(item), item)) { content() }
                } else {
                    key(item) { content() }
                }
            }
        }
    }
}
