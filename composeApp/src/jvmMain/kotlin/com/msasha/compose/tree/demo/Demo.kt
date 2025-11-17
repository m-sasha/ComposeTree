package com.msasha.compose.tree.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.singleWindowApplication
import androidx.compose.ui.zIndex
import com.msasha.compose.tree.TreeNode
import com.msasha.compose.tree.TreeState
import kotlinx.coroutines.CoroutineScope


@Immutable
private data class Page(
    val title: String,
    val content: @Composable (CoroutineScope, Modifier) -> Unit
) {
    override fun toString() = title
}

private val PAGES = listOf (
    Page("Column Tree") { _, modifier -> ColumnTreeDemo(modifier) },
    Page("Lazy Column Tree") { _, modifier -> LazyColumnTreeDemo(modifier) },
    Page("File Tree", ::FileTreeDemo)
)

@Composable
private fun PageList(
    selectedItem: MutableState<Page>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(IntrinsicSize.Max)
            .semantics {
                isTraversalGroup = true
            }
    ) {
        for (item in PAGES) {
            PageListItem(
                item = item,
                isSelected = selectedItem.value == item,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            selectedItem.value = item
                        }
                    },
                onClick = { selectedItem.value = item }
            )
        }
    }
}

@Composable
private fun PageListItem(
    item: Page,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val selectionColor = LocalTextSelectionColors.current.backgroundColor
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSelected) {
        if (isSelected && !isFocused) {
            focusRequester.requestFocus()
        }
    }
    Text(
        text = item.toString(),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        focusRequester.requestFocus()
                        onClick()
                    }
                )
            }
            .then(
                if (isSelected) {
                    Modifier.background(
                        color = if (isFocused) {
                            selectionColor
                        } else {
                            Color.Black.copy(alpha = 0.25f)
                        }
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        maxLines = 1,
    )
}

fun main() = singleWindowApplication(
    title = "Tree Demo",
) {
    Row(Modifier.fillMaxSize()) {
        val selectedDemo = remember { mutableStateOf(PAGES.first()) }
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(200.dp)
                .zIndex(1f),
            shadowElevation = 4.dp,
        ) {
            PageList(
                selectedItem = selectedDemo,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
        val saveableStateHolder = rememberSaveableStateHolder()
        val coroutineScope = rememberCoroutineScope()
        saveableStateHolder.SaveableStateProvider(selectedDemo.value) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .zIndex(-1f)
            ) {
                selectedDemo.value.content(coroutineScope, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun <T: TreeNode<T>> TreeDemo(
    title: String,
    treeState: TreeState<T>,
    modifier: Modifier = Modifier,
    tree: @Composable (Modifier, treeState: TreeState<T>) -> Unit
) {
    Column(modifier) {
        Surface(shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .alignByBaseline()
                )

                Spacer(modifier = Modifier.weight(1f))

                SmallCapsButton(
                    text = "Expand All",
                    onClick = {
                        treeState.roots.forEach {
                            treeState.expandWithDescendents(it)
                        }
                    },
                    modifier = Modifier.alignByBaseline()
                )
                SmallCapsButton(
                    text = "Collapse All",
                    onClick = {
                        treeState.roots.forEach {
                            treeState.collapse(it)
                        }
                    },
                    modifier = Modifier.alignByBaseline()
                )
            }
        }

        tree(
            Modifier.fillMaxWidth(),
            treeState
        )
    }
}

@Composable
private fun SmallCapsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .focusProperties {
                canFocus = false  // Don't steal focus from the tree
            },
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
