package com.msasha.compose.tree

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds

/**
 * A basic, minimally opinionated, tree widget based on (non-lazy) [Column].
 *
 * Note that non-lazy widgets are limited by pixel height. For large trees
 * prefer [BasicLazyColumnTree].
 *
 * To customize the look and feel, provide a custom [itemDecorator]. It wraps
 * each node's [TreeNode.Content] and implements all the tree's looks and
 * behaviors.
 *
 * @param state The tree state object.
 * @param modifier The modifier to apply to the tree.
 * @param scrollState The scroll state of the list. Pass `null` to make the list
 * non-scrollable, or to manage scrolling via other means.
 * @param itemDecorator A [TreeItemDecorator] that decorates each node.
 * @param pageSize Determines the number of items to skip when performing a
 *   page-up or page-down action. If `null`, the default page-size algorithm,
 *   based on the [scrollState], will be used.
 */
@Composable
fun <T: TreeNode<T>> BasicColumnTree(
    state: TreeState<T>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = rememberScrollState(),
    itemDecorator: TreeItemDecorator<T>? = null,
    pageSize: (() -> Int)? = null,
) {
    val nonNullDecorator = itemDecorator.orDefault()
    val layoutBoundsHolder = remember { LayoutBoundsHolder() }
    val nonNullPageSize = pageSize ?:
        remember(state, scrollState) {
            {
                defaultPageSize(state, scrollState, layoutBoundsHolder)
            }
        }
    Column(
        modifier = modifier
            .then(
                if (scrollState != null) {
                    Modifier
                        .verticalScroll(scrollState)
                        .layoutBounds(layoutBoundsHolder)
                } else {
                    Modifier
                }
            )
            .treeFocusInteractions(state)
    ) {
        for (root in state.roots) {
            SubTreeContent(
                state = state,
                node = root,
                itemDecorator = nonNullDecorator,
                depth = 0,
                pageSize = nonNullPageSize,
            )
        }
    }
}

/**
 * Emits the UI for a node and all of its expanded subtree.
 */
@Composable
private fun <T: TreeNode<T>> SubTreeContent(
    state: TreeState<T>,
    node: T,
    itemDecorator: TreeItemDecorator<T>,
    depth: Int,
    pageSize: (() -> Int)
) {
    key(node) {
        val isExpanded by remember {
            derivedStateOf { state.isExpanded(node) }
        }
        SingleNodeContent(
            treeState = state,
            itemDecorator = itemDecorator,
            node = node,
            isExpanded = isExpanded,
            depth = depth,
            pageSize = pageSize,
        )
        if (!isExpanded) return

        val children by remember(node) {
            derivedStateOf {
                node.children
            }
        }
        for (child in children) {
            SubTreeContent(
                state = state,
                node = child,
                itemDecorator = itemDecorator,
                depth = depth + 1,
                pageSize = pageSize,
            )
        }
    }
}

/**
 * The page size for [BasicColumnTree] when there's no better way to determine
 * it.
 */
private const val DEFAULT_PAGE_SIZE = 10

/**
 * The default page size algorithm, based on the column and viewport height.
 * It assumes that the tree items are mostly of identical height.
 */
private fun defaultPageSize(
    treeState: TreeState<*>,
    scrollState: ScrollState?,
    columnLayoutBoundsHolder: LayoutBoundsHolder
): Int {
    if (scrollState == null) {
        return DEFAULT_PAGE_SIZE
    }

    val columnHeight = columnLayoutBoundsHolder.bounds?.height ?: return DEFAULT_PAGE_SIZE
    val viewportHeight = scrollState.viewportSize
    val itemCount = treeState.visibleNodes().count()
    return (itemCount * viewportHeight / columnHeight).coerceAtLeast(1)
}
