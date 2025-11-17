package com.msasha.compose.tree.demo

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.msasha.compose.tree.BasicLazyColumnTree
import com.msasha.compose.tree.TreeNode
import com.msasha.compose.tree.TreeState
import com.msasha.compose.tree.defaultTreeItemDecorator
import com.msasha.compose.tree.rememberTreeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun FileTreeDemo(
    coroutineScope: CoroutineScope,
    modifier: Modifier
) {
    val treeState = fileTreeState(
        rootFilename = System.getProperty("user.home"),
        coroutineScope = coroutineScope,
    )
    TreeDemo(
        title = "File Tree",
        treeState = treeState,
        modifier = modifier
    ) { modifier, treeState ->
        val lazyListState = rememberLazyListState()
        Box(modifier) {
            BasicLazyColumnTree(
                state = treeState,
                modifier = Modifier.fillMaxSize(),
                lazyListState = lazyListState,
                itemDecorator = defaultTreeItemDecorator { node ->
                    if (node is FileNode) {
                        runCatching {
                            Desktop.getDesktop().open(node.file)
                        }
                    }
                }
            )
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).matchParentSize(),
            )
        }
    }
}

private sealed interface FileTreeNode: TreeNode<FileTreeNode>

private data class LoadingDirectoryNode(
    override val parent: FileNode?,
): FileTreeNode {
    override val isLeaf: Boolean
        get() = true

    override val children: List<FileTreeNode>
        get() = emptyList()

    @Composable
    override fun Content(
        treeState: TreeState<FileTreeNode>,
        isExpanded: Boolean,
        depth: Int
    ) {
        Text("Loading…")
    }

    override fun toString() = "Loading children of $parent"
}

private class FileTreeContext(
    var coroutineScope: CoroutineScope,
    val treeState: () -> TreeState<FileTreeNode>,
)

private class FileNode(
    val file: File,
    override val parent: FileNode?,
    val context: FileTreeContext,
): FileTreeNode {

    override val isLeaf: Boolean by lazy {
        !file.isDirectory
    }

    private var childrenLoadingJob: Job? = null

    private var _children: List<FileTreeNode> by mutableStateOf(
        listOf(LoadingDirectoryNode(this))
    )

    override val children: List<FileTreeNode>
        get() {
            if (childrenLoadingJob == null) {
                childrenLoadingJob = loadChildren()
            }

            return _children
        }

    private fun loadChildren() = context.coroutineScope.launch {
        val fileList = withContext(Dispatchers.IO) {
            delay(0.5.seconds)  // Simulate the listing taking some time
            file.listFiles { !it.isHidden }.sorted()
        }

        // Notify TreeState that the LoadingDirectoryNode disappeared,
        // and move the cursor to this node, if needed.
        val treeState = context.treeState()
        val childIsCursorNode = _children.any { treeState.isCursor(it) }
        _children.forEach {
            treeState.onNodeDisappeared(it)
        }
        if (childIsCursorNode) {
            treeState.cursorNode = this@FileNode
        }
        _children = fileList.map {
            FileNode(
                file = it,
                parent = this@FileNode,
                context = context,
            )
        }
    }

    @Composable
    override fun Content(
        treeState: TreeState<FileTreeNode>,
        isExpanded: Boolean,
        depth: Int
    ) {
        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileNode) return false

        return (other.file == file) && (other.parent == parent)
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + (parent?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FileNode(${file.path})"
    }
}

@Composable
private fun fileTreeState(
    rootFilename: String,
    coroutineScope: CoroutineScope,
): TreeState<FileTreeNode> {
    var treeState by remember { mutableStateOf<TreeState<FileTreeNode>?>(null) }
    val context = remember {
        FileTreeContext(
            coroutineScope = coroutineScope,
            treeState = { treeState!! }
        )
    }
    return rememberTreeState(
        FileNode(
            file = File(rootFilename),
            parent = null,
            context = context,
        )
    ).also { treeState = it }
}