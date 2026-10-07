package com.frameender.protobooru.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink

/*
 * Building blocks shared by every Settings page, so they all look and behave the same:
 * grouped cards, rows with a live summary, and long-press-to-reset on every setting.
 */

/** Default values, used by long-press reset. */
val SettingsDefaults = AppSettings()

/** Applies [reset] and says so. Used as every setting row's long-press action. */
fun resetSetting(title: String, reset: (AppSettings) -> AppSettings) {
    Graph.updateSettings { reset(it) }
    Graph.toast("“$title” reset to default")
}

/** A Settings sub-page: back arrow, title, scrolling content with consistent padding. */
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            content = content,
        )
    }
}

/** Small monospace heading above a group of cards. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
        color = Ink.Amber,
        modifier = modifier.padding(start = 6.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A rounded card holding a group of rows. Put [CardDivider] between rows. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Ink.Surface,
        border = BorderStroke(1.dp, Ink.Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
fun CardDivider() = HorizontalDivider(color = Ink.Line)

/** Explanatory text under a card. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Ink.TextDim,
        modifier = modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
    )
}

/** Colored rounded-square icon used on navigation rows. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Int = 38) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** A row that opens another page, with a one-line summary of what's set there. */
@Composable
fun NavRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    summary: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), maxLines = 1)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Ink.OnAmber,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(Ink.Amber).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextDim, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Title + optional summary, used by the setting rows below. */
@Composable
private fun RowText(title: String, summary: String?, enabled: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            color = if (enabled) Ink.Text else Ink.TextDim,
        )
        if (!summary.isNullOrBlank()) {
            Text(summary, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
        }
    }
}

/** On/off setting. Tap anywhere to toggle; long-press to reset. */
@Composable
fun SwitchSetting(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onReset: ((AppSettings) -> AppSettings)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = { onChange(!checked) },
                onLongClick = onReset?.let { r -> { resetSetting(title, r) } },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, summary, enabled, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Slider setting with its value shown on the right. Long-press the title to reset. */
@Composable
fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    summary: String? = null,
    onReset: ((AppSettings) -> AppSettings)? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onReset?.let { r -> { resetSetting(title, r) } }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowText(title, summary, true, Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = Ink.Amber)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Pick one of a few options, shown as a segmented control. */
@Composable
fun <T> SegmentedSetting(
    options: List<Triple<T, String, ImageVector?>>,
    selected: T,
    modifier: Modifier = Modifier,
    onPick: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (key, label, icon) ->
            SegmentedButton(
                selected = key == selected,
                onClick = { onPick(key) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = { if (icon != null) Icon(icon, null, Modifier.size(18.dp)) else SegmentedButtonDefaults.Icon(key == selected) },
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** Pick one of several options as chips, with a title (long-press it to reset). */
@Composable
fun <T> ChipsSetting(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    summary: String? = null,
    onReset: ((AppSettings) -> AppSettings)? = null,
    onPick: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        RowText(
            title, summary, true,
            Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onReset?.let { r -> { resetSetting(title, r) } }),
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (key, label) ->
                FilterChip(selected = key == selected, onClick = { onPick(key) }, label = { Text(label, maxLines = 1) })
            }
        }
    }
}

/** A plain row that runs an action (not a toggle), e.g. "Clear history". */
@Composable
fun ActionRow(title: String, summary: String? = null, color: Color = Ink.Text, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = if (enabled) color else Ink.TextDim)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
        }
    }
}
