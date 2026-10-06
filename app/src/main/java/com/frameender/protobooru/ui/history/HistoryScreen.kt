package com.frameender.protobooru.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Snapshot
import com.frameender.protobooru.ui.common.Avatar
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.InfiniteScroll
import com.frameender.protobooru.ui.common.ListFooter
import com.frameender.protobooru.ui.common.PagedLoader
import com.frameender.protobooru.ui.common.PagedStates
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.Mono
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class HistoryViewModel(handle: SavedStateHandle) : ViewModel() {
    var text by mutableStateOf(handle.get<String>("q").orEmpty())
    var type by mutableStateOf<String?>(null)
        private set

    val loader = PagedLoader(viewModelScope, pageSize = { 40 }) { offset, limit ->
        val q = listOfNotNull(text.trim().ifBlank { null }, type?.let { "type:$it" }).joinToString(" ")
        Graph.api.snapshots(q, offset, limit)
    }

    init { loader.refresh() }

    fun pickType(t: String?) { type = t; loader.refresh() }
}

private val pretty = Json { prettyPrint = true }

class HistoryNav(
    val back: () -> Unit,
    val openPost: (Int) -> Unit,
    val openTag: (String) -> Unit,
    val openPool: (Int) -> Unit,
    val openUser: (String) -> Unit,
)

@Composable
fun HistoryScreen(nav: HistoryNav, vm: HistoryViewModel = viewModel()) {
    val listState = rememberLazyListState()
    InfiniteScroll(listState, onLoadMore = vm.loader::loadMore)
    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text("Site history") }, navigationIcon = { BackButton(nav.back) }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchField(
                value = vm.text,
                onValueChange = { vm.text = it },
                onSearch = vm.loader::refresh,
                placeholder = "user:name, id:123, operation:modified",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(null, "post", "tag", "tag_category", "pool", "pool_category").forEach { t ->
                    FilterChip(selected = vm.type == t, onClick = { vm.pickType(t) }, label = { Text(t?.replace('_', ' ') ?: "all") })
                }
            }
            PullToRefreshBox(isRefreshing = vm.loader.refreshing, onRefresh = vm.loader::refresh, modifier = Modifier.weight(1f)) {
                PagedStates(vm.loader, "No history entries.") {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(vm.loader.items) { s -> SnapshotCard(s, nav) }
                        item { ListFooter(vm.loader) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(s: Snapshot, nav: HistoryNav) {
    var expanded by remember { mutableStateOf(false) }
    val (icon, tint) = when (s.operation) {
        "created" -> Icons.Default.AddCircle to Ink.Green
        "modified" -> Icons.Default.Edit to Ink.Amber
        "deleted" -> Icons.Default.Delete to Ink.Red
        else -> Icons.Default.MergeType to Ink.Violet
    }
    val open: (() -> Unit)? = when (s.type) {
        "post" -> s.idText.toIntOrNull()?.let { id -> { nav.openPost(id) } }
        "pool" -> s.idText.toIntOrNull()?.let { id -> { nav.openPool(id) } }
        "tag" -> if (s.operation != "deleted") ({ nav.openTag(s.idText) }) else null
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Ink.Surface,
        border = BorderStroke(1.dp, Ink.Line),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, s.operation, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${s.operation} ${s.type.replace('_', ' ')} ${if (s.type == "post" || s.type == "pool") "#" else ""}${s.idText}", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(s.user?.avatarUrl, 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (s.user?.name ?: "system") + " · " + Format.ago(s.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.TextDim,
                            modifier = Modifier.clickable { s.user?.name?.let(nav.openUser) },
                        )
                    }
                }
                if (open != null) IconButton(onClick = open) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open", tint = Ink.TextDim) }
            }
            if (expanded && s.data != null) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Ink.Bg, shape = RoundedCornerShape(6.dp)) {
                    SelectionContainer {
                        Text(
                            prettyJson(s.data),
                            fontFamily = Mono,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun prettyJson(e: JsonElement): String =
    runCatching { pretty.encodeToString(JsonElement.serializer(), e) }.getOrDefault(e.toString())
