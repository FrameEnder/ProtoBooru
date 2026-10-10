package com.frameender.protobooru.ui.posts

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frameender.protobooru.data.Blacklist
import com.frameender.protobooru.data.BulkOps
import com.frameender.protobooru.data.matchesTagPattern
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.SzuruException
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.PostSource
import com.frameender.protobooru.data.SearchHistory
import com.frameender.protobooru.data.Tag
import com.frameender.protobooru.ui.common.PagedLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SortOption(val label: String, val term: String?)

val SORTS = listOf(
    SortOption("Newest", null),
    SortOption("Oldest", "-sort:id"),
    SortOption("Score", "sort:score"),
    SortOption("Favorites", "sort:fav-count"),
    SortOption("Comments", "sort:comment-count"),
    SortOption("Tag count", "sort:tag-count"),
    SortOption("Recently edited", "sort:edit-time"),
    SortOption("Recently commented", "sort:comment-time"),
    SortOption("Recently favorited", "sort:fav-time"),
    SortOption("Largest file", "sort:file-size"),
    SortOption("Biggest area", "sort:area"),
    SortOption("Random", "sort:random"),
)

class PostsViewModel(handle: SavedStateHandle) : ViewModel() {
    private val api = Graph.api

    var text by mutableStateOf(handle.get<String>("q").orEmpty())
        private set
    var activeQuery by mutableStateOf(text)
        private set
    var sort by mutableStateOf(SORTS.first())
        private set
    var suggestions by mutableStateOf<List<Tag>>(emptyList())
        private set
    var selected by mutableStateOf<Set<Int>>(emptySet())
        private set

    val selecting: Boolean get() = selected.isNotEmpty()

    /**
     * Scroll state for the grid, kept here rather than in the screen. Opening a post disposes
     * the grid; a fresh state would only remember the scroll index, and the staggered grid would
     * have to re-guess which column every post sat in, shuffling tiles as you scroll. Keeping
     * the same state object keeps those column assignments, so the layout comes back exactly.
     */
    val staggeredState = LazyStaggeredGridState()
    val gridState = LazyGridState()

    private var suggestJob: Job? = null

    val effectiveQuery: String
        get() {
            val parts = mutableListOf<String>()
            if (activeQuery.isNotBlank()) parts += activeQuery.trim()
            val hasSort = activeQuery.contains("sort:")
            if (!hasSort && sort.term != null) parts += sort.term!!
            parts += Graph.settings.value.filterTerms(activeQuery)
            return parts.joinToString(" ")
        }

    /** True when the last load came from the offline library rather than the server. */
    var showingOffline by mutableStateOf(false)
        private set

    val loader = PagedLoader(viewModelScope) { offset, limit ->
        fun local() = Graph.library.search(effectiveQuery, activeQuery, Graph.offlineSaver.collections(), offset, limit)
            .also { showingOffline = true }
        if (Graph.offlineMode) {
            local()
        } else {
            try {
                api.posts(effectiveQuery, offset, limit).also { showingOffline = false }
            } catch (e: java.io.IOException) {
                // Server unreachable mid-browse: show what's saved instead of an error.
                if (e is SzuruException || !Graph.offlineMode) throw e
                local()
            }
        }
    }

    /**
     * The posts to draw: the loaded pages minus any blacklisted post that slipped through
     * (Hide mode). Searches already leave them out on the server; this catches the rest,
     * e.g. saved offline copies from before a tag was blacklisted.
     */
    val shown: List<Post>
        get() {
            val s = Graph.settings.value
            if (!Blacklist.hideMode(s)) return loader.items
            val searched = Blacklist.searchedWords(activeQuery)
            return loader.items.filterNot { Blacklist.hidden(it.id, searched, s) }
        }

    val source = object : PostSource {
        override val ids: List<Int> get() = shown.map { it.id }
        override val canLoadMore: Boolean get() = !loader.endReached
        override val query: String get() = effectiveQuery
        override fun loadMore() = loader.loadMore()
    }

    init {
        loader.refresh()
        // Refresh after bulk edits/deletes or uploads change what this search shows.
        viewModelScope.launch { Graph.bulk.finished.collect { loader.refresh() } }
    }

    fun onTextChange(v: String) {
        text = v
        suggestJob?.cancel()
        val last = v.substringAfterLast(' ').removePrefix("-")
        if (v.endsWith(" ") || last.length < 2 || last.contains(':')) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(220)
            val s = Graph.settings.value
            val found: List<Tag> = if (Graph.offlineMode) {
                // Offline: suggest tags found on saved posts, with their counts on the phone.
                Graph.library.searchTags("${last.lowercase()}*", null, "usages", 0, 12).results
            } else {
                runCatching { api.suggestTags(last) }.getOrDefault(emptyList())
            }
            // Hide mode: blacklisted tags aren't suggested (unless you're typing one to exclude it).
            suggestions = if (!Blacklist.hideMode(s) || v.substringAfterLast(' ').startsWith("-")) found
            else found.filter { t -> t.names.none { n -> s.blacklistTags.any { matchesTagPattern(it, n) } } }
        }
    }

    fun applySuggestion(t: Tag) {
        val head = text.substringBeforeLast(' ', "")
        val last = text.substringAfterLast(' ')
        val neg = if (last.startsWith("-")) "-" else ""
        text = (if (head.isBlank()) "" else "$head ") + neg + t.name + " "
        suggestions = emptyList()
    }

    fun search(q: String = text) {
        text = q
        activeQuery = q.trim()
        SearchHistory.record(activeQuery)
        suggestions = emptyList()
        selected = emptySet()
        loader.refresh()
    }

    /** Adds a term to the current query (used by quick filters and tag menus). */
    fun addTerm(term: String) {
        val parts = activeQuery.split(' ').filter { it.isNotBlank() }.toMutableList()
        if (term !in parts) parts += term
        search(parts.joinToString(" "))
    }

    fun setSortOption(s: SortOption) {
        sort = s
        loader.refresh()
    }

    /** Pull to refresh: while offline this first checks whether the server is back. */
    fun refresh() {
        if (Graph.offline.value) {
            viewModelScope.launch {
                Graph.checkConnection()
                loader.refresh()
            }
        } else {
            loader.refresh()
        }
    }

    fun toggleSelect(id: Int) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAllLoaded() {
        selected = loader.items.map { it.id }.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun downloadSelected() {
        Graph.downloads.enqueue(loader.items.map { it.id }.filter { it in selected })
        selected = emptySet()
    }

    fun bulkEdit(ops: BulkOps) {
        Graph.bulk.apply(loader.items.map { it.id }.filter { it in selected }, ops)
        selected = emptySet()
    }

    fun deleteSelected() {
        Graph.bulk.delete(loader.items.map { it.id }.filter { it in selected })
        selected = emptySet()
    }

    fun favoriteSelected(fav: Boolean) {
        val ids = selected.toList()
        selected = emptySet()
        viewModelScope.launch {
            var ok = 0
            for (id in ids) {
                runCatching { if (fav) api.favorite(id) else api.unfavorite(id) }.onSuccess { p ->
                    ok++
                    replace(p)
                }
            }
            Graph.toast(if (fav) "Favorited $ok of ${ids.size}" else "Unfavorited $ok of ${ids.size}")
        }
    }

    fun replace(p: Post) {
        loader.update { list -> list.map { if (it.id == p.id) p else it } }
    }
}
