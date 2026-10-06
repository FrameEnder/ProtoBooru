package com.frameender.protobooru.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.ui.comments.CommentItem
import com.frameender.protobooru.ui.common.Avatar
import com.frameender.protobooru.ui.common.KeyValue
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.SafetyBadge
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagChip
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor

@Composable
fun PostInfoSheet(
    post: Post,
    onSearchTag: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenPool: (Int) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenPost: (Int) -> Unit,
    onSimilar: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val cats by Graph.tagCategories.collectAsState()
    // Group tags by category, ordered by the server's category order.
    val grouped = post.tags
        .groupBy { it.category }
        .toList()
        .sortedBy { (cat, _) -> cats[cat]?.order ?: Int.MAX_VALUE }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.user?.avatarUrl, 40.dp, Modifier.clickable { post.user?.name?.let(onOpenUser) })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        post.user?.name ?: "Anonymous",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable { post.user?.name?.let(onOpenUser) },
                    )
                    Text("Uploaded ${Format.ago(post.creationTime)}", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                }
                SafetyBadge(post.safety)
            }
        }

        if (grouped.isEmpty()) {
            item { SectionHeader("Tags"); Text("No tags", color = Ink.TextDim) }
        }
        grouped.forEach { (cat, tags) ->
            item(key = "cat-$cat") {
                SectionHeader(cat, trailing = {
                    Text("${tags.size}", style = MaterialTheme.typography.labelSmall, color = categoryColor(cats[cat]?.color))
                })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.sortedBy { it.name }.forEach { t ->
                        TagChip(t, onLongClick = { onOpenTag(t.name) }) { onSearchTag(t.name) }
                    }
                }
            }
        }
        item {
            Text(
                "Tap a tag to search it · long-press for tag details",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (post.pools.isNotEmpty()) {
            item {
                SectionHeader("Pools")
                post.pools.forEach { pool ->
                    KeyValue("#${pool.id}", "${pool.name} (${pool.postCount})", onClick = { onOpenPool(pool.id) })
                }
            }
        }

        if (post.relations.isNotEmpty()) {
            item {
                SectionHeader("Related posts")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.relations, key = { it.id }) { r ->
                        RemoteImage(
                            r.thumbnailUrl,
                            Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpenPost(r.id) },
                        )
                    }
                }
            }
        }

        if (post.notes.isNotEmpty()) {
            item {
                SectionHeader("Notes")
                post.notes.forEachIndexed { i, n ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, color = Ink.Amber, modifier = Modifier.width(28.dp))
                        Text(n.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            SectionHeader("Details")
            KeyValue("ID", "#${post.id}")
            KeyValue("Type", post.type + (post.mimeType?.let { " · $it" } ?: ""))
            if (post.canvasWidth != null) KeyValue("Dimensions", "${post.canvasWidth} × ${post.canvasHeight}")
            KeyValue("File size", Format.bytes(post.fileSize))
            KeyValue("Uploaded", Format.dateTime(post.creationTime))
            if (post.lastEditTime != null) KeyValue("Edited", Format.dateTime(post.lastEditTime))
            KeyValue("Score", post.score.toString())
            KeyValue("Favorites", post.favoriteCount.toString())
            KeyValue("Comments", post.commentCount.toString())
            if (post.featureCount > 0) KeyValue("Featured", "${post.featureCount}× · last ${Format.date(post.lastFeatureTime)}")
            if (post.flags.isNotEmpty()) KeyValue("Flags", post.flags.joinToString(", "))
            post.source?.lines()?.filter { it.isNotBlank() }?.forEachIndexed { i, src ->
                val isUrl = src.startsWith("http://") || src.startsWith("https://")
                KeyValue(if (i == 0) "Source" else "", src, onClick = if (isUrl) ({ onOpenUrl(src) }) else null)
            }
            post.checksumMD5?.let { KeyValue("MD5", it) }
            post.checksum?.let { KeyValue("SHA1", it) }
        }

        if (post.favoritedBy.isNotEmpty()) {
            item {
                SectionHeader("Favorited by")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.favoritedBy.forEach { u ->
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpenUser(u.name) }.padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(u.avatarUrl, 26.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(u.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (!post.isVideo && !post.isFlash) {
            item {
                Spacer(Modifier.size(16.dp))
                OutlinedButton(onClick = onSimilar, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ImageSearch, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find similar posts")
                }
            }
        }
    }
}

@Composable
fun CommentsSheet(post: Post, vm: PostViewerViewModel, onOpenUser: (String) -> Unit) {
    val settings by Graph.settings.collectAsState()
    var draft by remember { mutableStateOf("") }
    val comments = post.comments.sortedBy { it.creationTime ?: "" }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
        Text(
            "Comments · ${comments.size}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (comments.isEmpty()) item { Text("No comments yet.", color = Ink.TextDim) }
            items(comments, key = { it.id }) { c ->
                CommentItem(
                    c = c,
                    onOpenUser = onOpenUser,
                    onVote = vm::voteComment,
                    onEdit = vm::editComment,
                    onDelete = vm::deleteComment,
                )
            }
        }
        if (settings.loggedIn && Graph.can("comments:create")) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Add a comment…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                )
                IconButton(onClick = { vm.addComment(post.id, draft) { draft = "" } }, enabled = draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (draft.isNotBlank()) Ink.Amber else Ink.TextDim)
                }
            }
        } else if (!settings.loggedIn) {
            Text(
                "Log in from the Account tab to comment.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.TextDim,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
