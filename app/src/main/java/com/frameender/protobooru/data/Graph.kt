package com.frameender.protobooru.data

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    lateinit var updater: Updater
        private set
    lateinit var offlineSaver: OfflineSaver
        private set
    lateinit var library: OfflineLibrary
        private set
    lateinit var lock: com.frameender.protobooru.security.AppLock
        private set

    /** HTTP client for images. No HTTP cache: Coil keeps its own (bigger) image disk cache. */
    lateinit var imageHttp: OkHttpClient
        private set

    /** True while the server can't be reached; screens then show only what's saved. */
    val offline = MutableStateFlow(false)

    /**
     * Offline mode: either the server can't be reached, or the user turned offline mode on.
     * Grids then list only posts saved on the phone, and their files load from storage.
     */
    val offlineMode: Boolean get() = settings.value.forceOffline || offline.value

    /**
     * Changes of [offlineMode] (going offline or back online), without the current value.
     * Screens collect it to reload from the right place, so every list and count switches
     * between "what the server has" and "what's saved on this phone".
     */
    val offlineModeChanges: kotlinx.coroutines.flow.Flow<Boolean>
        get() = combine(offline, settings.map { it.forceOffline }) { down, forced -> down || forced }
            .distinctUntilChanged()
            .drop(1)

    /**
     * While offline mode is on (on purpose): whether the server answers right now. Checked
     * every 30 seconds while the app is open, so the app can offer to go back online.
     */
    val serverReachable = MutableStateFlow(false)

    /** Set when the "server is reachable, go online?" prompt should be shown. */
    val offlinePrompt = MutableStateFlow(false)

    /** Leaves offline mode (from the prompt, the offline pill or Settings) and reconnects. */
    fun goOnline() {
        offlinePrompt.value = false
        updateSettings { it.copy(forceOffline = false) }
        scope.launch {
            if (checkConnection()) {
                refreshServerState()
                toast("Back online")
            } else {
                toast("Offline mode is off, but the server didn't answer")
            }
        }
    }

    /**
     * Tries the server right now (skipping the "it just failed, use saved copies" window).
     * Returns true when it answered.
     */
    suspend fun checkConnection(): Boolean {
        if (settings.value.forceOffline || !settings.value.configured) return false
        api.resetFailure()
        return runCatching { info.value = api.info() }.isSuccess && !offline.value
    }

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

    /** The post the viewer was last showing, so the grid it came from can scroll to it on return. */
    var lastViewedPostId: Int? = null

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
        // First, so the app is already locked before any screen is drawn.
        lock = com.frameender.protobooru.security.AppLock(application)
        store = SettingsStore(application)
        http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // Generous timeouts: big uploads and server-side URL fetches (yt-dlp) take a while.
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            // Saved API responses: lets screens you've visited (or saved for offline) open when
            // the server can't be reached. See SzuruApi.exec.
            .cache(Cache(File(application.cacheDir, "http"), 128L * 1024 * 1024))
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val resp = chain.proceed(req)
                // Szurubooru doesn't send cache headers. Store JSON answers as "already stale"
                // (max-age=0): normal requests still always go to the server, but the saved copy
                // can be served when the server is unreachable. ("no-cache" would forbid that:
                // OkHttp never serves a no-cache response, not even for only-if-cached requests.)
                if (req.method == "GET" && req.header("Accept") == "application/json" && resp.isSuccessful) {
                    resp.newBuilder()
                        .header("Cache-Control", "max-age=0")
                        .removeHeader("Pragma")
                        .removeHeader("Expires")
                        .removeHeader("Vary")
                        .build()
                } else {
                    resp
                }
            }
            .build()
        imageHttp = http.newBuilder().cache(null).build()
        api = SzuruApi(http) { settings.value }
        downloads = Downloader(application, http, api)
        uploads = Uploader(application, api)
        bulk = BulkEditor(api)
        updater = Updater(application, http)
        offlineSaver = OfflineSaver(application)
        library = OfflineLibrary(application)
        startConnectionChecks()

        // Settings are tiny; load synchronously so the first API call already knows the server.
        settings.value = runBlocking { store.flow.first() }
        com.frameender.protobooru.ui.theme.Accents.select(settings.value.accent)
        scope.launch { settings.collect { com.frameender.protobooru.ui.theme.Accents.select(it.accent) } }
        scope.launch { store.flow.collect { settings.value = it } }
        refreshServerState()

        // Updates: background schedule + one check per app start.
        UpdateScheduler.apply(application, settings.value)
        OfflineRefreshScheduler.apply(application, settings.value)
        if (settings.value.autoUpdateCheck) scope.launch { updater.check() }
    }

    /**
     * Background connection checks, only while the app is on screen:
     *  - offline because the server couldn't be reached: try again every 30 seconds and switch
     *    back to online by itself as soon as it answers;
     *  - offline mode turned on by the user: never switch by itself, but check whether the
     *    server answers and offer to go back online (see [offlinePrompt]).
     */
    private fun startConnectionChecks() {
        val fg = lock.foreground
        // Automatic offline → automatic online.
        scope.launch {
            combine(offline, fg, settings.map { it.forceOffline }.distinctUntilChanged()) { down, open, forced -> down && open && !forced }
                .distinctUntilChanged()
                .collectLatest { checking ->
                    while (checking) {
                        delay(30_000)
                        checkConnection()
                    }
                }
        }
        // Offline mode on purpose → ask once the server is reachable.
        scope.launch {
            var before: Pair<Boolean, Boolean>? = null
            combine(settings.map { it.forceOffline && it.configured }.distinctUntilChanged(), fg) { forced, open -> forced to open }
                .distinctUntilChanged()
                .collectLatest { now ->
                    val (forced, open) = now
                    // Turned on just now, with the app open: the user knows the server is there,
                    // so don't ask straight away; only once it has been unreachable and comes back.
                    val justTurnedOn = before?.let { !it.first && it.second } == true && forced && open
                    before = now
                    if (!forced) {
                        serverReachable.value = false
                        offlinePrompt.value = false
                        return@collectLatest
                    }
                    if (!open) return@collectLatest
                    var last: Boolean? = if (justTurnedOn) true else null
                    while (true) {
                        val up = api.ping()
                        serverReachable.value = up
                        if (up && last != true) offlinePrompt.value = true
                        if (!up) offlinePrompt.value = false
                        last = up
                        delay(30_000)
                    }
                }
        }
    }

    /** Screen to open from outside the UI (e.g. tapping the update notification). */
    val pendingRoute = MutableStateFlow<String?>(null)

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
            // Saved pages belonged to that account; don't show them to whoever logs in next.
            withContext(Dispatchers.IO) { runCatching { http.cache?.evictAll() } }
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
