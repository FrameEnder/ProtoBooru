package com.frameender.protobooru.ui.post

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frameender.protobooru.data.Note
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.ui.comments.renderCommentText
import com.frameender.protobooru.ui.theme.Grotesk
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.Mono
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How note text is shown on the image. */
object NoteTextMode {
    const val OFF = "off"        // outlines only; read notes in the info sheet
    const val TAP = "tap"        // tap a note's outline to show its text
    const val ALWAYS = "always"  // every note's text is shown in its box

    val all = listOf(OFF to "Outlines only", TAP to "Tap to show", ALWAYS to "Always show")
}

private const val PAD_DP = 4f
private val FIT_SIZES = listOf(15f, 14f, 13f, 12f, 11f, 10f, 9f, 8f)

private fun noteStyle(sizeSp: Float) = TextStyle(
    fontFamily = Grotesk,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 1.25f).sp,
    color = Ink.Text,
)

/** Where and how one note's text is drawn: inside its own box, or as a bubble next to it. */
private data class NotePlacement(val rect: Rect, val sizeSp: Float, val inside: Boolean)

/**
 * Finds the biggest font that fits the note's box. If even the smallest size doesn't fit
 * (a tiny box with lots of text), the text goes in a bubble just below (or above) the box.
 */
private fun placeNote(
    text: AnnotatedString,
    box: Rect,
    container: Rect,
    measurer: TextMeasurer,
    density: Density,
): NotePlacement {
    val pad = with(density) { PAD_DP.dp.toPx() }
    val innerW = (box.width - pad * 2).roundToInt()
    val innerH = box.height - pad * 2
    if (innerW > 8 && innerH > 8) {
        for (size in FIT_SIZES) {
            val layout = measurer.measure(text, noteStyle(size), constraints = Constraints(maxWidth = innerW))
            if (layout.size.height <= innerH && !layout.hasVisualOverflow) {
                return NotePlacement(box, size, inside = true)
            }
        }
    }
    // Bubble: at least 180dp wide, never wider than the image.
    val margin = with(density) { 6.dp.toPx() }
    val bubbleW = min(max(box.width, with(density) { 180.dp.toPx() }), container.width - margin * 2)
    val size = 12f
    val layout = measurer.measure(text, noteStyle(size), constraints = Constraints(maxWidth = (bubbleW - pad * 2).roundToInt().coerceAtLeast(1)))
    val bubbleH = layout.size.height + pad * 2
    val x = (box.left + box.width / 2 - bubbleW / 2).coerceIn(container.left + margin, container.right - bubbleW - margin)
    val below = box.bottom + margin
    val y = if (below + bubbleH <= container.bottom) below else max(container.top + margin, box.top - margin - bubbleH)
    return NotePlacement(Rect(x, y, x + bubbleW, y + bubbleH), size, inside = false)
}

/**
 * Note outlines plus their text, drawn inside the zoomable image layer so everything scales
 * with pinch zoom. [mode] is one of [NoteTextMode].
 */
@Composable
fun NotesLayer(post: Post, mode: String) {
    val cw = (post.canvasWidth ?: 0).toFloat()
    val ch = (post.canvasHeight ?: 0).toFloat()
    if (cw <= 0f || ch <= 0f || post.notes.isEmpty()) return

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val open = remember(post.id, mode) {
        mutableStateListOf<Boolean>().apply { repeat(post.notes.size) { add(mode == NoteTextMode.ALWAYS) } }
    }
    val texts = remember(post.id) { post.notes.map { renderCommentText(it.text) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val s = min(w / cw, h / ch)
        val image = Rect((w - cw * s) / 2f, (h - ch * s) / 2f, (w + cw * s) / 2f, (h + ch * s) / 2f)

        fun points(n: Note): List<Offset> = n.polygon.mapNotNull { p ->
            if (p.size >= 2) Offset(image.left + p[0].toFloat() * image.width, image.top + p[1].toFloat() * image.height) else null
        }

        val accent = Ink.Amber
        // Outlines + number tags
        Canvas(Modifier.fillMaxSize()) {
            post.notes.forEachIndexed { i, note ->
                val pts = points(note)
                if (pts.size < 2) return@forEachIndexed
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                val isOpen = open.getOrElse(i) { false }
                drawPath(path, accent.copy(alpha = if (isOpen) 0.06f else 0.12f))
                drawPath(path, accent, style = Stroke(width = 2.dp.toPx()))
                if (!isOpen || mode == NoteTextMode.OFF) {
                    val label = measurer.measure(
                        "${i + 1}",
                        TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                    )
                    val anchor = pts.minBy { it.x + it.y }
                    drawRect(accent, topLeft = anchor, size = androidx.compose.ui.geometry.Size(label.size.width + 8f, label.size.height.toFloat()))
                    drawText(label, topLeft = Offset(anchor.x + 4f, anchor.y))
                }
            }
        }

        if (mode == NoteTextMode.OFF) return@BoxWithConstraints

        post.notes.forEachIndexed { i, note ->
            val pts = points(note)
            if (pts.size < 2) return@forEachIndexed
            val box = Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
            val toggle = { open[i] = !open[i] }

            // Invisible tap target over the note's area.
            Box(
                Modifier
                    .offset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
                    .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = toggle),
            )

            if (open.getOrElse(i) { false }) {
                val placement = remember(note.text, box, image) { placeNote(texts[i], box, image, measurer, density) }
                val r = placement.rect
                Box(
                    Modifier
                        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                        .clip(RoundedCornerShape(if (placement.inside) 3.dp else 8.dp))
                        .background(Ink.Bg.copy(alpha = if (placement.inside) 0.82f else 0.92f))
                        .border(1.dp, accent.copy(alpha = if (placement.inside) 0.0f else 0.7f), RoundedCornerShape(8.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = toggle)
                        .padding(PAD_DP.dp),
                    contentAlignment = if (placement.inside) Alignment.Center else Alignment.TopStart,
                ) {
                    Text(texts[i], style = noteStyle(placement.sizeSp))
                }
            }
        }
    }
}
