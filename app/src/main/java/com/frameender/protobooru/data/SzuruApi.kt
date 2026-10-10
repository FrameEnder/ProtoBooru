package com.frameender.protobooru.data

import android.util.Base64
import android.os.SystemClock
import okhttp3.Response
import okhttp3.CacheControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SzuruException(
    val status: Int,
    val errorName: String?,
    message: String,
    val detail: String? = null,
) : IOException(message)

/**
 * Thin client over the Szurubooru REST API. Everything except post editing/creation
 * is covered. Credentials and server address are read fresh from [settings] on every call.
 */
class SzuruApi(
    private val client: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val jsonType = "application/json".toMediaType()

    // ---------------- URL helpers ----------------

    /**
     * Address to show a post's picture or video from: the downloaded copy when it's in the
     * offline library, otherwise the server. Use [resolve] when the server copy is required.
     */
    fun media(path: String?, s: AppSettings = settings()): String? =
        Graph.library.localUri(path) ?: resolve(path, s)

    /** Turns a relative `data/...` path from the API into an absolute URL. */
    fun resolve(path: String?, s: AppSettings = settings()): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return s.root + "/" + path.trimStart('/')
    }

    fun postWebUrl(id: Int): String = settings().root + "/post/" + id

    private fun url(
        segments: List<String>,
        params: Map<String, String?> = emptyMap(),
        s: AppSettings = settings(),
    ): HttpUrl {
        val base = s.apiBase.toHttpUrlOrNull()
            ?: throw SzuruException(0, "BadServer", "Server address is not set or is not a valid URL")
        val b = base.newBuilder()
        segments.forEach { b.addPathSegment(it) }
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun authHeader(s: AppSettings): String? =
        if (s.loggedIn) "Token " + b64("${s.username}:${s.token}") else null

    private fun b64(v: String): String = Base64.encodeToString(v.toByteArray(), Base64.NO_WRAP)

    private fun request(u: HttpUrl, auth: String?): Request.Builder {
        val b = Request.Builder().url(u).header("Accept", "application/json")
        if (auth != null) b.header("Authorization", auth)
        return b
    }

    /** When the server last failed to answer (elapsed-realtime ms); 0 = it's been fine. */
    @Volatile private var lastFailureAt = 0L

    /** Makes the next request try the server even if it just failed. */
    fun resetFailure() {
        lastFailureAt = 0L
    }

    /** The saved copy of this exact request, or null if there isn't one. */
    private fun cachedCopy(req: Request): Response? {
        val r = runCatching {
            client.newCall(req.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()).execute()
        }.getOrNull() ?: return null
        if (!r.isSuccessful) {
            r.close()
            return null
        }
        return r
    }

    private suspend fun exec(req: Request): String = withContext(Dispatchers.IO) {
        val forced = settings().forceOffline
        if (forced) {
            // Offline mode on purpose: never touch the network.
            if (req.method != "GET") throw IOException("Offline mode is on. Turn it off in Settings → Storage & offline to make changes.")
            return@withContext cachedCopy(req)?.use { it.body?.string().orEmpty() }
                ?: throw IOException("Offline mode is on, and this page hasn't been saved for offline")
        }
        val offlineOk = req.method == "GET" && settings().offlineFallback
        val now = SystemClock.elapsedRealtime()
        // The server just failed: use saved copies right away instead of waiting out another
        // connection timeout per request. The network is tried again every 30 seconds.
        val recentlyDown = lastFailureAt != 0L && now - lastFailureAt < 30_000
        val early = if (offlineOk && recentlyDown) cachedCopy(req) else null
        val resp = early ?: try {
            client.newCall(req).execute().also {
                if (it.networkResponse != null) {
                    lastFailureAt = 0L
                    Graph.offline.value = false
                }
            }
        } catch (e: IOException) {
            lastFailureAt = SystemClock.elapsedRealtime()
            // Couldn't reach the server at all (not a slow upload or an error answer):
            // switch the app to offline mode until it's reachable again.
            val unreachable = e is java.net.ConnectException || e is java.net.UnknownHostException ||
                e is java.net.NoRouteToHostException || (e is java.net.SocketTimeoutException && req.method == "GET")
            if (unreachable) Graph.offline.value = true
            if (!offlineOk) throw e
            // Server unreachable: fall back to the last saved copy of this exact page.
            cachedCopy(req) ?: throw IOException(
                "Can't reach the server, and this page hasn't been saved for offline (${e.message ?: e.javaClass.simpleName})",
                e,
            )
        }
        if (resp.networkResponse == null && resp.cacheResponse != null) Graph.offline.value = true
        resp.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = runCatching { json.decodeFromString(ApiError.serializer(), body) }.getOrNull()
                throw SzuruException(
                    status = resp.code,
                    errorName = err?.name,
                    message = err?.title?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}",
                    detail = err?.description ?: body.take(300).ifBlank { null },
                )
            }
            body
        }
    }

    private fun JsonObject.body(): RequestBody = toString().toRequestBody(jsonType)

    private suspend inline fun <reified T> get(
        segments: List<String>,
        params: Map<String, String?> = emptyMap(),
    ): T {
        val s = settings()
        return json.decodeFromString(exec(request(url(segments, params, s), authHeader(s)).get().build()))
    }

    private suspend inline fun <reified T> send(
        method: String,
        segments: List<String>,
        body: JsonObject = JsonObject(emptyMap()),
    ): T {
        val s = settings()
        val req = request(url(segments, s = s), authHeader(s)).method(method, body.body()).build()
        return json.decodeFromString(exec(req))
    }

    /**
     * Szurubooru's file convention: multipart/form-data where the JSON request goes in a
     * part named `metadata` and each file in a part named after its field.
     */
    private suspend inline fun <reified T> sendMultipart(
        method: String,
        segments: List<String>,
        metadata: JsonObject?,
        files: Map<String, Pair<String, RequestBody>>,
    ): T {
        val s = settings()
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        if (metadata != null) mb.addFormDataPart("metadata", null, metadata.toString().toRequestBody(jsonType))
        files.forEach { (field, file) -> mb.addFormDataPart(field, file.first, file.second) }
        val req = request(url(segments, s = s), authHeader(s)).method(method, mb.build()).build()
        return json.decodeFromString(exec(req))
    }

    private fun strings(v: List<String>) = JsonArray(v.map { JsonPrimitive(it) })
    private fun ints(v: List<Int>) = JsonArray(v.map { JsonPrimitive(it) })

    private fun pageParams(offset: Int, limit: Int, query: String?, fields: String? = null) = mapOf(
        "offset" to offset.toString(),
        "limit" to limit.toString(),
        "query" to query?.trim()?.ifBlank { null },
        "fields" to fields,
    )

    // ---------------- Info / connection ----------------

    suspend fun info(): Info = get<Info>(listOf("info")).also { i -> i.featuredPost?.let { Blacklist.learn(it) } }

    /**
     * Whether the server answers right now. Always asks the server itself (never a saved
     * copy, even in offline mode) and gives up after a few seconds. Used to offer leaving
     * offline mode once the server is reachable.
     */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        val s = settings()
        if (!s.configured) return@withContext false
        runCatching {
            val req = request(url(listOf("info"), s = s), authHeader(s)).cacheControl(CacheControl.FORCE_NETWORK).get().build()
            client.newBuilder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Checks a server address before saving it. */
    suspend fun testServer(candidate: AppSettings): Info {
        val req = request(url(listOf("info"), s = candidate), null).get().build()
        return json.decodeFromString(exec(req))
    }

    /**
     * Verifies a username/password, then creates a dedicated login token so the password
     * never has to be stored on the device.
     */
    suspend fun login(candidate: AppSettings, user: String, password: String): Pair<User, UserToken> {
        val basic = "Basic " + b64("$user:$password")
        val userReq = request(
            url(listOf("user", user), mapOf("bump-login" to "true"), candidate), basic,
        ).get().build()
        val u: User = json.decodeFromString(exec(userReq))
        val body = buildJsonObject {
            put("enabled", true)
            put("note", "ProtoBooru (Android)")
        }
        val tokReq = request(url(listOf("user-token", u.name), s = candidate), basic)
            .post(body.body()).build()
        val t: UserToken = json.decodeFromString(exec(tokReq))
        return u to t
    }

    /** Validates a pasted token by fetching the user with it. */
    suspend fun verifyToken(candidate: AppSettings): User {
        val req = request(
            url(listOf("user", candidate.username), mapOf("bump-login" to "true"), candidate),
            authHeader(candidate),
        ).get().build()
        return json.decodeFromString(exec(req))
    }

    // ---------------- Posts (read + interactions) ----------------

    /**
     * A page of posts. While a tag blacklist is set, grid pages also bring each post's tags
     * so the app can tell which ones to hide or blur (see [Blacklist]).
     */
    suspend fun posts(query: String?, offset: Int, limit: Int, fields: String? = gridFields()): Paged<Post> {
        val page: Paged<Post> = get(listOf("posts"), pageParams(offset, limit, query, fields))
        if (fields == null || fields.split(',').contains("tags")) Blacklist.learn(page.results)
        return page
    }

    /** [GRID_FIELDS], plus tags while a blacklist is set. */
    fun gridFields(s: AppSettings = settings()): String =
        if (Blacklist.active(s)) "$GRID_FIELDS,tags" else GRID_FIELDS

    suspend fun post(id: Int): Post = get<Post>(listOf("post", id.toString())).also { Blacklist.learn(it) }

    suspend fun around(id: Int, query: String?): Around =
        get(listOf("post", id.toString(), "around"), mapOf("query" to query?.ifBlank { null }, "fields" to "id,thumbnailUrl"))

    suspend fun featured(): Post? = runCatching<Post> { get(listOf("featured-post")) }.getOrNull()

    suspend fun ratePost(id: Int, score: Int): Post =
        send("PUT", listOf("post", id.toString(), "score"), buildJsonObject { put("score", score) })

    suspend fun favorite(id: Int): Post = send("POST", listOf("post", id.toString(), "favorite"))

    suspend fun unfavorite(id: Int): Post = send("DELETE", listOf("post", id.toString(), "favorite"))

    suspend fun reverseSearch(bytes: ByteArray, mime: String): ReverseSearchResult {
        val s = settings()
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("content", "query", bytes.toRequestBody(mime.toMediaType()))
            .build()
        val req = request(url(listOf("posts", "reverse-search"), s = s), authHeader(s)).post(multipart).build()
        return json.decodeFromString(exec(req))
    }

    /** Downloads any URL (usually post content) through the shared client. */
    suspend fun fetchBytes(absoluteUrl: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(absoluteUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw SzuruException(r.code, null, "Download failed (HTTP ${r.code})")
            r.body?.bytes() ?: ByteArray(0)
        }
    }

    // ---------------- Comments ----------------

    suspend fun comments(query: String?, offset: Int, limit: Int): Paged<Comment> =
        get(listOf("comments"), pageParams(offset, limit, query))

    suspend fun createComment(postId: Int, text: String): Comment =
        send("POST", listOf("comments"), buildJsonObject {
            put("text", text)
            put("postId", postId)
        })

    suspend fun editComment(c: Comment, text: String): Comment =
        send("PUT", listOf("comment", c.id.toString()), buildJsonObject {
            put("version", c.version)
            put("text", text)
        })

    suspend fun deleteComment(c: Comment) {
        send<JsonObject>("DELETE", listOf("comment", c.id.toString()), buildJsonObject { put("version", c.version) })
    }

    suspend fun rateComment(id: Int, score: Int): Comment =
        send("PUT", listOf("comment", id.toString(), "score"), buildJsonObject { put("score", score) })

    // ---------------- Tags ----------------

    suspend fun tags(query: String?, offset: Int, limit: Int): Paged<Tag> =
        get(listOf("tags"), pageParams(offset, limit, query))

    suspend fun tag(name: String): Tag = get(listOf("tag", name))

    suspend fun tagSiblings(name: String): Unpaged<TagSibling> = get(listOf("tag-siblings", name))

    suspend fun tagCategories(): Unpaged<TagCategory> = get(listOf("tag-categories"))

    /** Autocomplete: tags whose name starts with [prefix], most used first. */
    suspend fun suggestTags(prefix: String, limit: Int = 12): List<Tag> {
        val clean = prefix.replace(":", "\\:")
        return tags("$clean* sort:usages", 0, limit).results
    }

    // ---------------- Pools ----------------

    suspend fun pools(query: String?, offset: Int, limit: Int): Paged<Pool> =
        get(listOf("pools"), pageParams(offset, limit, query))

    suspend fun pool(id: Int): Pool = get(listOf("pool", id.toString()))

    suspend fun poolCategories(): Unpaged<PoolCategory> = get(listOf("pool-categories"))

    // ---------------- Users & account ----------------

    suspend fun users(query: String?, offset: Int, limit: Int): Paged<User> =
        get(listOf("users"), pageParams(offset, limit, query))

    suspend fun user(name: String): User = get(listOf("user", name))

    suspend fun updateUser(
        u: User,
        email: String? = null,
        password: String? = null,
        avatarStyle: String? = null,
        rank: String? = null,
    ): User = send("PUT", listOf("user", u.name), buildJsonObject {
        put("version", u.version)
        if (email != null) put("email", email)
        if (password != null) put("password", password)
        if (avatarStyle != null) put("avatarStyle", avatarStyle)
        if (rank != null) put("rank", rank)
    })

    /** Switches the user to a manually uploaded avatar. */
    suspend fun uploadAvatar(u: User, file: RequestBody, filename: String): User =
        sendMultipart(
            "PUT", listOf("user", u.name),
            buildJsonObject {
                put("version", u.version)
                put("avatarStyle", "manual")
            },
            mapOf("avatar" to (filename to file)),
        )

    suspend fun deleteUser(u: User) {
        send<JsonObject>("DELETE", listOf("user", u.name), buildJsonObject { put("version", u.version) })
    }

    suspend fun tokens(user: String): Unpaged<UserToken> = get(listOf("user-tokens", user))

    suspend fun createToken(user: String, note: String, expirationTime: String?): UserToken =
        send("POST", listOf("user-token", user), buildJsonObject {
            put("enabled", true)
            put("note", note)
            if (expirationTime != null) put("expirationTime", expirationTime)
        })

    suspend fun updateToken(user: String, t: UserToken, enabled: Boolean, note: String): UserToken =
        send("PUT", listOf("user-token", user, t.token), buildJsonObject {
            put("version", t.version)
            put("enabled", enabled)
            put("note", note)
        })

    suspend fun deleteToken(user: String, t: UserToken) {
        send<JsonObject>("DELETE", listOf("user-token", user, t.token), buildJsonObject { put("version", t.version) })
    }

    // ---------------- History ----------------

    suspend fun snapshots(query: String?, offset: Int, limit: Int): Paged<Snapshot> =
        get(listOf("snapshots"), pageParams(offset, limit, query))

    // =====================================================================
    // Write side
    // =====================================================================

    // ---------------- Uploads ----------------

    /** Uploads a file to temporary storage and returns its token (valid for a limited time). */
    suspend fun uploadTemp(file: RequestBody, filename: String): String {
        val r: UploadToken = sendMultipart("POST", listOf("uploads"), null, mapOf("content" to (filename to file)))
        return r.token
    }

    suspend fun reverseSearchToken(token: String): ReverseSearchResult =
        send("POST", listOf("posts", "reverse-search"), buildJsonObject { put("contentToken", token) })

    /**
     * Creates a post from either an upload token or a remote URL (the server fetches the URL,
     * using yt-dlp for supported sites).
     */
    suspend fun createPost(
        tags: List<String>,
        safety: String,
        source: String?,
        relations: List<Int>,
        flags: List<String>,
        anonymous: Boolean,
        contentToken: String? = null,
        contentUrl: String? = null,
    ): Post = send("POST", listOf("posts"), buildJsonObject {
        put("tags", strings(tags))
        put("safety", safety)
        if (!source.isNullOrBlank()) put("source", source)
        if (relations.isNotEmpty()) put("relations", ints(relations))
        if (flags.isNotEmpty()) put("flags", strings(flags))
        if (anonymous) put("anonymous", true)
        if (contentToken != null) put("contentToken", contentToken)
        if (contentUrl != null) put("contentUrl", contentUrl)
    })

    // ---------------- Post editing ----------------

    /** Partial post update. Only non-null fields are sent. */
    suspend fun updatePost(
        p: Post,
        tags: List<String>? = null,
        safety: String? = null,
        source: String? = null,
        relations: List<Int>? = null,
        flags: List<String>? = null,
        notes: List<Note>? = null,
        contentToken: String? = null,
    ): Post = send("PUT", listOf("post", p.id.toString()), buildJsonObject {
        put("version", p.version)
        if (tags != null) put("tags", strings(tags))
        if (safety != null) put("safety", safety)
        if (source != null) put("source", source)
        if (relations != null) put("relations", ints(relations))
        if (flags != null) put("flags", strings(flags))
        if (notes != null) put("notes", JsonArray(notes.map { n ->
            buildJsonObject {
                put("polygon", JsonArray(n.polygon.map { pt -> JsonArray(pt.map { JsonPrimitive(it) }) }))
                put("text", n.text)
            }
        }))
        if (contentToken != null) put("contentToken", contentToken)
    })

    /** Sets a custom thumbnail, or resets to the generated one when [file] is null. */
    suspend fun setThumbnail(p: Post, file: RequestBody?, filename: String = "thumbnail.jpg"): Post =
        sendMultipart(
            "PUT", listOf("post", p.id.toString()),
            buildJsonObject { put("version", p.version) },
            mapOf("thumbnail" to (filename to (file ?: ByteArray(0).toRequestBody(null)))),
        )

    suspend fun deletePost(p: Post) {
        send<JsonObject>("DELETE", listOf("post", p.id.toString()), buildJsonObject { put("version", p.version) })
    }

    suspend fun mergePosts(remove: Post, into: Post, replaceContent: Boolean): Post =
        send("POST", listOf("post-merge"), buildJsonObject {
            put("removeVersion", remove.version)
            put("remove", remove.id)
            put("mergeToVersion", into.version)
            put("mergeTo", into.id)
            put("replaceContent", replaceContent)
        })

    suspend fun featurePost(id: Int): Post = send("POST", listOf("featured-post"), buildJsonObject { put("id", id) })

    // ---------------- Tags ----------------

    suspend fun createTag(
        names: List<String>,
        category: String,
        description: String?,
        implications: List<String>,
        suggestions: List<String>,
    ): Tag = send("POST", listOf("tags"), buildJsonObject {
        put("names", strings(names))
        put("category", category)
        if (!description.isNullOrBlank()) put("description", description)
        put("implications", strings(implications))
        put("suggestions", strings(suggestions))
    })

    suspend fun updateTag(
        t: Tag,
        names: List<String>,
        category: String,
        description: String,
        implications: List<String>,
        suggestions: List<String>,
    ): Tag = send("PUT", listOf("tag", t.name), buildJsonObject {
        put("version", t.version)
        put("names", strings(names))
        put("category", category)
        put("description", description)
        put("implications", strings(implications))
        put("suggestions", strings(suggestions))
    })

    suspend fun deleteTag(t: Tag) {
        send<JsonObject>("DELETE", listOf("tag", t.name), buildJsonObject { put("version", t.version) })
    }

    suspend fun mergeTags(remove: Tag, into: Tag): Tag = send("POST", listOf("tag-merge"), buildJsonObject {
        put("removeVersion", remove.version)
        put("remove", remove.name)
        put("mergeToVersion", into.version)
        put("mergeTo", into.name)
    })

    // ---------------- Tag categories ----------------

    suspend fun createTagCategory(name: String, color: String, order: Int): TagCategory =
        send("POST", listOf("tag-categories"), buildJsonObject {
            put("name", name)
            put("color", color)
            put("order", order)
        })

    suspend fun updateTagCategory(c: TagCategory, name: String, color: String, order: Int): TagCategory =
        send("PUT", listOf("tag-category", c.name), buildJsonObject {
            put("version", c.version)
            if (name != c.name) put("name", name)
            put("color", color)
            put("order", order)
        })

    suspend fun deleteTagCategory(c: TagCategory) {
        send<JsonObject>("DELETE", listOf("tag-category", c.name), buildJsonObject { put("version", c.version) })
    }

    suspend fun setDefaultTagCategory(c: TagCategory): TagCategory =
        send("PUT", listOf("tag-category", c.name, "default"))

    // ---------------- Pools ----------------

    suspend fun createPool(names: List<String>, category: String, description: String?, posts: List<Int>): Pool =
        send("POST", listOf("pool"), buildJsonObject {
            put("names", strings(names))
            put("category", category)
            if (!description.isNullOrBlank()) put("description", description)
            put("posts", ints(posts))
        })

    suspend fun updatePool(
        p: Pool,
        names: List<String>? = null,
        category: String? = null,
        description: String? = null,
        posts: List<Int>? = null,
    ): Pool = send("PUT", listOf("pool", p.id.toString()), buildJsonObject {
        put("version", p.version)
        if (names != null) put("names", strings(names))
        if (category != null) put("category", category)
        if (description != null) put("description", description)
        if (posts != null) put("posts", ints(posts))
    })

    suspend fun deletePool(p: Pool) {
        send<JsonObject>("DELETE", listOf("pool", p.id.toString()), buildJsonObject { put("version", p.version) })
    }

    suspend fun mergePools(remove: Pool, into: Pool): Pool = send("POST", listOf("pool-merge"), buildJsonObject {
        put("removeVersion", remove.version)
        put("remove", remove.id)
        put("mergeToVersion", into.version)
        put("mergeTo", into.id)
    })

    // ---------------- Pool categories ----------------

    suspend fun createPoolCategory(name: String, color: String): PoolCategory =
        send("POST", listOf("pool-categories"), buildJsonObject {
            put("name", name)
            put("color", color)
        })

    suspend fun updatePoolCategory(c: PoolCategory, name: String, color: String): PoolCategory =
        send("PUT", listOf("pool-category", c.name), buildJsonObject {
            put("version", c.version)
            if (name != c.name) put("name", name)
            put("color", color)
        })

    suspend fun deletePoolCategory(c: PoolCategory) {
        send<JsonObject>("DELETE", listOf("pool-category", c.name), buildJsonObject { put("version", c.version) })
    }

    suspend fun setDefaultPoolCategory(c: PoolCategory): PoolCategory =
        send("PUT", listOf("pool-category", c.name, "default"))
}
