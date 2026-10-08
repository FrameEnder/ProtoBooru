package com.frameender.protobooru.ui.post

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Double-tap on the left half skips back this far. */
private const val SKIP_BACK_MS = 5_000L

/** Double-tap on the right half skips forward this far. */
private const val SKIP_FORWARD_MS = 15_000L

/** Two taps closer together than this count as a double tap. */
private const val DOUBLE_TAP_MS = 280L

/** After a double-tap seek, each further tap within this window seeks again. */
private const val TAP_CHAIN_MS = 800L

/** Controls hide themselves after this long while the video plays. */
private const val CONTROLS_HIDE_MS = 3_000L

/**
 * Mute state shared by every video in this app session, so unmuting once keeps sound on
 * as you swipe. Starts from the "Start muted" setting.
 */
object VideoPrefs {
    var mutedOverride by mutableStateOf<Boolean?>(null)
    fun muted(settings: AppSettings): Boolean = mutedOverride ?: settings.startMuted
    fun toggle(settings: AppSettings) { mutedOverride = !muted(settings) }
}

/** What the controls need to know about the player, refreshed from its events and a 4 Hz poll. */
@Stable
private class PlaybackUi {
    var playing by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    /** Bumped on every interaction so the auto-hide timer restarts. */
    var touches by mutableIntStateOf(0)
}

@Composable
private fun rememberPlaybackUi(player: ExoPlayer): PlaybackUi {
    val ui = remember(player) { PlaybackUi() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                ui.playing = p.isPlaying
                ui.ended = p.playbackState == Player.STATE_ENDED
                ui.position = p.currentPosition
                ui.duration = p.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            ui.position = player.currentPosition
            ui.duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    return ui
}

private fun togglePlay(player: ExoPlayer, ui: PlaybackUi) {
    when {
        ui.ended -> { player.seekTo(0); player.play() }
        player.isPlaying -> player.pause()
        else -> player.play()
    }
}

/**
 * ExoPlayer-backed video page. Plays only while [active] (the current pager page).
 *
 * Inline it shows a compact control bar (play, seek, fullscreen). Fullscreen is a real
 * immersive window: system bars hidden, rotated to fit the video, with double-tap seeking
 * and brightness / volume swipes. [controlsBottom] keeps the inline bar clear of the
 * viewer's own action bar.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PostVideo(post: Post, settings: AppSettings, active: Boolean, controlsBottom: Dp = 72.dp) {
    val context = LocalContext.current
    val url = Graph.api.media(post.contentUrl, settings) ?: return
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

    val ui = rememberPlaybackUi(player)
    var fullscreen by remember(post.id) { mutableStateOf(false) }
    var controls by remember(post.id) { mutableStateOf(true) }
    LaunchedEffect(active) { if (!active) fullscreen = false }
    LaunchedEffect(controls, ui.playing, ui.touches) {
        if (controls && ui.playing) {
            delay(CONTROLS_HIDE_MS)
            controls = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            // Only one surface can show the video: hand it to the fullscreen window while it's open.
            update = { it.player = if (fullscreen) null else player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        // Tap layer. Taps only: swipes still reach the pager (next post) and swipe-up (details).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls; ui.touches++ }) },
        )
        AnimatedVisibility(
            visible = controls || !ui.playing,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                BigPlayButton(ui, Modifier.align(Alignment.Center)) { togglePlay(player, ui); ui.touches++ }
                SeekRow(
                    player, ui,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = controlsBottom, start = 8.dp, end = 4.dp),
                ) {
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Default.Fullscreen, "Fullscreen", tint = Color.White)
                    }
                }
            }
        }
    }

    if (fullscreen) {
        FullscreenVideo(post, player, settings, ui, onExit = { fullscreen = false })
    }
}

// =====================================================================
// Shared pieces
// =====================================================================

@Composable
private fun BigPlayButton(ui: PlaybackUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        modifier = modifier.size(68.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                when {
                    ui.ended -> Icons.Default.Replay
                    ui.playing -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                if (ui.playing) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** Time, seek bar, duration, then any extra buttons. */
@Composable
private fun SeekRow(
    player: ExoPlayer,
    ui: PlaybackUi,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val dur = ui.duration
    val scrubbing = scrub
    val frac = scrubbing ?: if (dur > 0) (ui.position.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatTime(if (scrubbing != null) (scrubbing * dur).toLong() else ui.position),
            color = Color.White, style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = frac,
            onValueChange = { scrub = it; ui.touches++ },
            onValueChangeFinished = {
                scrub?.let { if (dur > 0) player.seekTo((it * dur).toLong()) }
                scrub = null
            },
            enabled = dur > 0,
            colors = SliderDefaults.colors(
                thumbColor = Ink.Amber,
                activeTrackColor = Ink.Amber,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(formatTime(dur), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        trailing()
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// =====================================================================
// Fullscreen
// =====================================================================

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FullscreenVideo(post: Post, player: ExoPlayer, settings: AppSettings, ui: PlaybackUi, onExit: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val previousOrientation = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    var landscape by remember {
        val vs = player.videoSize
        mutableStateOf(
            if (vs.width > 0 && vs.height > 0) vs.width * vs.pixelWidthHeightRatio >= vs.height
            else post.aspect >= 1f,
        )
    }
    LaunchedEffect(landscape) {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = previousOrientation }
    }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            if (window != null) makeImmersive(window)
            onDispose {}
        }
        FullscreenContent(
            post = post,
            player = player,
            settings = settings,
            ui = ui,
            window = window,
            onRotate = { landscape = !landscape },
            onExit = onExit,
        )
    }
}

/** Edge to edge, behind the camera cutout, with the status and navigation bars hidden. */
private fun makeImmersive(window: Window) {
    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
    window.setDimAmount(0f)
    window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

private enum class AdjustKind { BRIGHTNESS, VOLUME }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FullscreenContent(
    post: Post,
    player: ExoPlayer,
    settings: AppSettings,
    ui: PlaybackUi,
    window: Window?,
    onRotate: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val muted = VideoPrefs.muted(settings)

    var controls by remember { mutableStateOf(true) }
    LaunchedEffect(controls, ui.playing, ui.touches) {
        if (controls && ui.playing) {
            delay(CONTROLS_HIDE_MS)
            controls = false
        }
    }

    // Double-tap seek feedback: which side (-1 left, 1 right, 0 none) and how far in total.
    var seekSide by remember { mutableIntStateOf(0) }
    var seekTotalMs by remember { mutableLongStateOf(0L) }
    // Brightness / volume swipe feedback.
    var adjustKind by remember { mutableStateOf<AdjustKind?>(null) }
    var adjustLevel by remember { mutableFloatStateOf(0f) }

    fun brightnessNow(): Float {
        val b = window?.attributes?.screenBrightness ?: -1f
        if (b >= 0f) return b
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }
    fun setBrightness(v: Float) {
        // Only this window's brightness changes; it goes back to normal when fullscreen closes.
        val w = window ?: return
        w.attributes = w.attributes.apply { screenBrightness = v.coerceIn(0.01f, 1f) }
    }
    fun volumeNow(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
    fun setVolume(v: Float): Float {
        val steps = (v * maxVolume).roundToInt().coerceIn(0, maxVolume)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0) }
        // Turning the volume up while muted means you want sound.
        if (steps > 0 && VideoPrefs.muted(settings)) VideoPrefs.mutedOverride = false
        return steps.toFloat() / maxVolume
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // ---------- Gesture layer ----------
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    var lastTapUp = 0L
                    var chainUntil = 0L
                    var pendingTap: Job? = null
                    var seekHide: Job? = null
                    var adjustHide: Job? = null

                    fun seek(side: Int) {
                        val delta = if (side < 0) -SKIP_BACK_MS else SKIP_FORWARD_MS
                        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + delta).coerceIn(0L, dur))
                        if (seekSide != side) seekTotalMs = 0L
                        seekSide = side
                        seekTotalMs += abs(delta)
                        seekHide?.cancel()
                        seekHide = scope.launch {
                            delay(TAP_CHAIN_MS + 250)
                            seekSide = 0
                            seekTotalMs = 0L
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val leftHalf = down.position.x < size.width / 2f
                        val slop = viewConfiguration.touchSlop
                        var dragging = false
                        var startLevel = 0f
                        var upX = down.position.x
                        var upTime = down.uptimeMillis
                        var movedSideways = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val d = change.position - down.position
                            if (change.changedToUpIgnoreConsumed()) {
                                upX = change.position.x
                                upTime = change.uptimeMillis
                                break
                            }
                            if (!dragging && abs(d.x) > slop * 2 && abs(d.x) > abs(d.y)) movedSideways = true
                            if (!dragging && !movedSideways && abs(d.y) > slop && abs(d.y) > abs(d.x)) {
                                dragging = true
                                pendingTap?.cancel()
                                startLevel = if (leftHalf) brightnessNow() else volumeNow()
                                adjustHide?.cancel()
                            }
                            if (dragging) {
                                // Up is more; a swipe across ~3/4 of the screen covers the full range.
                                val target = (startLevel - d.y / (size.height * 0.75f)).coerceIn(0f, 1f)
                                if (leftHalf) {
                                    setBrightness(target)
                                    adjustKind = AdjustKind.BRIGHTNESS
                                    adjustLevel = target
                                } else {
                                    adjustLevel = setVolume(target)
                                    adjustKind = AdjustKind.VOLUME
                                }
                                change.consume()
                            }
                        }

                        if (dragging) {
                            adjustHide = scope.launch { delay(700); adjustKind = null }
                            return@awaitEachGesture
                        }
                        if (movedSideways) return@awaitEachGesture

                        val side = if (upX < size.width / 2f) -1 else 1
                        when {
                            // Already seeking: every tap keeps going (and switches direction if needed).
                            upTime < chainUntil -> {
                                seek(side)
                                chainUntil = upTime + TAP_CHAIN_MS
                            }
                            // Second tap of a double tap.
                            upTime - lastTapUp < DOUBLE_TAP_MS && pendingTap?.isActive == true -> {
                                pendingTap?.cancel()
                                seek(side)
                                chainUntil = upTime + TAP_CHAIN_MS
                                lastTapUp = 0L
                            }
                            // Maybe a single tap: wait to see if a second one follows.
                            else -> {
                                lastTapUp = upTime
                                pendingTap = scope.launch {
                                    delay(DOUBLE_TAP_MS)
                                    controls = !controls
                                    ui.touches++
                                }
                            }
                        }
                    }
                },
        )

        // ---------- Double-tap seek feedback ----------
        if (seekSide != 0) {
            SeekBubble(
                forward = seekSide > 0,
                totalMs = seekTotalMs,
                modifier = Modifier.align(if (seekSide > 0) Alignment.CenterEnd else Alignment.CenterStart),
            )
        }

        // ---------- Brightness / volume feedback ----------
        adjustKind?.let { kind ->
            AdjustPill(
                icon = when {
                    kind == AdjustKind.BRIGHTNESS -> Icons.Default.BrightnessMedium
                    adjustLevel <= 0f || muted -> Icons.Default.VolumeOff
                    else -> Icons.Default.VolumeUp
                },
                level = adjustLevel,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            )
        }

        // ---------- Controls ----------
        AnimatedVisibility(
            visible = controls || !ui.playing,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                FullscreenTopBar(post, muted, settings, onRotate, onExit)
                BigPlayButton(ui, Modifier.align(Alignment.Center)) { togglePlay(player, ui); ui.touches++ }
                SeekRow(
                    player, ui,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 12.dp, bottom = 16.dp),
                ) {
                    IconButton(onClick = { VideoPrefs.toggle(settings); ui.touches++ }) {
                        Icon(
                            if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            if (muted) "Unmute" else "Mute",
                            tint = if (muted) Color.White else Ink.Amber,
                        )
                    }
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.FullscreenExit, "Exit fullscreen", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.FullscreenTopBar(post: Post, muted: Boolean, settings: AppSettings, onRotate: () -> Unit, onExit: () -> Unit) {
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit fullscreen", tint = Color.White) }
        Text("#${post.id}", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onRotate) { Icon(Icons.Default.ScreenRotation, "Rotate", tint = Color.White) }
        IconButton(onClick = { VideoPrefs.toggle(settings) }) {
            Icon(
                if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                if (muted) "Unmute" else "Mute",
                tint = if (muted) Color.White else Ink.Amber,
            )
        }
    }
}

/** The "« 10s" ripple on whichever side is being double-tapped. */
@Composable
private fun SeekBubble(forward: Boolean, totalMs: Long, modifier: Modifier = Modifier) {
    val shape = if (forward) {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    } else {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    }
    Box(
        modifier
            .fillMaxHeight(0.7f)
            .width(140.dp)
            .background(Color.White.copy(alpha = 0.14f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (forward) Icons.Default.FastForward else Icons.Default.FastRewind,
                null, tint = Color.White, modifier = Modifier.size(36.dp),
            )
            Text(
                (if (forward) "+" else "−") + "${totalMs / 1000}s",
                color = Color.White, style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AdjustPill(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        LinearProgressIndicator(
            progress = { level },
            color = Ink.Amber,
            trackColor = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.width(140.dp),
        )
        Text("${(level * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(2.dp))
    }
}
