package com.frameender.protobooru.ui.upload

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.UploadJob
import com.frameender.protobooru.data.UploadState
import com.frameender.protobooru.data.uriInfo
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.SafetySelector
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagCopyBar
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.safetyColor

/** A file or URL picked but not yet sent. Per-item values override the shared ones. */
data class Draft(
    val id: Long,
    val uri: Uri?,
    val url: String?,
    val name: String,
    val size: Long,
    val mime: String,
    val extraTags: List<String> = emptyList(),
    val safety: String? = null,
    val source: String? = null,
)

class UploadViewModel : ViewModel() {
    var drafts by mutableStateOf<List<Draft>>(emptyList())
        private set
    var tags by mutableStateOf<List<String>>(emptyList())
    var safety by mutableStateOf("safe")
    var source by mutableStateOf("")
    var anonymous by mutableStateOf(false)
    var skipDuplicates by mutableStateOf(true)
    var relateBatch by mutableStateOf(false)

    fun addUris(context: Context, uris: List<Uri>) {
        val known = drafts.mapNotNull { it.uri }.toSet()
        drafts = drafts + uris.filter { it !in known }.map { u ->
            val info = context.uriInfo(u)
            Draft(Graph.uploads.nextId(), u, null, info.name, info.size, info.mime)
        }
    }

    fun addUrl(raw: String) {
        // Pull the first http(s) link out of shared text like "Check this out https://…"
        val url = Regex("https?://\\S+").find(raw)?.value?.trimEnd('.', ',', ')') ?: return
        if (drafts.any { it.url == url }) return
        drafts = drafts + Draft(Graph.uploads.nextId(), null, url, url, -1, "url")
    }

    fun remove(d: Draft) { drafts = drafts.filterNot { it.id == d.id } }

    fun replace(d: Draft) { drafts = drafts.map { if (it.id == d.id) d else it } }

    fun start() {
        val batch = Graph.uploads.nextId()
        val jobs = drafts.map { d ->
            UploadJob(
                id = d.id,
                uri = d.uri,
                url = d.url,
                name = d.name,
                size = d.size,
                mime = d.mime,
                tags = (tags + d.extraTags).distinctBy { it.lowercase() },
                safety = d.safety ?: safety,
                source = d.source ?: source,
                anonymous = anonymous,
                skipDuplicates = skipDuplicates,
                relateBatch = relateBatch,
                batchId = batch,
            )
        }
        Graph.uploads.enqueue(jobs)
        drafts = emptyList()
    }
}

@Composable
fun UploadScreen(onBack: () -> Unit, onOpenPost: (Int) -> Unit, vm: UploadViewModel = viewModel()) {
    val context = LocalContext.current
    val jobs by Graph.uploads.jobs.collectAsState()
    val pendingUris by Graph.pendingUploadUris.collectAsState()
    val pendingUrl by Graph.pendingUploadUrl.collectAsState()
    var urlDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Draft?>(null) }

    val media = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.addUris(context, uris)
    }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addUris(context, uris)
    }

    LaunchedEffect(pendingUris) {
        if (pendingUris.isNotEmpty()) {
            vm.addUris(context, pendingUris)
            Graph.pendingUploadUris.value = emptyList()
        }
    }
    LaunchedEffect(pendingUrl) {
        pendingUrl?.let {
            vm.addUrl(it)
            Graph.pendingUploadUrl.value = null
        }
    }

    val canAnon = Graph.can("posts:create:anonymous")

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text("Upload") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (jobs.any { it.finished }) TextButton(onClick = { Graph.uploads.clearFinished() }) { Text("Clear done") }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { Graph.lock.allowLeave(); media.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Gallery", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { Graph.lock.allowLeave(); docs.launch(arrayOf("image/*", "video/*", "application/x-shockwave-flash")) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Files", maxLines = 1)
                    }
                    OutlinedButton(onClick = { urlDialog = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("URL", maxLines = 1)
                    }
                }
                Text(
                    "Tip: share images, videos or links to ProtoBooru from any app and pick “Upload to ProtoBooru”.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (vm.drafts.isNotEmpty()) {
                item { SectionHeader("Ready to upload · ${vm.drafts.size}") }
                items(vm.drafts, key = { "d" + it.id }) { d ->
                    DraftRow(d, onEdit = { editing = d }, onRemove = { vm.remove(d) })
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    SectionHeader("Applies to all")
                    TagEditor(vm.tags, { vm.tags = it })
                    TagCopyBar(vm.tags, { vm.tags = it })
                    Spacer(Modifier.height(12.dp))
                    Text("Safety", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                    SafetySelector(vm.safety, { vm.safety = it ?: "safe" })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        vm.source, { vm.source = it }, label = { Text("Source (optional)") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 3,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.height(8.dp))
                    CheckRow("Skip images that are already on the booru", vm.skipDuplicates) { vm.skipDuplicates = it }
                    CheckRow("Link these uploads as related posts", vm.relateBatch) { vm.relateBatch = it }
                    if (canAnon) CheckRow("Upload anonymously", vm.anonymous) { vm.anonymous = it }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::start, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Upload ${vm.drafts.size} item${if (vm.drafts.size == 1) "" else "s"}")
                    }
                }
            } else if (jobs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text("Pick files or add a URL to get started.", color = Ink.TextDim)
                    }
                }
            }

            if (jobs.isNotEmpty()) {
                item { SectionHeader("Queue") }
                items(jobs.reversed(), key = { "j" + it.id }) { j ->
                    JobRow(j, onOpenPost)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (urlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("Upload from URL") },
            text = {
                Column {
                    OutlinedTextField(url, { url = it }, label = { Text("https://…") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Text(
                        "The server downloads it (yt-dlp handles most video sites).",
                        style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.addUrl(url); urlDialog = false }, enabled = url.startsWith("http")) { Text("Add") } },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancel") } },
            containerColor = Ink.Surface2,
        )
    }

    editing?.let { d -> DraftEditDialog(d, onDismiss = { editing = null }) { vm.replace(it); editing = null } }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DraftThumb(uri: Uri?, mime: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Ink.Surface3), contentAlignment = Alignment.Center) {
        when {
            uri == null -> Icon(Icons.Default.Link, null, tint = Ink.TextDim)
            mime.startsWith("image/") -> AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Icon(Icons.Default.PlayArrow, null, tint = Ink.TextDim)
        }
    }
}

@Composable
private fun DraftRow(d: Draft, onEdit: () -> Unit, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = Ink.Surface, border = BorderStroke(1.dp, Ink.Line)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DraftThumb(d.uri, d.mime, Modifier.size(56.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val extras = buildList {
                    if (d.uri != null) add(Format.bytes(d.size)) else add("from URL")
                    if (d.extraTags.isNotEmpty()) add("+${d.extraTags.size} tags")
                    d.safety?.let { add(it) }
                    if (d.source != null) add("own source")
                }
                Text(extras.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit item", tint = Ink.TextDim) }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove", tint = Ink.TextDim) }
        }
    }
}

@Composable
private fun DraftEditDialog(d: Draft, onDismiss: () -> Unit, onSave: (Draft) -> Unit) {
    var tags by remember { mutableStateOf(d.extraTags) }
    var safety by remember { mutableStateOf(d.safety) }
    var source by remember { mutableStateOf(d.source ?: "") }
    var ownSource by remember { mutableStateOf(d.source != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Extra tags for this item", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                TagEditor(tags, { tags = it })
                Spacer(Modifier.height(12.dp))
                Text("Safety", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                SafetySelector(safety, { safety = it }, allowUnchanged = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ownSource, { ownSource = it })
                    Text("Use its own source", style = MaterialTheme.typography.bodyMedium)
                }
                if (ownSource) OutlinedTextField(source, { source = it }, label = { Text("Source") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(d.copy(extraTags = tags, safety = safety, source = if (ownSource) source else null)) }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

@Composable
private fun JobRow(j: UploadJob, onOpenPost: (Int) -> Unit) {
    val context = LocalContext.current
    val clickable = j.postId != null && (j.state == UploadState.DONE || j.state == UploadState.DUPLICATE)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Ink.Surface,
        border = BorderStroke(1.dp, Ink.Line),
        modifier = Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable { onOpenPost(j.postId!!) } else Modifier),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DraftThumb(j.uri, j.mime, Modifier.size(48.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(j.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val (label, color) = when (j.state) {
                        UploadState.QUEUED -> "Queued" to Ink.TextDim
                        UploadState.UPLOADING -> "Uploading ${(j.progress * 100).toInt()}%" to Ink.Amber
                        UploadState.CHECKING -> "Checking for duplicates…" to Ink.Amber
                        UploadState.CREATING -> "Creating post…" to Ink.Amber
                        UploadState.DONE -> "Posted as #${j.postId}" to Ink.Green
                        UploadState.DUPLICATE -> (j.message ?: "Duplicate") to Ink.Sketchy
                        UploadState.FAILED -> (j.message ?: "Failed") to Ink.Red
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(safetyColor(j.safety)))
                        Text("${j.tags.size} tags", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    }
                }
                when (j.state) {
                    UploadState.DONE -> Icon(Icons.Default.CheckCircle, null, tint = Ink.Green)
                    UploadState.FAILED -> Row {
                        IconButton(onClick = {
                            com.frameender.protobooru.ui.post.copyText(context, j.message ?: "")
                        }) { Icon(Icons.Default.ContentCopy, "Copy error", tint = Ink.TextDim) }
                        IconButton(onClick = { Graph.uploads.retry(j.id) }) { Icon(Icons.Default.Refresh, "Retry", tint = Ink.Amber) }
                    }
                    UploadState.DUPLICATE -> Icon(Icons.Default.Error, null, tint = Ink.Sketchy)
                    else -> {}
                }
            }
            if (j.state == UploadState.UPLOADING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth())
            } else if (j.state == UploadState.CHECKING || j.state == UploadState.CREATING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
