package com.frameender.protobooru.ui.post

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.MicroPool
import com.frameender.protobooru.data.MicroPost
import com.frameender.protobooru.data.Note
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.UriRequestBody
import com.frameender.protobooru.data.uriInfo
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ConfirmDialog
import com.frameender.protobooru.ui.common.ErrorBox
import com.frameender.protobooru.ui.common.LoadingBox
import com.frameender.protobooru.ui.common.PoolPickerDialog
import com.frameender.protobooru.ui.common.PostIdDialog
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.SafetySelector
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagCategoryCache
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

class PostEditViewModel(handle: SavedStateHandle) : ViewModel() {
    private val api = Graph.api
    val id: Int = handle.get<String>("id")?.toIntOrNull() ?: 0

    var post by mutableStateOf<Post?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    // Editable copies
    var tags by mutableStateOf<List<String>>(emptyList())
    var safety by mutableStateOf("safe")
    var source by mutableStateOf("")
    var relations by mutableStateOf<List<MicroPost>>(emptyList())
    var flags by mutableStateOf<Set<String>>(emptySet())
    var notes by mutableStateOf<List<Note>>(emptyList())
    var pools by mutableStateOf<List<MicroPool>>(emptyList())

    init { load() }

    fun load() {
        error = null
        viewModelScope.launch {
            try { reset(api.post(id)) } catch (e: Exception) { error = e.message ?: "Could not load post" }
        }
    }

    private fun reset(p: Post) {
        post = p
        p.tags.forEach { TagCategoryCache.learn(it.name, it.category) }
        tags = p.tags.map { it.name }
        safety = p.safety
        source = p.source.orEmpty()
        relations = p.relations
        flags = p.flags.toSet()
        notes = p.notes
        pools = p.pools
    }

    val dirty: Boolean
        get() {
            val p = post ?: return false
            return tags != p.tags.map { it.name } || safety != p.safety || source != p.source.orEmpty() ||
                relations.map { it.id } != p.relations.map { it.id } || flags != p.flags.toSet() ||
                notes != p.notes || pools.map { it.id } != p.pools.map { it.id }
        }

    private fun run(label: String, block: suspend (Post) -> Unit) {
        val p = post ?: return
        busy = label
        viewModelScope.launch {
            try {
                block(p)
            } catch (e: Exception) {
                Graph.toast(e.message ?: "$label failed")
            } finally {
                busy = null
            }
        }
    }

    fun save(onDone: () -> Unit) = run("Saving") { p ->
        val c = Graph::can
        val updated = api.updatePost(
            p,
            tags = if (tags != p.tags.map { it.name } && c("posts:edit:tags")) tags else null,
            safety = if (safety != p.safety && c("posts:edit:safety")) safety else null,
            source = if (source != p.source.orEmpty() && c("posts:edit:source")) source else null,
            relations = if (relations.map { it.id } != p.relations.map { it.id } && c("posts:edit:relations")) relations.map { it.id } else null,
            flags = if (flags != p.flags.toSet() && c("posts:edit:flags")) flags.toList() else null,
            notes = if (notes != p.notes && c("posts:edit:notes")) notes else null,
        )
        // Pool membership lives on the pools, not the post.
        val before = p.pools.map { it.id }.toSet()
        val after = pools.map { it.id }.toSet()
        for (pid in after - before) {
            val pool = api.pool(pid)
            api.updatePool(pool, posts = (pool.posts.map { it.id } + p.id).distinct())
        }
        for (pid in before - after) {
            val pool = api.pool(pid)
            api.updatePool(pool, posts = pool.posts.map { it.id }.filter { it != p.id })
        }
        reset(if (before != after) api.post(p.id) else updated)
        Graph.postChanged.tryEmit(p.id)
        Graph.toast("Saved #${p.id}")
        onDone()
    }

    fun replaceContent(context: Context, uri: Uri) = run("Uploading new file") { p ->
        val info = context.uriInfo(uri)
        val token = api.uploadTemp(UriRequestBody(context, uri, info.mime, info.size), info.name)
        reset(api.updatePost(p, contentToken = token))
        Graph.postChanged.tryEmit(p.id)
        Graph.toast("File replaced")
    }

    fun setThumbnail(context: Context, uri: Uri?) = run("Updating thumbnail") { p ->
        val updated = if (uri == null) {
            api.setThumbnail(p, null)
        } else {
            val info = context.uriInfo(uri)
            api.setThumbnail(p, UriRequestBody(context, uri, info.mime, info.size), info.name)
        }
        reset(updated)
        Graph.postChanged.tryEmit(p.id)
        Graph.toast(if (uri == null) "Thumbnail reset" else "Thumbnail updated")
    }

    fun feature() = run("Featuring") { p ->
        api.featurePost(p.id)
        Graph.refreshServerState()
        Graph.toast("#${p.id} is now featured")
    }

    fun mergeInto(target: Post, replaceContent: Boolean, onDone: (Int) -> Unit) = run("Merging") { p ->
        val merged = api.mergePosts(remove = p, into = target, replaceContent = replaceContent)
        Graph.postChanged.tryEmit(merged.id)
        Graph.toast("Merged #${p.id} into #${merged.id}")
        onDone(merged.id)
    }

    fun delete(onDone: () -> Unit) = run("Deleting") { p ->
        api.deletePost(p)
        Graph.postChanged.tryEmit(p.id)
        Graph.toast("Deleted #${p.id}")
        onDone()
    }
}

@Composable
fun PostEditScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onMerged: (Int) -> Unit,
    vm: PostEditViewModel = viewModel(),
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var addRelation by remember { mutableStateOf(false) }
    var addPool by remember { mutableStateOf(false) }

    val contentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.replaceContent(context, uri)
    }
    val thumbPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setThumbnail(context, uri)
    }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text("Edit #${vm.id}") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (vm.busy != null) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    TextButton(onClick = { vm.save(onBack) }, enabled = vm.dirty && vm.busy == null) { Text("Save") }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = vm.post != null) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            if (Graph.can("posts:edit:content")) DropdownMenuItem(
                                text = { Text("Replace file…") },
                                onClick = { menu = false; contentPicker.launch(arrayOf("image/*", "video/*", "application/x-shockwave-flash")) },
                            )
                            if (Graph.can("posts:edit:thumbnail")) {
                                DropdownMenuItem(text = { Text("Custom thumbnail…") }, onClick = {
                                    menu = false
                                    thumbPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                                DropdownMenuItem(text = { Text("Reset thumbnail") }, onClick = { menu = false; vm.setThumbnail(context, null) })
                            }
                            if (Graph.can("posts:feature")) DropdownMenuItem(text = { Text("Feature on home page") }, onClick = { menu = false; vm.feature() })
                            if (Graph.can("posts:merge")) DropdownMenuItem(text = { Text("Merge into another post…") }, onClick = { menu = false; merging = true })
                            if (Graph.can("posts:delete")) {
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Delete post", color = Ink.Red) }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        val p = vm.post
        when {
            p == null && vm.error != null -> ErrorBox(vm.error!!, Modifier.padding(pad), onRetry = vm::load)
            p == null -> LoadingBox(Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        RemoteImage(p.thumbnailUrl, Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${p.type} · ${p.canvasWidth ?: "?"}×${p.canvasHeight ?: "?"}", style = MaterialTheme.typography.bodyMedium)
                            Text("version ${p.version}", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                            if (vm.busy != null) Text(vm.busy!! + "…", style = MaterialTheme.typography.labelSmall, color = Ink.Amber)
                        }
                    }
                }
                item {
                    SectionHeader("Tags · ${vm.tags.size}")
                    TagEditor(vm.tags, { vm.tags = it }, enabled = Graph.can("posts:edit:tags"))
                }
                item {
                    SectionHeader("Safety")
                    if (Graph.can("posts:edit:safety")) SafetySelector(vm.safety, { vm.safety = it ?: vm.safety })
                    else Text(vm.safety, color = Ink.TextDim)
                }
                item {
                    SectionHeader("Source")
                    OutlinedTextField(
                        vm.source, { vm.source = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
                        enabled = Graph.can("posts:edit:source"),
                        supportingText = { Text("One URL per line") },
                    )
                }
                item {
                    SectionHeader("Related posts · ${vm.relations.size}", trailing = {
                        if (Graph.can("posts:edit:relations")) {
                            IconButton(onClick = { addRelation = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, "Add relation", tint = Ink.Amber) }
                        }
                    })
                    if (vm.relations.isEmpty()) Text("None", color = Ink.TextDim)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.relations, key = { it.id }) { r ->
                            Box {
                                RemoteImage(r.thumbnailUrl, Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)))
                                if (Graph.can("posts:edit:relations")) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .clickable { vm.relations = vm.relations.filterNot { it.id == r.id } },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                                }
                                Text(
                                    "#${r.id}", style = MaterialTheme.typography.labelSmall, color = Color.White,
                                    modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    SectionHeader("Pools · ${vm.pools.size}", trailing = {
                        if (Graph.can("pools:edit:posts")) {
                            IconButton(onClick = { addPool = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, "Add to pool", tint = Ink.Amber) }
                        }
                    })
                    if (vm.pools.isEmpty()) Text("Not in any pool", color = Ink.TextDim)
                    vm.pools.forEach { pool ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${pool.id}", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim, modifier = Modifier.width(52.dp))
                            Text(pool.name.replace('_', ' '), modifier = Modifier.weight(1f))
                            if (Graph.can("pools:edit:posts")) {
                                IconButton(onClick = { vm.pools = vm.pools.filterNot { it.id == pool.id } }) {
                                    Icon(Icons.Default.Close, "Remove from pool", tint = Ink.TextDim)
                                }
                            }
                        }
                    }
                }
                if (p.isVideo || p.isAnimation || vm.flags.isNotEmpty()) {
                    item {
                        SectionHeader("Flags")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf("loop", "sound").forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = f in vm.flags,
                                        onCheckedChange = { on -> vm.flags = if (on) vm.flags + f else vm.flags - f },
                                        enabled = Graph.can("posts:edit:flags"),
                                    )
                                    Text(f)
                                }
                            }
                        }
                    }
                }
                if (vm.notes.isNotEmpty()) {
                    item {
                        SectionHeader("Notes · ${vm.notes.size}")
                        Text(
                            "Edit note text or remove notes here. Drawing new note areas is easiest in the web UI.",
                            style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                        )
                    }
                    items(vm.notes.indices.toList(), key = { "note$it" }) { i ->
                        val n = vm.notes[i]
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 8.dp)) {
                            Text("${i + 1}.", color = Ink.Amber, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp).padding(top = 16.dp))
                            OutlinedTextField(
                                n.text,
                                { t -> vm.notes = vm.notes.mapIndexed { j, x -> if (j == i) x.copy(text = t) else x } },
                                modifier = Modifier.weight(1f),
                                enabled = Graph.can("posts:edit:notes"),
                            )
                            if (Graph.can("posts:edit:notes")) {
                                IconButton(onClick = { vm.notes = vm.notes.filterIndexed { j, _ -> j != i } }) {
                                    Icon(Icons.Default.Delete, "Delete note", tint = Ink.TextDim)
                                }
                            }
                        }
                    }
                }
                if (vm.dirty) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = Ink.Surface2, border = BorderStroke(1.dp, Ink.AmberDim)) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Unsaved changes", modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { vm.post?.let { vm.load() } }) { Text("Discard") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { vm.save(onBack) }, enabled = vm.busy == null) { Text("Save") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete post #${vm.id}?",
            text = "The file, tags, comments, and favorites for this post are removed. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { vm.delete(onDeleted) },
        )
    }
    if (merging) {
        var replace by remember { mutableStateOf(false) }
        PostIdDialog(
            title = "Merge #${vm.id} into…",
            confirmLabel = "Merge",
            onDismiss = { merging = false },
            extra = {
                Text(
                    "#${vm.id} will be deleted. Its tags, relations, favorites and comments move to the target.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(replace, { replace = it })
                    Text("Use this post's file on the target", style = MaterialTheme.typography.bodySmall)
                }
            },
        ) { target -> if (target.id != vm.id) vm.mergeInto(target, replace, onMerged) }
    }
    if (addRelation) {
        PostIdDialog(title = "Add related post", confirmLabel = "Add", onDismiss = { addRelation = false }) { r ->
            if (r.id != vm.id && vm.relations.none { it.id == r.id }) {
                vm.relations = vm.relations + MicroPost(r.id, r.thumbnailUrl)
            }
        }
    }
    if (addPool) {
        PoolPickerDialog(onDismiss = { addPool = false }) { pool ->
            if (vm.pools.none { it.id == pool.id }) {
                vm.pools = vm.pools + MicroPool(pool.id, pool.names, pool.category, pool.description, pool.postCount)
            }
        }
    }
}
