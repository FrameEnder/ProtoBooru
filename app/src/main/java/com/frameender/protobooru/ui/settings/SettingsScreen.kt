package com.frameender.protobooru.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.frameender.protobooru.BuildConfigInfo
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.GridStyle
import com.frameender.protobooru.data.SearchHistory
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.post.NoteTextMode
import com.frameender.protobooru.ui.theme.Accents
import com.frameender.protobooru.ui.theme.Ink
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit, onUpdates: () -> Unit, onCustomizeHome: () -> Unit = {}) {
    val s by Graph.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackButton(onBack) }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionHeader("Server")
            ServerSetupCard()

            SectionHeader("Accent color")
            AccentPicker(s.accent) { key -> Graph.updateSettings { it.copy(accent = key) } }

            SectionHeader("Home screen")
            val homeCustom = s.homeLayout.isNotBlank()
            Text(
                if (homeCustom) "Using your custom layout." else "Using the default layout.",
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onCustomizeHome, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Dashboard, null)
                Spacer(Modifier.width(8.dp))
                Text("Customize Home widgets")
            }

            SectionHeader("Browsing")
            Text("Grid columns: ${s.gridColumns}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = s.gridColumns.toFloat(),
                onValueChange = { v -> Graph.updateSettings { it.copy(gridColumns = v.roundToInt()) } },
                valueRange = 1f..6f,
                steps = 4,
            )
            Text("Grid style", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.gridStyle == GridStyle.STAGGERED, { Graph.updateSettings { it.copy(gridStyle = GridStyle.STAGGERED) } }, { Text("Staggered") })
                FilterChip(s.gridStyle == GridStyle.SQUARE, { Graph.updateSettings { it.copy(gridStyle = GridStyle.SQUARE) } }, { Text("Square") })
            }
            Text("Results per page: ${s.pageSize}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = s.pageSize.toFloat(),
                onValueChange = { v -> Graph.updateSettings { it.copy(pageSize = (v / 10).roundToInt() * 10) } },
                valueRange = 20f..100f,
                steps = 7,
            )
            Toggle("Show score / favorite badges on thumbnails", s.showGridBadges) { v -> Graph.updateSettings { it.copy(showGridBadges = v) } }
            Toggle("Show notes on images by default", s.showNotes) { v -> Graph.updateSettings { it.copy(showNotes = v) } }
            Text("Note text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteTextMode.all.forEach { (key, label) ->
                    FilterChip(s.noteTextMode == key, { Graph.updateSettings { it.copy(noteTextMode = key) } }, { Text(label) })
                }
            }
            Text(
                when (s.noteTextMode) {
                    NoteTextMode.ALWAYS -> "Each note's text is shown inside its box. Tap a note to hide it."
                    NoteTextMode.TAP -> "Tap a note's outline to show its text inside the box."
                    else -> "Only outlines are drawn; read notes under the post's info."
                },
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
            )
            Toggle("Pure black background (AMOLED)", s.amoled) { v -> Graph.updateSettings { it.copy(amoled = v) } }

            SectionHeader("Tag blacklist")
            Toggle("Hide posts with blacklisted tags", s.blacklistEnabled) { v -> Graph.updateSettings { it.copy(blacklistEnabled = v) } }
            val blTags = remember(s.blacklist) { s.blacklist.split(Regex("[\\s,]+")).filter { it.isNotBlank() } }
            TagEditor(
                blTags,
                { list -> Graph.updateSettings { it.copy(blacklist = list.joinToString(" ")) } },
                label = "Add tag to blacklist",
                enabled = s.blacklistEnabled,
            )
            Text(
                "Searches, the Posts tab and Home leave these out. Use * as a wildcard (e.g. *_gore). " +
                    "Posts you reach another way, like pools or related posts, open covered with a “Show anyway” button. " +
                    "Searching for a blacklisted tag on purpose still finds it.",
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(top = 4.dp),
            )

            SectionHeader("Search history")
            Toggle("Remember my searches", s.searchHistoryEnabled) { v -> Graph.updateSettings { it.copy(searchHistoryEnabled = v) } }
            val historyCount = SearchHistory.list(s).size
            OutlinedButton(onClick = { SearchHistory.clear(); Graph.toast("Search history cleared") }, enabled = historyCount > 0) {
                Text(if (historyCount > 0) "Clear $historyCount saved searches" else "No saved searches", maxLines = 1)
            }

            SectionHeader("Video")
            Toggle("Autoplay videos", s.autoplayVideo) { v -> Graph.updateSettings { it.copy(autoplayVideo = v) } }
            Toggle("Start muted", s.startMuted) { v -> Graph.updateSettings { it.copy(startMuted = v) } }
            Toggle("Loop", s.loopVideo) { v -> Graph.updateSettings { it.copy(loopVideo = v) } }

            SectionHeader("Downloads")
            var pattern by remember(s.filenamePattern) { mutableStateOf(s.filenamePattern) }
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("Filename pattern") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Tokens: {id} {md5} {sha1} {tags} {safety} {type}") },
            )
            OutlinedButton(
                onClick = { Graph.updateSettings { it.copy(filenamePattern = pattern.ifBlank { "{id}_{md5}" }) }; Graph.toast("Saved") },
                enabled = pattern != s.filenamePattern,
            ) { Text("Save pattern") }
            Text(
                "Images save to Pictures/ProtoBooru, videos to Movies/ProtoBooru.",
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(top = 4.dp),
            )

            SectionHeader("Storage & offline")
            StorageSection()

            SectionHeader("About")
            val update by Graph.updater.available.collectAsState()
            OutlinedButton(onClick = onUpdates, modifier = Modifier.fillMaxWidth()) {
                Text(if (update != null) "Update available: ${update!!.title}" else "Check for updates")
            }
            Spacer(Modifier.height(8.dp))
            Text("ProtoBooru ${BuildConfigInfo.versionName(context)} (build ${BuildConfigInfo.versionCode(context)})", style = MaterialTheme.typography.bodyMedium)
            Text("A Szurubooru client. Fonts: Space Grotesk & JetBrains Mono (OFL).", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AccentPicker(selected: String, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Accents.all.forEach { a ->
            val on = a.key == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(a.key) }.padding(vertical = 4.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(a.main)
                        .border(if (on) 3.dp else 0.dp, if (on) Ink.Text else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Default.Check, null, tint = a.on, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    a.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) a.main else Ink.TextDim,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

/** Server address entry with a live connection test. Used by Settings and first-run setup. */
@Composable
fun ServerSetupCard(onSaved: () -> Unit = {}) {
    val s by Graph.settings.collectAsState()
    val info by Graph.info.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var apiPath by remember(s.apiPath) { mutableStateOf(s.apiPath) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(12.dp), color = Ink.Surface, border = BorderStroke(1.dp, Ink.Line)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(info?.config?.name ?: "Szurubooru server", style = MaterialTheme.typography.titleMedium)
                    if (info != null && s.configured) {
                        Text("${Format.count(info!!.postCount)} posts · ${Format.bytes(info!!.diskUsage)}", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim(); status = null },
                label = { Text("Server URL") },
                placeholder = { Text("http://100.x.y.z:8390") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiPath,
                onValueChange = { apiPath = it.trim(); status = null },
                label = { Text("API path or URL") },
                supportingText = { Text("Default /api. Use a full URL if your API is hosted elsewhere.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    if (ok) Icon(Icons.Default.CheckCircle, null, tint = Ink.Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(status!!, color = if (ok) Ink.Green else Ink.Red, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !busy && url.isNotBlank(),
                onClick = {
                    busy = true
                    status = null
                    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
                    val candidate = AppSettings(serverUrl = normalized, apiPath = apiPath.ifBlank { "/api" })
                    scope.launch {
                        try {
                            val i = Graph.api.testServer(candidate)
                            ok = true
                            status = "Connected to ${i.config.name ?: "server"} · ${i.postCount} posts"
                            val serverChanged = normalized.trimEnd('/') != s.root
                            Graph.updateSettings {
                                val base = it.copy(serverUrl = normalized, apiPath = candidate.apiPath)
                                // Tokens belong to a server; drop them when switching servers.
                                if (serverChanged) base.copy(username = "", token = "") else base
                            }
                            Graph.refreshServerState()
                            onSaved()
                        } catch (e: Exception) {
                            ok = false
                            status = e.message ?: "Could not connect"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test & save")
            }
        }
    }
}

/** Cache sizes, offline fallback, and clearing saved data. */
@Composable
private fun StorageSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Graph.settings.collectAsState()
    val saving by Graph.offlineSaver.progress.collectAsState()
    var usage by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh, saving == null) {
        usage = withContext(Dispatchers.IO) {
            val images = SingletonImageLoader.get(context).diskCache?.size ?: 0L
            val data = runCatching { Graph.http.cache?.size() ?: 0L }.getOrDefault(0L)
            images to data
        }
    }

    Toggle("Use saved copies when the server can't be reached", s.offlineFallback) { v -> Graph.updateSettings { it.copy(offlineFallback = v) } }
    Text(
        "Pages you've opened, and searches or pools you choose to “Save for offline”, still open " +
            "when Tailscale or the server is down. Videos are not saved, only their thumbnails.",
        style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
    )

    Text("Image cache size", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(256 to "256 MB", 512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB").forEach { (mb, label) ->
            FilterChip(s.imageCacheMb == mb, { Graph.updateSettings { it.copy(imageCacheMb = mb) } }, { Text(label) })
        }
    }
    Text(
        "Bigger keeps more images for offline use. A new size takes effect the next time the app starts.",
        style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
    )

    usage?.let { (images, data) ->
        Text(
            "Using ${Format.bytes(images)} for images and ${Format.bytes(data)} for saved pages",
            style = MaterialTheme.typography.labelMedium, color = Ink.TextDim, modifier = Modifier.padding(top = 10.dp),
        )
    }

    saving?.let { p ->
        Text(
            "Saving “${p.label}” for offline… ${p.done}/${p.total}",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
        )
        OutlinedButton(onClick = { Graph.offlineSaver.cancel() }) { Text("Stop saving", maxLines = 1) }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp),
    ) {
        OutlinedButton(onClick = {
            scope.launch {
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                Graph.toast("Image cache cleared")
                refresh++
            }
        }) { Text("Clear images", maxLines = 1) }
        OutlinedButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Graph.http.cache?.evictAll() } }
                Graph.toast("Saved pages cleared")
                refresh++
            }
        }) { Text("Clear saved pages", maxLines = 1) }
    }
}
