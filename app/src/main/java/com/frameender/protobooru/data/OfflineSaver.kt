package com.frameender.protobooru.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/** A search or pool the user saved for offline viewing. Remembered so it can be refreshed. */
@Serializable
data class OfflineCollection(
    val label: String,
    val query: String,
    val max: Int,
    val fullImages: Boolean = true,
    /** Also download videos (they can be large). */
    val videos: Boolean = false,
    /** Pool id when this is a pool (its page is saved too), else 0. */
    val poolId: Int = 0,
    /** How many posts the last save stored. */
    val count: Int = 0,
    /** When it was last saved (epoch ms). */
    val savedAt: Long = 0,
    /** The saved posts, in the order the search returned them (for the offline grid). */
    val ids: List<Int> = emptyList(),
) {
    val key: String get() = if (poolId > 0) "pool:$poolId" else "q:$query"
}

/**
 * "Save for offline": walks a search (or a pool) the same way the app browses it, so every
 * page of results, every post's details, and their images land in the caches. Later, when the
 * server can't be reached, those screens open from the saved copies.
 *
 * Images go into the image cache (size set in Settings); API answers into the data cache.
 * Videos are not saved, only their thumbnails. Saved collections are listed in
 * Settings → Storage & offline, where they can be refreshed or forgotten.
 */
class OfflineSaver(private val context: Context) {
    data class Progress(val label: String, val done: Int, val total: Int)

    val progress = MutableStateFlow<Progress?>(null)
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    // ---------------- remembered collections ----------------

    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(OfflineCollection.serializer())

    fun collections(s: AppSettings = Graph.settings.value): List<OfflineCollection> =
        if (s.offlineCollections.isBlank()) emptyList()
        else runCatching { json.decodeFromString(listSer, s.offlineCollections) }.getOrDefault(emptyList())

    private fun remember(c: OfflineCollection) {
        Graph.updateSettings { s ->
            val list = listOf(c) + collections(s).filter { it.key != c.key }
            s.copy(offlineCollections = json.encodeToString(listSer, list))
        }
    }

    /** Takes a collection off the list and deletes its files (unless another collection uses them). */
    fun forget(c: OfflineCollection) {
        Graph.scope.launch(Dispatchers.IO) {
            Graph.library.prune(c.key, emptySet())
            if (c.poolId > 0) Graph.library.deletePool(c.poolId)
        }
        Graph.updateSettings { s ->
            s.copy(offlineCollections = json.encodeToString(listSer, collections(s).filter { it.key != c.key }))
        }
    }

    // ---------------- saving ----------------

    /** Starts saving in the background (from a screen). */
    fun save(label: String, query: String, max: Int, videos: Boolean, poolId: Int = 0) {
        start(OfflineCollection(label, query, max, fullImages = true, videos = videos, poolId = poolId))
    }

    /** Re-saves a remembered collection with its original options. */
    fun refresh(c: OfflineCollection) = start(c)

    private fun start(c: OfflineCollection) {
        if (running) {
            Graph.toast("Already saving “${progress.value?.label}”")
            return
        }
        job = Graph.scope.launch(Dispatchers.IO) {
            try {
                val n = run(c)
                Graph.toast("Saved $n posts from “${c.label}” for offline")
            } catch (e: CancellationException) {
                Graph.toast("Stopped saving “${c.label}”")
                throw e
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Saving for offline failed")
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Does the actual saving. Suspends until done; also used by the background refresh. */
    suspend fun run(c: OfflineCollection): Int = withContext(Dispatchers.IO) {
        try {
            progress.value = Progress(c.label, 0, 0)
            if (c.poolId > 0) Graph.library.storePool(Graph.api.pool(c.poolId))
            // Same page size and offsets as the grid, so the saved pages match what it asks for.
            val pageSize = Graph.settings.value.pageSize
            val posts = mutableListOf<Post>()
            var offset = 0
            while (posts.size < c.max) {
                currentCoroutineContext().ensureActive()
                val page = Graph.api.posts(c.query, offset, pageSize)
                posts += page.results
                offset += pageSize
                if (page.results.isEmpty() || offset >= page.total) break
            }
            val todo = posts.take(c.max)
            // Each post's details and files go into the offline library (app storage, never
            // cleared by Android). Details are also fetched through the API so they're cached.
            todo.forEachIndexed { i, p ->
                currentCoroutineContext().ensureActive()
                progress.value = Progress(c.label, i, todo.size)
                val full = runCatching { Graph.api.post(p.id) }.getOrDefault(p)
                val withMedia = when {
                    p.isFlash -> false
                    p.isVideo -> c.videos
                    else -> c.fullImages
                }
                Graph.library.store(full, c.key, withMedia)
                if ((i + 1) % 25 == 0) Graph.library.commit()
            }
            // Posts that left the search (e.g. unfavorited) are dropped from this collection.
            Graph.library.prune(c.key, todo.map { it.id }.toSet())
            remember(c.copy(count = todo.size, savedAt = System.currentTimeMillis(), ids = todo.map { it.id }))
            todo.size
        } finally {
            progress.value = null
        }
    }
}

/** Daily re-save of every remembered collection, on Wi-Fi while charging. */
class OfflineRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val s = Graph.settings.value
        if (!s.offlineAutoRefresh || !s.configured) return Result.success()
        for (c in Graph.offlineSaver.collections(s)) {
            runCatching { Graph.offlineSaver.run(c) }
        }
        return Result.success()
    }
}

object OfflineRefreshScheduler {
    private const val WORK = "protobooru-offline-refresh"

    /** Turns the daily refresh on or off to match settings. Safe to call repeatedly. */
    fun apply(context: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!s.offlineAutoRefresh) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<OfflineRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(true)
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
