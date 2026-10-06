package com.frameender.protobooru.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val noteTextMode: String = "tap",              // "off", "tap", or "always" (see NoteTextMode)
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
        val noteText = stringPreferencesKey("note_text")
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
            noteTextMode = this[K.noteText] ?: d.noteTextMode,
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
            p[K.noteText] = s.noteTextMode
        }
    }
}
