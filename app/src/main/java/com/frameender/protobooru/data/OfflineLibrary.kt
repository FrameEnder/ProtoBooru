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
        // Saved posts carry their tags, so the blacklist knows them without asking the server.
        Blacklist.learn(entries.values.map { it.post })
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

    /** Space used by the posts in one saved collection (posts shared with others count fully). */
    fun bytesFor(collectionKey: String): Long =
        entries.values.filter { collectionKey in it.collections }.sumOf { e ->
            listOfNotNull(e.thumb, e.media).sumOf { File(dir, it).length() }
        }

    // ---------------- saved pool pages ----------------

    private val poolsDir get() = File(dir, "pools").apply { mkdirs() }

    /** Keeps a pool's page permanently, so it opens offline even after the cache is cleared. */
    fun storePool(pool: Pool) {
        runCatching { File(poolsDir, "${pool.id}.json").writeText(Graph.api.json.encodeToString(Pool.serializer(), pool)) }
    }

    fun pool(id: Int): Pool? = runCatching {
        File(poolsDir, "$id.json").takeIf { it.exists() }?.let { Graph.api.json.decodeFromString(Pool.serializer(), it.readText()) }
    }.getOrNull()

    fun deletePool(id: Int) {
        File(poolsDir, "$id.json").delete()
    }

    /** Whether the safety switches and (in Hide mode) the blacklist let this post show. */
    private fun allowed(p: Post, s: AppSettings, searched: Collection<String> = emptyList()): Boolean {
        val safe = when (p.safety) {
            "safe" -> s.showSafe
            "sketchy" -> s.showSketchy
            "unsafe" -> s.showUnsafe
            else -> true
        }
        if (!safe) return false
        if (!Blacklist.hideMode(s)) return true
        val hits = Blacklist.hits(p, s)
        return hits.isEmpty() || hits.all { it in searched }
    }

    /**
     * Everything that "exists" while offline: posts that can be opened, minus the ones the
     * safety switches or the blacklist leave out. Every offline count is taken from this.
     */
    fun visible(s: AppSettings = Graph.settings.value): List<Post> =
        entries.values.filter { it.media != null }.map { it.post }.filter { allowed(it, s) }

    /**
     * The offline version of a post search. If the query is one that was saved, its posts come
     * back in their saved order; otherwise every viewable post is filtered by the plain tag
     * terms in [userQuery] ("tag", "-tag", "ta*") plus "fav:me" / "uploader:name" / "pool:id",
     * and the safety switches and blacklist.
     */
    fun search(fullQuery: String, userQuery: String, collections: List<OfflineCollection>, offset: Int, limit: Int): Paged<Post> {
        val s = Graph.settings.value
        val searched = Blacklist.searchedWords(userQuery)
        val saved = collections.firstOrNull { it.query == fullQuery }
        val pool: List<Post> = if (saved != null && saved.ids.isNotEmpty()) {
            saved.ids.mapNotNull { id -> entries[id]?.takeIf { it.media != null }?.post }.filter { allowed(it, s, searched) }
        } else {
            val words = userQuery.lowercase().split(' ').map { it.trim() }.filter { it.isNotBlank() }
            val include = words.filter { !it.startsWith("-") && !it.contains(':') }
            val exclude = words.filter { it.startsWith("-") && !it.contains(':') }.map { it.removePrefix("-") }
            val named = words.filter { it.contains(':') && !it.startsWith("-") && !it.startsWith("sort:") }
            viewable()
                .filter { p ->
                    val names = p.tags.flatMap { it.names }
                    include.all { t -> names.any { matchesTagPattern(t, it) } } &&
                        exclude.none { t -> names.any { matchesTagPattern(t, it) } } &&
                        named.all { matchesNamed(it, p, s) }
                }
                .filter { allowed(it, s, searched) }
                .let { list -> if (words.contains("sort:random")) list.shuffled() else list.sortedByDescending { it.id } }
        }
        return Paged(query = fullQuery, offset = offset, limit = limit, total = pool.size, results = pool.drop(offset).take(limit))
    }

    /** The few named search terms that can be answered from saved posts; others match everything. */
    private fun matchesNamed(term: String, p: Post, s: AppSettings): Boolean {
        val key = term.substringBefore(':')
        val value = term.substringAfter(':').replace("\\", "")
        return when (key) {
            "fav" -> if (value.equals(s.username, ignoreCase = true)) p.ownFavorite || p.favoritedBy.any { it.name.equals(value, true) }
                else p.favoritedBy.any { it.name.equals(value, true) }
            "uploader", "submit", "upload" -> p.user?.name.equals(value, ignoreCase = true)
            "pool" -> value.toIntOrNull()?.let { id -> p.pools.any { it.id == id } || pool(id)?.posts?.any { it.id == p.id } == true } ?: true
            "safety", "rating" -> value.split(',').any { it == p.safety }
            "type" -> value.split(',').any { it == p.type }
            "id" -> value.split(',').mapNotNull { it.toIntOrNull() }.let { ids -> ids.isEmpty() || p.id in ids }
            else -> true
        }
    }

    // ---------------- offline counts: what's on the phone, not what the server has ----------------

    /** Tag name (lowercase, any alias) → how many visible posts carry it. */
    fun tagUsages(posts: List<Post> = visible()): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (p in posts) for (t in p.tags) for (n in t.names) out[n.lowercase()] = (out[n.lowercase()] ?: 0) + 1
        return out
    }

    /** Every tag on a visible post, with its usages counted on the phone. */
    fun tags(posts: List<Post> = visible()): List<Tag> {
        val byName = LinkedHashMap<String, Pair<MicroTag, Int>>()
        for (p in posts) for (t in p.tags) {
            val k = t.name.lowercase()
            byName[k] = t to ((byName[k]?.second ?: 0) + 1)
        }
        return byName.values.map { (t, n) -> Tag(names = t.names, category = t.category, usages = n) }
    }

    /**
     * The offline Tags list: [nameQuery] is what the Tags screen would send ("*cat*", "ca*",
     * plain names; other named terms are ignored), filtered by [category] and sorted by [sort].
     */
    fun searchTags(nameQuery: String, category: String?, sort: String, offset: Int, limit: Int): Paged<Tag> {
        val patterns = nameQuery.lowercase().split(' ').filter { it.isNotBlank() && !it.contains(':') }
        val list = tags()
            .filter { t -> category == null || t.category == category }
            .filter { t -> patterns.all { pt -> t.names.any { matchesTagPattern(pt, it) } } }
            .let { l ->
                when (sort) {
                    "name" -> l.sortedBy { it.name.lowercase() }
                    "random" -> l.shuffled()
                    else -> l.sortedWith(compareByDescending<Tag> { it.usages }.thenBy { it.name.lowercase() })
                }
            }
        return Paged(offset = offset, limit = limit, total = list.size, results = list.drop(offset).take(limit))
    }

    /** Offline details for one tag (or null when no visible post carries it). */
    fun tag(name: String): Tag? {
        val n = name.lowercase()
        return tags().firstOrNull { t -> t.names.any { it.lowercase() == n } }
    }

    /** Tags that appear together with [name] on the phone, most often first. */
    fun tagSiblings(name: String): List<TagSibling> {
        val n = name.lowercase()
        val counts = LinkedHashMap<String, Pair<MicroTag, Int>>()
        for (p in visible()) {
            if (p.tags.none { t -> t.names.any { it.lowercase() == n } }) continue
            for (t in p.tags) {
                if (t.names.any { it.lowercase() == n }) continue
                val k = t.name.lowercase()
                counts[k] = t to ((counts[k]?.second ?: 0) + 1)
            }
        }
        val usages = tagUsages()
        return counts.values.sortedByDescending { it.second }
            .map { (t, c) -> TagSibling(t.copy(usages = usages[t.name.lowercase()] ?: 0), c) }
    }

    /** A saved post with its counts rewritten to what's on the phone (tag usages, relations, pools). */
    fun localized(p: Post, posts: List<Post> = visible()): Post {
        val usages = tagUsages(posts)
        val ids = posts.mapTo(HashSet()) { it.id }
        val relations = p.relations.filter { it.id in ids }
        val poolCounts = pools(posts).associate { it.id to it.postCount }
        return p.copy(
            tags = p.tags.map { it.copy(usages = usages[it.name.lowercase()] ?: 0) },
            relations = relations,
            relationCount = relations.size,
            pools = p.pools.map { mp -> mp.copy(postCount = poolCounts[mp.id] ?: 0) },
        )
    }

    /**
     * Pools as they exist on the phone: pools saved with "Save offline" plus pools the saved
     * posts belong to. Each lists only its saved posts, and pools with none are left out.
     */
    fun pools(posts: List<Post> = visible()): List<Pool> {
        val ids = posts.mapTo(HashSet()) { it.id }
        val out = LinkedHashMap<Int, Pool>()
        poolsDir.listFiles()?.forEach { f ->
            val p = runCatching { Graph.api.json.decodeFromString(Pool.serializer(), f.readText()) }.getOrNull() ?: return@forEach
            out[p.id] = p
        }
        // Pools known only from the posts in them.
        for (post in posts) for (mp in post.pools) {
            if (mp.id !in out) out[mp.id] = Pool(id = mp.id, names = mp.names, category = mp.category, description = mp.description)
        }
        return out.values.map { p ->
            val members = p.posts.filter { it.id in ids }.toMutableList()
            val seen = members.mapTo(HashSet()) { it.id }
            // Posts that say they're in this pool but aren't in its saved page (or there's no saved page).
            posts.filter { q -> q.id !in seen && q.pools.any { it.id == p.id } }
                .sortedBy { it.id }
                .forEach { members += MicroPost(it.id, it.thumbnailUrl) }
            p.copy(posts = members, postCount = members.size)
        }.filter { it.postCount > 0 }
    }

    /** One pool as it exists on the phone, or null. */
    fun localPool(id: Int): Pool? = pools().firstOrNull { it.id == id }

    /** The offline Pools list, filtered like the Pools screen's search. */
    fun searchPools(nameQuery: String, category: String?, offset: Int, limit: Int): Paged<Pool> {
        val patterns = nameQuery.lowercase().split(' ').filter { it.isNotBlank() && !it.contains(':') }
        val list = pools()
            .filter { p -> category == null || p.category == category }
            .filter { p -> patterns.all { pt -> p.names.any { matchesTagPattern(pt, it) } } }
            .sortedByDescending { it.lastEditTime ?: "" }
        return Paged(offset = offset, limit = limit, total = list.size, results = list.drop(offset).take(limit))
    }

    /** Comments on the saved posts, newest first. Understands "user:name" and "post:id"; other words search the text. */
    fun comments(query: String, offset: Int, limit: Int): Paged<Comment> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() && !it.startsWith("sort:") }
        val list = visible().flatMap { p -> p.comments.map { it.copy(postId = if (it.postId == 0) p.id else it.postId) } }
            .filter { c ->
                words.all { w ->
                    when {
                        w.startsWith("user:") -> c.user?.name.equals(w.removePrefix("user:"), ignoreCase = true)
                        w.startsWith("post:") -> c.postId == w.removePrefix("post:").toIntOrNull()
                        w.contains(':') -> true
                        else -> c.text.lowercase().contains(w.trim('*'))
                    }
                }
            }
            .sortedByDescending { it.creationTime ?: "" }
        return Paged(offset = offset, limit = limit, total = list.size, results = list.drop(offset).take(limit))
    }

    /** People who uploaded or commented on the saved posts, with their counts on the phone. */
    fun users(nameQuery: String, offset: Int, limit: Int): Paged<User> {
        val posts = visible()
        val avatars = LinkedHashMap<String, String?>()
        posts.forEach { p ->
            p.user?.let { avatars.putIfAbsent(it.name, it.avatarUrl) }
            p.comments.forEach { c -> c.user?.let { avatars.putIfAbsent(it.name, it.avatarUrl) } }
        }
        val patterns = nameQuery.lowercase().split(' ').filter { it.isNotBlank() && !it.contains(':') }
        val list = avatars.keys
            .filter { n -> patterns.all { matchesTagPattern(it, n) } }
            .sortedBy { it.lowercase() }
            .map { n -> userStats(n, posts).copy(avatarUrl = avatars[n]) }
        return Paged(offset = offset, limit = limit, total = list.size, results = list.drop(offset).take(limit))
    }

    /** A user's uploads, favorites and comments counted on the saved posts (only the counts are filled in). */
    fun userStats(name: String, posts: List<Post> = visible()): User {
        val me = Graph.isMe(name)
        return User(
            name = name,
            rank = "",
            uploadedPostCount = posts.count { it.user?.name.equals(name, ignoreCase = true) },
            favoritePostCount = posts.count { p -> (me && p.ownFavorite) || p.favoritedBy.any { it.name.equals(name, ignoreCase = true) } },
            commentCount = posts.sumOf { p -> p.comments.count { it.user?.name.equals(name, ignoreCase = true) } },
            likedPostCount = if (me) kotlinx.serialization.json.JsonPrimitive(posts.count { it.ownScore > 0 }) else null,
            dislikedPostCount = if (me) kotlinx.serialization.json.JsonPrimitive(posts.count { it.ownScore < 0 }) else null,
        )
    }

    /** Home's numbers while offline. */
    data class Stats(val posts: Int, val tags: Int, val pools: Int, val comments: Int, val users: Int, val favorites: Int, val bytes: Long)

    fun stats(): Stats {
        val s = Graph.settings.value
        val posts = visible(s)
        val users = HashSet<String>()
        posts.forEach { p ->
            p.user?.let { users += it.name.lowercase() }
            p.comments.forEach { c -> c.user?.let { users += it.name.lowercase() } }
        }
        return Stats(
            posts = posts.size,
            tags = posts.flatMap { p -> p.tags.map { it.name.lowercase() } }.toSet().size,
            pools = pools(posts).size,
            comments = posts.sumOf { it.comments.size },
            users = users.size,
            favorites = if (s.loggedIn) posts.count { p -> p.ownFavorite || p.favoritedBy.any { it.name.equals(s.username, true) } } else 0,
            bytes = bytes(),
        )
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
        if (post.tags.isNotEmpty() || post.tagCount == 0) Blacklist.learn(post)
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
        poolsDir.listFiles()?.forEach { it.delete() }
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
