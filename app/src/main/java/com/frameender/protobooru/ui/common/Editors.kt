package com.frameender.protobooru.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Pool
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.SAFETIES
import com.frameender.protobooru.data.Tag
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor
import com.frameender.protobooru.ui.theme.safetyColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tag name -> category, learned from anything we've loaded, so chips can be colored. */
object TagCategoryCache {
    val map = mutableStateMapOf<String, String>()
    fun learn(name: String, category: String) { map[name.lowercase()] = category }
}

/** Normalizes user input into a Szurubooru tag name (spaces become underscores). */
fun cleanTag(raw: String): String = raw.trim().trim(',').replace(' ', '_')

/**
 * Chip-style tag editor with server-side autocomplete. Typing a space or comma commits
 * the current tag; pasting "a b c" adds all three.
 */
@Composable
fun TagEditor(
    tags: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Add tags",
    enabled: Boolean = true,
) {
    var input by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Tag>>(emptyList()) }
    val cats by Graph.tagCategories.collectAsState()

    LaunchedEffect(input) {
        val t = input.trim().removePrefix("-")
        if (t.length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        delay(220)
        suggestions = runCatching { Graph.api.suggestTags(t, 8) }.getOrDefault(emptyList())
        suggestions.forEach { TagCategoryCache.learn(it.name, it.category) }
    }

    fun commit(raw: String) {
        val new = raw.split(Regex("[\\s,]+")).map(::cleanTag).filter { it.isNotBlank() }
        if (new.isNotEmpty()) onChange((tags + new).distinctBy { it.lowercase() })
        input = ""
        suggestions = emptyList()
    }

    Column(modifier.fillMaxWidth()) {
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                tags.forEach { t ->
                    val color = categoryColor(cats[TagCategoryCache.map[t.lowercase()]]?.color)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(6.dp))
                            .padding(start = 8.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        IconButton(
                            onClick = { onChange(tags.filterNot { it == t }) },
                            enabled = enabled,
                            modifier = Modifier.size(28.dp),
                        ) { Icon(Icons.Default.Close, "Remove $t", tint = color, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { v ->
                if (v.endsWith(" ") || v.endsWith(",") || v.endsWith("\n")) commit(v) else input = v
            },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
            keyboardActions = KeyboardActions(onDone = { commit(input) }),
            trailingIcon = {
                if (input.isNotBlank()) IconButton(onClick = { commit(input) }) { Icon(Icons.Default.Add, "Add tag") }
            },
        )
        if (suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Ink.Surface2,
                border = BorderStroke(1.dp, Ink.Line),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    suggestions.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { commit(s.name) }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(cats[s.category]?.color)))
                            Spacer(Modifier.width(10.dp))
                            Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(Format.count(s.usages), style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafetySelector(value: String?, onChange: (String?) -> Unit, allowUnchanged: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowUnchanged) {
            FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text("unchanged") })
        }
        SAFETIES.forEach { s ->
            FilterChip(
                selected = value == s,
                onClick = { onChange(s) },
                label = { Text(s) },
                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(safetyColor(s))) },
            )
        }
    }
}

/** Search pools by name and pick one, or create a new one on the spot. */
@Composable
fun PoolPickerDialog(onDismiss: () -> Unit, onPick: (Pool) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pool>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(250)
        loading = true
        val q = query.trim().let { if (it.isEmpty()) "sort:last-edit-time" else "*${it.replace(' ', '_')}* sort:last-edit-time" }
        results = runCatching { Graph.api.pools(q, 0, 30).results }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a pool") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search or new pool name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(p); onDismiss() }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("#${p.id}", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim, modifier = Modifier.width(48.dp))
                            Text(p.name.replace('_', ' '), modifier = Modifier.weight(1f))
                            Text("${p.postCount}", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                        }
                        HorizontalDivider(color = Ink.Line)
                    }
                }
            }
        },
        confirmButton = {
            if (query.isNotBlank() && Graph.can("pools:create")) {
                TextButton(enabled = !creating, onClick = {
                    creating = true
                    scope.launch {
                        try {
                            val cat = Graph.poolCategories.value.values.firstOrNull { it.default }?.name ?: "default"
                            val p = Graph.api.createPool(listOf(cleanTag(query)), cat, null, emptyList())
                            onPick(p)
                            onDismiss()
                        } catch (e: Exception) {
                            Graph.toast(e.message ?: "Could not create pool")
                        } finally {
                            creating = false
                        }
                    }
                }) { Text("Create “${cleanTag(query)}”") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

/** Ask for a post id, preview it, and confirm. Used for merges and relations. */
@Composable
fun PostIdDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    onConfirm: (Post) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var post by remember { mutableStateOf<Post?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        post = null
        error = null
        val id = text.trim().removePrefix("#").toIntOrNull() ?: return@LaunchedEffect
        delay(300)
        runCatching { Graph.api.post(id) }.onSuccess { post = it }.onFailure { error = it.message }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it }, label = { Text("Post ID") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val p = post
                if (p != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(p.thumbnailUrl, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text("#${p.id} · ${p.type} · ${p.tagCount} tags", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (error != null) Text(error!!, color = Ink.Red, style = MaterialTheme.typography.bodySmall)
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(enabled = post != null, onClick = { post?.let(onConfirm); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

val PRESET_COLORS = listOf(
    "#e5574f", "#f2a93b", "#e6c15a", "#8bc37a", "#7db8b5", "#6fa8f0", "#b394e8", "#f08fb8", "#9a978f", "#e9e5db",
)

/** Hex color input with a row of preset swatches. */
@Composable
fun ColorField(value: String, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value, onChange, label = { Text("Color (#hex or CSS name)") }, singleLine = true,
            leadingIcon = { Box(Modifier.size(18.dp).clip(CircleShape).background(categoryColor(value))) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESET_COLORS.forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(categoryColor(c))
                        .border(2.dp, if (value.equals(c, true)) Color.White else Color.Transparent, CircleShape)
                        .clickable { onChange(c) },
                )
            }
        }
    }
}
