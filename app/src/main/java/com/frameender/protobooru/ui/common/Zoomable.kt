package com.frameender.protobooru.ui.common

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Pinch / double-tap zoom container that plays nicely with a HorizontalPager:
 * single-finger drags pass through to the pager while not zoomed in.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    maxScale: Float = 6f,
    onTap: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resetKey) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged(false)
    }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = max(0f, size.width * (s - 1f) / 2f)
        val maxY = max(0f, size.height * (s - 1f) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale > 1.05f) {
                                val startOffset = offset
                                val startScale = scale
                                animate(1f, 0f) { f, _ ->
                                    scale = 1f + (startScale - 1f) * f
                                    offset = startOffset * f
                                }
                                onZoomChanged(false)
                            } else {
                                val target = 2.5f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val end = clamp((center - tap) * (target - 1f), target)
                                onZoomChanged(true)
                                animate(0f, 1f) { f, _ ->
                                    scale = 1f + (target - 1f) * f
                                    offset = end * f
                                }
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val current = scale
                        if (pressed > 1 || current > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (current * zoom).coerceIn(1f, maxScale)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            var o = if (centroid != Offset.Unspecified) {
                                // Keep the content point under the fingers fixed while scaling.
                                centroid - center - (centroid - center - offset) * (newScale / current)
                            } else offset
                            o += pan
                            offset = clamp(o, newScale)
                            scale = newScale
                            onZoomChanged(newScale > 1.01f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.01f) {
                        offset = Offset.Zero
                        scale = 1f
                        onZoomChanged(false)
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        content()
    }
}
