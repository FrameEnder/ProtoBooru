package com.frameender.protobooru.ui.posts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.GridStyle
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.CountLabel
import com.frameender.protobooru.ui.common.InfiniteScroll
import com.frameender.protobooru.ui.common.ListFooter
import com.frameender.protobooru.ui.common.PagedStates
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor
import com.frameender.protobooru.ui.theme.safetyColor

@Composable
fun PostsScreen(
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpenPost: (id: Int) -> Unit,
    onUpload: () -> Unit,
    vm: PostsViewModel = viewModel(),
) {
    val settings by Graph.settings.collectAsState()
    val info by Graph.info.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var selMenu by remember { mutableStateOf(false) }
    var bulkEdit by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val canUpload = (settings.loggedIn && Graph.can("posts:create:identified")) || Graph.can("posts:create:anonymous")

    Scaffold(
        contentWindowInsets = screenInsets(),
        floatingActionButton = {
            if (canUpload && !vm.selecting) {
                FloatingActionButton(onClick = onUpload, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.CloudUpload, "Upload")
                }
            }
        },
        topBar = {
            if (vm.selecting) {
                TopAppBar(
                    title = { Text("${vm.selected.size} selected") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = vm::selectAllLoaded) { Icon(Icons.Default.SelectAll, "Select all loaded") }
                        if (settings.loggedIn) {
                            IconButton(onClick = { vm.favoriteSelected(true) }) { Icon(Icons.Default.Favorite, "Favorite selected") }
                            IconButton(onClick = { vm.favoriteSelected(false) }) { Icon(Icons.Default.FavoriteBorder, "Unfavorite selected") }
                        }
                        IconButton(onClick = vm::downloadSelected) { Icon(Icons.Default.Download, "Download selected") }
                        if (settings.loggedIn) {
                            Box {
                                IconButton(onClick = { selMenu = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                                DropdownMenu(selMenu, onDismissRequest = { selMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Edit selected…") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = { selMenu = false; bulkEdit = true },
                                    )
                                    if (Graph.can("posts:delete")) {
                                        DropdownMenuItem(
                                            text = { Text("Delete selected…", color = Ink.Red) },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Ink.Red) },
                                            onClick = { selMenu = false; confirmBulkDelete = true },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink.Surface2),
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(info?.config?.name ?: "Posts", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (vm.loader.loadedOnce) {
                                Text(
                                    "${Format.count(vm.loader.total)} posts · ${vm.sort.label.lowercase()}",
                                    style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                                )
                            }
                        }
                    },
                    navigationIcon = { if (canGoBack) BackButton(onBack) },
                    actions = {
                        Box {
                            IconButton(onClick = { filterMenu = true }) { Icon(Icons.Default.FilterList, "Quick filters") }
                            QuickFilterMenu(filterMenu, settings, onDismiss = { filterMenu = false }) { term ->
                                filterMenu = false
                                if (term == null) vm.search("") else vm.addTerm(term)
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                            DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                                SORTS.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label, color = if (s == vm.sort) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                                        onClick = { sortMenu = false; vm.setSortOption(s) },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            Graph.updateSettings {
                                it.copy(gridStyle = if (it.gridStyle == GridStyle.SQUARE) GridStyle.STAGGERED else GridStyle.SQUARE)
                            }
                        }) {
                            Icon(if (settings.gridStyle == GridStyle.SQUARE) Icons.Default.ViewQuilt else Icons.Default.GridView, "Grid style")
                        }
                    },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchArea(vm)
            SafetyRow(settings) { vm.refresh() }
            PullToRefreshBox(isRefreshing = vm.loader.refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
                PagedStates(vm.loader, "No posts match this search.") {
                    PostGrid(
                        posts = vm.loader.items,
                        settings = settings,
                        selected = vm.selected,
                        footer = { ListFooter(vm.loader) },
                        onLoadMore = vm.loader::loadMore,
                        onClick = { p ->
                            if (vm.selecting) vm.toggleSelect(p.id)
                            else {
                                Graph.viewerSource = vm.source
                                onOpenPost(p.id)
                            }
                        },
                        onLongClick = { p -> vm.toggleSelect(p.id) },
                    )
                }
            }
        }
    }
    BulkDialogs(vm, bulkEdit, confirmBulkDelete, onCloseEdit = { bulkEdit = false }, onCloseDelete = { confirmBulkDelete = false })
}

@Composable
private fun BulkDialogs(vm: PostsViewModel, bulkEdit: Boolean, confirmDelete: Boolean, onCloseEdit: () -> Unit, onCloseDelete: () -> Unit) {
    if (bulkEdit) BulkEditDialog(vm.selected.size, onDismiss = onCloseEdit) { ops -> vm.bulkEdit(ops) }
    if (confirmDelete) {
        com.frameender.protobooru.ui.common.ConfirmDialog(
            title = "Delete ${vm.selected.size} posts?",
            text = "They're deleted one by one in the background. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = onCloseDelete,
            onConfirm = vm::deleteSelected,
        )
    }
}

@Composable
private fun SearchArea(vm: PostsViewModel) {
    val cats by Graph.tagCategories.collectAsState()
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        SearchField(
            value = vm.text,
            onValueChange = vm::onTextChange,
            onSearch = { vm.search() },
            placeholder = "tags, -exclude, sort:score, fav:me…",
        )
        if (vm.suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Ink.Surface2,
                border = BorderStroke(1.dp, Ink.Line),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 280.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    vm.suggestions.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.applySuggestion(t) }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(cats[t.category]?.color)))
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(Format.count(t.usages), style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyRow(settings: AppSettings, onChanged: () -> Unit) {
    val info by Graph.info.collectAsState()
    if (info?.config?.enableSafety == false) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("safe", "sketchy", "unsafe").forEach { s ->
            val on = when (s) { "safe" -> settings.showSafe; "sketchy" -> settings.showSketchy; else -> settings.showUnsafe }
            FilterChip(
                selected = on,
                onClick = {
                    Graph.updateSettings {
                        when (s) {
                            "safe" -> it.copy(showSafe = !it.showSafe)
                            "sketchy" -> it.copy(showSketchy = !it.showSketchy)
                            else -> it.copy(showUnsafe = !it.showUnsafe)
                        }
                    }
                    onChanged()
                },
                label = { Text(s, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(safetyColor(s))) },
            )
        }
    }
}

@Composable
private fun QuickFilterMenu(open: Boolean, settings: AppSettings, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    DropdownMenu(open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Clear search") }, onClick = { onPick(null) })
        HorizontalDivider()
        if (settings.loggedIn) {
            val me = settings.username
            DropdownMenuItem(text = { Text("My favorites") }, onClick = { onPick("fav:$me") })
            DropdownMenuItem(text = { Text("My uploads") }, onClick = { onPick("uploader:$me") })
            DropdownMenuItem(text = { Text("Liked by me") }, onClick = { onPick("special:liked") })
            DropdownMenuItem(text = { Text("Disliked by me") }, onClick = { onPick("special:disliked") })
            HorizontalDivider()
        }
        DropdownMenuItem(text = { Text("Images only") }, onClick = { onPick("type:image") })
        DropdownMenuItem(text = { Text("Animations only") }, onClick = { onPick("type:animation") })
        DropdownMenuItem(text = { Text("Videos only") }, onClick = { onPick("type:video") })
        DropdownMenuItem(text = { Text("Untagged-ish (≤ 2 tags)") }, onClick = { onPick("tag-count:..2") })
        DropdownMenuItem(text = { Text("Tumbleweeds (no score/faves/comments)") }, onClick = { onPick("special:tumbleweed") })
        DropdownMenuItem(text = { Text("Has notes") }, onClick = { onPick("note-count:1..") })
        DropdownMenuItem(text = { Text("Has comments") }, onClick = { onPick("comment-count:1..") })
    }
}

/** Shared thumbnail grid used by search results, pools, and similar-image results. */
@Composable
fun PostGrid(
    posts: List<Post>,
    settings: AppSettings,
    selected: Set<Int> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(8.dp),
    footer: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onLoadMore: () -> Unit = {},
    onLongClick: ((Post) -> Unit)? = null,
    onClick: (Post) -> Unit,
) {
    val cols = settings.gridColumns.coerceIn(1, 8)
    val gap = 6.dp
    if (settings.gridStyle == GridStyle.STAGGERED) {
        val state = rememberLazyStaggeredGridState()
        InfiniteScroll(state, onLoadMore = onLoadMore)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(cols),
            state = state,
            contentPadding = contentPadding,
            verticalItemSpacing = gap,
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (header != null) item(span = StaggeredGridItemSpan.FullLine) { header() }
            items(posts, key = { it.id }) { p ->
                PostTile(p, settings, p.id in selected, Modifier.aspectRatio(p.aspect.coerceIn(0.4f, 2.5f)), onClick, onLongClick)
            }
            if (footer != null) item(span = StaggeredGridItemSpan.FullLine) { footer() }
        }
    } else {
        val state = rememberLazyGridState()
        InfiniteScroll(state, onLoadMore = onLoadMore)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
            items(posts, key = { it.id }) { p ->
                PostTile(p, settings, p.id in selected, Modifier.aspectRatio(1f), onClick, onLongClick)
            }
            if (footer != null) item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}

@Composable
private fun PostTile(
    p: Post,
    settings: AppSettings,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: (Post) -> Unit,
    onLongClick: ((Post) -> Unit)?,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = { onClick(p) }, onLongClick = onLongClick?.let { cb -> { cb(p) } }),
    ) {
        RemoteImage(p.thumbnailUrl, Modifier.fillMaxSize())

        if (settings.showGridBadges) {
            // Type badge
            val typeIcon = when {
                p.isVideo -> Icons.Default.PlayArrow
                p.isAnimation -> Icons.Default.Gif
                p.isFlash -> Icons.Default.Widgets
                else -> null
            }
            // Type + related-posts badges, top-left
            Row(
                Modifier.align(Alignment.TopStart).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (typeIcon != null) {
                    Icon(
                        typeIcon, p.type,
                        tint = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(2.dp).size(16.dp),
                    )
                }
                if (p.relationCount > 0) {
                    Row(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Ink.Amber.copy(alpha = 0.9f)).padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Link, "Has related posts", tint = Ink.OnAmber, modifier = Modifier.size(14.dp))
                        Text(
                            p.relationCount.toString(),
                            color = Ink.OnAmber,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
            // Safety stripe
            if (p.safety != "safe") {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(CircleShape).background(safetyColor(p.safety)))
            }
            // Bottom counters
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (p.score != 0) CountLabel(Icons.Default.ThumbUp, p.score, tint = Color.White.copy(alpha = 0.85f))
                if (p.favoriteCount > 0) {
                    CountLabel(
                        Icons.Default.Favorite, p.favoriteCount,
                        tint = if (p.ownFavorite) Ink.Amber else Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle, "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            )
        }
    }
}
