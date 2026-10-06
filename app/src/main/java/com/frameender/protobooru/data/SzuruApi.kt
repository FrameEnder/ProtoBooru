package com.frameender.protobooru.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

    private suspend fun exec(req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
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

    private fun pageParams(offset: Int, limit: Int, query: String?, fields: String? = null) = mapOf(
        "offset" to offset.toString(),
        "limit" to limit.toString(),
        "query" to query?.trim()?.ifBlank { null },
        "fields" to fields,
    )

    // ---------------- Info / connection ----------------

    suspend fun info(): Info = get(listOf("info"))

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

    suspend fun posts(query: String?, offset: Int, limit: Int, fields: String? = GRID_FIELDS): Paged<Post> =
        get(listOf("posts"), pageParams(offset, limit, query, fields))

    suspend fun post(id: Int): Post = get(listOf("post", id.toString()))

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
    ): User = send("PUT", listOf("user", u.name), buildJsonObject {
        put("version", u.version)
        if (email != null) put("email", email)
        if (password != null) put("password", password)
        if (avatarStyle != null) put("avatarStyle", avatarStyle)
    })

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
}
