package com.msasha.compose.tree

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * A basic, minimally opinionated, tree widget based on [LazyColumn].
 *
 * To customize the look and feel, provide a custom [itemDecorator]. It wraps
 * each node's [TreeNode.Content] and implements all the tree's looks and
 * behaviors.
 *
 * @param state The tree state object.
 * @param modifier The modifier to apply to the tree.
 * @param lazyListState The state object for the underlying [LazyColumn].
 * @param itemDecorator A [TreeItemDecorator] that decorates each node.
 * @param pageSize Determines the number of items to skip when performing a
 *   page-up or page-down action. If `null`, the default page-size algorithm,
 *   based on the [lazyListState], will be used.
 */
@Composable
fun <T: TreeNode<T>> BasicLazyColumnTree(
    state: TreeState<T>,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    itemDecorator: TreeItemDecorator<T>? = null,
    pageSize: (() -> Int)? = null,
) {
    val nonNullDecorator = itemDecorator.orDefault()
    val nonNullPageSize = pageSize ?: lazyListState::defaultPageSize
    LazyColumn(
        modifier = modifier
            .treeFocusInteractions(state),
        state = lazyListState,
    ) {
        emitVisibleNodes(
            treeState = state,
            nodes = state.roots,
            itemDecorator = nonNullDecorator,
            depth = 0,
            pageSize = nonNullPageSize,
        )
    }

    // Scroll the cursor node into view.
    // In the non-lazy tree, this isn't necessary because the focused element
    // is automatically brought into view via Modifier.bringIntoViewRequester
    // With LazyColumn, however, this doesn't work if the focused element is
    // not currently visible (hasn't been laid out)
    LaunchedEffect(state, lazyListState) {
        snapshotFlow { state.cursorNode }
            .distinctUntilChanged()
            .filterNotNull()
            .collectLatest { cursorNode ->
                // If the node is already visible, there's nothing to do
                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                if (visibleItems.any { it.key == cursorNode }) return@collectLatest

                val cursorNodeIndex = state.findNodeIndex(cursorNode)
                if (cursorNodeIndex == -1) return@collectLatest  // Just in case

                // Animated scroll doesn't work very well here because when the
                // changes come in faster than the animation completes, the
                // previous scroll animation gets cancelled, and the result is
                // a stuttering animation.
                lazyListState.scrollToMakeVisible(
                    index = cursorNodeIndex,
                    animate = false
                )
            }
    }
}

/**
 * Emits the visible nodes into the [LazyListScope].
 */
private fun <T: TreeNode<T>> LazyListScope.emitVisibleNodes(
    treeState: TreeState<T>,
    nodes: List<T>,
    itemDecorator: TreeItemDecorator<T>,
    depth: Int,
    pageSize: () -> Int,
) {
    fun T.emit(isExpanded: Boolean) {
        item(
            key = this,
            contentType = contentType
        ) {
            SingleNodeContent(
                treeState = treeState,
                itemDecorator = itemDecorator,
                node = this@emit,
                isExpanded = isExpanded,
                depth = depth,
                pageSize = pageSize
            )
        }
    }

    var rangeStart = 0
    while (rangeStart < nodes.size) {
        // Find end of unexpanded range
        var index = rangeStart
        while ((index < nodes.size) && !treeState.isExpanded(nodes[index])) {
            index++
        }

        // Emit unexpanded range
        if (index > rangeStart) {
            val rangeNodes = nodes.subList(rangeStart, index)
            // Emit the items one-by-one instead of using `items(rangeNodes)`
            // because doing it this way means that when an item becomes
            // expanded or collapsed, the structure of the emitted items
            // changes, causing LazyList to clear and recreate the corresponding
            // element.
            // This causes the node to lose focus.
            rangeNodes.forEach {
                it.emit(isExpanded = false)
            }
        }

        // Emit expanded nodes
        while ((index < nodes.size) && treeState.isExpanded(nodes[index])) {
            val node = nodes[index]

            node.emit(isExpanded = true)

            // Emit expanded node children
            emitVisibleNodes(
                treeState = treeState,
                nodes = node.children,
                itemDecorator = itemDecorator,
                depth = depth + 1,
                pageSize = pageSize,
            )

            index++
        }
        rangeStart = index
    }
}

/**
 * The default page size algorithm for [BasicLazyColumnTree], based on the
 * number of visible items in the [LazyListState].
 */
private fun LazyListState.defaultPageSize(): Int {
    return (layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)
}

/**
 * Returns the index of [target] in the visible tree list.
 */
private fun <T: TreeNode<T>> TreeState<T>.findNodeIndex(target: T): Int {
    return visibleNodes().withIndex().find { it.value == target }?.index ?: -1
}

/**
 * Scrolls the minimum distance to make the item at the given index fully
 * visible.
 */
private suspend fun LazyListState.scrollToMakeVisible(
    index: Int,
    animate: Boolean = true
) {
    val viewportHeight =
        layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return

    suspend fun maybeAnimatedScrollToItem(
        index: Int,
        scrollOffset: Int = 0
    ) {
        if (animate) {
            animateScrollToItem(index, scrollOffset)
        } else {
            scrollToItem(index, scrollOffset)
        }
    }

    val firstVisibleItem = visibleItemsInfo.first()
    val lastVisibleItem = visibleItemsInfo.last()

    if (index <= firstVisibleItem.index) {  // Target item is before the first visible item
        maybeAnimatedScrollToItem(index)
    } else if (index == lastVisibleItem.index) {  // Target item is the last visible item (may be partially visible)
        // The last visible item is already fully visible, such as when the
        // viewport is taller than the contents
        if (lastVisibleItem.offset + lastVisibleItem.size <= viewportHeight) {
            return
        }

        // Walk up until we overshoot the viewport height.
        // The item reached is the item we need to scroll to, and the
        // offset is the amount we overshoot
        var visibleItemIndex = visibleItemsInfo.lastIndex
        var distanceToItem = 0
        while (distanceToItem < viewportHeight){
            distanceToItem += visibleItemsInfo[visibleItemIndex].size
            visibleItemIndex -= 1
        }
        maybeAnimatedScrollToItem(
            index = visibleItemsInfo[visibleItemIndex+1].index,
            scrollOffset = distanceToItem - viewportHeight
        )
    } else if (index == layoutInfo.totalItemsCount - 1) {
        // Target item is the last item in the list.
        // In this special case there's an easy way, so we use it
        maybeAnimatedScrollToItem(index + 1)
    } else if (index == lastVisibleItem.index + 1) {  // Target item is the first non-visible item below
        // It's impossible to accurately align the bottom of the target item
        // with the bottom of the list because we don't know the height of its
        // view. Best we can do is assume it's the same as the last visible
        // item's view.
        var visibleItemIndex = visibleItemsInfo.lastIndex
        var distanceToItem = lastVisibleItem.size  // <-- Assumption is here
        while (distanceToItem < viewportHeight){
            distanceToItem += visibleItemsInfo[visibleItemIndex].size
            visibleItemIndex -= 1
        }

        maybeAnimatedScrollToItem(
            index = visibleItemsInfo[visibleItemIndex + 1].index,
            scrollOffset = distanceToItem - viewportHeight
        )

        // This is jumpy, but we risk ending up at the wrong position otherwise
        if (animate) {
            scrollToItem(index + 1, -viewportHeight)
        }
    }  else if (index > lastVisibleItem.index) {
        // Assume all the items are of the same size, which is hopefully true.
        // There's nothing better to do here because the views for these items
        // don't actually exist, so it's impossible to know their sizes.
        val itemHeight = lastVisibleItem.size
        val scrollIndex = index - viewportHeight / itemHeight
        val scrollOffset = itemHeight - viewportHeight % itemHeight

        maybeAnimatedScrollToItem(scrollIndex, scrollOffset)

        // This is jumpy, but we risk ending up at the wrong position otherwise
        if (animate) {
            scrollToItem(index + 1, -viewportHeight)
        }
    }
}
