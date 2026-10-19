package com.natyz.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Right-edge scroll indicator that only appears while the list is scrolling
 * and fades out once it comes to rest.
 */
@Composable
private fun RailScaffold(
    visible: Boolean,
    start: Float,
    end: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(450))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(36.dp)
                .graphicsLayer { translationY = start * size.height }
        ) {
            Box(
                Modifier
                    .fillMaxHeight((end - start).coerceIn(0.02f, 1f))
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.Gray.copy(alpha = 0.6f))
            )
        }
    }
}

private fun rangeOf(total: Int, first: Int, last: Int): Pair<Float, Float> =
    if (total > 0) {
        (first.toFloat() / total) to ((last + 1).toFloat() / total)
    } else 0f to 1f

@Composable
fun ScrollRail(state: LazyStaggeredGridState, modifier: Modifier = Modifier) {
    val range by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val vis = info.visibleItemsInfo
            if (vis.isNotEmpty()) rangeOf(info.totalItemsCount, vis.first().index, vis.last().index)
            else 0f to 1f
        }
    }
    RailScaffold(state.isScrollInProgress, range.first, range.second, modifier)
}

@Composable
fun ScrollRail(state: LazyGridState, modifier: Modifier = Modifier) {
    val range by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val vis = info.visibleItemsInfo
            if (vis.isNotEmpty()) rangeOf(info.totalItemsCount, vis.first().index, vis.last().index)
            else 0f to 1f
        }
    }
    RailScaffold(state.isScrollInProgress, range.first, range.second, modifier)
}

@Composable
fun ScrollRail(state: LazyListState, modifier: Modifier = Modifier) {
    val range by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val vis = info.visibleItemsInfo
            if (vis.isNotEmpty()) rangeOf(info.totalItemsCount, vis.first().index, vis.last().index)
            else 0f to 1f
        }
    }
    RailScaffold(state.isScrollInProgress, range.first, range.second, modifier)
}
