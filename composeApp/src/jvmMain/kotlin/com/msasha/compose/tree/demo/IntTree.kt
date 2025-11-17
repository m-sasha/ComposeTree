package com.msasha.compose.tree.demo

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.msasha.compose.tree.*
import kotlin.math.log10


data class IntNode(
    val number: Int,
    val maxDepth: Int,
): TreeNode<IntNode> {

    private val depth = log10(number.toDouble()).toInt()

    override val parent: IntNode?
        get() = if (depth == 0) null else IntNode(number / 10, maxDepth)

    override val children: List<IntNode>
        get() = (0..9).map {
            IntNode(number = 10 * number + it, maxDepth)
        }

    override val isLeaf: Boolean
        get() = depth == maxDepth

    @Composable
    override fun Content(treeState: TreeState<IntNode>, isExpanded: Boolean, depth: Int) {
        Text(
            text = "Item $number",
            maxLines = 1,
        )
    }

    override fun toString(): String {
        return "Node $number"
    }
}

private const val COLUMN_TREE_DEPTH = 3
private const val LAZY_COLUMN_TREE_DEPTH = 5

@Composable
internal fun ColumnTreeDemo(modifier: Modifier) {
    val treeState = rememberTreeState(
        roots = (1..9).map { IntNode(it, COLUMN_TREE_DEPTH) }
    )
    TreeDemo(
        title = "Column Tree (depth=$COLUMN_TREE_DEPTH)",
        treeState = treeState,
        modifier = modifier,
    ) { innerModifier, treeState ->
        val scrollState = rememberScrollState()
        Box(innerModifier) {
            BasicColumnTree(
                state = treeState,
                modifier = Modifier
                    .fillMaxSize(),
                scrollState = scrollState,
            )
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).matchParentSize(),
            )
        }
    }
}

@Composable
internal fun LazyColumnTreeDemo(modifier: Modifier) {
    val treeState = rememberTreeState(
        roots = (1..9).map { IntNode(it, LAZY_COLUMN_TREE_DEPTH) }
    )
    TreeDemo(
        title = "Lazy Column Tree (depth=$LAZY_COLUMN_TREE_DEPTH)",
        treeState = treeState,
        modifier = modifier
    ) { innerModifier, treeState ->
        val lazyListState = rememberLazyListState()
        Box(innerModifier) {
            BasicLazyColumnTree(
                state = treeState,
                modifier = Modifier.fillMaxSize(),
                lazyListState = lazyListState,
            )
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).matchParentSize(),
            )
        }
    }
}
