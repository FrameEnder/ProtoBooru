package com.frameender.protobooru.data

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * "Save for offline": walks a search (or a pool) the same way the app browses it, so every
 * page of results, every post's details, and their images land in the caches. Later, when the
 * server can't be reached, those screens open from the saved copies.
 *
 * Images go into the image cache (size set in Settings); API answers into the data cache.
 * Videos are not saved, only their thumbnails.
 */
class OfflineSaver(private val context: Context) {
    data class Progress(val label: String, val done: Int, val total: Int)

    val progress = MutableStateFlow<Progress?>(null)
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /**
     * Saves up to [max] posts of [query] (the exact query the screen uses, filters included).
     * [extra] runs first, e.g. loading the pool page itself so it's cached too.
     */
    fun save(label: String, query: String, max: Int, fullImages: Boolean, extra: (suspend () -> Unit)? = null) {
        if (running) {
            Graph.toast("Already saving “${progress.value?.label}”")
            return
        }
        job = Graph.scope.launch(Dispatchers.IO) {
            try {
                progress.value = Progress(label, 0, 0)
                extra?.invoke()
                // Same page size and offsets as the grid, so the saved pages match what it asks for.
                val pageSize = Graph.settings.value.pageSize
                val posts = mutableListOf<Post>()
                var offset = 0
                while (posts.size < max) {
                    ensureActive()
                    val page = Graph.api.posts(query, offset, pageSize)
                    posts += page.results
                    offset += pageSize
                    if (page.results.isEmpty() || offset >= page.total) break
                }
                val todo = posts.take(max)
                val loader = SingletonImageLoader.get(context)
                todo.forEachIndexed { i, p ->
                    ensureActive()
                    progress.value = Progress(label, i, todo.size)
                    runCatching { Graph.api.post(p.id) }
                    val urls = listOfNotNull(
                        p.thumbnailUrl,
                        p.contentUrl.takeIf { fullImages && !p.isVideo && !p.isFlash },
                    )
                    for (path in urls) {
                        val url = Graph.api.resolve(path) ?: continue
                        loader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .build(),
                        )
                    }
                }
                Graph.toast("Saved ${todo.size} posts from “$label” for offline")
            } catch (e: CancellationException) {
                Graph.toast("Stopped saving “$label”")
                throw e
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Saving for offline failed")
            } finally {
                progress.value = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
