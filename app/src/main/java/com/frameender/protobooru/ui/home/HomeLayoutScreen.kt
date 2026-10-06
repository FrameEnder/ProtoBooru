package com.frameender.protobooru.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.HomeLayouts
import com.frameender.protobooru.data.HomeWidget
import com.frameender.protobooru.data.WidgetOptions
import com.frameender.protobooru.data.WidgetType
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ConfirmDialog
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import java.util.UUID
import kotlin.math.roundToInt

fun widgetIcon(type: String): ImageVector = when (type) {
    "search" -> Icons.Default.Search
    "stats" -> Icons.Default.BarChart
    "featured" -> Icons.Default.Star
    "strip" -> Icons.Default.ViewCarousel
    "grid" -> Icons.Default.GridView
    "random" -> Icons.Default.Casino
    "tags" -> Icons.Default.LocalOffer
    "pools" -> Icons.Default.Collections
    "comments" -> Icons.Default.Comment
    "shortcuts" -> Icons.Default.TouchApp
    "saved" -> Icons.Default.Bookmark
    else -> Icons.Default.Title
}

/** One-line description of a widget's current settings, shown under its name. */
private fun summary(w: HomeWidget): String {
    val q = w.query.ifBlank { "everything" }
    return when (w.type) {
        "search" -> "Search box"
        "stats" -> "${w.items.size} tiles · ${w.columns} per row"
        "featured" -> if (w.size == "compact") "Compact card" else "Large card"
        "strip" -> "${w.count} posts · $q · ${sizeLabel(w.size)}"
        "grid" -> "${w.count} posts · $q · ${w.columns} columns"
        "random" -> "From $q · ${if (w.size == "compact") "compact" else "large"}"
        "tags" -> "${w.count} tags · ${w.category.ifBlank { "any category" }} · " +
            (WidgetOptions.tagSorts.firstOrNull { it.first == w.sort.ifBlank { "usages" } }?.second ?: "Most used")
        "pools" -> "${w.count} pools · ${w.query.ifBlank { "recently edited" }}"
        "comments" -> "Latest ${w.count}"
        "shortcuts" -> "${w.items.size} buttons"
        "saved" -> w.items.joinToString(", ") { parseSaved(it).first }.ifBlank { "No searches yet" }
        else -> "Heading"
    }
}

private fun sizeLabel(s: String) = when (s) { "s" -> "small"; "l" -> "large"; else -> "medium" }

@Composable
fun HomeLayoutScreen(onBack: () -> Unit) {
    val settings by Graph.settings.collectAsState()
    val list = remember(settings.homeLayout) { HomeLayouts.decode(settings.homeLayout) }
    val isDefault = settings.homeLayout.isBlank()
    var editing by remember { mutableStateOf<HomeWidget?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HomeWidget?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    fun save(newList: List<HomeWidget>) {
        Graph.updateSettings { it.copy(homeLayout = HomeLayouts.encode(newList)) }
    }
    fun replace(w: HomeWidget) =
        save(if (list.any { it.id == w.id }) list.map { if (it.id == w.id) w else it } else list + w)
    fun move(i: Int, d: Int) {
        val j = i + d
        if (j !in list.indices) return
        save(list.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t })
    }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = {
                    Column {
                        Text("Customize Home")
                        Text(
                            if (isDefault) "Default layout" else "${list.count { it.enabled }} of ${list.size} widgets shown",
                            style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Reset to default") },
                            enabled = !isDefault,
                            onClick = { menu = false; confirmReset = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy layout") },
                            onClick = {
                                menu = false
                                clipboard.setText(AnnotatedString(HomeLayouts.encode(list)))
                                Graph.messages.tryEmit("Layout copied to clipboard")
                            },
                        )
                        DropdownMenuItem(text = { Text("Paste layout…") }, onClick = { menu = false; importing = true })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add widget") },
                containerColor = Ink.Amber,
                contentColor = Ink.OnAmber,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Widgets appear on Home from top to bottom. Tap one to fine-tune it, use the arrows to reorder, " +
                        "or switch it off to hide it without losing its settings.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            itemsIndexed(list, key = { _, w -> w.id }) { i, w ->
                WidgetRow(
                    w = w,
                    first = i == 0,
                    last = i == list.lastIndex,
                    onToggle = { replace(w.copy(enabled = it)) },
                    onUp = { move(i, -1) },
                    onDown = { move(i, 1) },
                    onEdit = { editing = w },
                    onDuplicate = {
                        save(list.toMutableList().also { it.add(i + 1, w.copy(id = UUID.randomUUID().toString())) })
                    },
                    onDelete = { deleting = w },
                )
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "No widgets. Add one, or reset to the default layout.",
                        color = Ink.TextDim, modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
        }
    }

    if (adding) {
        AddWidgetDialog(
            onDismiss = { adding = false },
            onPick = { type ->
                adding = false
                val w = HomeLayouts.create(type)
                save(list + w)
                // Search and stats-free types have little to set; open the editor for the rest.
                if (type != WidgetType.SEARCH) editing = w
            },
        )
    }
    editing?.let { w ->
        WidgetEditorDialog(
            initial = w,
            onDismiss = { editing = null },
            onSave = { replace(it); editing = null },
        )
    }
    deleting?.let { w ->
        ConfirmDialog(
            title = "Remove widget?",
            text = "\"${w.title.ifBlank { WidgetType.of(w.type)?.label ?: w.type }}\" will be removed from Home. " +
                "Tip: switch it off instead if you might want it back.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = { save(list.filterNot { it.id == w.id }) },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset Home?",
            text = "Your custom widgets will be replaced with the original Home layout.",
            confirmLabel = "Reset",
            destructive = true,
            onDismiss = { confirmReset = false },
            onConfirm = { Graph.updateSettings { it.copy(homeLayout = "") } },
        )
    }
    if (importing) {
        ImportDialog(
            onDismiss = { importing = false },
            onImport = { raw ->
                val parsed = runCatching { HomeLayouts.decodeStrict(raw) }.getOrNull()
                if (parsed == null) {
                    Graph.messages.tryEmit("That doesn't look like a ProtoBooru layout")
                } else {
                    save(parsed)
                    importing = false
                    Graph.messages.tryEmit("Layout imported (${parsed.size} widgets)")
                }
            },
        )
    }
}

@Composable
private fun WidgetRow(
    w: HomeWidget,
    first: Boolean,
    last: Boolean,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val type = WidgetType.of(w.type)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (w.enabled) Ink.Surface2 else Ink.Surface,
        border = BorderStroke(1.dp, if (w.enabled) Ink.Line else Ink.Line.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.clickable(onClick = onEdit).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    widgetIcon(w.type), null,
                    tint = if (w.enabled) Ink.Amber else Ink.TextDim,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        w.title.ifBlank { type?.label ?: w.type },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (w.enabled) Ink.Text else Ink.TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (if (w.title.isNotBlank()) "${type?.label} · " else "") + summary(w),
                        style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = w.enabled, onCheckedChange = onToggle)
            }
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUp, enabled = !first) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                IconButton(onClick = onDown, enabled = !last) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Ink.TextDim) }
                IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "Duplicate", tint = Ink.TextDim) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remove", tint = Ink.Red) }
            }
        }
    }
}

@Composable
private fun AddWidgetDialog(onDismiss: () -> Unit, onPick: (WidgetType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add widget") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WidgetType.entries.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(t) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(widgetIcon(t.key), null, tint = Ink.Amber)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(t.label, style = MaterialTheme.typography.titleSmall)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var raw by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste layout") },
        text = {
            Column {
                Text(
                    "Paste a layout copied from ProtoBooru (on this or another device). It replaces your current Home.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = raw, onValueChange = { raw = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text("[{\"type\":\"search\", …}]") },
                )
                TextButton(onClick = { raw = clipboard.getText()?.text.orEmpty() }) { Text("Paste from clipboard") }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(raw) }, enabled = raw.isNotBlank()) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

// =====================================================================
// Per-widget editor
// =====================================================================

@Composable
private fun WidgetEditorDialog(initial: HomeWidget, onDismiss: () -> Unit, onSave: (HomeWidget) -> Unit) {
    var w: HomeWidget by remember(initial.id) { mutableStateOf(initial) }
    val type = WidgetType.of(w.type) ?: return
    val categories by Graph.tagCategories.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(widgetIcon(w.type), null, tint = Ink.Amber) },
        title = { Text(type.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(type.description, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)

                if (type != WidgetType.SEARCH) {
                    OutlinedTextField(
                        value = w.title, onValueChange = { w = w.copy(title = it) },
                        label = { Text(if (type == WidgetType.HEADER) "Heading" else "Title (blank hides it)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }

                when (type) {
                    WidgetType.STRIP, WidgetType.GRID, WidgetType.RANDOM -> {
                        QueryField(w.query, "Search query", "e.g. type:video, sort:score, fav:{me}") { w = w.copy(query = it) }
                        QuickQueries { w = w.copy(query = it) }
                    }
                    WidgetType.POOLS -> {
                        QueryField(w.query, "Pool query", "blank = recently edited, e.g. sort:post-count") { w = w.copy(query = it) }
                    }
                    else -> {}
                }

                when (type) {
                    WidgetType.STRIP -> CountSlider("Posts", w.count, 3..60) { w = w.copy(count = it) }
                    WidgetType.GRID -> CountSlider("Posts", w.count, 2..30) { w = w.copy(count = it) }
                    WidgetType.TAGS -> CountSlider("Tags", w.count, 5..100) { w = w.copy(count = it) }
                    WidgetType.POOLS -> CountSlider("Pools", w.count, 1..40) { w = w.copy(count = it) }
                    WidgetType.COMMENTS -> CountSlider("Comments", w.count, 1..30) { w = w.copy(count = it) }
                    else -> {}
                }

                when (type) {
                    WidgetType.STRIP -> ChipChoice("Thumbnail size", listOf("s" to "Small", "m" to "Medium", "l" to "Large"), w.size.ifBlank { "m" }) { w = w.copy(size = it) }
                    WidgetType.FEATURED, WidgetType.RANDOM ->
                        ChipChoice("Card style", listOf("large" to "Large", "compact" to "Compact"), if (w.size == "compact") "compact" else "large") { w = w.copy(size = it) }
                    WidgetType.GRID -> CountSlider("Columns", w.columns, 2..5) { w = w.copy(columns = it) }
                    WidgetType.STATS -> CountSlider("Tiles per row", w.columns, 2..4) { w = w.copy(columns = it) }
                    else -> {}
                }

                when (type) {
                    WidgetType.STATS -> MultiPick("Tiles", WidgetOptions.stats, w.items) { w = w.copy(items = it) }
                    WidgetType.SHORTCUTS -> MultiPick("Buttons", WidgetOptions.shortcuts, w.items) { w = w.copy(items = it) }
                    WidgetType.SAVED -> SavedEditor(w.items) { w = w.copy(items = it) }
                    WidgetType.TAGS -> {
                        val cats = listOf("" to "Any") + categories.values.sortedBy { it.order }.map { it.name to it.name }
                        ChipChoice("Category", cats, w.category) { w = w.copy(category = it) }
                        ChipChoice("Sort", WidgetOptions.tagSorts, w.sort.ifBlank { "usages" }) { w = w.copy(sort = it) }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(w) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

@Composable
private fun QueryField(value: String, label: String, hint: String, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange,
            label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "$hint · {me} = your username",
            style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

/** Common queries one tap away, so nobody has to remember Szurubooru's syntax. */
@Composable
private fun QuickQueries(onPick: (String) -> Unit) {
    val presets = listOf(
        "Newest" to "",
        "Top score" to "sort:score",
        "Most faved" to "sort:fav-count",
        "Videos" to "type:video",
        "Animated" to "type:animated",
        "My faves" to "fav:{me}",
        "My uploads" to "uploader:{me}",
        "Untagged" to "tag-count:0",
        "Commented" to "sort:comment-date",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { (label, q) ->
            FilterChip(selected = false, onClick = { onPick(q) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@Composable
private fun CountSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val v = value.coerceIn(range)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$v", style = MaterialTheme.typography.labelLarge, color = Ink.Amber)
        }
        Slider(
            value = v.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceIn(0, 60),
        )
    }
}

@Composable
private fun ChipChoice(label: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, text) ->
                FilterChip(selected = selected == key, onClick = { onPick(key) }, label = { Text(text) })
            }
        }
    }
}

/** Multi-select chips. Selected items keep the order they were picked in, so you control tile order. */
@Composable
private fun MultiPick(label: String, options: List<Pair<String, String>>, selected: List<String>, onChange: (List<String>) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${selected.size} picked", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
        }
        Text("Shown in the order you pick them.", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, text) ->
                val idx = selected.indexOf(key)
                FilterChip(
                    selected = idx >= 0,
                    onClick = { onChange(if (idx >= 0) selected - key else selected + key) },
                    label = { Text(if (idx >= 0) "${idx + 1}. $text" else text) },
                )
            }
        }
    }
}

@Composable
private fun SavedEditor(items: List<String>, onChange: (List<String>) -> Unit) {
    var label by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Searches", style = MaterialTheme.typography.labelLarge)
        if (items.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
        items.forEachIndexed { i, item ->
            val (l, q) = parseSaved(item)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(q, style = MaterialTheme.typography.labelSmall, color = Ink.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {
                    if (i > 0) onChange(items.toMutableList().also { val t = it[i]; it[i] = it[i - 1]; it[i - 1] = t })
                }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                IconButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Default.Close, "Remove", tint = Ink.Red)
                }
            }
        }
        HorizontalDivider(color = Ink.Line)
        OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(query, { query = it }, label = { Text("Query") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(
            onClick = {
                val q = query.trim()
                onChange(items + "${label.trim().ifBlank { q }}|$q")
                label = ""; query = ""
            },
            enabled = query.isNotBlank(),
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Add search")
        }
    }
}
