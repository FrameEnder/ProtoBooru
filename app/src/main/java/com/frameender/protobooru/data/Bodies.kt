package com.frameender.protobooru.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import okio.source

/** Name/size/type of a picked file, read from the content resolver. */
data class UriInfo(val name: String, val size: Long, val mime: String)

fun Context.uriInfo(uri: Uri): UriInfo {
    var name = uri.lastPathSegment ?: "file"
    var size = -1L
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
    }
    val mime = contentResolver.getType(uri) ?: guessMime(name)
    return UriInfo(name, size, mime)
}

fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "avif" -> "image/avif"
    "heic", "heif" -> "image/heif"
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "swf" -> "application/x-shockwave-flash"
    else -> "application/octet-stream"
}

/** Streams a content:// Uri straight into the request instead of loading it into memory. */
class UriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mime: String,
    private val length: Long,
) : RequestBody() {
    override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
    override fun contentLength(): Long = length
    override fun writeTo(sink: BufferedSink) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
        input.source().use { sink.writeAll(it) }
    }
}

/** Wraps a body and reports bytes written, for upload progress bars. */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (written: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                onProgress(written, total)
            }
        }
        val buffered = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
