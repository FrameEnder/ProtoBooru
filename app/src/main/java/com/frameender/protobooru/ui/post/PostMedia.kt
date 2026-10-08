package com.frameender.protobooru.ui.post

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.ui.common.ZoomableBox
import com.frameender.protobooru.ui.theme.Ink

/** Full-size image (or GIF) with thumbnail placeholder, pinch zoom and note outlines. */
@Composable
fun PostImage(
    post: Post,
    settings: AppSettings,
    showNotes: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var fullLoaded by remember(post.id) { mutableStateOf(false) }
    var failed by remember(post.id) { mutableStateOf(false) }
    val fullUrl = Graph.api.media(post.contentUrl, settings)

    ZoomableBox(
        modifier = Modifier.fillMaxSize(),
        resetKey = post.id,
        onTap = onTap,
        onZoomChanged = onZoomChanged,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!fullLoaded) {
                AsyncImage(
                    model = Graph.api.media(post.thumbnailUrl, settings),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(fullUrl)
                    // Cap decode size so zooming stays sharp without blowing up memory on huge files.
                    .size(4096, 4096)
                    .build(),
                contentDescription = "Post ${post.id}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onState = { st ->
                    when (st) {
                        is AsyncImagePainter.State.Success -> fullLoaded = true
                        is AsyncImagePainter.State.Error -> failed = true
                        else -> {}
                    }
                },
            )
            if (showNotes && post.notes.isNotEmpty()) NotesLayer(post, settings.noteTextMode)
        }
    }
    if (failed && !fullLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                "Full image failed to load — showing thumbnail",
                color = Ink.Red, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 120.dp),
            )
        }
    }
}

/** Flash can't be played on Android; offer the thumbnail and an external link. */
@Composable
fun PostFlash(post: Post, settings: AppSettings) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        AsyncImage(
            model = Graph.api.media(post.thumbnailUrl, settings),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(260.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Flash content can't be played on Android.", color = Ink.TextDim)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            Graph.api.resolve(post.contentUrl, settings)?.let {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            }
        }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
            Spacer(Modifier.size(8.dp))
            Text("Open file externally")
        }
    }
}

@Composable
fun PostLoadError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Icon(Icons.Default.BrokenImage, null, tint = Ink.Red, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = Ink.TextDim)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
