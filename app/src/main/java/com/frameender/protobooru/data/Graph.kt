package com.frameender.protobooru.data

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** A list of post ids the full-screen viewer can page through (search results, a pool, ...). */
interface PostSource {
    val ids: List<Int>
    val canLoadMore: Boolean
    val query: String?
    fun loadMore()
}

class StaticPostSource(override val ids: List<Int>, override val query: String? = null) : PostSource {
    override val canLoadMore = false
    override fun loadMore() {}
}

/**
 * Tiny service locator. Everything app-wide lives here so ViewModels can use
 * default constructors (no DI framework needed for an app this size).
 */
object Graph {
    lateinit var app: Application
        private set
    lateinit var store: SettingsStore
        private set
    lateinit var http: OkHttpClient
        private set
    lateinit var api: SzuruApi
        private set
    lateinit var downloads: Downloader
        private set
    lateinit var uploads: Uploader
        private set
    lateinit var bulk: BulkEditor
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = MutableStateFlow(AppSettings())
    val info = MutableStateFlow<Info?>(null)
    val me = MutableStateFlow<User?>(null)
    val tagCategories = MutableStateFlow<Map<String, TagCategory>>(emptyMap())
    val poolCategories = MutableStateFlow<Map<String, PoolCategory>>(emptyMap())
    val serverError = MutableStateFlow<String?>(null)

    /** One-off messages shown as snackbars by the root scaffold. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Set right before navigating to the viewer so it can swipe through the same list. */
    var viewerSource: PostSource? = null

    /** Image shared into the app from another app, waiting for the reverse-search screen. */
    val pendingSharedImage = MutableStateFlow<android.net.Uri?>(null)

    /** Files or links shared via "Upload to ProtoBooru", waiting for the upload screen. */
    val pendingUploadUris = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val pendingUploadUrl = MutableStateFlow<String?>(null)

    /** Emits a post id whenever that post is edited, so open viewers can reload it. */
    val postChanged = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** (old name, new name or null when deleted/merged away) whenever a tag is edited. */
    val tagChanged = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 16)

    /** Emits a pool id whenever that pool is edited (or deleted). */
    val poolChanged = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** Re-reads tag and pool categories after they're edited. */
    fun refreshCategories() {
        scope.launch {
            runCatching { tagCategories.value = api.tagCategories().results.associateBy { it.name } }
            runCatching { poolCategories.value = api.poolCategories().results.associateBy { it.name } }
        }
    }

    fun init(application: Application) {
        app = application
        store = SettingsStore(application)
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Generous timeouts: big uploads and server-side URL fetches (yt-dlp) take a while.
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .cache(Cache(File(application.cacheDir, "http"), 64L * 1024 * 1024))
            .build()
        api = SzuruApi(http) { settings.value }
        downloads = Downloader(application, http, api)
        uploads = Uploader(application, api)
        bulk = BulkEditor(api)

        // Settings are tiny; load synchronously so the first API call already knows the server.
        settings.value = runBlocking { store.flow.first() }
        scope.launch { store.flow.collect { settings.value = it } }
        refreshServerState()
    }

    fun toast(msg: String) {
        messages.tryEmit(msg)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        // Update in-memory immediately so callers can use the new values right away.
        settings.value = transform(settings.value)
        scope.launch { store.update(transform) }
    }

    /** Re-reads server info, categories and the logged-in user. */
    fun refreshServerState() {
        val s = settings.value
        if (!s.configured) return
        scope.launch {
            try {
                info.value = api.info()
                serverError.value = null
            } catch (e: Exception) {
                serverError.value = e.message ?: "Could not reach server"
            }
            runCatching { tagCategories.value = api.tagCategories().results.associateBy { it.name } }
            runCatching { poolCategories.value = api.poolCategories().results.associateBy { it.name } }
            if (s.loggedIn) {
                runCatching { me.value = api.user(s.username) }
            } else {
                me.value = null
            }
        }
    }

    fun logout(revokeToken: Boolean) {
        val s = settings.value
        scope.launch {
            if (revokeToken && s.loggedIn) {
                runCatching {
                    val t = api.tokens(s.username).results.firstOrNull { it.token == s.token }
                    if (t != null) api.deleteToken(s.username, t)
                }
            }
            updateSettings { it.copy(username = "", token = "") }
            me.value = null
            refreshServerState()
            toast("Logged out")
        }
    }

    val currentRank: String
        get() = me.value?.rank ?: if (settings.value.loggedIn) "regular" else "anonymous"

    /**
     * Whether the current user holds a privilege, per the server's /info config.
     * Unknown privileges are allowed; the server has the final say anyway.
     */
    fun can(privilege: String): Boolean {
        val need = info.value?.config?.privilegeMap?.get(privilege) ?: return true
        return Ranks.level(currentRank) >= Ranks.level(need)
    }

    fun isMe(name: String?): Boolean =
        settings.value.loggedIn && name != null && name.equals(settings.value.username, ignoreCase = true)
}
