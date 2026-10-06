package com.frameender.protobooru.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.StaticPostSource
import com.frameender.protobooru.ui.account.ActionTile
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.StatTile
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.settings.ServerSetupCard
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    var recent by mutableStateOf<List<Post>>(emptyList())
        private set
    var topFaves by mutableStateOf<List<Post>>(emptyList())
        private set
    var tagCount by mutableStateOf<Int?>(null)
        private set
    var poolCount by mutableStateOf<Int?>(null)
        private set
    var commentCount by mutableStateOf<Int?>(null)
        private set

    fun load() {
        if (!Graph.settings.value.configured) return
        Graph.refreshServerState()
        val safety = Graph.settings.value.safetyTerm
        viewModelScope.launch {
            runCatching { recent = Graph.api.posts(safety, 0, 15).results }
            runCatching { topFaves = Graph.api.posts(listOfNotNull("sort:fav-count", safety).joinToString(" "), 0, 15).results }
            runCatching { tagCount = Graph.api.tags(null, 0, 1).total }
            runCatching { poolCount = Graph.api.pools(null, 0, 1).total }
            runCatching { commentCount = Graph.api.comments(null, 0, 1).total }
        }
    }
}

class HomeNav(
    val search: (String) -> Unit,
    val openPost: (Int) -> Unit,
    val tags: () -> Unit,
    val pools: () -> Unit,
    val comments: () -> Unit,
    val users: () -> Unit,
    val history: () -> Unit,
    val imageSearch: () -> Unit,
    val settings: () -> Unit,
    val upload: () -> Unit,
)

@Composable
fun HomeScreen(nav: HomeNav, vm: HomeViewModel = viewModel()) {
    val settings by Graph.settings.collectAsState()
    val info by Graph.info.collectAsState()
    val serverError by Graph.serverError.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(settings.root, settings.token) { vm.load() }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(info?.config?.name ?: "ProtoBooru")
                        if (settings.configured) {
                            Text(
                                settings.root.removePrefix("http://").removePrefix("https://") +
                                    (if (settings.loggedIn) " · ${settings.username}" else " · anonymous"),
                                style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = nav.settings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        if (!settings.configured) {
            Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
                Text("Welcome", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Point ProtoBooru at your Szurubooru instance to get started.",
                    style = MaterialTheme.typography.bodyLarge, color = Ink.TextDim,
                )
                Spacer(Modifier.height(20.dp))
                ServerSetupCard()
            }
            return@Scaffold
        }

        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            if (serverError != null) {
                item {
                    Surface(shape = RoundedCornerShape(10.dp), color = Ink.Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Ink.Red.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, null, tint = Ink.Red)
                            Spacer(Modifier.width(10.dp))
                            Text("Can't reach the server: $serverError", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = { nav.search(query) },
                    placeholder = "Search posts…",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Posts", info?.postCount?.let { Format.count(it) } ?: "—", Icons.Default.PhotoLibrary, Modifier.weight(1f)) { nav.search("") }
                    StatTile("Tags", vm.tagCount?.let { Format.count(it) } ?: "—", Icons.Default.LocalOffer, Modifier.weight(1f), nav.tags)
                    StatTile("Pools", vm.poolCount?.let { Format.count(it) } ?: "—", Icons.Default.Collections, Modifier.weight(1f), nav.pools)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Comments", vm.commentCount?.let { Format.count(it) } ?: "—", Icons.Default.Comment, Modifier.weight(1f), nav.comments)
                    StatTile("Disk", info?.diskUsage?.let { Format.bytes(it) } ?: "—", Icons.Default.Storage, Modifier.weight(1f))
                    StatTile("Random", "Surprise", Icons.Default.Shuffle, Modifier.weight(1f)) { nav.search("sort:random") }
                }
            }

            info?.featuredPost?.let { fp ->
                item {
                    SectionHeader("Featured post")
                    FeaturedCard(fp, info?.featuringUser?.name, info?.featuringTime) {
                        Graph.viewerSource = StaticPostSource(listOf(fp.id))
                        nav.openPost(fp.id)
                    }
                }
            }

            if (vm.recent.isNotEmpty()) {
                item {
                    SectionHeader("Latest uploads", trailing = {
                        Text("see all", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { nav.search("") }.padding(4.dp))
                    })
                    ThumbStrip(vm.recent, nav)
                }
            }
            if (vm.topFaves.isNotEmpty()) {
                item {
                    SectionHeader("Most favorited")
                    ThumbStrip(vm.topFaves, nav)
                }
            }

            item {
                SectionHeader("Explore")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if ((settings.loggedIn && Graph.can("posts:create:identified")) || Graph.can("posts:create:anonymous")) {
                        ActionTile("Upload posts", Icons.Default.CloudUpload, Modifier.fillMaxWidth(), nav.upload)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile("Users", Icons.Default.Group, Modifier.weight(1f), nav.users)
                        ActionTile("Image search", Icons.Default.ImageSearch, Modifier.weight(1f), nav.imageSearch)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (Graph.can("snapshots:list")) {
                            ActionTile("Site history", Icons.Default.History, Modifier.weight(1f), nav.history)
                        }
                        if (settings.loggedIn) {
                            ActionTile("My favorites", Icons.Default.Star, Modifier.weight(1f)) { nav.search("fav:${settings.username}") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbStrip(posts: List<Post>, nav: HomeNav) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(posts, key = { it.id }) { p ->
            Surface(
                onClick = {
                    Graph.viewerSource = StaticPostSource(posts.map { it.id })
                    nav.openPost(p.id)
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                RemoteImage(p.thumbnailUrl, Modifier.size(width = 110.dp, height = 140.dp))
            }
        }
    }
}

@Composable
private fun FeaturedCard(p: Post, by: String?, time: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Ink.Line)) {
        Box(Modifier.fillMaxWidth().aspectRatio(p.aspect.coerceIn(0.75f, 1.8f)).heightIn(max = 420.dp)) {
            RemoteImage(p.contentUrl.takeIf { !p.isVideo && !p.isFlash } ?: p.thumbnailUrl, Modifier.fillMaxSize())
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(14.dp),
            ) {
                Text("#${p.id}", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    listOfNotNull(by?.let { "featured by $it" }, time?.let { Format.ago(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}
