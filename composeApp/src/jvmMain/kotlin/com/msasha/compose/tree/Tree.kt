package com.msasha.compose.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The interface for a node in the tree.
 *
 * If the tree is dynamic, its structure ([parent], [isLeaf] and [children])
 * should be backed by snapshot state, in order for the UI to be updated as the
 * tree changes.
 *
 * [TreeNode]s are used as keys in hash tables, and therefore implementations
 * must implement [equals] and [hashCode] correctly.
 */
@Stable
interface TreeNode<T: TreeNode<T>> {

    /**
     * The parent of this node; `null` if it's a root node.
     */
    val parent: T?

    /**
     * Returns whether this is a leaf node.
     */
    val isLeaf: Boolean

    /**
     * Returns the children of this node; may be empty.
     *
     * This will not be called on leaf nodes.
     */
    val children: List<T>

    /**
     * Returns the content type of this node. This is passed as the item's
     * `contentType` when the tree is displayed in a lazy column.
     *
     * See [androidx.compose.foundation.lazy.LazyListScope]
     */
    val contentType: Any?
        get() = null

    /**
     * Emits the UI for the node.
     */
    @Composable
    fun Content(treeState: TreeState<T>, isExpanded: Boolean, depth: Int)

    /**
     * [TreeNode]s are used as keys in hash tables and must thus implement
     * [equals] and [hashCode].
     */
    override fun equals(other: Any?): Boolean

    /**
     * [TreeNode]s are used as keys in hash tables and must thus implement
     * [equals] and [hashCode].
     */
    override fun hashCode(): Int
}

/**
 * The state object for a tree widget.
 */
@Stable
class TreeState<T: TreeNode<T>>(
    /**
     * The roots of the tree; must not be empty.
     */
    val roots: List<T>,
) {
    init {
        require(roots.isNotEmpty()) { "Must have at least one root node" }
    }

    /**
     * Whether the tree currently has focus (it or one of its descendent nodes
     * is focused).
     */
    var hasFocus: Boolean by mutableStateOf(false)

    /**
     * The set of expanded nodes.
     */
    private val expanded: MutableMap<T, Unit> = SnapshotStateMap()

    /**
     * Returns whether the given node is currently expanded.
     *
     * @param node The node to expand.
     */
    fun isExpanded(node: T): Boolean {
        return node in expanded
    }

    /**
     * Expands the given node.
     *
     * @param node The node to expand; must not be a leaf.
     */
    fun expand(node: T) {
        require(!node.isLeaf) { "Can't expand leaf node $node" }
        expanded[node] = Unit
    }

    /**
     * Collapses the given node and all of its descendents, recursively.
     *
     * @param node The node to collapse.
     */
    fun collapse(node: T) {
        if (!isExpanded(node)) return
        node.children.forEach {
            collapse(it)
        }
        collapseWithoutDescendents(node)
    }

    /**
     * Toggles the expanded state of the given node.
     *
     * @param node The node whose expanded state to toggle.
     */
    fun toggleExpanded(node: T) {
        if (isExpanded(node)) {
            collapse(node)
        } else {
            expand(node)
        }
    }

    /**
     * Collapses only the given node; if any of its descendents are expanded,
     * they remain expanded (but not visible).
     *
     * This function is unlikely to be needed as typically nodes should be
     * collapsed together with their descendents.
     *
     * @param node The node to collapse.
     */
    fun collapseWithoutDescendents(node: T) {
        expanded.remove(node)
    }

    /**
     * Expands the given node and all of its descendents, recursively.
     *
     * @param node The node to expand.
     */
    fun expandWithDescendents(node: T) {
        expand(node)
        node.children.forEach {
            if (!it.isLeaf) {
                expandWithDescendents(it)
            }
        }
    }

    /**
     * The "cursor", or last-selected tree node.
     *
     * Note that this is related to, but distinct from, the element that has
     * keyboard focus. [defaultTreeItemDecorator] synchronizes between the
     * two as long as the tree has focus, but other implementations may choose
     * different strategies.
     *
     * For trees where only a single item may be selected at a time, this can be
     * used to indicate the selected item. If multiple-selection is needed,
     * developers are expected to implement it by themselves by providing a
     * custom, selection-aware [TreeItemDecorator].
     */
    var cursorNode: T? by mutableStateOf(null)

    /**
     * Returns whether the given node is the cursor ([cursorNode]) node.
     *
     * @param node The node to check.
     */
    fun isCursor(node: T): Boolean {
        return cursorNode == node
    }

    /**
     * In trees with dynamic content, when a node is removed from the tree, this
     * function should be called to clear any state related to it, and avoid
     * memory leaks.
     */
    fun onNodeDisappeared(node: T) {
        expanded.remove(node)
        if (cursorNode == node) {
            cursorNode = null
        }
    }
}

/**
 * Returns a remembered [TreeState] with the given list of roots.
 *
 * @param roots The roots of the tree.
 */
@Composable
fun <T: TreeNode<T>> rememberTreeState(roots: List<T>): TreeState<T> {
    return rememberSaveable(roots) { TreeState(roots) }
}

/**
 * Returns a remembered [TreeState] with the given list of roots.
 *
 * @param roots The roots of the tree.
 */
@Composable
fun <T: TreeNode<T>> rememberTreeState(vararg roots: T): TreeState<T> {
    return rememberTreeState(roots.toList())
}

/**
 * Composable interface that decorates tree node item's [TreeNode.Content],
 * providing all the typical looks and behaviors of the tree.
 * For example, it is responsible for:
 * - Indenting the content by the depth.
 * - Displaying the expand/collapse indicator and reacts to pressing it.
 * - Displaying the selected state of the node.
 * - Reacting to key and mouse presses on tree nodes.
 */
fun interface TreeItemDecorator<T: TreeNode<T>> {
    /**
     * Emits the decoration and the tree node item itself.
     *
     * Must emit a single top-level element. In order to emit several elements,
     * wrap them in `Box(propagateMinConstraints = true)`.
     *
     * @param treeState The state object of the tree.
     * @param node The node for which to emit the decoration.
     * @param isExpanded Whether the node is expanded.
     * @param depth The depth of the node in the tree. Roots are at depth 0.
     * @param pageSize Returns the amount of items to skip on page-up or
     * page-down actions.
     * @param innerTreeItem The tree item content. May not be called more than
     * once.
     */
    @Composable
    fun Decoration(
        treeState: TreeState<T>,
        node: T,
        isExpanded: Boolean,
        depth: Int,
        pageSize: () -> Int,
        innerTreeItem: @Composable () -> Unit
    )
}

/**
 * The UI content for a single node.
 */
@Composable
internal fun <T: TreeNode<T>> SingleNodeContent(
    treeState: TreeState<T>,
    itemDecorator: TreeItemDecorator<T>,
    node: T,
    isExpanded: Boolean,
    depth: Int,
    pageSize: () -> Int,
) {
    itemDecorator.Decoration(
        treeState = treeState,
        node = node,
        isExpanded = isExpanded,
        depth = depth,
        pageSize = pageSize,
    ) {
        node.Content(
            treeState = treeState,
            isExpanded = isExpanded,
            depth = depth
        )
    }
}

private val TreeItemPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
private val TreeItemIndent = 16.dp
private val ExpandIndicatorSize = 16.dp

/**
 * The default [TreeItemDecorator].
 *
 * @param onLeafActivated Invoked when a leaf node is "activated"
 * (double-clicked, enter key is pressed etc.)
 */
@Suppress("ObjectLiteralToLambda")
fun <T: TreeNode<T>> defaultTreeItemDecorator(
    onLeafActivated: ((T) -> Unit)? = null,
) = object: TreeItemDecorator<T> {
    @Composable
    override fun Decoration(
        treeState: TreeState<T>,
        node: T,
        isExpanded: Boolean,
        depth: Int,
        pageSize: () -> Int,
        innerTreeItem: @Composable (() -> Unit)
    ) {
        val focusRequester = remember { FocusRequester() }
        Box(
            modifier = Modifier
                .treeItemCursorIndication(treeState, node)
                .treeItemFocusInteractions(treeState, node, focusRequester)
                .treeItemKeyInteractions(treeState, node, pageSize, onLeafActivated)
                .treeItemMouseInteractions(treeState, node, focusRequester, onLeafActivated)
                .padding(TreeItemPadding)
                .padding(start = TreeItemIndent * depth)
                .fillMaxWidth(),
            propagateMinConstraints = true
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpandCollapseIndicator(
                    isExpanded = isExpanded,
                    isLeaf = node.isLeaf,
                    modifier = Modifier
                        .pointerInput(treeState, node) {
                            detectTapGestures(
                                onPress = {
                                    if (!node.isLeaf) {
                                        treeState.toggleExpanded(node)
                                    }
                                }
                            )
                        }
                )
                innerTreeItem()
            }
        }
    }
}

/**
 * The expand/collapse widget used in the default [TreeItemDecorator].
 */
@Composable
private fun ExpandCollapseIndicator(
    isExpanded: Boolean,
    isLeaf: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ExpandIndicatorSize),
        contentAlignment = Alignment.Center,
    ) {
        if (!isLeaf) {
            Text(
                text = if (isExpanded) "－" else "＋",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                modifier = Modifier
                    .semantics { hideFromAccessibility() }
            )
        }
    }
}

/**
 * Returns either the given [TreeItemDecorator] or if it's null - the default
 * one.
 */
@Composable
internal fun <T: TreeNode<T>> TreeItemDecorator<T>?.orDefault() =
    this ?: remember { defaultTreeItemDecorator<T>() }

/**
 * Returns a [Modifier] responsible for managing the keyboard focus of the tree
 * widget.
 *
 * It is responsible for setting [TreeState.hasFocus].
 */
internal fun Modifier.treeFocusInteractions(
    treeState: TreeState<*>
): Modifier {
    return this
        .onFocusChanged { treeState.hasFocus = it.hasFocus }
}

/**
 * Returns a [Modifier] responsible for managing the keyboard focus of a
 * [TreeItemDecorator].
 *
 * It is responsible for synchronizing between keyboard focus and
 * [TreeState.cursorNode].
 */
@Composable
internal fun <T: TreeNode<T>> Modifier.treeItemFocusInteractions(
    treeState: TreeState<T>,
    node: T,
    focusRequester: FocusRequester,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(treeState, node) {
        // When this node becomes the cursor, make it focused
        snapshotFlow { treeState.isCursor(node) }.collect { isCursor ->
            if (isCursor && !isFocused && treeState.hasFocus) {
                focusRequester.requestFocus()
            }
        }
    }
    return this
        .focusRequester(focusRequester)
        .onFocusChanged {
            isFocused = it.isFocused
            // When this element becomes focused, make it the cursor node
            if (it.isFocused && !treeState.isCursor(node)) {
                treeState.cursorNode = node
            }
        }
        .focusable()
}

/**
 * Returns a [Modifier] responsible for reacting to standard tree key shortcuts.
 */
internal fun <T: TreeNode<T>> Modifier.treeItemKeyInteractions(
    treeState: TreeState<T>,
    node: T,
    pageSize: () -> Int,
    onLeafActivated: ((T) -> Unit)?,
): Modifier {
    return this
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (event.isAltPressed ||
                event.isCtrlPressed ||
                event.isMetaPressed ||
                event.isShiftPressed) {
                return@onKeyEvent false
            }

            with(treeState) {
                when (event.key) {
                    Key.Enter -> {  // Activate or toggle expanded state
                        if (node.isLeaf) {
                            if (onLeafActivated != null) {
                                onLeafActivated(node)
                            } else {
                                return@onKeyEvent false
                            }
                        } else {
                            toggleExpanded(node)
                        }
                    }

                    Key.DirectionRight -> {  // Expand or move to the next node
                        if (node.isLeaf || isExpanded(node)) {
                            cursorNode = successorNode(node)
                        } else {
                            expand(node)
                        }
                    }

                    Key.DirectionLeft -> {  // Collapse or move to the parent
                        if (isExpanded(node)) {
                            collapse(node)
                        } else {
                            val target = node.parent ?: roots.first()
                            cursorNode = target
                        }
                    }

                    Key.DirectionDown -> {  //  Move to the following node
                        cursorNode = successorNode(node)
                    }

                    Key.DirectionUp -> {  // Move to the preceding node
                        cursorNode = predecessorNode(node)
                    }

                    Key.PageDown -> {  // Move page-full nodes down
                        cursorNode = successorNode(node, offset = pageSize())
                    }

                    Key.PageUp -> {  // Move page-full nodes up
                        cursorNode = predecessorNode(node, offset = pageSize())
                    }

                    Key.MoveHome -> {  // Move to the very top
                        cursorNode = roots.first()
                    }

                    Key.MoveEnd -> {  // Move to the very bottom
                        cursorNode = lastVisibleNode()
                    }

                    else -> return@onKeyEvent false
                }
                return@onKeyEvent true
            }
        }
}

/**
 * Returns a modifier responsible for reacting to standard mouse events.
 */
internal fun <T: TreeNode<T>> Modifier.treeItemMouseInteractions(
    treeState: TreeState<T>,
    node: T,
    focusRequester: FocusRequester,
    onLeafActivated: ((T) -> Unit)?,
): Modifier {
    return this
        .pointerInput(treeState, node) {
            detectTapGestures(
                onDoubleTap = {
                    if (node.isLeaf) {
                        onLeafActivated?.invoke(node)
                    } else {
                        treeState.toggleExpanded(node)
                    }
                },
                onPress = {
                    treeState.cursorNode = node
                    focusRequester.requestFocus()
                }
            )
        }
}

/**
 * Returns a modifier that draws the cursor indication on the tree item.
 */
@Composable
internal fun <T: TreeNode<T>> Modifier.treeItemCursorIndication(
    treeState: TreeState<T>,
    node: T,
): Modifier {
    // Ideally, it should use [Indication], but they are not displayed in touch
    // input mode on the desktop. Also, [Indication] doesn't support an
    // unfocused but selected state.

    val selectionColor = LocalTextSelectionColors.current.backgroundColor
    val isCursor by remember(treeState, node) {
        derivedStateOf { treeState.isCursor(node) }
    }
    if (!isCursor) return this

    return this
        .background(
            color = if (treeState.hasFocus) {
                selectionColor
            } else {
                Color.Black.copy(alpha = 0.25f)
            }
        )
}

/**
 * Returns a node that succeeds [node] by [offset] nodes.
 *
 * @param node The original node.
 * @param offset The distance of the returned node from [node].
 */
fun <T: TreeNode<T>> TreeState<T>.successorNode(
    node: T,
    offset: Int = 1
): T {
    val stack = ArrayDeque(listOf(node))
    var remainingSteps = offset
    while (remainingSteps > 0) {
        val next = stack.removeFirst()
        if (isExpanded(next)) {
            stack.addAll(0, next.children)
        }
        remainingSteps -= 1

        // If the stack is empty, go up until we find older siblings,
        // then add them to the stack
        var currentNode = next
        var parentNode = next.parent
        while (stack.isEmpty()) {
            val siblings = parentNode?.children ?: roots
            val indexOfCurrentNode = siblings.indexOf(currentNode)
            if (indexOfCurrentNode < siblings.lastIndex) {
                stack.addAll(0, siblings.subList(indexOfCurrentNode + 1, siblings.size))
            } else if (parentNode != null) {
                currentNode = parentNode
                parentNode = parentNode.parent
            } else {
                return next
            }
        }
    }
    return stack.first()
}

/**
 * Returns the oldest unexpanded descendent of [node], or [node] itself if it is
 * not expanded.
 */
private fun <T: TreeNode<T>> TreeState<T>.oldestUnexpandedDescendentOrSelf(
    node: T
): T {
    var currentNode = node
    while (isExpanded(currentNode)) {
        currentNode = currentNode.children.lastOrNull() ?: break
    }
    return currentNode
}

/**
 * Returns a node that precedes [node] by [offset] nodes.
 *
 * @param node The original node.
 * @param offset The distance of the returned node from [node].
 */
fun <T: TreeNode<T>> TreeState<T>.predecessorNode(
    node: T,
    offset: Int = 1
): T {
    var remainingSteps = offset
    var currentNode = node
    while (remainingSteps > 0) {
        val siblings = currentNode.parent?.children ?: roots
        var indexOfCurrentNode = siblings.indexOf(currentNode)
        if (indexOfCurrentNode == 0) {
            currentNode = currentNode.parent ?: return currentNode
            remainingSteps -= 1
        } else {
            while ((indexOfCurrentNode > 0) && (remainingSteps > 0)){
                currentNode = siblings[indexOfCurrentNode-1]
                remainingSteps -= 1
                indexOfCurrentNode -= 1
                if (isExpanded(currentNode)) {
                    currentNode = oldestUnexpandedDescendentOrSelf(currentNode)
                    break
                }
            }
        }
    }

    return currentNode
}

/**
 * Returns the last visible tree node.
 */
fun <T: TreeNode<T>> TreeState<T>.lastVisibleNode(): T {
    return oldestUnexpandedDescendentOrSelf(roots.last())
}

/**
 * Returns the sequence of visible tree nodes if it is presented as a list.
 *
 * This is the depth-first order of visible nodes. A node is visible if it is
 * a root node, or is a child of an expanded node.
 */
fun <T: TreeNode<T>> TreeState<T>.visibleNodes(): Sequence<T> = sequence {
    val stack = ArrayDeque(roots)
    while (stack.isNotEmpty()) {
        val node = stack.removeFirst()
        yield(node)
        if (isExpanded(node)) {
            stack.addAll(0, node.children)
        }
    }
}
