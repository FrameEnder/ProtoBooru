package com.frameender.protobooru.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.TagClipboard
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * Copy / paste tags between posts, shown under a tag editor:
 *  - Copy tags:   remembers the tags currently in the editor (unsaved edits included).
 *  - Paste tags:  adds the remembered tags here.
 *  - From post #: pulls the tags straight from another post, no copying first.
 *  - Replace existing tags: paste / pull replaces instead of adding.
 * Nothing is saved to the server until the screen's own Save, same as typing tags.
 *
 * [fromPostId] is the post being edited (labels the copied set); null elsewhere.
 * [allowFetch] hides the "From post #" field where it makes no sense.
 */
@Composable
fun TagCopyBar(
    current: List<String>,
    onChange: (List<String>) -> Unit,
    fromPostId: Int? = null,
    allowFetch: Boolean = true,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val s by Graph.settings.collectAsState()
    val copied = TagClipboard.tags(s)
    val copiedFrom = TagClipboard.from(s)
    var replace by remember { mutableStateOf(false) }
    var postId by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun apply(incoming: List<String>, what: String) {
        val before = current.size
        val next = TagClipboard.merge(current, incoming, replace)
        onChange(next)
        status = if (replace) "Replaced with ${next.size} tags $what" else "Added ${next.size - before} tags $what"
    }

    fun fetch() {
        val id = postId.trim().removePrefix("#").toIntOrNull() ?: run { status = "Enter a post number"; return }
        focus.clearFocus()
        fetching = true
        status = null
        scope.launch {
            try {
                val p = Graph.api.post(id)
                p.tags.forEach { TagCategoryCache.learn(it.name, it.category) }
                apply(p.tags.map { it.name }, "from #$id")
                postId = ""
            } catch (e: Exception) {
                status = e.message ?: "Couldn't load post #$id"
            } finally {
                fetching = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { TagClipboard.copy(context, current, fromPostId) }, enabled = current.isNotEmpty()) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy tags", maxLines = 1)
            }
            TextButton(onClick = { apply(copied, copiedFrom?.let { "from #$it" } ?: "") }, enabled = enabled && copied.isNotEmpty()) {
                Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (copied.isEmpty()) "Paste tags"
                    else "Paste ${copied.size}" + (copiedFrom?.let { " from #$it" } ?: ""),
                    maxLines = 1,
                )
            }
        }
        if (allowFetch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = postId,
                    onValueChange = { v -> postId = v.filter { it.isDigit() }.take(9) },
                    label = { Text("From post #") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { fetch() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { fetch() }, enabled = enabled && !fetching && postId.isNotBlank()) {
                    if (fetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Add", maxLines = 1)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(replace, { replace = it }, enabled = enabled)
            Text("Replace existing tags", style = MaterialTheme.typography.bodyMedium)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink.TextDim) }
    }
}
