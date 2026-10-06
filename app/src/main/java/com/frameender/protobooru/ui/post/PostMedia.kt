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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    val fullUrl = Graph.api.resolve(post.contentUrl, settings)

    ZoomableBox(
        modifier = Modifier.fillMaxSize(),
        resetKey = post.id,
        onTap = onTap,
        onZoomChanged = onZoomChanged,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!fullLoaded) {
                AsyncImage(
                    model = Graph.api.resolve(post.thumbnailUrl, settings),
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

/**
 * Mute state shared by every video in this app session, so unmuting once keeps sound on
 * as you swipe. Starts from the "Start muted" setting.
 */
object VideoPrefs {
    var mutedOverride by mutableStateOf<Boolean?>(null)
    fun muted(settings: AppSettings): Boolean = mutedOverride ?: settings.startMuted
    fun toggle(settings: AppSettings) { mutedOverride = !muted(settings) }
}

/** ExoPlayer-backed video page. Plays only while [active] (the current pager page). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PostVideo(post: Post, settings: AppSettings, active: Boolean) {
    val context = LocalContext.current
    val url = Graph.api.resolve(post.contentUrl, settings) ?: return
    val player = remember(post.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (settings.loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (VideoPrefs.muted(settings)) 0f else 1f
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val muted = VideoPrefs.muted(settings)
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(active) {
        if (active) {
            if (settings.autoplayVideo) player.play()
        } else {
            player.pause()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                controllerShowTimeoutMs = 2500
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize().padding(vertical = 64.dp),
    )
}

/** Flash can't be played on Android; offer the thumbnail and an external link. */
@Composable
fun PostFlash(post: Post, settings: AppSettings) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        AsyncImage(
            model = Graph.api.resolve(post.thumbnailUrl, settings),
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
