package com.frameender.protobooru.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/** What the tag blacklist does to a post that carries a blacklisted tag. */
object BlacklistMode {
    /** Left out everywhere: grids, searches, Home, related posts, pools. */
    const val HIDE = "hide"
    /** Still listed, but every picture of it is blurred until you open it and choose to see it. */
    const val BLUR = "blur"
}

/**
 * Knows which posts carry blacklisted tags, so every place that shows a post can hide or blur it.
 *
 * Searches already leave blacklisted posts out on the server (in Hide mode), but plenty of
 * places show posts that aren't a search: a pool's posts, related posts, the featured post,
 * comment thumbnails. Those only come with an id and a thumbnail, so the tags are learned as
 * posts pass through the API (and from the offline library), and looked up in batches with
 * [check] where needed.
 *
 * Reading [version] (done by [hidden] and [blurred]) makes a composable redraw when more is learned.
 */
object Blacklist {
    /** Post id → its tag names (every alias, lowercase). */
    private val tagsById = ConcurrentHashMap<Int, List<String>>()
    /** Thumbnail / file path → post id, so an image can be blurred knowing only its path. */
    private val idByPath = ConcurrentHashMap<String, Int>()

    /** Bumped whenever more posts are learned. Snapshot state: composables reading it redraw. */
    var version by mutableIntStateOf(0)
        private set

    // ---------------------------------------------------------------- rules

    /** Compiled blacklist patterns, rebuilt only when the blacklist text changes. */
    private class Rules(val key: String, patterns: List<String>) {
        val exact = patterns.filter { '*' !in it }.toSet()
        val wild = patterns.filter { '*' in it }.map { p ->
            p to Regex("^" + p.split('*').joinToString(".*") { Regex.escape(it) } + "$")
        }

        /** The patterns these tag names hit. */
        fun hits(names: List<String>): List<String> {
            if (exact.isEmpty() && wild.isEmpty()) return emptyList()
            val out = LinkedHashSet<String>()
            for (n in names) {
                if (n in exact) out += n
                for ((p, rx) in wild) if (rx.matches(n)) out += p
            }
            return out.toList()
        }
    }

    @Volatile private var rules: Rules? = null

    private fun rules(s: AppSettings): Rules {
        val tags = s.blacklistTags
        val key = tags.joinToString(" ")
        rules?.let { if (it.key == key) return it }
        return Rules(key, tags).also { rules = it }
    }

    fun active(s: AppSettings = Graph.settings.value): Boolean = s.blacklistTags.isNotEmpty()
    fun hideMode(s: AppSettings = Graph.settings.value): Boolean = active(s) && s.blacklistMode != BlacklistMode.BLUR
    fun blurMode(s: AppSettings = Graph.settings.value): Boolean = active(s) && s.blacklistMode == BlacklistMode.BLUR

    // ---------------------------------------------------------------- learning

    private fun namesOf(p: Post): List<String> = p.tags.flatMap { it.names }.map { it.lowercase() }

    /** Remembers the tags of posts whose tags were fetched (tag-less posts must not be passed). */
    fun learn(posts: Collection<Post>) {
        if (posts.isEmpty()) return
        for (p in posts) {
            tagsById[p.id] = namesOf(p)
            p.thumbnailUrl?.let { idByPath[it] = p.id }
            p.contentUrl?.let { idByPath[it] = p.id }
        }
        version++
    }

    fun learn(p: Post) = learn(listOf(p))

    private fun names(id: Int): List<String>? =
        tagsById[id] ?: Graph.library.post(id)?.let { p ->
            namesOf(p).also { n ->
                tagsById[id] = n
                p.thumbnailUrl?.let { idByPath[it] = id }
                p.contentUrl?.let { idByPath[it] = id }
            }
        }

    fun known(id: Int): Boolean = names(id) != null

    /**
     * Looks up the tags of posts that aren't known yet (one request per 100), so lists that
     * only have ids and thumbnails can be filtered. Does nothing while offline: everything
     * shown offline comes from the library, which has the tags already.
     */
    suspend fun check(ids: Collection<Int>) {
        if (!active() || Graph.offlineMode) return
        val todo = ids.distinct().filter { !known(it) }
        for (chunk in todo.chunked(100)) {
            runCatching { Graph.api.posts("id:" + chunk.joinToString(","), 0, chunk.size, "id,thumbnailUrl,contentUrl,tags") }
        }
    }

    // ---------------------------------------------------------------- questions

    /** Blacklisted patterns this post hits (empty when unknown or fine). */
    fun hits(id: Int, s: AppSettings = Graph.settings.value): List<String> {
        if (!active(s)) return emptyList()
        return names(id)?.let { rules(s).hits(it) } ?: emptyList()
    }

    /** Same, for a post at hand (uses its own tags when it has them). */
    fun hits(p: Post, s: AppSettings = Graph.settings.value): List<String> {
        if (!active(s)) return emptyList()
        return if (p.tags.isNotEmpty()) rules(s).hits(namesOf(p)) else hits(p.id, s)
    }

    /**
     * Whether Hide mode leaves this post out. [searched] are the words of the search the user
     * typed: a blacklisted tag searched for on purpose doesn't hide its posts.
     */
    fun hidden(id: Int, searched: Collection<String> = emptyList(), s: AppSettings = Graph.settings.value): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = version
        if (!hideMode(s)) return false
        val h = hits(id, s)
        return h.isNotEmpty() && h.any { it !in searched }
    }

    /** Whether Blur mode blurs the picture at [path] (a thumbnail or a post's file). */
    fun blurred(path: String?, s: AppSettings = Graph.settings.value): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = version
        if (path.isNullOrBlank() || !blurMode(s)) return false
        val id = idByPath[path] ?: return false
        return hits(id, s).isNotEmpty()
    }

    /** The words of a typed search, as [hidden] wants them. */
    fun searchedWords(query: String): Set<String> =
        query.lowercase().split(' ').map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("-") }.toSet()
}
