package com.frameender.protobooru.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "protobooru")

enum class GridStyle { SQUARE, STAGGERED }

data class AppSettings(
    val serverUrl: String = "",
    val apiPath: String = "/api",
    val username: String = "",
    val token: String = "",
    val gridColumns: Int = 3,
    val gridStyle: GridStyle = GridStyle.STAGGERED,
    val showSafe: Boolean = true,
    val showSketchy: Boolean = true,
    val showUnsafe: Boolean = true,
    val showGridBadges: Boolean = true,
    val autoplayVideo: Boolean = true,
    val startMuted: Boolean = true,
    val loopVideo: Boolean = true,
    val showNotes: Boolean = true,
    val filenamePattern: String = "{id}_{md5}",
    val pageSize: Int = 60,
    val amoled: Boolean = false,
    val accent: String = "amber",
    // In-app updates (GitHub Releases)
    val updateChannel: String = "stable",          // "stable" or "nightly"
    val autoUpdateCheck: Boolean = true,
    val updateNotify: Boolean = false,
    val updateRepo: String = "FrameEnder/ProtoBooru",
    val githubToken: String = "",                  // only needed while the repo is private
    val skippedUpdate: Long = 0,                   // build number the user chose to skip in the update pop-up
    // Tag blacklist: posts with these tags are left out of searches. "*" works as a wildcard.
    val blacklistEnabled: Boolean = true,
    val blacklist: String = "",                    // tags separated by spaces
    // Search history (newest first, one per line)
    val searchHistoryEnabled: Boolean = true,
    val searchHistory: String = "",
    // Tags copied with "Copy tags", ready to paste onto another post
    val copiedTags: String = "",                   // tags separated by spaces
    val copiedTagsFrom: Int = 0,                   // post they came from (0 = typed/other)
    // Offline
    val imageCacheMb: Int = 512,                   // size of the image disk cache (applies on next app start)
    val offlineFallback: Boolean = true,           // show saved copies when the server can't be reached
    val noteTextMode: String = "tap",              // "off", "tap", or "always" (see NoteTextMode)
    val homeLayout: String = "",                   // JSON list of HomeWidget; blank = default layout
) {
    val configured: Boolean get() = serverUrl.isNotBlank()
    val loggedIn: Boolean get() = username.isNotBlank() && token.isNotBlank()

    /** e.g. http://100.114.238.73:8390 with no trailing slash */
    val root: String get() = serverUrl.trim().trimEnd('/')

    /** e.g. http://100.114.238.73:8390/api */
    val apiBase: String
        get() {
            val p = apiPath.trim()
            if (p.startsWith("http://") || p.startsWith("https://")) return p.trimEnd('/')
            return root + "/" + p.trim('/')
        }

    /** Blacklisted tag patterns (lowercase), or nothing when the blacklist is switched off. */
    val blacklistTags: List<String>
        get() = if (!blacklistEnabled) emptyList()
        else blacklist.split(Regex("[\\s,]+")).map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()

    /**
     * Terms added to every post search: the safety filter plus a `-tag` for each blacklisted
     * tag. A blacklisted tag you search for on purpose (e.g. "gore") is not excluded.
     */
    fun filterTerms(query: String): List<String> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return buildList {
            if (!query.contains("safety:")) safetyTerm?.let { add(it) }
            blacklistTags.forEach { t -> if (t !in words) add("-" + escapeQueryTerm(t)) }
        }
    }

    /** Safety filter term appended to post searches, or null when everything is shown. */
    val safetyTerm: String?
        get() {
            val s = buildList {
                if (showSafe) add("safe")
                if (showSketchy) add("sketchy")
                if (showUnsafe) add("unsafe")
            }
            return if (s.size == 3 || s.isEmpty()) null else "safety:" + s.joinToString(",")
        }
}

class SettingsStore(private val context: Context) {
    private object K {
        val server = stringPreferencesKey("server")
        val apiPath = stringPreferencesKey("api_path")
        val user = stringPreferencesKey("user")
        val token = stringPreferencesKey("token")
        val cols = intPreferencesKey("cols")
        val gridStyle = stringPreferencesKey("grid_style")
        val safe = booleanPreferencesKey("safe")
        val sketchy = booleanPreferencesKey("sketchy")
        val unsafe = booleanPreferencesKey("unsafe")
        val badges = booleanPreferencesKey("badges")
        val autoplay = booleanPreferencesKey("autoplay")
        val muted = booleanPreferencesKey("muted")
        val loop = booleanPreferencesKey("loop")
        val notes = booleanPreferencesKey("notes")
        val pattern = stringPreferencesKey("pattern")
        val pageSize = intPreferencesKey("page_size")
        val amoled = booleanPreferencesKey("amoled")
        val accent = stringPreferencesKey("accent")
        val updChannel = stringPreferencesKey("upd_channel")
        val updAuto = booleanPreferencesKey("upd_auto")
        val updNotify = booleanPreferencesKey("upd_notify")
        val updRepo = stringPreferencesKey("upd_repo")
        val ghToken = stringPreferencesKey("gh_token")
        val updSkip = longPreferencesKey("upd_skip")
        val blOn = booleanPreferencesKey("bl_on")
        val bl = stringPreferencesKey("bl")
        val histOn = booleanPreferencesKey("hist_on")
        val hist = stringPreferencesKey("hist")
        val clipTags = stringPreferencesKey("clip_tags")
        val clipFrom = intPreferencesKey("clip_from")
        val imgCache = intPreferencesKey("img_cache_mb")
        val offline = booleanPreferencesKey("offline_fallback")
        val noteText = stringPreferencesKey("note_text")
        val homeLayout = stringPreferencesKey("home_layout")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            serverUrl = this[K.server] ?: d.serverUrl,
            apiPath = this[K.apiPath] ?: d.apiPath,
            username = this[K.user] ?: d.username,
            token = this[K.token] ?: d.token,
            gridColumns = this[K.cols] ?: d.gridColumns,
            gridStyle = runCatching { GridStyle.valueOf(this[K.gridStyle] ?: "") }.getOrDefault(d.gridStyle),
            showSafe = this[K.safe] ?: d.showSafe,
            showSketchy = this[K.sketchy] ?: d.showSketchy,
            showUnsafe = this[K.unsafe] ?: d.showUnsafe,
            showGridBadges = this[K.badges] ?: d.showGridBadges,
            autoplayVideo = this[K.autoplay] ?: d.autoplayVideo,
            startMuted = this[K.muted] ?: d.startMuted,
            loopVideo = this[K.loop] ?: d.loopVideo,
            showNotes = this[K.notes] ?: d.showNotes,
            filenamePattern = this[K.pattern] ?: d.filenamePattern,
            pageSize = this[K.pageSize] ?: d.pageSize,
            amoled = this[K.amoled] ?: d.amoled,
            accent = this[K.accent] ?: d.accent,
            updateChannel = this[K.updChannel] ?: d.updateChannel,
            autoUpdateCheck = this[K.updAuto] ?: d.autoUpdateCheck,
            updateNotify = this[K.updNotify] ?: d.updateNotify,
            updateRepo = this[K.updRepo] ?: d.updateRepo,
            githubToken = this[K.ghToken] ?: d.githubToken,
            skippedUpdate = this[K.updSkip] ?: d.skippedUpdate,
            blacklistEnabled = this[K.blOn] ?: d.blacklistEnabled,
            blacklist = this[K.bl] ?: d.blacklist,
            searchHistoryEnabled = this[K.histOn] ?: d.searchHistoryEnabled,
            searchHistory = this[K.hist] ?: d.searchHistory,
            copiedTags = this[K.clipTags] ?: d.copiedTags,
            copiedTagsFrom = this[K.clipFrom] ?: d.copiedTagsFrom,
            imageCacheMb = this[K.imgCache] ?: d.imageCacheMb,
            offlineFallback = this[K.offline] ?: d.offlineFallback,
            noteTextMode = this[K.noteText] ?: d.noteTextMode,
            homeLayout = this[K.homeLayout] ?: d.homeLayout,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.server] = s.serverUrl
            p[K.apiPath] = s.apiPath
            p[K.user] = s.username
            p[K.token] = s.token
            p[K.cols] = s.gridColumns
            p[K.gridStyle] = s.gridStyle.name
            p[K.safe] = s.showSafe
            p[K.sketchy] = s.showSketchy
            p[K.unsafe] = s.showUnsafe
            p[K.badges] = s.showGridBadges
            p[K.autoplay] = s.autoplayVideo
            p[K.muted] = s.startMuted
            p[K.loop] = s.loopVideo
            p[K.notes] = s.showNotes
            p[K.pattern] = s.filenamePattern
            p[K.pageSize] = s.pageSize
            p[K.amoled] = s.amoled
            p[K.accent] = s.accent
            p[K.updChannel] = s.updateChannel
            p[K.updAuto] = s.autoUpdateCheck
            p[K.updNotify] = s.updateNotify
            p[K.updRepo] = s.updateRepo
            p[K.ghToken] = s.githubToken
            p[K.updSkip] = s.skippedUpdate
            p[K.blOn] = s.blacklistEnabled
            p[K.bl] = s.blacklist
            p[K.histOn] = s.searchHistoryEnabled
            p[K.hist] = s.searchHistory
            p[K.clipTags] = s.copiedTags
            p[K.clipFrom] = s.copiedTagsFrom
            p[K.imgCache] = s.imageCacheMb
            p[K.offline] = s.offlineFallback
            p[K.noteText] = s.noteTextMode
            p[K.homeLayout] = s.homeLayout
        }
    }
}

/** Escapes characters Szurubooru's query parser treats specially (":" splits named tokens). */
fun escapeQueryTerm(t: String): String = t.replace("\\", "\\\\").replace(":", "\\:")

/** Whether a tag name matches a blacklist pattern ("*" = any characters). */
fun matchesTagPattern(pattern: String, name: String): Boolean {
    if (!pattern.contains('*')) return pattern == name.lowercase()
    val rx = pattern.split('*').joinToString(".*") { Regex.escape(it) }
    return Regex("^$rx$").matches(name.lowercase())
}
