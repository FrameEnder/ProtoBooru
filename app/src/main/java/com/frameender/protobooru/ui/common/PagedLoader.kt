package com.frameender.protobooru.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Paged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Offset-based infinite list backed by Compose state. One instance per list screen. */
class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val pageSize: () -> Int = { Graph.settings.value.pageSize },
    private val fetch: suspend (offset: Int, limit: Int) -> Paged<T>,
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var total by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    var loadedOnce by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var generation = 0

    fun refresh() {
        job?.cancel()
        val g = ++generation
        refreshing = items.isNotEmpty()
        loading = true
        error = null
        endReached = false
        job = scope.launch {
            try {
                val r = fetch(0, pageSize())
                if (g != generation) return@launch
                items = r.results
                total = r.total
                endReached = r.results.isEmpty() || r.results.size >= r.total
                loadedOnce = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (g == generation) error = e.message ?: e.javaClass.simpleName
            } finally {
                if (g == generation) {
                    loading = false
                    refreshing = false
                }
            }
        }
    }

    fun loadMore() {
        if (loading || endReached || error != null) return
        if (!loadedOnce) { refresh(); return }
        val g = generation
        loading = true
        job = scope.launch {
            try {
                val r = fetch(items.size, pageSize())
                if (g != generation) return@launch
                items = items + r.results
                total = r.total
                endReached = r.results.isEmpty() || items.size >= r.total
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (g == generation) error = e.message ?: e.javaClass.simpleName
            } finally {
                if (g == generation) loading = false
            }
        }
    }

    fun retry() {
        error = null
        if (!loadedOnce) refresh() else loadMore()
    }

    /** Replace items in place, e.g. after favoriting a post from the grid. */
    fun update(transform: (List<T>) -> List<T>) {
        items = transform(items)
    }
}
