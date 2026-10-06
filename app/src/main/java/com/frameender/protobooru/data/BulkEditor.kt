package com.frameender.protobooru.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BulkOps(
    val addTags: List<String> = emptyList(),
    val removeTags: List<String> = emptyList(),
    val safety: String? = null,
    val source: String? = null,
    val linkRelated: Boolean = false,
    val addToPoolId: Int? = null,
)

data class TaskProgress(val label: String, val done: Int, val total: Int)

/**
 * Applies edits to many posts. Writes run strictly one after another: parallel writes
 * to the same tags/relations can deadlock Szurubooru's PostgreSQL backend.
 */
class BulkEditor(private val api: SzuruApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    val progress = MutableStateFlow<TaskProgress?>(null)

    /** Emits once a batch finishes so open lists can refresh. */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    fun apply(ids: List<Int>, ops: BulkOps) {
        if (ids.isEmpty()) return
        scope.launch {
            lock.withLock {
                var ok = 0
                var failed = 0
                val add = ops.addTags.map { it.lowercase() }
                val remove = ops.removeTags.map { it.lowercase() }.toSet()
                ids.forEachIndexed { i, id ->
                    progress.value = TaskProgress("Editing posts", i, ids.size)
                    try {
                        val p = api.post(id)
                        val current = p.tags.map { it.name }
                        val tagsChanged = add.isNotEmpty() || remove.isNotEmpty()
                        val newTags = (current.filterNot { it.lowercase() in remove } + add).distinctBy { it.lowercase() }
                        val newRelations = if (ops.linkRelated) {
                            (p.relations.map { it.id } + ids.filter { it != id }).distinct()
                        } else null
                        if (tagsChanged || ops.safety != null || ops.source != null || newRelations != null) {
                            api.updatePost(
                                p,
                                tags = if (tagsChanged) newTags else null,
                                safety = ops.safety,
                                source = ops.source,
                                relations = newRelations,
                            )
                        }
                        ok++
                    } catch (e: Exception) {
                        failed++
                    }
                }
                if (ops.addToPoolId != null) {
                    progress.value = TaskProgress("Adding to pool", ids.size, ids.size)
                    runCatching {
                        val pool = api.pool(ops.addToPoolId)
                        api.updatePool(pool, posts = (pool.posts.map { it.id } + ids).distinct())
                    }.onFailure { Graph.toast("Pool update failed: ${it.message}") }
                }
                progress.value = null
                Graph.toast(if (failed == 0) "Updated $ok posts" else "Updated $ok posts, $failed failed")
                finished.tryEmit(Unit)
            }
        }
    }

    fun delete(ids: List<Int>) {
        if (ids.isEmpty()) return
        scope.launch {
            lock.withLock {
                var ok = 0
                ids.forEachIndexed { i, id ->
                    progress.value = TaskProgress("Deleting posts", i, ids.size)
                    runCatching { api.deletePost(api.post(id)) }.onSuccess { ok++ }
                }
                progress.value = null
                Graph.toast("Deleted $ok of ${ids.size} posts")
                finished.tryEmit(Unit)
            }
        }
    }
}
