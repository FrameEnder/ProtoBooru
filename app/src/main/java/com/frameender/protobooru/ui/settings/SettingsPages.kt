package com.frameender.protobooru.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.frameender.protobooru.BuildConfigInfo
import com.frameender.protobooru.R
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.GridStyle
import com.frameender.protobooru.data.OfflineCollection
import com.frameender.protobooru.data.OfflineRefreshScheduler
import com.frameender.protobooru.data.SearchHistory
import com.frameender.protobooru.ui.common.Avatar
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.post.NoteTextMode
import com.frameender.protobooru.ui.post.openUrl
import com.frameender.protobooru.ui.theme.Accents
import com.frameender.protobooru.ui.theme.Ink
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val D = SettingsDefaults

// =====================================================================
// Server & account
// =====================================================================

@Composable
fun ServerPage(onBack: () -> Unit, onOpenAccount: () -> Unit) {
    val s by Graph.settings.collectAsState()
    val me by Graph.me.collectAsState()
    SettingsPage("Server & account", onBack) {
        GroupLabel("Connection")
        ServerSetupCard()
        GroupLabel("Account")
        SettingsCard {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenAccount).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(me?.avatarUrl, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s.loggedIn) s.username else "Not signed in", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (s.loggedIn) (me?.rank?.replaceFirstChar { it.uppercase() } ?: "Signed in") + " · profile, tokens and log out"
                        else "Sign in from the Account tab",
                        style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                    )
                }
                Icon(Icons.Default.AccountCircle, null, tint = Ink.TextDim)
            }
        }
        Hint(
            "Signing in creates a login token for this phone, so your password is never stored. " +
                "Manage tokens from the Account tab.",
        )
    }
}

// =====================================================================
// Appearance
// =====================================================================

@Composable
fun AppearancePage(onBack: () -> Unit) {
    val s by Graph.settings.collectAsState()
    SettingsPage("Appearance", onBack) {
        GroupLabel("Accent color")
        SettingsCard {
            Column(Modifier.padding(14.dp)) {
                AccentPicker(s.accent) { key -> Graph.updateSettings { it.copy(accent = key) } }
            }
        }
        Hint("Used for buttons, highlights, the selected tab and the update pop-up.")
        GroupLabel("Theme")
        SettingsCard {
            SwitchSetting(
                "Pure black background",
                "Saves battery on OLED screens",
                s.amoled,
                onReset = { it.copy(amoled = D.amoled) },
            ) { v -> Graph.updateSettings { it.copy(amoled = v) } }
        }
        Hint("Long-press any setting to put it back to its default.")
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
                Text(a.label, style = MaterialTheme.typography.labelSmall, color = if (on) a.main else Ink.TextDim, maxLines = 1)
            }
        }
    }
}

// =====================================================================
// Browsing
// =====================================================================

@Composable
fun BrowsingPage(onBack: () -> Unit) {
    val s by Graph.settings.collectAsState()
    SettingsPage("Browsing", onBack) {
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Column(Modifier.padding(12.dp)) {
                GridPreview(s.gridColumns, s.gridStyle)
                Text(
                    "Live preview",
                    style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        GroupLabel("Grid")
        SegmentedSetting(
            listOf(
                Triple(GridStyle.STAGGERED, "Flow", Icons.Default.ViewQuilt),
                Triple(GridStyle.SQUARE, "Boxes", Icons.Default.GridView),
            ),
            selected = s.gridStyle,
        ) { v -> Graph.updateSettings { it.copy(gridStyle = v) } }
        Hint(if (s.gridStyle == GridStyle.STAGGERED) "Each post keeps its shape." else "Every post is cropped to a square.")
        Spacer(Modifier.height(12.dp))
        SettingsCard {
            SliderSetting(
                "Columns", "${s.gridColumns}", s.gridColumns.toFloat(), 1f..6f, 4,
                onReset = { it.copy(gridColumns = D.gridColumns) },
            ) { v -> Graph.updateSettings { it.copy(gridColumns = v.roundToInt()) } }
            CardDivider()
            SliderSetting(
                "Posts per page", "${s.pageSize}", s.pageSize.toFloat(), 20f..100f, 7,
                summary = "More means fewer pauses while scrolling",
                onReset = { it.copy(pageSize = D.pageSize) },
            ) { v -> Graph.updateSettings { it.copy(pageSize = (v / 10).roundToInt() * 10) } }
        }
        GroupLabel("Thumbnails & viewer")
        SettingsCard {
            SwitchSetting(
                "Thumbnail badges", "Score, favorites, video and related-post markers", s.showGridBadges,
                onReset = { it.copy(showGridBadges = D.showGridBadges) },
            ) { v -> Graph.updateSettings { it.copy(showGridBadges = v) } }
            CardDivider()
            SwitchSetting(
                "Show notes on images", "Note outlines appear when a post opens", s.showNotes,
                onReset = { it.copy(showNotes = D.showNotes) },
            ) { v -> Graph.updateSettings { it.copy(showNotes = v) } }
            CardDivider()
            ChipsSetting(
                "Note text",
                NoteTextMode.all,
                s.noteTextMode,
                summary = when (s.noteTextMode) {
                    NoteTextMode.ALWAYS -> "Each note's text sits inside its box"
                    NoteTextMode.TAP -> "Tap a note's outline to show its text"
                    else -> "Outlines only; read notes under the post's info"
                },
                onReset = { it.copy(noteTextMode = D.noteTextMode) },
            ) { v -> Graph.updateSettings { it.copy(noteTextMode = v) } }
        }
    }
}

/** A small stand-in grid that redraws as the layout settings change. */
@Composable
private fun GridPreview(columns: Int, style: GridStyle) {
    val cols = columns.coerceIn(1, 6)
    val shapes = remember { listOf(0.75f, 1.3f, 1f, 0.6f, 1.5f, 0.85f, 1.15f, 0.7f, 1.4f, 0.9f, 1.2f, 0.65f, 1f, 1.35f, 0.8f, 1.1f, 0.7f, 1.25f) }
    val hues = remember { listOf(Ink.Amber, Ink.Violet, Ink.Teal, Ink.Red, Ink.Green, Color(0xFF6FA8F0), Color(0xFFF48FB1), Color(0xFFE6C15A)) }
    Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(cols) { c ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until 8) {
                        val i = r * cols + c
                        val ratio = if (style == GridStyle.STAGGERED) shapes[i % shapes.size] else 1f
                        val hue = hues[i % hues.size]
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f / ratio)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(hue.copy(alpha = 0.75f), hue.copy(alpha = 0.25f)))),
                        )
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.Surface))),
        )
    }
}

// =====================================================================
// Filters
// =====================================================================

@Composable
fun FiltersPage(onBack: () -> Unit) {
    val s by Graph.settings.collectAsState()
    val info by Graph.info.collectAsState()
    SettingsPage("Filters", onBack) {
        GroupLabel("Safety ratings")
        SettingsCard {
            SwitchSetting("Safe", null, s.showSafe, onReset = { it.copy(showSafe = D.showSafe) }) { v -> Graph.updateSettings { it.copy(showSafe = v) } }
            CardDivider()
            SwitchSetting("Sketchy", null, s.showSketchy, onReset = { it.copy(showSketchy = D.showSketchy) }) { v -> Graph.updateSettings { it.copy(showSketchy = v) } }
            CardDivider()
            SwitchSetting("Unsafe", null, s.showUnsafe, onReset = { it.copy(showUnsafe = D.showUnsafe) }) { v -> Graph.updateSettings { it.copy(showUnsafe = v) } }
        }
        Hint(
            if (info?.config?.enableSafety == false) "Your server has safety ratings switched off, so these don't change anything."
            else "Posts with a rating that's switched off are left out of every search. The Posts tab has the same switches.",
        )

        GroupLabel("Tag blacklist")
        SettingsCard {
            SwitchSetting(
                "Hide posts with these tags", null, s.blacklistEnabled,
                onReset = { it.copy(blacklistEnabled = D.blacklistEnabled) },
            ) { v -> Graph.updateSettings { it.copy(blacklistEnabled = v) } }
            CardDivider()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val tags = remember(s.blacklist) { s.blacklist.split(Regex("[\\s,]+")).filter { it.isNotBlank() } }
                TagEditor(
                    tags,
                    { list -> Graph.updateSettings { it.copy(blacklist = list.joinToString(" ")) } },
                    label = "Add tag",
                    enabled = s.blacklistEnabled,
                )
            }
        }
        Hint(
            "Use * as a wildcard (e.g. *_gore). Posts reached another way, like pools or related posts, " +
                "open covered with a “Show anyway” button. Searching for a blacklisted tag on purpose still finds it.",
        )
    }
}

// =====================================================================
// Search
// =====================================================================

@Composable
fun SearchPage(onBack: () -> Unit) {
    val s by Graph.settings.collectAsState()
    val history = SearchHistory.list(s)
    SettingsPage("Search", onBack) {
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            SwitchSetting(
                "Remember my searches", "Shown when you tap a search box", s.searchHistoryEnabled,
                onReset = { it.copy(searchHistoryEnabled = D.searchHistoryEnabled) },
            ) { v -> Graph.updateSettings { it.copy(searchHistoryEnabled = v) } }
        }
        GroupLabel(if (history.isEmpty()) "No saved searches" else "Recent searches · ${history.size}")
        if (history.isNotEmpty()) {
            SettingsCard {
                history.forEachIndexed { i, q ->
                    if (i > 0) CardDivider()
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = Ink.TextDim, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(q, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { SearchHistory.remove(q) }) {
                            Icon(Icons.Default.Close, "Remove", tint = Ink.TextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                CardDivider()
                ActionRow("Clear all", color = Ink.Red) { SearchHistory.clear(); Graph.toast("Search history cleared") }
            }
        }
        Hint("Keeps your last 30 searches on this phone only.")
    }
}

// =====================================================================
// Video
// =====================================================================

@Composable
fun VideoPage(onBack: () -> Unit) {
    val s by Graph.settings.collectAsState()
    SettingsPage("Video", onBack) {
        GroupLabel("Playback")
        SettingsCard {
            SwitchSetting("Autoplay", "Start playing when a video opens", s.autoplayVideo, onReset = { it.copy(autoplayVideo = D.autoplayVideo) }) { v ->
                Graph.updateSettings { it.copy(autoplayVideo = v) }
            }
            CardDivider()
            SwitchSetting("Start muted", "The mute button remembers your choice until the app closes", s.startMuted, onReset = { it.copy(startMuted = D.startMuted) }) { v ->
                Graph.updateSettings { it.copy(startMuted = v) }
            }
            CardDivider()
            SwitchSetting("Loop", null, s.loopVideo, onReset = { it.copy(loopVideo = D.loopVideo) }) { v ->
                Graph.updateSettings { it.copy(loopVideo = v) }
            }
        }
        GroupLabel("Fullscreen gestures")
        SettingsCard {
            Gesture(Icons.Default.FastRewind, "Double-tap left", "Back 5 seconds")
            CardDivider()
            Gesture(Icons.Default.FastForward, "Double-tap right", "Forward 15 seconds")
            CardDivider()
            Gesture(Icons.Default.TouchApp, "Keep tapping", "Each extra tap skips again")
            CardDivider()
            Gesture(Icons.Default.BrightnessMedium, "Swipe up or down on the left", "Brightness (only while in fullscreen)")
            CardDivider()
            Gesture(Icons.Default.VolumeUp, "Swipe up or down on the right", "Volume")
        }
    }
}

@Composable
private fun Gesture(icon: ImageVector, title: String, what: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, Ink.Green, size = 34)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(what, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
        }
    }
}

// =====================================================================
// Downloads
// =====================================================================

private val TOKENS = listOf("{id}", "{md5}", "{sha1}", "{tags}", "{safety}", "{type}")

@Composable
fun DownloadsPage(onBack: () -> Unit) {
    val s by Graph.settings.collectAsState()
    var pattern by remember(s.filenamePattern) { mutableStateOf(s.filenamePattern) }
    SettingsPage("Downloads", onBack) {
        GroupLabel("File names")
        SettingsCard {
            Column(Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Tap to add", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TOKENS.forEach { t ->
                        androidx.compose.material3.AssistChip(
                            onClick = { pattern += if (pattern.isEmpty() || pattern.endsWith("_")) t else "_$t" },
                            label = { Text(t, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                Text(
                    "Example: " + examplePattern(pattern.ifBlank { "{id}_{md5}" }) + ".jpg",
                    style = MaterialTheme.typography.labelMedium, color = Ink.TextDim, modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pattern = D.filenamePattern }) { Text("Default", maxLines = 1) }
                    TextButton(
                        onClick = { Graph.updateSettings { it.copy(filenamePattern = pattern.ifBlank { D.filenamePattern }) }; Graph.toast("Saved") },
                        enabled = pattern != s.filenamePattern,
                    ) { Text("Save", maxLines = 1) }
                }
            }
        }
        GroupLabel("Where files go")
        SettingsCard {
            Gallery(Icons.Default.Image, "Images & GIFs", "Pictures/ProtoBooru")
            CardDivider()
            Gallery(Icons.Default.Movie, "Videos", "Movies/ProtoBooru")
        }
        Hint("Files you've already downloaded are skipped, so downloading a whole pool twice is quick.")
    }
}

private fun examplePattern(p: String) = p
    .replace("{id}", "4821").replace("{md5}", "9f86d081").replace("{sha1}", "a94a8fe5")
    .replace("{tags}", "mira_sunset_scenery").replace("{safety}", "safe").replace("{type}", "image")

@Composable
private fun Gallery(icon: ImageVector, title: String, where: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, Color(0xFFE6C15A), size = 34)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(where, style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
        }
    }
}

// =====================================================================
// Storage & offline
// =====================================================================

private data class Usage(val library: Long, val images: Long, val pages: Long, val updates: Long)

private fun dirSize(f: File): Long = if (!f.exists()) 0L else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

@Composable
fun StoragePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Graph.settings.collectAsState()
    val saving by Graph.offlineSaver.progress.collectAsState()
    val collections = Graph.offlineSaver.collections(s)
    var usage by remember { mutableStateOf<Usage?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(refresh, saving == null) {
        usage = withContext(Dispatchers.IO) {
            Usage(
                library = Graph.library.bytes(),
                images = SingletonImageLoader.get(context).diskCache?.size ?: 0L,
                pages = runCatching { Graph.http.cache?.size() ?: 0L }.getOrDefault(0L),
                updates = dirSize(File(context.cacheDir, "updates")),
            )
        }
    }

    SettingsPage("Storage & offline", onBack) {
        Spacer(Modifier.height(8.dp))
        // ---------- usage ----------
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                val u = usage
                // The bar shows the whole total; the cache part has a limit, the offline library doesn't.
                val used = u?.let { it.library + it.images + it.pages + it.updates } ?: 0L
                val capacity = used.coerceAtLeast(1L)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (u == null) "…" else Format.bytes(used), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("used on this phone", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Ink.Surface3),
                ) {
                    if (u != null && capacity > 0) {
                        var rest = 1f
                        listOf(u.library to Ink.Green, u.images to Ink.Amber, u.pages to Ink.Teal, u.updates to Ink.Violet).forEach { (bytes, color) ->
                            val f = (bytes.toFloat() / capacity).coerceIn(0f, rest)
                            if (f > 0.001f) {
                                Box(Modifier.fillMaxHeight().weight(f).background(color))
                                rest -= f
                            }
                        }
                        if (rest > 0.001f) Spacer(Modifier.weight(rest))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Ink.Green, "Saved offline ${u?.let { Format.bytes(it.library) } ?: "…"}")
                    Legend(Ink.Amber, "Image cache ${u?.let { Format.bytes(it.images) } ?: "…"}")
                    Legend(Ink.Teal, "Pages ${u?.let { Format.bytes(it.pages) } ?: "…"}")
                    Legend(Ink.Violet, "Updates ${u?.let { Format.bytes(it.updates) } ?: "…"}")
                }
            }
        }

        // ---------- saved collections ----------
        GroupLabel("Saved for offline")
        saving?.let { p ->
            SettingsCard(Modifier.padding(bottom = 8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Saving “${p.label}”" + if (p.total > 0) " · ${p.done}/${p.total}" else "",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { Graph.offlineSaver.cancel() }) { Text("Stop", maxLines = 1) }
                    }
                    if (p.total > 0) {
                        LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        }
        if (collections.isEmpty()) {
            SettingsCard {
                Text(
                    "Nothing saved yet. Use “Save these results for offline” in the Posts filter menu, or “Save offline” on a pool.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.TextDim, modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            SettingsCard {
                collections.forEachIndexed { i, c ->
                    if (i > 0) CardDivider()
                    CollectionRow(c, busy = saving != null)
                }
            }
            Hint("Removing one deletes its files from the phone, except posts another saved collection still uses.")
        }

        // ---------- behaviour ----------
        GroupLabel("Behaviour")
        SettingsCard {
            SwitchSetting(
                "Offline mode",
                "Use only what's saved on this phone, even when the server is reachable",
                s.forceOffline,
                onReset = { it.copy(forceOffline = D.forceOffline) },
            ) { v ->
                Graph.updateSettings { it.copy(forceOffline = v) }
                if (!v) scope.launch { Graph.checkConnection() }
            }
            CardDivider()
            SwitchSetting(
                "Switch to offline automatically", "When the server can't be reached, show only what's saved", s.offlineFallback,
                onReset = { it.copy(offlineFallback = D.offlineFallback) },
            ) { v -> Graph.updateSettings { it.copy(offlineFallback = v) } }
            CardDivider()
            SwitchSetting(
                "Refresh saved collections", "Once a day, on Wi-Fi while charging", s.offlineAutoRefresh,
                onReset = { it.copy(offlineAutoRefresh = D.offlineAutoRefresh) },
            ) { v ->
                Graph.updateSettings { it.copy(offlineAutoRefresh = v) }
                OfflineRefreshScheduler.apply(context, Graph.settings.value)
            }
            CardDivider()
            ChipsSetting(
                "Image cache size",
                listOf(256 to "256 MB", 512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB"),
                s.imageCacheMb,
                summary = "Bigger keeps more pictures for offline. Applies the next time the app starts.",
                onReset = { it.copy(imageCacheMb = D.imageCacheMb) },
            ) { v -> Graph.updateSettings { it.copy(imageCacheMb = v) } }
        }

        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                    Graph.toast("Images cleared")
                    refresh++
                }
            }) { Text("Clear images", maxLines = 1) }
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { Graph.http.cache?.evictAll() } }
                    Graph.toast("Saved pages cleared")
                    refresh++
                }
            }) { Text("Clear pages", maxLines = 1) }
        }
        if (collections.isNotEmpty()) {
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Delete everything saved for offline", color = Ink.Red, maxLines = 1)
            }
        }
        if (confirmClear) {
            com.frameender.protobooru.ui.common.ConfirmDialog(
                title = "Delete everything saved?",
                text = "All ${collections.size} saved collections and their files are removed from this phone.",
                confirmLabel = "Delete",
                destructive = true,
                onDismiss = { confirmClear = false },
                onConfirm = {
                    scope.launch(Dispatchers.IO) {
                        collections.forEach { Graph.offlineSaver.forget(it) }
                        Graph.library.clear()
                        refresh++
                    }
                },
            )
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
    }
}

@Composable
private fun CollectionRow(c: OfflineCollection, busy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(if (c.poolId > 0) Icons.Default.Collections else Icons.Default.Search, if (c.poolId > 0) Ink.Violet else Ink.Amber, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${c.count} posts · " + (if (c.videos) "pictures & videos" else "pictures") +
                    (if (c.savedAt > 0) " · " + Format.agoMillis(c.savedAt) else ""),
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { Graph.offlineSaver.refresh(c) }, enabled = !busy) {
            Icon(Icons.Default.Refresh, "Refresh", tint = if (busy) Ink.Line else Ink.TextDim)
        }
        IconButton(onClick = { Graph.offlineSaver.forget(c) }) {
            Icon(Icons.Default.Delete, "Remove", tint = Ink.Red)
        }
    }
}

// =====================================================================
// About
// =====================================================================

@Composable
fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by Graph.settings.collectAsState()
    val repo = "https://github.com/${s.updateRepo.trim('/')}"
    SettingsPage("About", onBack) {
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Ink.Bg), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(12.dp))
                Text("ProtoBooru", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${BuildConfigInfo.versionName(context)} · build ${BuildConfigInfo.versionCode(context)} · ${s.updateChannel}",
                    style = MaterialTheme.typography.labelMedium, color = Ink.TextDim,
                )
                Text(
                    "A Szurubooru client for Android",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.TextDim, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        GroupLabel("Links")
        SettingsCard {
            LinkRow("Source code", repo.removePrefix("https://")) { openUrl(context, repo) }
            CardDivider()
            LinkRow("Report a problem", "GitHub issues") { openUrl(context, "$repo/issues") }
            CardDivider()
            LinkRow("Szurubooru", "github.com/rr-/szurubooru") { openUrl(context, "https://github.com/rr-/szurubooru") }
        }
        GroupLabel("Licenses")
        SettingsCard {
            Text(
                "Space Grotesk and JetBrains Mono fonts: SIL Open Font License 1.1.\n" +
                    "Jetpack Compose, Media3, WorkManager, DataStore: Apache 2.0.\n" +
                    "Coil, OkHttp, kotlinx.serialization: Apache 2.0.",
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun LinkRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Ink.TextDim, modifier = Modifier.size(18.dp))
    }
}
