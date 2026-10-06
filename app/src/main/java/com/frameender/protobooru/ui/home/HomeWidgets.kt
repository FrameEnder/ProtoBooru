package com.frameender.protobooru.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Comment
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.HomeLayouts
import com.frameender.protobooru.data.HomeWidget
import com.frameender.protobooru.data.Info
import com.frameender.protobooru.data.MicroTag
import com.frameender.protobooru.data.Pool
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.StaticPostSource
import com.frameender.protobooru.data.Tag
import com.frameender.protobooru.ui.account.ActionTile
import com.frameender.protobooru.ui.comments.renderCommentText
import com.frameender.protobooru.ui.common.Avatar
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.StatTile
import com.frameender.protobooru.ui.common.TagChip
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

// =====================================================================
// Data
// =====================================================================

sealed interface WidgetData {
    data class Posts(val posts: List<Post>) : WidgetData
    data class Tags(val tags: List<Tag>) : WidgetData
    data class Pools(val pools: List<Pool>) : WidgetData
    data class Comments(val comments: List<Comment>, val thumbs: Map<Int, String?>) : WidgetData
    data class Failed(val message: String) : WidgetData
}

/** Loads data per widget. A widget only reloads when its settings (or the server/user) change. */
class HomeViewModel : ViewModel() {
    private val api get() = Graph.api
    val data = mutableStateMapOf<String, WidgetData>()
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    private val loadedKey = mutableMapOf<String, String>()
    private var countsKey = ""

    fun load(layout: List<HomeWidget>, force: Boolean = false) {
        val s = Graph.settings.value
        if (!s.configured) return
        if (force) Graph.refreshServerState()
        val stamp = "${s.root}|${s.username}|${s.safetyTerm}"
        layout.filter { it.enabled }.forEach { w ->
            val key = "$stamp|$w"
            if (!force && loadedKey[w.id] == key) return@forEach
            loadedKey[w.id] = key
            loadWidget(w, s)
        }
        val wanted = layout.filter { it.enabled && it.type == "stats" }.flatMap { it.items }.toSortedSet()
        val ck = "$stamp|$wanted"
        if (wanted.isNotEmpty() && (force || ck != countsKey)) {
            countsKey = ck
            loadCounts(wanted, s)
        }
    }

    fun reload(w: HomeWidget) = loadWidget(w, Graph.settings.value)

    private fun loadWidget(w: HomeWidget, s: AppSettings) {
        val needsData = w.type in setOf("strip", "grid", "random", "tags", "pools", "comments")
        if (!needsData) return
        viewModelScope.launch {
            data[w.id] = try {
                when (w.type) {
                    "strip", "grid" -> WidgetData.Posts(api.posts(HomeLayouts.postQuery(w.query, s), 0, w.count.coerceIn(1, 60)).results)
                    "random" -> WidgetData.Posts(
                        api.posts(HomeLayouts.postQuery(w.query + " sort:random", s), 0, 1, fields = null).results,
                    )
                    "tags" -> {
                        val q = listOfNotNull(
                            w.category.ifBlank { null }?.let { "category:$it" },
                            "sort:" + w.sort.ifBlank { "usages" },
                        ).joinToString(" ")
                        WidgetData.Tags(api.tags(q, 0, w.count.coerceIn(1, 100)).results)
                    }
                    "pools" -> WidgetData.Pools(api.pools(w.query.ifBlank { "sort:last-edit-time" }, 0, w.count.coerceIn(1, 40)).results)
                    "comments" -> {
                        val page = api.comments("sort:creation-time", 0, w.count.coerceIn(1, 30))
                        val ids = page.results.map { it.postId }.distinct()
                        val thumbs = if (ids.isEmpty()) emptyMap() else runCatching {
                            api.posts("id:${ids.joinToString(",")}", 0, ids.size, "id,thumbnailUrl").results.associate { it.id to it.thumbnailUrl }
                        }.getOrDefault(emptyMap())
                        WidgetData.Comments(page.results, thumbs)
                    }
                    else -> return@launch
                }
            } catch (e: Exception) {
                WidgetData.Failed(e.message ?: "Couldn't load")
            }
        }
    }

    private fun loadCounts(wanted: Set<String>, s: AppSettings) {
        viewModelScope.launch {
            val out = mutableMapOf<String, Int>()
            for (k in wanted) {
                val n = runCatching {
                    when (k) {
                        "tags" -> api.tags(null, 0, 1).total
                        "pools" -> api.pools(null, 0, 1).total
                        "comments" -> api.comments(null, 0, 1).total
                        "users" -> api.users(null, 0, 1).total
                        "favorites" -> if (s.loggedIn) api.posts("fav:${s.username}", 0, 1, "id").total else null
                        else -> null
                    }
                }.getOrNull()
                if (n != null) out[k] = n
            }
            counts = out
        }
    }
}

// =====================================================================
// Rendering
// =====================================================================

/** Draws one widget. Returns nothing visible for disabled or unknown widgets. */
@Composable
fun HomeWidgetView(w: HomeWidget, vm: HomeViewModel, nav: HomeNav, settings: AppSettings, info: Info?) {
    if (!w.enabled) return
    when (w.type) {
        "search" -> SearchWidget(nav)
        "stats" -> StatsWidget(w, vm, nav, settings, info)
        "featured" -> FeaturedWidget(w, nav, info)
        "strip" -> PostsWidget(w, vm, nav, settings, grid = false)
        "grid" -> PostsWidget(w, vm, nav, settings, grid = true)
        "random" -> RandomWidget(w, vm, nav, settings)
        "tags" -> TagsWidget(w, vm, nav)
        "pools" -> PoolsWidget(w, vm, nav)
        "comments" -> CommentsWidget(w, vm, nav)
        "shortcuts" -> ShortcutsWidget(w, nav, settings)
        "saved" -> SavedSearchesWidget(w, nav)
        "header" -> Text(
            w.title.ifBlank { "Section" },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Header(title: String, onSeeAll: (() -> Unit)? = null) {
    if (title.isBlank() && onSeeAll == null) {
        Spacer(Modifier.height(16.dp))
        return
    }
    SectionHeader(title.ifBlank { " " }, trailing = onSeeAll?.let { cb ->
        {
            Text(
                "see all", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = cb).padding(4.dp),
            )
        }
    })
}

@Composable
private fun Placeholder(height: Int = 120) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(10.dp)).background(Ink.Surface))
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = Ink.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

// ---------- search ----------

@Composable
private fun SearchWidget(nav: HomeNav) {
    var query by remember { mutableStateOf("") }
    SearchField(
        value = query,
        onValueChange = { query = it },
        onSearch = { nav.search(query) },
        placeholder = "Search posts…",
        modifier = Modifier.padding(top = 8.dp),
    )
}

// ---------- stats ----------

private fun statIcon(k: String): ImageVector = when (k) {
    "posts" -> Icons.Default.PhotoLibrary
    "tags" -> Icons.Default.LocalOffer
    "pools" -> Icons.Default.Collections
    "comments" -> Icons.Default.Comment
    "users" -> Icons.Default.Group
    "disk" -> Icons.Default.Storage
    "random" -> Icons.Default.Shuffle
    "favorites" -> Icons.Default.Favorite
    else -> Icons.Default.Star
}

@Composable
private fun StatsWidget(w: HomeWidget, vm: HomeViewModel, nav: HomeNav, s: AppSettings, info: Info?) {
    val tiles = w.items.filter { it != "favorites" || s.loggedIn }
    if (tiles.isEmpty()) return
    Header(w.title)
    val cols = w.columns.coerceIn(2, 4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val tile: Triple<String, String, (() -> Unit)?> = when (k) {
                        "posts" -> Triple("Posts", info?.postCount?.let { Format.count(it) } ?: "—", { nav.search("") })
                        "tags" -> Triple("Tags", vm.counts[k]?.let { Format.count(it) } ?: "—", nav.tags)
                        "pools" -> Triple("Pools", vm.counts[k]?.let { Format.count(it) } ?: "—", nav.pools)
                        "comments" -> Triple("Comments", vm.counts[k]?.let { Format.count(it) } ?: "—", nav.comments)
                        "users" -> Triple("Users", vm.counts[k]?.let { Format.count(it) } ?: "—", nav.users)
                        "disk" -> Triple("Disk", info?.diskUsage?.let { Format.bytes(it) } ?: "—", null)
                        "random" -> Triple("Random", "Surprise", { nav.search("sort:random") })
                        "favorites" -> Triple("Favorites", vm.counts[k]?.let { Format.count(it) } ?: "—", { nav.search("fav:${s.username}") })
                        else -> Triple(k, "—", null)
                    }
                    val (label, value, action) = tile
                    StatTile(label, value, statIcon(k), Modifier.weight(1f), action)
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------- featured ----------

@Composable
private fun FeaturedWidget(w: HomeWidget, nav: HomeNav, info: Info?) {
    val fp = info?.featuredPost ?: return
    Header(w.title)
    val open = {
        Graph.viewerSource = StaticPostSource(listOf(fp.id))
        nav.openPost(fp.id)
    }
    val caption = listOfNotNull(info.featuringUser?.name?.let { "featured by $it" }, info.featuringTime?.let { Format.ago(it) }).joinToString(" · ")
    if (w.size == "compact") CompactPostCard(fp, "#${fp.id}", caption, open) else BigPostCard(fp, "#${fp.id}", caption, open)
}

@Composable
private fun BigPostCard(p: Post, title: String, caption: String, onClick: () -> Unit, overlay: (@Composable () -> Unit)? = null) {
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
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            }
            if (overlay != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { overlay() }
        }
    }
}

@Composable
private fun CompactPostCard(p: Post, title: String, caption: String, onClick: () -> Unit, overlay: (@Composable () -> Unit)? = null) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Ink.Surface, border = BorderStroke(1.dp, Ink.Line), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(p.thumbnailUrl, Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                Text("${p.score} score · ${p.favoriteCount} faves · ${p.tagCount} tags", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
            }
            overlay?.invoke()
        }
    }
}

// ---------- post row / grid ----------

@Composable
private fun PostsWidget(w: HomeWidget, vm: HomeViewModel, nav: HomeNav, s: AppSettings, grid: Boolean) {
    Header(w.title) { nav.search(w.query.replace("{me}", s.username)) }
    when (val d = vm.data[w.id]) {
        null -> Placeholder(if (grid) 220 else 140)
        is WidgetData.Failed -> Failed(d.message) { vm.reload(w) }
        is WidgetData.Posts -> {
            if (d.posts.isEmpty()) {
                Text("Nothing matches this search.", color = Ink.TextDim, style = MaterialTheme.typography.bodySmall)
                return
            }
            val open = { p: Post ->
                Graph.viewerSource = StaticPostSource(d.posts.map { it.id }, w.query)
                nav.openPost(p.id)
            }
            if (grid) {
                val cols = w.columns.coerceIn(2, 5)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.posts.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { p ->
                                Surface(onClick = { open(p) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).aspectRatio(1f)) {
                                    RemoteImage(p.thumbnailUrl, Modifier.fillMaxSize())
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                val (tw, th) = when (w.size) {
                    "s" -> 84 to 108
                    "l" -> 150 to 192
                    else -> 110 to 140
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d.posts, key = { it.id }) { p ->
                        Surface(onClick = { open(p) }, shape = RoundedCornerShape(10.dp)) {
                            RemoteImage(p.thumbnailUrl, Modifier.size(width = tw.dp, height = th.dp))
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

// ---------- random ----------

@Composable
private fun RandomWidget(w: HomeWidget, vm: HomeViewModel, nav: HomeNav, s: AppSettings) {
    Header(w.title)
    when (val d = vm.data[w.id]) {
        null -> Placeholder(if (w.size == "compact") 100 else 260)
        is WidgetData.Failed -> Failed(d.message) { vm.reload(w) }
        is WidgetData.Posts -> {
            val p = d.posts.firstOrNull()
            if (p == null) {
                Text("Nothing matches this search.", color = Ink.TextDim, style = MaterialTheme.typography.bodySmall)
                return
            }
            val open = {
                Graph.viewerSource = StaticPostSource(listOf(p.id))
                nav.openPost(p.id)
            }
            val reroll: @Composable () -> Unit = {
                IconButton(onClick = { vm.reload(w) }, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))) {
                    Icon(Icons.Default.Casino, "Another random post", tint = Color.White)
                }
            }
            val caption = w.query.replace("{me}", s.username).ifBlank { "from all posts" }
            if (w.size == "compact") CompactPostCard(p, "#${p.id}", caption, open, reroll)
            else BigPostCard(p, "#${p.id}", caption, open, reroll)
        }
        else -> {}
    }
}

// ---------- tags ----------

@Composable
private fun TagsWidget(w: HomeWidget, vm: HomeViewModel, nav: HomeNav) {
    Header(w.title) { nav.tags() }
    when (val d = vm.data[w.id]) {
        null -> Placeholder(80)
        is WidgetData.Failed -> Failed(d.message) { vm.reload(w) }
        is WidgetData.Tags -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            d.tags.forEach { t ->
                TagChip(MicroTag(t.names, t.category, t.usages), onLongClick = { nav.openTag(t.name) }) { nav.search(t.name) }
            }
        }
        else -> {}
    }
}

// ---------- pools ----------

@Composable
private fun PoolsWidget(w: HomeWidget, vm: HomeViewModel, nav: HomeNav) {
    Header(w.title) { nav.pools() }
    when (val d = vm.data[w.id]) {
        null -> Placeholder(170)
        is WidgetData.Failed -> Failed(d.message) { vm.reload(w) }
        is WidgetData.Pools -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(d.pools, key = { it.id }) { pool ->
                Surface(
                    onClick = { nav.openPool(pool.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = Ink.Surface,
                    border = BorderStroke(1.dp, Ink.Line),
                    modifier = Modifier.width(128.dp),
                ) {
                    Column {
                        RemoteImage(pool.posts.firstOrNull()?.thumbnailUrl, Modifier.fillMaxWidth().height(120.dp))
                        Column(Modifier.padding(8.dp)) {
                            Text(pool.name.replace('_', ' '), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${pool.postCount} posts", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

// ---------- comments ----------

@Composable
private fun CommentsWidget(w: HomeWidget, vm: HomeViewModel, nav: HomeNav) {
    Header(w.title) { nav.comments() }
    when (val d = vm.data[w.id]) {
        null -> Placeholder(160)
        is WidgetData.Failed -> Failed(d.message) { vm.reload(w) }
        is WidgetData.Comments -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.comments.isEmpty()) Text("No comments yet.", color = Ink.TextDim, style = MaterialTheme.typography.bodySmall)
            d.comments.forEach { c ->
                Surface(
                    onClick = { nav.openPost(c.postId) },
                    shape = RoundedCornerShape(10.dp),
                    color = Ink.Surface,
                    border = BorderStroke(1.dp, Ink.Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(d.thumbs[c.postId], Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(c.user?.avatarUrl, 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(c.user?.name ?: "deleted user", style = MaterialTheme.typography.titleSmall)
                                Text(" · ${Format.ago(c.creationTime)}", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                            }
                            Text(renderCommentText(c.text), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

// ---------- shortcuts ----------

private data class Shortcut(val label: String, val icon: ImageVector, val action: () -> Unit)

@Composable
private fun ShortcutsWidget(w: HomeWidget, nav: HomeNav, s: AppSettings) {
    val list = w.items.mapNotNull { k ->
        when (k) {
            "upload" -> if ((s.loggedIn && Graph.can("posts:create:identified")) || Graph.can("posts:create:anonymous")) Shortcut("Upload", Icons.Default.CloudUpload, nav.upload) else null
            "users" -> Shortcut("Users", Icons.Default.Group, nav.users)
            "imageSearch" -> Shortcut("Image search", Icons.Default.ImageSearch, nav.imageSearch)
            "history" -> if (Graph.can("snapshots:list")) Shortcut("Site history", Icons.Default.History, nav.history) else null
            "favorites" -> if (s.loggedIn) Shortcut("My favorites", Icons.Default.Star) { nav.search("fav:${s.username}") } else null
            "myUploads" -> if (s.loggedIn) Shortcut("My uploads", Icons.Default.Person) { nav.search("uploader:${s.username}") } else null
            "tags" -> Shortcut("Tags", Icons.Default.LocalOffer, nav.tags)
            "pools" -> Shortcut("Pools", Icons.Default.Collections, nav.pools)
            "comments" -> Shortcut("Comments", Icons.Default.Comment, nav.comments)
            "random" -> Shortcut("Random post", Icons.Default.Shuffle) { nav.search("sort:random") }
            "settings" -> Shortcut("Settings", Icons.Default.Settings, nav.settings)
            "updates" -> Shortcut("Updates", Icons.Default.SystemUpdate, nav.updates)
            else -> null
        }
    }
    if (list.isEmpty()) return
    Header(w.title)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sc -> ActionTile(sc.label, sc.icon, Modifier.weight(1f), sc.action) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------- saved searches ----------

/** "Label|query" → (label, query). A line without "|" uses the query as its label. */
fun parseSaved(item: String): Pair<String, String> {
    val i = item.indexOf('|')
    return if (i < 0) item to item else item.substring(0, i).trim() to item.substring(i + 1).trim()
}

@Composable
private fun SavedSearchesWidget(w: HomeWidget, nav: HomeNav) {
    if (w.items.isEmpty()) return
    val s = Graph.settings.value
    Header(w.title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        w.items.map(::parseSaved).forEach { (label, query) ->
            AssistChip(
                onClick = { nav.search(query.replace("{me}", s.username)) },
                label = { Text(label) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}
