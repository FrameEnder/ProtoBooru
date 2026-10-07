package com.frameender.protobooru.ui.settings

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.BuildConfigInfo
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.GridStyle
import com.frameender.protobooru.data.HomeLayouts
import com.frameender.protobooru.data.SearchHistory
import com.frameender.protobooru.ui.common.Avatar
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.SearchField
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.post.openUrl
import com.frameender.protobooru.ui.theme.Accents
import com.frameender.protobooru.ui.theme.Ink

/**
 * The Settings pages. [key] goes in the route (settings/<key>); Home screen and Updates
 * have their own screens elsewhere in the app.
 */
enum class SettingsSection(val key: String, val title: String, val icon: ImageVector, val tint: Color) {
    SERVER("server", "Server & account", Icons.Default.Dns, Color(0xFFF2A93B)),
    APPEARANCE("appearance", "Appearance", Icons.Default.Palette, Color(0xFFF2A93B)),
    HOME("home", "Home screen", Icons.Default.Dashboard, Color(0xFF6FA8F0)),
    BROWSING("browsing", "Browsing", Icons.Default.GridView, Color(0xFFB394E8)),
    FILTERS("filters", "Filters", Icons.Default.VisibilityOff, Color(0xFFE5574F)),
    SEARCH("search", "Search", Icons.Default.History, Color(0xFF7DB8B5)),
    VIDEO("video", "Video", Icons.Default.PlayCircle, Color(0xFF8BC37A)),
    DOWNLOADS("downloads", "Downloads", Icons.Default.Download, Color(0xFFE6C15A)),
    STORAGE("storage", "Storage & offline", Icons.Default.CloudOff, Color(0xFF7DB8B5)),
    UPDATES("updates", "Updates", Icons.Default.SystemUpdate, Color(0xFFF2A93B)),
    ABOUT("about", "About", Icons.Default.Info, Color(0xFF9A978F));

    companion object {
        fun of(key: String?): SettingsSection? = entries.firstOrNull { it.key == key }
    }
}

/** One searchable setting: its name, other words people might type, and its page. */
private data class SettingEntry(val title: String, val section: SettingsSection, val keywords: String = "")

private val SEARCH_INDEX = listOf(
    SettingEntry("Server address", SettingsSection.SERVER, "url host ip port tailscale api path connect"),
    SettingEntry("Account & login", SettingsSection.SERVER, "user token sign in log out password"),
    SettingEntry("Accent color", SettingsSection.APPEARANCE, "theme colour highlight amber pink blue"),
    SettingEntry("Pure black background", SettingsSection.APPEARANCE, "amoled oled dark theme"),
    SettingEntry("Customize Home widgets", SettingsSection.HOME, "layout dashboard widgets start screen"),
    SettingEntry("Grid layout (flow or boxes)", SettingsSection.BROWSING, "staggered square masonry view"),
    SettingEntry("Grid columns", SettingsSection.BROWSING, "thumbnails size per row"),
    SettingEntry("Posts per page", SettingsSection.BROWSING, "page size load more"),
    SettingEntry("Thumbnail badges", SettingsSection.BROWSING, "score favorites counters icons"),
    SettingEntry("Notes on images", SettingsSection.BROWSING, "translation note text boxes"),
    SettingEntry("Safety filter", SettingsSection.FILTERS, "safe sketchy unsafe nsfw rating"),
    SettingEntry("Tag blacklist", SettingsSection.FILTERS, "hide block exclude tags mute"),
    SettingEntry("Search history", SettingsSection.SEARCH, "recent searches clear"),
    SettingEntry("Autoplay videos", SettingsSection.VIDEO, "play automatically"),
    SettingEntry("Start muted", SettingsSection.VIDEO, "sound audio volume mute"),
    SettingEntry("Loop videos", SettingsSection.VIDEO, "repeat"),
    SettingEntry("Fullscreen gestures", SettingsSection.VIDEO, "double tap seek skip brightness volume swipe"),
    SettingEntry("Filename pattern", SettingsSection.DOWNLOADS, "save name md5 id"),
    SettingEntry("Download location", SettingsSection.DOWNLOADS, "pictures movies folder"),
    SettingEntry("Saved for offline", SettingsSection.STORAGE, "offline collections pool favorites refresh"),
    SettingEntry("Image cache size", SettingsSection.STORAGE, "storage space disk"),
    SettingEntry("Clear cache", SettingsSection.STORAGE, "storage space free delete"),
    SettingEntry("Use saved copies offline", SettingsSection.STORAGE, "offline tailscale down unreachable"),
    SettingEntry("Update channel", SettingsSection.UPDATES, "stable nightly release version"),
    SettingEntry("Update notifications", SettingsSection.UPDATES, "notify pop-up background check"),
    SettingEntry("GitHub token", SettingsSection.UPDATES, "private repo"),
    SettingEntry("Version & licenses", SettingsSection.ABOUT, "about build fonts github"),
)

private data class SummaryExtras(val version: String, val build: Long)

private fun mb(n: Int) = if (n >= 1024) "${n / 1024} GB" else "$n MB"

/** Live summary shown under each category on the main page. */
private fun summary(section: SettingsSection, s: AppSettings, extra: SummaryExtras): String = when (section) {
    SettingsSection.SERVER -> s.root.removePrefix("http://").removePrefix("https://").ifBlank { "Not set up" }
    SettingsSection.APPEARANCE ->
        (Accents.all.firstOrNull { it.key == s.accent }?.label ?: "Amber") + " accent · AMOLED " + (if (s.amoled) "on" else "off")
    SettingsSection.HOME ->
        if (s.homeLayout.isBlank()) "Default layout"
        else "Custom layout · ${HomeLayouts.decode(s.homeLayout).count { it.enabled }} widgets"
    SettingsSection.BROWSING ->
        (if (s.gridStyle == GridStyle.STAGGERED) "Flow view" else "Box view") + " · ${s.gridColumns} columns · ${s.pageSize} per page"
    SettingsSection.FILTERS -> {
        val shown = listOfNotNull("safe".takeIf { s.showSafe }, "sketchy".takeIf { s.showSketchy }, "unsafe".takeIf { s.showUnsafe })
        val safety = if (shown.size == 3 || shown.isEmpty()) "All ratings" else shown.joinToString(" + ").replaceFirstChar { it.uppercase() }
        val bl = s.blacklistTags.size
        safety + " · " + when {
            !s.blacklistEnabled -> "blacklist off"
            bl == 0 -> "no blacklist"
            else -> "$bl blacklisted tags"
        }
    }
    SettingsSection.SEARCH ->
        if (!s.searchHistoryEnabled) "History off" else "History on · ${SearchHistory.list(s).size} saved"
    SettingsSection.VIDEO -> listOf(
        if (s.autoplayVideo) "Autoplay" else "Tap to play",
        if (s.startMuted) "start muted" else "sound on",
        if (s.loopVideo) "loop" else "no loop",
    ).joinToString(" · ")
    SettingsSection.DOWNLOADS -> s.filenamePattern + " · Pictures/ProtoBooru"
    SettingsSection.STORAGE -> {
        val n = Graph.offlineSaver.collections(s).size
        "${mb(s.imageCacheMb)} image cache · " + if (n == 0) "nothing saved offline" else "$n saved for offline"
    }
    SettingsSection.UPDATES ->
        s.updateChannel.replaceFirstChar { it.uppercase() } + " channel · " + if (s.autoUpdateCheck) "checks every 6 h" else "manual checks"
    SettingsSection.ABOUT -> "ProtoBooru ${extra.version} · build ${extra.build}"
}

private val GROUPS = listOf(
    "Look & feel" to listOf(SettingsSection.APPEARANCE, SettingsSection.HOME, SettingsSection.BROWSING),
    "Content" to listOf(SettingsSection.FILTERS, SettingsSection.SEARCH, SettingsSection.VIDEO),
    "Data" to listOf(SettingsSection.DOWNLOADS, SettingsSection.STORAGE),
    "App" to listOf(SettingsSection.UPDATES, SettingsSection.ABOUT),
)

/**
 * The main Settings page: a search box, the connection card, and every category with a
 * live one-line summary of what's set inside it.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onUpdates: () -> Unit,
    onCustomizeHome: () -> Unit = {},
    onOpenSection: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val s by Graph.settings.collectAsState()
    val update by Graph.updater.available.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val extras = remember { SummaryExtras(BuildConfigInfo.versionName(context), BuildConfigInfo.versionCode(context)) }

    fun open(section: SettingsSection) {
        when (section) {
            SettingsSection.HOME -> onCustomizeHome()
            SettingsSection.UPDATES -> onUpdates()
            else -> onOpenSection(section.key)
        }
    }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { openUrl(context, "https://github.com/${s.updateRepo.trim('/')}#readme") }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, "Help")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = {},
                placeholder = "Search settings",
                mono = false,
            )

            val q = query.trim()
            if (q.isNotEmpty()) {
                val words = q.split(' ').filter { it.isNotBlank() }
                val hits = SEARCH_INDEX.filter { e ->
                    words.all { w -> e.title.contains(w, true) || e.keywords.contains(w, true) || e.section.title.contains(w, true) }
                }
                GroupLabel(if (hits.isEmpty()) "No matches" else "${hits.size} matches")
                if (hits.isNotEmpty()) {
                    SettingsCard {
                        hits.forEachIndexed { i, e ->
                            if (i > 0) CardDivider()
                            NavRow(e.section.icon, e.section.tint, e.title, e.section.title) { open(e.section) }
                        }
                    }
                } else {
                    Hint("Try words like “cache”, “mute”, “blacklist” or “columns”.")
                }
            } else {
                Spacer(Modifier.height(14.dp))
                ConnectionCard(s) { open(SettingsSection.SERVER) }

                GROUPS.forEach { (label, sections) ->
                    GroupLabel(label)
                    SettingsCard {
                        sections.forEachIndexed { i, section ->
                            if (i > 0) CardDivider()
                            NavRow(
                                icon = section.icon,
                                tint = section.tint,
                                title = section.title,
                                summary = summary(section, s, extras),
                                badge = if (section == SettingsSection.UPDATES && update != null) "NEW" else null,
                            ) { open(section) }
                        }
                    }
                }
            }
        }
    }
}

/** Who you are and whether the server answers, with its response time. */
@Composable
private fun ConnectionCard(s: AppSettings, onClick: () -> Unit) {
    val me by Graph.me.collectAsState()
    val offline by Graph.offline.collectAsState()
    var ping by remember { mutableStateOf<Long?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(s.root, s.token) {
        if (!s.configured) return@LaunchedEffect
        failed = false
        ping = null
        val t0 = SystemClock.elapsedRealtime()
        try {
            Graph.info.value = Graph.api.info()
            ping = SystemClock.elapsedRealtime() - t0
        } catch (e: Exception) {
            failed = true
        }
    }
    val bad = s.configured && (failed || offline)
    val accent = if (bad) Ink.Red else Ink.Amber
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.04f))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(me?.avatarUrl, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        s.loggedIn -> s.username
                        s.configured -> "Browsing anonymously"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            when {
                                bad -> Ink.Red
                                ping != null -> Ink.Green
                                else -> Ink.TextDim
                            },
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.root.removePrefix("http://").removePrefix("https://").ifBlank { "Tap to set up your server" } +
                            when {
                                !s.configured -> ""
                                bad -> " · unreachable"
                                ping != null -> " · $ping ms"
                                else -> " · checking…"
                            },
                        style = MaterialTheme.typography.labelSmall, color = Ink.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (s.loggedIn) {
                    Text(
                        (me?.rank?.replaceFirstChar { it.uppercase() } ?: "Signed in") + " · signed in with a device token",
                        style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextDim)
        }
    }
}

/** Opens one Settings page by key (route settings/<key>). */
@Composable
fun SettingsSectionScreen(key: String?, onBack: () -> Unit, onOpenAccount: () -> Unit) {
    when (SettingsSection.of(key)) {
        SettingsSection.SERVER -> ServerPage(onBack, onOpenAccount)
        SettingsSection.APPEARANCE -> AppearancePage(onBack)
        SettingsSection.BROWSING -> BrowsingPage(onBack)
        SettingsSection.FILTERS -> FiltersPage(onBack)
        SettingsSection.SEARCH -> SearchPage(onBack)
        SettingsSection.VIDEO -> VideoPage(onBack)
        SettingsSection.DOWNLOADS -> DownloadsPage(onBack)
        SettingsSection.STORAGE -> StoragePage(onBack)
        SettingsSection.ABOUT -> AboutPage(onBack)
        else -> LaunchedEffect(Unit) { onBack() }
    }
}
