package com.frameender.protobooru.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Blacklisted patterns this post hits (empty when it's fine to show). Checks every alias. */
fun AppSettings.blacklistHits(p: Post): List<String> = Blacklist.hits(p, this)

/** Recent post searches, newest first. Stored in settings so they survive restarts. */
object SearchHistory {
    private const val MAX = 30

    fun list(s: AppSettings = Graph.settings.value): List<String> =
        s.searchHistory.split('\n').map { it.trim() }.filter { it.isNotBlank() }

    /** Remembers a search the user ran (moves it to the top if it's already there). */
    fun record(query: String) {
        val q = query.replace('\n', ' ').trim()
        if (q.isBlank() || !Graph.settings.value.searchHistoryEnabled) return
        Graph.updateSettings { s ->
            s.copy(searchHistory = (listOf(q) + list(s).filter { it != q }).take(MAX).joinToString("\n"))
        }
    }

    fun remove(query: String) {
        Graph.updateSettings { s -> s.copy(searchHistory = list(s).filter { it != query }.joinToString("\n")) }
    }

    fun clear() {
        Graph.updateSettings { it.copy(searchHistory = "") }
    }
}

/**
 * "Copy tags" / "Paste tags" between posts (the app version of the Tag Copy userscript).
 * The copied set is saved in settings, so it survives restarts, and also goes on the
 * system clipboard as plain text.
 */
object TagClipboard {
    fun tags(s: AppSettings = Graph.settings.value): List<String> =
        s.copiedTags.split(' ').map { it.trim() }.filter { it.isNotBlank() }

    /** The post the copied tags came from, or null. */
    fun from(s: AppSettings = Graph.settings.value): Int? = s.copiedTagsFrom.takeIf { it > 0 }

    fun copy(context: Context, tags: List<String>, fromPost: Int?) {
        val clean = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) {
            Graph.toast("No tags to copy")
            return
        }
        Graph.updateSettings { it.copy(copiedTags = clean.joinToString(" "), copiedTagsFrom = fromPost ?: 0) }
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("tags", clean.joinToString(" ")))
        }
        Graph.toast("Copied ${clean.size} tags" + (fromPost?.let { " from #$it" } ?: ""))
    }

    /**
     * Combines [incoming] with [current]: added after the existing tags (skipping ones already
     * there, ignoring case), or replacing them entirely when [replace] is set.
     */
    fun merge(current: List<String>, incoming: List<String>, replace: Boolean): List<String> {
        if (replace) return incoming.distinctBy { it.lowercase() }
        val have = current.map { it.lowercase() }.toMutableSet()
        return current + incoming.filter { have.add(it.lowercase()) }
    }
}
