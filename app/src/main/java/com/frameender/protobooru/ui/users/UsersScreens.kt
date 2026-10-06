package com.frameender.protobooru.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.frameender.protobooru.data.User
import com.frameender.protobooru.ui.common.Avatar
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
import com.frameender.protobooru.ui.common.StatTile
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

// ===================== User list =====================

class UsersViewModel : ViewModel() {
    var text by mutableStateOf("")
    val loader = PagedLoader(viewModelScope, pageSize = { 40 }) { offset, limit ->
        val t = text.trim()
        val q = (if (t.isEmpty()) "" else if (t.contains(':') || t.contains('*')) t else "*$t*") + " sort:name"
        Graph.api.users(q.trim(), offset, limit)
    }
    init { loader.refresh() }
}

@Composable
fun UsersScreen(onBack: () -> Unit, onOpenUser: (String) -> Unit, vm: UsersViewModel = viewModel()) {
    val listState = rememberLazyListState()
    InfiniteScroll(listState, onLoadMore = vm.loader::loadMore)
    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text("Users") }, navigationIcon = { BackButton(onBack) }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchField(
                value = vm.text,
                onValueChange = { vm.text = it },
                onSearch = vm.loader::refresh,
                placeholder = "Search users…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            PullToRefreshBox(isRefreshing = vm.loader.refreshing, onRefresh = vm.loader::refresh, modifier = Modifier.weight(1f)) {
                PagedStates(vm.loader, "No users found.") {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(vm.loader.items, key = { it.name }) { u ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenUser(u.name) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(u.avatarUrl, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(u.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${u.rank} · ${u.uploadedPostCount} uploads · joined ${Format.date(u.creationTime)}",
                                        style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                                    )
                                }
                            }
                            HorizontalDivider(color = Ink.Line.copy(alpha = 0.5f))
                        }
                        item { ListFooter(vm.loader) }
                    }
                }
            }
        }
    }
}

// ===================== User profile =====================

class UserDetailViewModel(handle: SavedStateHandle) : ViewModel() {
    val name: String = handle.get<String>("name").orEmpty()
    var user by mutableStateOf<User?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        error = null
        viewModelScope.launch {
            try { user = Graph.api.user(name) } catch (e: Exception) { error = e.message ?: "Could not load user" }
        }
    }
}

@Composable
fun UserDetailScreen(
    onBack: () -> Unit,
    onSearchPosts: (String) -> Unit,
    onComments: (String) -> Unit,
    onHistory: (String) -> Unit,
    vm: UserDetailViewModel = viewModel(),
) {
    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text(vm.name) }, navigationIcon = { BackButton(onBack) }) },
    ) { pad ->
        val u = vm.user
        when {
            u == null && vm.error != null -> ErrorBox(vm.error!!, Modifier.padding(pad), onRetry = vm::load)
            u == null -> LoadingBox(Modifier.padding(pad))
            else -> UserProfileBody(u, Modifier.padding(pad), onSearchPosts, onComments, onHistory)
        }
    }
}

/** Shared by the profile screen and the Account tab. */
@Composable
fun UserProfileBody(
    u: User,
    modifier: Modifier = Modifier,
    onSearchPosts: (String) -> Unit,
    onComments: (String) -> Unit,
    onHistory: ((String) -> Unit)?,
    extra: (@Composable () -> Unit)? = null,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(u.avatarUrl, 72.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(u.name, style = MaterialTheme.typography.headlineSmall)
                    Text(u.rank.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Joined ${Format.date(u.creationTime)} · seen ${Format.ago(u.lastLoginTime)}", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Uploads", Format.count(u.uploadedPostCount), Icons.Default.CloudUpload, Modifier.weight(1f)) {
                    onSearchPosts("uploader:${u.name}")
                }
                StatTile("Favorites", Format.count(u.favoritePostCount), Icons.Default.Favorite, Modifier.weight(1f)) {
                    onSearchPosts("fav:${u.name}")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Comments", Format.count(u.commentCount), Icons.Default.Comment, Modifier.weight(1f)) {
                    onComments("user:${u.name}")
                }
                val liked = u.likedCount
                StatTile(
                    "Liked", liked?.let { Format.count(it) } ?: "hidden", Icons.Default.ThumbUp, Modifier.weight(1f),
                    onClick = if (liked != null && Graph.isMe(u.name)) ({ onSearchPosts("special:liked") }) else null,
                )
            }
        }
        if (extra != null) item { extra() }
        item {
            SectionHeader("Details")
            KeyValue("Rank", u.rank)
            u.emailText?.let { KeyValue("Email", it) }
            KeyValue("Avatar", u.avatarStyle)
            u.dislikedCount?.let { KeyValue("Disliked", it.toString()) }
            KeyValue("Created", Format.dateTime(u.creationTime))
            KeyValue("Last login", Format.dateTime(u.lastLoginTime))
            if (onHistory != null && Graph.can("snapshots:list")) {
                KeyValue("History", "View edits by ${u.name}", onClick = { onHistory("user:${u.name}") })
            }
        }
    }
}
