package com.frameender.protobooru.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class DownloadProgress(val done: Int, val total: Int, val failed: Int, val skipped: Int, val current: Int?)

/**
 * Sequential download queue that saves post files into the shared media collections:
 * Pictures/ProtoBooru, Movies/ProtoBooru, or Download/ProtoBooru.
 */
class Downloader(
    private val context: Context,
    private val http: OkHttpClient,
    private val api: SzuruApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Int>(Channel.UNLIMITED)

    val progress = MutableStateFlow<DownloadProgress?>(null)

    private var total = 0
    private var done = 0
    private var failed = 0
    private var skipped = 0

    init {
        scope.launch {
            for (id in queue) {
                progress.value = DownloadProgress(done, total, failed, skipped, id)
                try {
                    if (save(id)) done++ else skipped++
                } catch (e: Exception) {
                    failed++
                }
                progress.value = DownloadProgress(done, total, failed, skipped, null)
                if (done + failed + skipped >= total) {
                    val msg = buildString {
                        append("Saved $done")
                        if (skipped > 0) append(", $skipped already saved")
                        if (failed > 0) append(", $failed failed")
                    }
                    Graph.toast(msg)
                    total = 0; done = 0; failed = 0; skipped = 0
                    progress.value = null
                }
            }
        }
    }

    fun enqueue(ids: List<Int>) {
        if (ids.isEmpty()) return
        total += ids.size
        ids.forEach { queue.trySend(it) }
        if (ids.size > 1) Graph.toast("Queued ${ids.size} downloads")
    }

    fun filenameFor(p: Post, pattern: String): String {
        val tags = p.tags.take(5).joinToString("_") { it.name }
        val base = pattern
            .replace("{id}", p.id.toString())
            .replace("{md5}", p.checksumMD5 ?: p.checksum ?: "")
            .replace("{sha1}", p.checksum ?: "")
            .replace("{tags}", tags)
            .replace("{safety}", p.safety)
            .replace("{type}", p.type)
            .replace(Regex("[^A-Za-z0-9._ ()\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', ' ', '.')
            .take(120)
            .ifBlank { "post_${p.id}" }
        return "$base.${p.extension}"
    }

    private fun mimeFor(p: Post): String = p.mimeType ?: when (p.extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }

    /** @return false when the file already existed and was skipped. */
    private suspend fun save(id: Int): Boolean {
        // Offline: details and the file come from the offline library when they're saved there.
        val p = if (Graph.offlineMode) Graph.library.post(id) ?: api.post(id) else api.post(id)
        val localFile = Graph.library.localUri(p.contentUrl)?.removePrefix("file://")?.let { java.io.File(it) }
        val url = api.resolve(p.contentUrl) ?: error("Post has no content URL")
        val mime = mimeFor(p)
        val name = filenameFor(p, Graph.settings.value.filenamePattern)

        val (collection, dir) = when {
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_MOVIES
            mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_PICTURES
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_DOWNLOADS
        }
        val relPath = "$dir/ProtoBooru/"
        val resolver = context.contentResolver

        // Skip files we already saved (same name in the same folder).
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, relPath),
            null,
        )?.use { if (it.moveToFirst()) return false }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val item = resolver.insert(collection, values) ?: error("Could not create file")
        try {
            if (localFile != null && localFile.exists()) {
                // Already saved for offline: copy it instead of downloading again.
                resolver.openOutputStream(item)?.use { out -> localFile.inputStream().use { it.copyTo(out) } }
                    ?: error("Could not open output")
            } else {
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("Empty response")
                    resolver.openOutputStream(item)?.use { out -> body.byteStream().copyTo(out) }
                        ?: error("Could not open output")
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(item, values, null, null)
        } catch (e: Exception) {
            resolver.delete(item, null, null)
            throw e
        }
        return true
    }

    /** Downloads a post into the cache and opens the system share sheet with the file. */
    suspend fun shareFile(context: Context, p: Post) {
        val full = if (p.contentUrl == null) api.post(p.id) else p
        val url = api.resolve(full.contentUrl) ?: return
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, filenameFor(full, Graph.settings.value.filenamePattern))
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                file.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
            }
        }
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(full)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share post #${full.id}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
