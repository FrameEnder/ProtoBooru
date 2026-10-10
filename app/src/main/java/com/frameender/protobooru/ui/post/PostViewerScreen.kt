package com.frameender.protobooru.ui.post

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.TagClipboard
import com.frameender.protobooru.data.blacklistHits
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

/** Navigation callbacks the viewer needs. */
class ViewerNav(
    val back: () -> Unit,
    val searchTag: (String) -> Unit,
    val openTag: (String) -> Unit,
    val openPool: (Int) -> Unit,
    val openUser: (String) -> Unit,
    val openPost: (Int) -> Unit,
    val similar: (Int) -> Unit,
    val edit: (Int) -> Unit,
)

private enum class ViewerSheet { INFO, COMMENTS }

@Composable
fun PostViewerScreen(nav: ViewerNav, vm: PostViewerViewModel = viewModel()) {
    val settings by Graph.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ids = vm.source.ids
    val startIndex = remember { ids.indexOf(vm.startId).coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { ids.size }

    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(settings.showNotes) }
    var sheet by remember { mutableStateOf<ViewerSheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showRelated by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val swipeThreshold = 72.dp

    val currentId = ids.getOrNull(pager.currentPage)
    val current: Post? = currentId?.let { vm.shown(it) }

    LaunchedEffect(pager.currentPage, ids.size) {
        val i = pager.currentPage
        ids.getOrNull(i)?.let { vm.ensureLoaded(it); Graph.lastViewedPostId = it }
        ids.getOrNull(i + 1)?.let { vm.ensureLoaded(it) }
        ids.getOrNull(i - 1)?.let { vm.ensureLoaded(it) }
        if (i >= ids.size - 5 && vm.source.canLoadMore) vm.source.loadMore()
        zoomed = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (ids.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
                key = { ids.getOrElse(it) { -it } },
                modifier = Modifier
                    .fillMaxSize()
                    // Swipe up anywhere on the post to open its details (when not zoomed in).
                    .pointerInput(zoomed) {
                        if (zoomed) return@pointerInput
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                if (dragged < -swipeThreshold.toPx()) sheet = ViewerSheet.INFO
                                dragged = 0f
                            },
                            onDragCancel = { dragged = 0f },
                        ) { change, dy ->
                            dragged += dy
                            change.consume()
                        }
                    },
            ) { page ->
                val id = ids[page]
                val post = vm.shown(id)
                val err = vm.errors[id]
                when {
                    post == null && err != null -> PostLoadError(err) { vm.ensureLoaded(id, force = true) }
                    post == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    // Opened from somewhere searches can't filter (pools, related posts, links).
                    id !in revealed && settings.blacklistHits(post).isNotEmpty() ->
                        BlacklistCover(post, settings.blacklistHits(post)) { revealed = revealed + id }
                    post.isVideo -> PostVideo(
                        post, settings,
                        active = page == pager.currentPage,
                        // Keep the video controls just above the action bar (and the related strip, if any).
                        controlsBottom = 64.dp +
                            (if (post.relations.isNotEmpty()) 40.dp else 0.dp) +
                            (if (post.relations.isNotEmpty() && showRelated) 80.dp else 0.dp),
                    )
                    post.isFlash -> PostFlash(post, settings)
                    else -> PostImage(
                        post = post,
                        settings = settings,
                        showNotes = showNotes,
                        onTap = { chrome = !chrome },
                        onZoomChanged = { zoomed = it },
                    )
                }
            }
        }

        val showChrome = chrome || current?.isVideo == true

        // ---------- Top bar ----------
        AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("#${currentId ?: ""}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (ids.size > 1) {
                        val more = if (vm.source.canLoadMore) "+" else ""
                        Text("${pager.currentPage + 1} / ${ids.size}$more", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                if (current != null && current.notes.isNotEmpty() && !current.isVideo) {
                    IconButton(onClick = { showNotes = !showNotes }) {
                        Icon(
                            if (showNotes) Icons.Filled.StickyNote2 else Icons.Outlined.StickyNote2,
                            "Toggle notes",
                            tint = if (showNotes) Ink.Amber else Color.White,
                        )
                    }
                }
                IconButton(onClick = { current?.let { Graph.downloads.enqueue(listOf(it.id)) } }, enabled = current != null) {
                    Icon(Icons.Default.Download, "Download", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        val p = current
                        DropdownMenuItem(
                            text = { Text("Share file") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            enabled = p != null,
                            onClick = {
                                menu = false
                                if (p != null) scope.launch {
                                    runCatching { Graph.downloads.shareFile(context, p) }
                                        .onFailure { Graph.toast(it.message ?: "Share failed") }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share link") },
                            leadingIcon = { Icon(Icons.Default.Link, null) },
                            onClick = {
                                menu = false
                                currentId?.let { shareText(context, Graph.api.postWebUrl(it)) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy link") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                menu = false
                                currentId?.let { copyText(context, Graph.api.postWebUrl(it)) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = {
                                menu = false
                                currentId?.let { openUrl(context, Graph.api.postWebUrl(it)) }
                            },
                        )
                        if (p != null && p.tags.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Copy tags") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = { menu = false; TagClipboard.copy(context, p.tags.map { it.name }, p.id) },
                            )
                        }
                        val copied = TagClipboard.tags(settings)
                        if (p != null && copied.isNotEmpty() && settings.loggedIn && Graph.can("posts:edit:tags")) {
                            DropdownMenuItem(
                                text = { Text("Paste ${copied.size} copied tags") },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                                onClick = { menu = false; vm.addTags(p, copied) },
                            )
                        }
                        if (p != null && settings.loggedIn && Graph.can("posts:edit:tags")) {
                            DropdownMenuItem(
                                text = { Text("Edit post") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menu = false; nav.edit(p.id) },
                            )
                        }
                        if (p != null && !p.isVideo && !p.isFlash) {
                            DropdownMenuItem(
                                text = { Text("Find similar") },
                                leadingIcon = { Icon(Icons.Default.ImageSearch, null) },
                                onClick = { menu = false; nav.similar(p.id) },
                            )
                        }
                    }
                }
            }
        }

        // ---------- Bottom action bar ----------
        AnimatedVisibility(showChrome && current != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val p = current ?: return@AnimatedVisibility
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
            if (p.relations.isNotEmpty()) RelatedStrip(p, expanded = showRelated, onToggle = { showRelated = !showRelated }, onOpen = nav.openPost)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Score
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.vote(p, 1) }) {
                        Icon(
                            if (p.ownScore == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Upvote",
                            tint = if (p.ownScore == 1) Ink.Amber else Color.White,
                        )
                    }
                    Text(p.score.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { vm.vote(p, -1) }) {
                        Icon(
                            if (p.ownScore == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Downvote",
                            tint = if (p.ownScore == -1) Ink.Red else Color.White,
                        )
                    }
                }
                ActionCount(
                    icon = { Icon(if (p.ownFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = if (p.ownFavorite) Ink.Amber else Color.White) },
                    count = p.favoriteCount,
                    onClick = { vm.toggleFavorite(p) },
                )
                ActionCount(
                    icon = { Icon(Icons.Default.Comment, "Comments", tint = Color.White) },
                    count = p.commentCount,
                    onClick = { sheet = ViewerSheet.COMMENTS },
                )
                if (p.isVideo) {
                    val muted = VideoPrefs.muted(settings)
                    IconButton(onClick = { VideoPrefs.toggle(settings) }) {
                        Icon(
                            if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            if (muted) "Unmute" else "Mute",
                            tint = if (muted) Color.White else Ink.Amber,
                        )
                    }
                }
                IconButton(onClick = { sheet = ViewerSheet.INFO }) { Icon(Icons.Default.Info, "Details", tint = Color.White) }
            }
            }
        }
    }

    // ---------- Sheets ----------
    val p = current
    if (sheet != null && p != null) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = sheet == ViewerSheet.COMMENTS)
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = state,
            containerColor = Ink.Surface,
        ) {
            when (sheet) {
                ViewerSheet.INFO -> PostInfoSheet(
                    post = p,
                    onSearchTag = { sheet = null; nav.searchTag(it) },
                    onOpenTag = { sheet = null; nav.openTag(it) },
                    onOpenPool = { sheet = null; nav.openPool(it) },
                    onOpenUser = { sheet = null; nav.openUser(it) },
                    onOpenPost = { sheet = null; nav.openPost(it) },
                    onSimilar = { sheet = null; nav.similar(p.id) },
                    onOpenUrl = { openUrl(context, it) },
                )
                ViewerSheet.COMMENTS -> CommentsSheet(p, vm, onOpenUser = { sheet = null; nav.openUser(it) })
                null -> {}
            }
        }
    }
}

/** Shown instead of a post that has blacklisted tags, until you choose to see it. */
@Composable
private fun BlacklistCover(post: Post, hits: List<String>, onShow: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RemoteImage(post.thumbnailUrl, Modifier.fillMaxSize().blur(40.dp))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("Hidden by your blacklist", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                hits.joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.OutlinedButton(onClick = onShow) { Text("Show anyway", maxLines = 1) }
        }
    }
}

/**
 * Related-posts indicator: a pill showing how many relations this post has. Tapping it
 * unfolds a strip of their thumbnails; tapping a thumbnail opens that post.
 */
@Composable
private fun RelatedStrip(p: Post, expanded: Boolean, onToggle: () -> Unit, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Ink.Amber.copy(alpha = 0.18f))
                .border(1.dp, Ink.Amber.copy(alpha = 0.6f), RoundedCornerShape(50))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Link, null, tint = Ink.Amber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${p.relations.size} related",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                if (expanded) "Hide related posts" else "Show related posts",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(p.relations, key = { it.id }) { r ->
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpen(r.id) }) {
                        RemoteImage(r.thumbnailUrl, Modifier.fillMaxSize())
                        Text(
                            "#${r.id}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCount(icon: @Composable () -> Unit, count: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick) { icon() }
        Text(Format.count(count), color = Color.White, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
    }
}

fun shareText(context: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(i, null))
}

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
    Graph.toast("Copied")
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Graph.toast("No app can open this link") }
}
