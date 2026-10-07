package com.frameender.protobooru.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.frameender.protobooru.R

// ---- Workshop palette: ink + amber ----
object Ink {
    val Bg = Color(0xFF0E1013)
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF15181D)
    val Surface2 = Color(0xFF1C2027)
    val Surface3 = Color(0xFF242932)
    val Line = Color(0xFF2E343E)
    val Text = Color(0xFFE9E5DB)
    val TextDim = Color(0xFF9A978F)
    // The highlight color follows the user's accent choice. Reads happen in composition,
    // so changing the accent recomposes everything that uses it.
    val Amber: Color get() = Accents.current.main
    val AmberDim: Color get() = Accents.current.dim
    val OnAmber: Color get() = Accents.current.on
    val Teal = Color(0xFF7DB8B5)
    val Red = Color(0xFFE5574F)
    val Green = Color(0xFF8BC37A)
    val Violet = Color(0xFFB394E8)

    val Safe = Color(0xFF8BC37A)
    val Sketchy = Color(0xFFE6C15A)
    val Unsafe = Color(0xFFE5574F)
}

val Grotesk = FontFamily(
    Font(R.font.space_grotesk_400, FontWeight.Normal),
    Font(R.font.space_grotesk_500, FontWeight.Medium),
    Font(R.font.space_grotesk_600, FontWeight.SemiBold),
    Font(R.font.space_grotesk_700, FontWeight.Bold),
)

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_600, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

private fun g(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.35).toInt(), track: Double = 0.0) =
    TextStyle(fontFamily = Grotesk, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = track.sp)

private fun m(size: Int, weight: FontWeight = FontWeight.Medium, track: Double = 0.4) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.4).toInt().sp, letterSpacing = track.sp)

private val AppTypography = Typography(
    displayLarge = g(48, FontWeight.Bold, track = -1.0),
    displayMedium = g(38, FontWeight.Bold, track = -0.8),
    displaySmall = g(30, FontWeight.Bold, track = -0.5),
    headlineLarge = g(28, FontWeight.SemiBold, track = -0.4),
    headlineMedium = g(24, FontWeight.SemiBold, track = -0.3),
    headlineSmall = g(20, FontWeight.SemiBold),
    titleLarge = g(20, FontWeight.SemiBold),
    titleMedium = g(16, FontWeight.SemiBold),
    titleSmall = g(14, FontWeight.SemiBold),
    bodyLarge = g(16),
    bodyMedium = g(14),
    bodySmall = g(12),
    // Buttons, chips and tabs: sans, not mono. Mono is ~30% wider and made dialog buttons wrap.
    labelLarge = g(14, FontWeight.SemiBold, line = 20, track = 0.1),
    labelMedium = m(12),
    labelSmall = m(10, track = 0.8),
)

/** A selectable highlight color. [dim] is used for containers like the nav-bar pill. */
data class Accent(val key: String, val label: String, val main: Color, val dim: Color, val on: Color = Color(0xFF15110C))

private fun accent(key: String, label: String, hex: Long): Accent {
    val main = Color(hex)
    // Dim = the accent mixed 45% into the ink background.
    val bg = Color(0xFF0E1013)
    val dim = Color(
        red = bg.red + (main.red - bg.red) * 0.45f,
        green = bg.green + (main.green - bg.green) * 0.45f,
        blue = bg.blue + (main.blue - bg.blue) * 0.45f,
    )
    return Accent(key, label, main, dim)
}

object Accents {
    val all = listOf(
        accent("amber", "Amber", 0xFFF2A93B),
        accent("sakura", "Sakura", 0xFFF48FB1),
        accent("coral", "Coral", 0xFFFF8A65),
        accent("crimson", "Crimson", 0xFFEF5D5D),
        accent("gold", "Gold", 0xFFE8C547),
        accent("lime", "Lime", 0xFFA8D65C),
        accent("mint", "Mint", 0xFF6FD6B0),
        accent("teal", "Teal", 0xFF4FC3C0),
        accent("sky", "Sky", 0xFF6FB6F5),
        accent("periwinkle", "Periwinkle", 0xFF8C9EFF),
        accent("lavender", "Lavender", 0xFFB39DFF),
        accent("orchid", "Orchid", 0xFFD98CF0),
    )

    private val state = mutableStateOf(all.first())
    val current: Accent get() = state.value

    fun byKey(key: String?): Accent = all.firstOrNull { it.key == key } ?: all.first()
    fun select(key: String?) { state.value = byKey(key) }
}

@Composable
fun ProtoBooruTheme(amoled: Boolean = false, content: @Composable () -> Unit) {
    // The accent itself is applied by Graph whenever settings change (see Graph.init).
    val bg = if (amoled) Ink.Black else Ink.Bg
    val scheme = darkColorScheme(
        primary = Ink.Amber,
        onPrimary = Ink.OnAmber,
        primaryContainer = Ink.AmberDim,
        onPrimaryContainer = Ink.Text,
        secondary = Ink.Teal,
        onSecondary = Ink.Bg,
        secondaryContainer = Ink.Surface3,
        onSecondaryContainer = Ink.Text,
        tertiary = Ink.Violet,
        background = bg,
        onBackground = Ink.Text,
        surface = bg,
        onSurface = Ink.Text,
        surfaceVariant = Ink.Surface2,
        onSurfaceVariant = Ink.TextDim,
        surfaceContainerLowest = bg,
        surfaceContainerLow = Ink.Surface,
        surfaceContainer = Ink.Surface,
        surfaceContainerHigh = Ink.Surface2,
        surfaceContainerHighest = Ink.Surface3,
        outline = Ink.Line,
        outlineVariant = Ink.Line,
        error = Ink.Red,
        onError = Ink.Bg,
        inverseSurface = Ink.Text,
        inverseOnSurface = Ink.Bg,
        inversePrimary = Ink.AmberDim,
        scrim = Color.Black,
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

/** Szurubooru category colors are CSS strings ("#ff8800", "red", "default"). */
fun categoryColor(css: String?): Color {
    if (css == null) return Ink.Teal
    val c = css.trim().lowercase()
    if (c.startsWith("#")) {
        val hex = c.removePrefix("#")
        val full = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6 -> hex
            8 -> hex.substring(0, 6)
            else -> null
        }
        if (full != null) return runCatching { Color(0xFF000000 or full.toLong(16)) }.getOrDefault(Ink.Teal)
    }
    return when (c) {
        "red" -> Color(0xFFE5574F)
        "orange" -> Color(0xFFF2A93B)
        "yellow", "gold" -> Color(0xFFE6C15A)
        "green", "lime" -> Color(0xFF8BC37A)
        "blue" -> Color(0xFF6FA8F0)
        "purple", "violet", "magenta", "fuchsia" -> Color(0xFFB394E8)
        "pink" -> Color(0xFFF08FB8)
        "cyan", "aqua", "teal" -> Color(0xFF7DB8B5)
        "gray", "grey", "silver" -> Color(0xFF9A978F)
        "white" -> Color(0xFFE9E5DB)
        else -> Ink.Teal
    }
}

fun safetyColor(safety: String): Color = when (safety) {
    "safe" -> Ink.Safe
    "sketchy" -> Ink.Sketchy
    "unsafe" -> Ink.Unsafe
    else -> Ink.TextDim
}
