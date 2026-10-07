package com.frameender.protobooru.ui.pools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Pool
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.StaticPostSource
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ConfirmDialog
import com.frameender.protobooru.ui.common.ErrorBox
import com.frameender.protobooru.ui.common.InfiniteScroll
import com.frameender.protobooru.ui.common.ListFooter
import com.frameender.protobooru.ui.common.LoadingBox
import com.frameender.protobooru.ui.common.PagedLoader
import com.frameender.protobooru.ui.common.PagedStates
import com.frameender.protobooru.ui.common.PoolPickerDialog
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.posts.PostGrid
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor
import kotlinx.coroutines.launch

// ===================== Pool list =====================

class PoolsViewModel : ViewModel() {
    var text by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
        private set

    val loader = PagedLoader(viewModelScope, pageSize = { 30 }) { offset, limit ->
        val parts = mutableListOf<String>()
        val t = text.trim()
        if (t.isNotEmpty()) parts += if (t.contains(':') || t.contains('*')) t else "*${t.replace(' ', '_')}*"
        category?.let { parts += "category:$it" }
        parts += "sort:last-edit-time"
        Graph.api.pools(parts.joinToString(" "), offset, limit)
    }

    init {
        loader.refresh()
        viewModelScope.launch { Graph.poolChanged.collect { loader.refresh() } }
    }

    fun search() = loader.refresh()
    fun pickCategory(c: String?) { category = c; loader.refresh() }
}

@Composable
fun PoolsScreen(
    onOpenPool: (Int) -> Unit,
    onNewPool: () -> Unit,
    onCategories: () -> Unit,
    vm: PoolsViewModel = viewModel(),
) {
    val cats by Graph.poolCategories.collectAsState()
    val settings by Graph.settings.collectAsState()
    val listState = rememberLazyListState()
    InfiniteScroll(listState, onLoadMore = vm.loader::loadMore)

    Scaffold(
        contentWindowInsets = screenInsets(),
        floatingActionButton = {
            if (settings.loggedIn && Graph.can("pools:create")) {
                FloatingActionButton(onClick = onNewPool, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "New pool")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pools")
                        if (vm.loader.loadedOnce) Text("${vm.loader.total} pools", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    }
                },
                actions = { IconButton(onClick = onCategories) { Icon(Icons.Default.Category, "Pool categories") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchField(
                value = vm.text,
                onValueChange = { vm.text = it },
                onSearch = vm::search,
                placeholder = "Search pool names…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (cats.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = vm.category == null, onClick = { vm.pickCategory(null) }, label = { Text("all") })
                    cats.values.forEach { c ->
                        FilterChip(
                            selected = vm.category == c.name,
                            onClick = { vm.pickCategory(if (vm.category == c.name) null else c.name) },
                            label = { Text(c.name) },
                        )
                    }
                }
            }
            PullToRefreshBox(isRefreshing = vm.loader.refreshing, onRefresh = vm.loader::refresh, modifier = Modifier.weight(1f)) {
                PagedStates(vm.loader, "No pools found.") {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.loader.items, key = { it.id }) { p -> PoolCard(p) { onOpenPool(p.id) } }
                        item { ListFooter(vm.loader) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolCard(p: Pool, onClick: () -> Unit) {
    val cats by Graph.poolCategories.collectAsState()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Ink.Surface,
        border = BorderStroke(1.dp, Ink.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Up to three stacked covers
            Box(Modifier.size(width = 96.dp, height = 84.dp)) {
                p.posts.take(3).reversed().forEachIndexed { i, mp ->
                    val n = minOf(p.posts.size, 3)
                    val depth = n - 1 - i
                    RemoteImage(
                        mp.thumbnailUrl,
                        Modifier
                            .padding(start = (depth * 10).dp, top = ((n - 1 - depth) * 4).dp)
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name.replace('_', ' '), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(cats[p.category]?.color)))
                    Spacer(Modifier.width(6.dp))
                    Text("${p.category} · ${p.postCount} posts", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                }
                if (!p.description.isNullOrBlank()) {
                    Text(p.description, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ===================== Pool detail =====================

class PoolDetailViewModel(handle: SavedStateHandle) : ViewModel() {
    val id: Int = handle.get<String>("id")?.toIntOrNull() ?: 0
    var pool by mutableStateOf<Pool?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var gone by mutableStateOf(false)
        private set

    init {
        load()
        viewModelScope.launch { Graph.poolChanged.collect { if (it == id && !gone) load() } }
    }

    fun delete() {
        val p = pool ?: return
        viewModelScope.launch {
            try {
                Graph.api.deletePool(Graph.api.pool(p.id))
                gone = true
                Graph.poolChanged.tryEmit(p.id)
                Graph.toast("Deleted pool #${p.id}")
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Delete failed")
            }
        }
    }

    fun mergeInto(target: Pool, onMerged: (Int) -> Unit) {
        val p = pool ?: return
        viewModelScope.launch {
            try {
                val merged = Graph.api.mergePools(Graph.api.pool(p.id), Graph.api.pool(target.id))
                Graph.poolChanged.tryEmit(p.id)
                Graph.poolChanged.tryEmit(merged.id)
                Graph.toast("Merged into pool #${merged.id}")
                onMerged(merged.id)
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Merge failed")
            }
        }
    }

    fun load() {
        error = null
        viewModelScope.launch {
            try {
                pool = Graph.api.pool(id)
            } catch (e: Exception) {
                error = e.message ?: "Could not load pool"
            }
        }
    }
}

@Composable
fun PoolDetailScreen(
    onBack: () -> Unit,
    onOpenPost: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onEdit: (Int) -> Unit,
    onOpenPool: (Int) -> Unit,
    vm: PoolDetailViewModel = viewModel(),
) {
    val settings by Graph.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm.gone) { if (vm.gone) onBack() }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text(vm.pool?.name?.replace('_', ' ') ?: "Pool #${vm.id}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    val p = vm.pool
                    if (p != null && settings.loggedIn) {
                        if (Graph.can("pools:edit:names") || Graph.can("pools:edit:posts")) {
                            IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Default.Edit, "Edit pool") }
                        }
                        if (Graph.can("pools:merge") || Graph.can("pools:delete")) {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    if (Graph.can("pools:merge")) DropdownMenuItem(text = { Text("Merge into…") }, onClick = { menu = false; merging = true })
                                    if (Graph.can("pools:delete")) DropdownMenuItem(text = { Text("Delete pool", color = Ink.Red) }, onClick = { menu = false; confirmDelete = true })
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        val p = vm.pool
        when {
            p == null && vm.error != null -> ErrorBox(vm.error!!, Modifier.padding(pad), onRetry = vm::load)
            p == null -> LoadingBox(Modifier.padding(pad))
            else -> Box(Modifier.padding(pad)) {
                val posts = p.posts.map { Post(id = it.id, thumbnailUrl = it.thumbnailUrl) }
                PostGrid(
                    posts = posts,
                    settings = settings.copy(showGridBadges = false, gridStyle = com.frameender.protobooru.data.GridStyle.SQUARE),
                    header = {
                        Column(Modifier.padding(4.dp)) {
                            Text("${p.category} · ${p.postCount} posts · updated ${Format.ago(p.lastEditTime)}", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                            if (p.names.size > 1) Text("aka " + p.names.drop(1).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
                            if (!p.description.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(p.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSearch("pool:${p.id}") }) {
                                    Icon(Icons.Default.Search, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open as search", maxLines = 1)
                                }
                                OutlinedButton(onClick = { Graph.downloads.enqueue(p.posts.map { it.id }) }) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download all", maxLines = 1)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    },
                ) { post ->
                    Graph.viewerSource = StaticPostSource(p.posts.map { it.id }, "pool:${p.id}")
                    onOpenPost(post.id)
                }
            }
        }
    }
    if (merging) {
        PoolPickerDialog(onDismiss = { merging = false }) { target ->
            if (target.id != vm.id) vm.mergeInto(target, onOpenPool)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete pool #${vm.id}?",
            text = "The posts stay; only the pool is removed.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = vm::delete,
        )
    }
}
