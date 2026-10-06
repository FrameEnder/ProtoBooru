package com.frameender.protobooru.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class UploadState { QUEUED, UPLOADING, CHECKING, CREATING, DONE, DUPLICATE, FAILED }

/** One file (or URL) headed for the booru, with the metadata it will be posted with. */
data class UploadJob(
    val id: Long,
    val uri: Uri?,
    val url: String?,
    val name: String,
    val size: Long,
    val mime: String,
    val tags: List<String>,
    val safety: String,
    val source: String,
    val anonymous: Boolean,
    val skipDuplicates: Boolean,
    val relateBatch: Boolean,
    val batchId: Long,
    val state: UploadState = UploadState.QUEUED,
    val progress: Float = 0f,
    val postId: Int? = null,
    val message: String? = null,
) {
    val finished: Boolean get() = state == UploadState.DONE || state == UploadState.DUPLICATE || state == UploadState.FAILED
}

/**
 * Sequential upload queue (one at a time keeps the server and its database happy).
 * Lives in the app scope, so uploads keep going if you leave the Upload screen.
 */
class Uploader(private val context: Context, private val api: SzuruApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val ids = AtomicLong(1)

    /** Post ids created so far per batch, used to relate posts uploaded together. */
    private val batchPosts = mutableMapOf<Long, MutableList<Int>>()

    val jobs = MutableStateFlow<List<UploadJob>>(emptyList())

    fun nextId(): Long = ids.getAndIncrement()

    init {
        scope.launch {
            for (id in queue) {
                val job = jobs.value.firstOrNull { it.id == id } ?: continue
                process(job)
                if (jobs.value.none { !it.finished }) {
                    val done = jobs.value.count { it.state == UploadState.DONE }
                    val dup = jobs.value.count { it.state == UploadState.DUPLICATE }
                    val failed = jobs.value.count { it.state == UploadState.FAILED }
                    Graph.toast(buildString {
                        append("Uploads finished: $done posted")
                        if (dup > 0) append(", $dup duplicates skipped")
                        if (failed > 0) append(", $failed failed")
                    })
                }
            }
        }
    }

    fun enqueue(newJobs: List<UploadJob>) {
        if (newJobs.isEmpty()) return
        jobs.update { it + newJobs }
        newJobs.forEach { queue.trySend(it.id) }
    }

    fun retry(id: Long) {
        set(id) { it.copy(state = UploadState.QUEUED, progress = 0f, message = null, skipDuplicates = false) }
        queue.trySend(id)
    }

    fun clearFinished() {
        jobs.update { list -> list.filterNot { it.finished } }
    }

    private fun set(id: Long, transform: (UploadJob) -> UploadJob) {
        jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private suspend fun process(job: UploadJob) {
        try {
            val relations = if (job.relateBatch) batchPosts[job.batchId].orEmpty().toList() else emptyList()
            val post = if (job.uri != null) {
                set(job.id) { it.copy(state = UploadState.UPLOADING, progress = 0f) }
                var lastPct = -1
                val body = ProgressRequestBody(UriRequestBody(context, job.uri, job.mime, job.size)) { w, t ->
                    if (t > 0) {
                        val pct = (w * 100 / t).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            set(job.id) { it.copy(progress = pct / 100f) }
                        }
                    }
                }
                val token = api.uploadTemp(body, job.name)

                if (job.skipDuplicates && job.mime.startsWith("image/")) {
                    set(job.id) { it.copy(state = UploadState.CHECKING, progress = 1f) }
                    val exact = runCatching { api.reverseSearchToken(token).exactPost }.getOrNull()
                    if (exact != null) {
                        set(job.id) {
                            it.copy(state = UploadState.DUPLICATE, postId = exact.id, message = "Already on the booru as #${exact.id}")
                        }
                        return
                    }
                }
                set(job.id) { it.copy(state = UploadState.CREATING, progress = 1f) }
                api.createPost(job.tags, job.safety, job.source, relations, emptyList(), job.anonymous, contentToken = token)
            } else {
                set(job.id) { it.copy(state = UploadState.CREATING, progress = 0f, message = "Server is fetching the URL…") }
                api.createPost(job.tags, job.safety, job.source.ifBlank { job.url }, relations, emptyList(), job.anonymous, contentUrl = job.url)
            }
            batchPosts.getOrPut(job.batchId) { mutableListOf() } += post.id
            set(job.id) { it.copy(state = UploadState.DONE, progress = 1f, postId = post.id, message = null) }
        } catch (e: Exception) {
            set(job.id) { it.copy(state = UploadState.FAILED, message = e.message ?: e.javaClass.simpleName) }
        }
    }
}
