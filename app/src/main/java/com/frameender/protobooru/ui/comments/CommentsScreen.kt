package com.frameender.protobooru.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Comment
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.InfiniteScroll
import com.frameender.protobooru.ui.common.ListFooter
import com.frameender.protobooru.ui.common.PagedLoader
import com.frameender.protobooru.ui.common.PagedStates
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.frameender.protobooru.data.Blacklist

class CommentsViewModel(handle: SavedStateHandle) : ViewModel() {
    private val api = Graph.api
    var text by mutableStateOf(handle.get<String>("q").orEmpty())

    /** Post id -> thumbnail path, filled in after each page loads. */
    val thumbs = mutableStateMapOf<Int, String?>()

    val loader = PagedLoader(viewModelScope, pageSize = { 25 }) { offset, limit ->
        val q = text.trim().let { if (it.contains("sort:")) it else "$it sort:creation-time".trim() }
        val offline = Graph.offlineMode
        // Offline: the comments saved with the posts on this phone.
        val page = if (offline) withContext(Dispatchers.Default) { Graph.library.comments(q, offset, limit) }
        else api.comments(q, offset, limit)
        if (offline) page.results.forEach { c -> thumbs[c.postId] = Graph.library.post(c.postId)?.thumbnailUrl }
        val missing = if (offline) emptyList() else page.results.map { it.postId }.distinct().filter { it !in thumbs }
        if (missing.isNotEmpty()) {
            // With a blacklist set, the posts' tags come along so their comments can be hidden or blurred.
            val fields = if (Blacklist.active()) "id,thumbnailUrl,contentUrl,tags" else "id,thumbnailUrl"
            runCatching {
                api.posts("id:${missing.joinToString(",")}", 0, missing.size, fields).results
            }.getOrNull()?.forEach { thumbs[it.id] = it.thumbnailUrl }
        }
        page
    }

    init {
        loader.refresh()
        viewModelScope.launch { Graph.offlineModeChanges.collect { loader.refresh() } }
    }

    fun search() = loader.refresh()

    private fun replace(c: Comment) = loader.update { l -> l.map { if (it.id == c.id) c else it } }

    fun vote(c: Comment, dir: Int) {
        if (!Graph.settings.value.loggedIn) { Graph.toast("Log in to vote"); return }
        viewModelScope.launch {
            runCatching { api.rateComment(c.id, if (c.ownScore == dir) 0 else dir) }
                .onSuccess(::replace).onFailure { Graph.toast(it.message ?: "Vote failed") }
        }
    }

    fun edit(c: Comment, text: String) {
        viewModelScope.launch {
            runCatching { api.editComment(c, text.trim()) }
                .onSuccess(::replace).onFailure { Graph.toast(it.message ?: "Edit failed") }
        }
    }

    fun delete(c: Comment) {
        viewModelScope.launch {
            runCatching { api.deleteComment(c) }
                .onSuccess { loader.update { l -> l.filterNot { it.id == c.id } }; Graph.toast("Comment deleted") }
                .onFailure { Graph.toast(it.message ?: "Delete failed") }
        }
    }
}

@Composable
fun CommentsScreen(
    onBack: () -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenUser: (String) -> Unit,
    vm: CommentsViewModel = viewModel(),
) {
    val listState = rememberLazyListState()
    InfiniteScroll(listState, onLoadMore = vm.loader::loadMore)
    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Comments")
                        if (vm.loader.loadedOnce) Text("${vm.loader.total} total", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    }
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchField(
                value = vm.text,
                onValueChange = { vm.text = it },
                onSearch = vm::search,
                placeholder = "user:name, post:123, text:*word*",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            PullToRefreshBox(isRefreshing = vm.loader.refreshing, onRefresh = vm.loader::refresh, modifier = Modifier.weight(1f)) {
                PagedStates(vm.loader, "No comments found.") {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Hide mode: comments on blacklisted posts are left out too.
                        items(vm.loader.items.filterNot { Blacklist.hidden(it.postId) }, key = { it.id }) { c ->
                            CommentItem(
                                c = c,
                                onOpenUser = onOpenUser,
                                onVote = vm::vote,
                                onEdit = vm::edit,
                                onDelete = vm::delete,
                                postThumb = vm.thumbs[c.postId] ?: "",
                                onOpenPost = onOpenPost,
                            )
                        }
                        item { ListFooter(vm.loader) }
                    }
                }
            }
        }
    }
}
