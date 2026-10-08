package com.frameender.protobooru.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One post kept on the phone: its details plus the files that were downloaded for it. */
@Serializable
data class OfflineEntry(
    val post: Post,
    /** Local thumbnail file name inside the library folder, if downloaded. */
    val thumb: String? = null,
    /** Local full file (image, GIF or video) name, if downloaded. */
    val media: String? = null,
    /** Keys of the saved collections this post belongs to. */
    val collections: Set<String> = emptySet(),
)

/**
 * Everything saved for offline: post details and their files, stored in the app's private
 * files folder (not the cache, so Android never clears it on its own).
 *
 * While offline, the app works from this library only: grids list just the posts whose
 * picture or video is here, and pictures load from these files. Online, these files are
 * still used when present, which saves data.
 */
class OfflineLibrary(context: Context) {
    private val dir = File(context.filesDir, "offline").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val serializer = ListSerializer(OfflineEntry.serializer())

    private val entries = ConcurrentHashMap<Int, OfflineEntry>()
    /** Remote path (as the API gives it) → local file, for [localUri]. */
    private val local = ConcurrentHashMap<String, File>()

    /** Bumped whenever the library changes, so screens can refresh. */
    val version = MutableStateFlow(0)

    init {
        runCatching {
            if (indexFile.exists()) {
                Graph.api.json.decodeFromString(serializer, indexFile.readText()).forEach { put(it, save = false) }
            }
        }
    }

    // ---------------- reading ----------------

    val size: Int get() = entries.size

    /** Posts that can actually be opened offline (their picture or video is downloaded). */
    fun viewable(): List<Post> = entries.values.filter { it.media != null && File(dir, it.media).exists() }.map { it.post }

    val viewableCount: Int get() = entries.values.count { it.media != null }

    fun post(id: Int): Post? = entries[id]?.post

    fun has(id: Int): Boolean = entries[id]?.media != null

    /** A file:// address for a downloaded thumbnail or file, or null to use the server. */
    fun localUri(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val f = local[path] ?: return null
        return if (f.exists()) "file://" + f.absolutePath else null
    }

    fun bytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * The offline version of a post search. If the query is one that was saved, its posts come
     * back in their saved order; otherwise every viewable post is filtered by the plain tag
     * terms in [userQuery] ("tag", "-tag", "ta*"), plus the safety switches and blacklist.
     */
    fun search(fullQuery: String, userQuery: String, collections: List<OfflineCollection>, offset: Int, limit: Int): Paged<Post> {
        val s = Graph.settings.value
        val saved = collections.firstOrNull { it.query == fullQuery }
        val pool: List<Post> = if (saved != null && saved.ids.isNotEmpty()) {
            saved.ids.mapNotNull { id -> entries[id]?.takeIf { it.media != null }?.post }
        } else {
            val words = userQuery.lowercase().split(' ').map { it.trim() }.filter { it.isNotBlank() }
            val include = words.filter { !it.startsWith("-") && !it.contains(':') }
            val exclude = words.filter { it.startsWith("-") && !it.contains(':') }.map { it.removePrefix("-") }
            viewable()
                .filter { p ->
                    val names = p.tags.flatMap { it.names }
                    include.all { t -> names.any { matchesTagPattern(t, it) } } &&
                        exclude.none { t -> names.any { matchesTagPattern(t, it) } }
                }
                .filter { p ->
                    when (p.safety) {
                        "safe" -> s.showSafe
                        "sketchy" -> s.showSketchy
                        "unsafe" -> s.showUnsafe
                        else -> true
                    } && s.blacklistHits(p).isEmpty()
                }
                .sortedByDescending { it.id }
        }
        return Paged(query = fullQuery, offset = offset, limit = limit, total = pool.size, results = pool.drop(offset).take(limit))
    }

    // ---------------- writing ----------------

    private fun put(e: OfflineEntry, save: Boolean = true) {
        entries[e.post.id]?.let { old ->
            old.post.thumbnailUrl?.let { local.remove(it) }
            old.post.contentUrl?.let { local.remove(it) }
        }
        entries[e.post.id] = e
        e.thumb?.let { name -> e.post.thumbnailUrl?.let { local[it] = File(dir, name) } }
        e.media?.let { name -> e.post.contentUrl?.let { local[it] = File(dir, name) } }
        if (save) persist()
    }

    @Synchronized
    private fun persist() {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(Graph.api.json.encodeToString(serializer, entries.values.toList()))
        tmp.renameTo(indexFile)
        version.value++
    }

    /**
     * Stores [post] for [collectionKey]: downloads its thumbnail, and its full file when
     * [withMedia] is set. Files already downloaded (same name and size) are kept.
     */
    suspend fun store(post: Post, collectionKey: String, withMedia: Boolean) = withContext(Dispatchers.IO) {
        val old = entries[post.id]
        val thumb = download(post.thumbnailUrl, "t${post.id}") ?: old?.thumb
        val media = if (withMedia) download(post.contentUrl, "m${post.id}") ?: old?.media else old?.media
        put(
            OfflineEntry(
                post = post,
                thumb = thumb,
                media = media,
                collections = (old?.collections ?: emptySet()) + collectionKey,
            ),
            save = false,
        )
    }

    /** Writes the index after a batch of [store] calls. */
    fun commit() = persist()

    /** Removes [collectionKey] from every post not in [keep]; deletes posts nothing uses any more. */
    fun prune(collectionKey: String, keep: Set<Int>) {
        for (e in entries.values.toList()) {
            if (collectionKey in e.collections && e.post.id !in keep) {
                val left = e.collections - collectionKey
                if (left.isEmpty()) delete(e) else put(e.copy(collections = left), save = false)
            }
        }
        persist()
    }

    /** Deletes everything in the library. */
    fun clear() {
        entries.values.toList().forEach { delete(it) }
        persist()
    }

    private fun delete(e: OfflineEntry) {
        e.thumb?.let { File(dir, it).delete() }
        e.media?.let { File(dir, it).delete() }
        e.post.thumbnailUrl?.let { local.remove(it) }
        e.post.contentUrl?.let { local.remove(it) }
        entries.remove(e.post.id)
    }

    /** Downloads a server file into the library; returns its local name, or null on failure. */
    private fun download(path: String?, base: String): String? {
        if (path.isNullOrBlank()) return null
        val url = Graph.api.resolve(path) ?: return null
        val ext = path.substringAfterLast('.', "").substringBefore('?').take(5).ifBlank { "bin" }
        val name = "$base.$ext"
        val target = File(dir, name)
        // Already downloaded: post files never change on Szurubooru without their name changing.
        if (target.exists() && target.length() > 0) return name
        return runCatching {
            Graph.imageHttp.newCall(Request.Builder().url(url).header("User-Agent", "ProtoBooru-Android").build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val body = r.body ?: error("empty")
                val part = File(dir, "$name.part")
                part.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (!part.renameTo(target)) error("rename failed")
            }
            name
        }.getOrNull()
    }
}
