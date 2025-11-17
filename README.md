## A tree widget for Compose Desktop

Two "basic" variants of the widget are offered, which can be used to implement a
complete version:
- `BasicColumnTree`: emits the tree nodes into a `Column`.
- `BasicLazyColumnTree`: emits the tree nodes into a `LazyColumn`.

### Customization

The main customization mechanism is providing a custom `TreeItemDecorator`.
The decorator wraps each node's content and determines the general look and
behavior of tree items (e.g. indent, expand/collapse indicator, mouse and
keyboard behaviors).

### Selection

The basic tree widgets do not implement a true item selection mechanism, as it's
considered outside the scope. It does implement the concept of a single
"cursor" node, which can be used when a single-item selection model is
sufficient.

To implement a true multi-item selection model, create your own, selection-aware
`TreeItemDecorator` and pass the selection state to it. The cursor node is still
relevant in this scenario as the node from which selection extends when e.g.
pressing shift-up.

### Known Issues

#### Node Disappearance

For dynamic trees (where nodes can appear and disappear), the tree node
implementor is responsible for notifying the tree state when a node goes away.
This is inconvenient as it requires the nodes to access the tree state, creating
an "upstream" flow of information. So a different mechanism for the state to be
notified of node disappearance should be considered.

This is easy to solve for a non-lazy tree, by simply using `DisposedEffect` in
the content of each node's decorator. For lazy trees, this is harder.

#### Node-based vs. index-based cursor/selection

The "cursor" node is backed by a reference to the actual node. This is
convenient for most cases, as the index of the node in the tree-list is not
typically interesting. But it could have unexpected side effects when the
structure of the tree changes. For example, when a node disappears, the cursor
disappears with it, rather than simply moving to the node at the same index.

#### Semantics

Semantics (for accessibility) have not been implemented yet. It needs support
from Compose Multiplatform.
