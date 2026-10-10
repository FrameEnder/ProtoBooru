package com.frameender.protobooru.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.LaunchedEffect
import com.frameender.protobooru.ui.common.ConfirmDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Tag
import com.frameender.protobooru.data.TagSibling
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ErrorBox
import com.frameender.protobooru.ui.common.InfiniteScroll
import com.frameender.protobooru.ui.common.KeyValue
import com.frameender.protobooru.ui.common.ListFooter
import com.frameender.protobooru.ui.common.LoadingBox
import com.frameender.protobooru.ui.common.PagedLoader
import com.frameender.protobooru.ui.common.PagedStates
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagChip
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

// ===================== Tag list =====================

class TagsViewModel : ViewModel() {
    var text by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
        private set
    var sort by mutableStateOf("usages")
        private set

    val loader = PagedLoader(viewModelScope) { offset, limit ->
        if (Graph.offlineMode) {
            // Offline: the tags on saved posts, counted on the phone.
            val t = text.trim().lowercase()
            val names = if (t.isEmpty()) "" else if (t.contains(':') || t.contains('*')) t else "*${t.replace(' ', '_')}*"
            withContext(Dispatchers.Default) { Graph.library.searchTags(names, category, sort, offset, limit) }
        } else {
            Graph.api.tags(buildQuery(), offset, limit)
        }
    }

    private fun buildQuery(): String {
        val parts = mutableListOf<String>()
        val t = text.trim()
        if (t.isNotEmpty()) {
            // Plain words become a wildcard name match; anything with ':' is passed through as syntax.
            parts += if (t.contains(':') || t.contains('*')) t else "*${t.replace(' ', '_')}*"
        }
        category?.let { parts += "category:$it" }
        parts += "sort:$sort"
        return parts.joinToString(" ")
    }

    init {
        loader.refresh()
        viewModelScope.launch { Graph.tagChanged.collect { loader.refresh() } }
        viewModelScope.launch { Graph.offlineModeChanges.collect { loader.refresh() } }
    }

    fun search() = loader.refresh()
    fun pickCategory(c: String?) { category = c; loader.refresh() }
    fun pickSort(s: String) { sort = s; loader.refresh() }
}

private val TAG_SORTS = listOf(
    "usages" to "Most used",
    "name" to "Name",
    "creation-time" to "Newest",
    "last-edit-time" to "Recently edited",
    "implication-count" to "Implications",
    "suggestion-count" to "Suggestions",
    "random" to "Random",
)

@Composable
fun TagsScreen(
    onOpenTag: (String) -> Unit,
    onNewTag: () -> Unit,
    onCategories: () -> Unit,
    vm: TagsViewModel = viewModel(),
) {
    val cats by Graph.tagCategories.collectAsState()
    val settings by Graph.settings.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    InfiniteScroll(listState, onLoadMore = vm.loader::loadMore)

    Scaffold(
        contentWindowInsets = screenInsets(),
        floatingActionButton = {
            if (settings.loggedIn && Graph.can("tags:create")) {
                FloatingActionButton(onClick = onNewTag, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "New tag")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tags")
                        if (vm.loader.loadedOnce) Text("${Format.count(vm.loader.total)} tags", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    }
                },
                actions = {
                    IconButton(onClick = onCategories) { Icon(Icons.Default.Category, "Tag categories") }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                            TAG_SORTS.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (vm.sort == key) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                                    onClick = { sortMenu = false; vm.pickSort(key) },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchField(
                value = vm.text,
                onValueChange = { vm.text = it },
                onSearch = vm::search,
                placeholder = "Search tag names…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (cats.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = vm.category == null, onClick = { vm.pickCategory(null) }, label = { Text("all") })
                    cats.values.sortedBy { it.order }.forEach { c ->
                        FilterChip(
                            selected = vm.category == c.name,
                            onClick = { vm.pickCategory(if (vm.category == c.name) null else c.name) },
                            label = { Text(c.name) },
                            leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(c.color))) },
                        )
                    }
                }
            }
            PullToRefreshBox(isRefreshing = vm.loader.refreshing, onRefresh = vm.loader::refresh, modifier = Modifier.weight(1f)) {
                PagedStates(vm.loader, "No tags found.") {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(vm.loader.items, key = { it.name }) { t -> TagRow(t) { onOpenTag(t.name) } }
                        item { ListFooter(vm.loader) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(t: Tag, onClick: () -> Unit) {
    val cats by Graph.tagCategories.collectAsState()
    val color = categoryColor(cats[t.category]?.color)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.bodyLarge, color = color)
            val sub = buildList {
                add(t.category)
                if (t.names.size > 1) add("aka " + t.names.drop(1).take(3).joinToString(", "))
                if (t.implications.isNotEmpty()) add("→ ${t.implications.size} implied")
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Ink.TextDim, maxLines = 1)
        }
        Text(Format.count(t.usages), style = MaterialTheme.typography.labelLarge, color = Ink.TextDim)
    }
    HorizontalDivider(color = Ink.Line.copy(alpha = 0.5f))
}

// ===================== Tag detail =====================

class TagDetailViewModel(handle: SavedStateHandle) : ViewModel() {
    // Navigation already URL-decodes arguments. Renames update it in place.
    var name: String by mutableStateOf(handle.get<String>("name").orEmpty())
        private set
    var tag by mutableStateOf<Tag?>(null)
        private set
    var siblings by mutableStateOf<List<TagSibling>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var gone by mutableStateOf(false)
        private set

    init {
        load()
        viewModelScope.launch { Graph.offlineModeChanges.collect { load() } }
        viewModelScope.launch {
            Graph.tagChanged.collect { (old, new) ->
                if (old.equals(name, ignoreCase = true)) {
                    if (new == null) gone = true else { name = new; load() }
                }
            }
        }
    }

    fun delete() {
        val t = tag ?: return
        viewModelScope.launch {
            try {
                Graph.api.deleteTag(Graph.api.tag(t.name))
                Graph.tagChanged.tryEmit(t.name to null)
                Graph.toast("Deleted ${t.name}")
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Delete failed")
            }
        }
    }

    fun load() {
        error = null
        viewModelScope.launch {
            try {
                if (Graph.offlineMode) {
                    // Offline: counted on the saved posts. Details (description, implications)
                    // come from the saved copy of the tag page when there is one.
                    val local = withContext(Dispatchers.Default) { Graph.library.tag(name) }
                        ?: throw java.io.IOException("No saved post has this tag")
                    val saved = runCatching { Graph.api.tag(name) }.getOrNull()
                    tag = saved?.copy(usages = local.usages) ?: local
                    siblings = withContext(Dispatchers.Default) { Graph.library.tagSiblings(name) }
                } else {
                    tag = Graph.api.tag(name)
                    siblings = runCatching { Graph.api.tagSiblings(name).results }.getOrDefault(emptyList())
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load tag"
            }
        }
    }
}

@Composable
fun TagDetailScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onEdit: (String) -> Unit,
    vm: TagDetailViewModel = viewModel(),
) {
    val cats by Graph.tagCategories.collectAsState()
    val settings by Graph.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm.gone) { if (vm.gone) onBack() }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text(vm.name) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    val t = vm.tag
                    if (t != null && settings.loggedIn) {
                        if (Graph.can("tags:edit:names") || Graph.can("tags:edit:category") || Graph.can("tags:edit:implications")) {
                            IconButton(onClick = { onEdit(t.name) }) { Icon(Icons.Default.Edit, "Edit tag") }
                        }
                        if (Graph.can("tags:merge") || Graph.can("tags:delete")) {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    if (Graph.can("tags:merge")) DropdownMenuItem(text = { Text("Merge into…") }, onClick = { menu = false; merging = true })
                                    if (Graph.can("tags:delete")) DropdownMenuItem(text = { Text("Delete tag", color = Ink.Red) }, onClick = { menu = false; confirmDelete = true })
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        val t = vm.tag
        when {
            t == null && vm.error != null -> ErrorBox(vm.error!!, Modifier.padding(pad), onRetry = vm::load)
            t == null -> LoadingBox(Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
                item {
                    val color = categoryColor(cats[t.category]?.color)
                    Text(t.name, style = MaterialTheme.typography.headlineMedium, color = color)
                    Text("${t.category} · ${Format.count(t.usages)} posts", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onSearch(t.name) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Collections, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Browse ${Format.count(t.usages)} posts")
                    }
                }
                if (!t.description.isNullOrBlank()) {
                    item {
                        SectionHeader("Description")
                        Text(t.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (t.names.size > 1) {
                    item {
                        SectionHeader("Aliases")
                        Text(t.names.drop(1).joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (t.implications.isNotEmpty()) {
                    item {
                        SectionHeader("Implies")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            t.implications.forEach { m -> TagChip(m) { onOpenTag(m.name) } }
                        }
                    }
                }
                if (t.suggestions.isNotEmpty()) {
                    item {
                        SectionHeader("Suggests")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            t.suggestions.forEach { m -> TagChip(m) { onOpenTag(m.name) } }
                        }
                    }
                }
                if (vm.siblings.isNotEmpty()) {
                    item { SectionHeader("Often appears with") }
                    items(vm.siblings, key = { "sib-" + it.tag.name }) { s ->
                        val c = categoryColor(cats[s.tag.category]?.color)
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenTag(s.tag.name) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                            Spacer(Modifier.width(10.dp))
                            Text(s.tag.name, color = c, modifier = Modifier.weight(1f))
                            Text("${s.occurrences}×", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                        }
                    }
                }
                item {
                    SectionHeader("History")
                    KeyValue("Created", Format.dateTime(t.creationTime))
                    KeyValue("Last edited", Format.dateTime(t.lastEditTime))
                    KeyValue("Version", t.version.toString())
                }
            }
        }
    }
    vm.tag?.let { t ->
        // The detail screen follows the merge itself via Graph.tagChanged.
        if (merging) TagMergeDialog(t, onDismiss = { merging = false }) { }
        if (confirmDelete) {
            ConfirmDialog(
                title = "Delete ${t.name}?",
                text = "It's removed from ${t.usages} posts. This can't be undone.",
                confirmLabel = "Delete",
                destructive = true,
                onDismiss = { confirmDelete = false },
                onConfirm = vm::delete,
            )
        }
    }
}
