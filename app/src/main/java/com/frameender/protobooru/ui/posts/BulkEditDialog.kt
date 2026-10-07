package com.frameender.protobooru.ui.posts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.BulkOps
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Pool
import com.frameender.protobooru.ui.common.PoolPickerDialog
import com.frameender.protobooru.ui.common.SafetySelector
import com.frameender.protobooru.ui.common.TagCopyBar
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.theme.Ink

/** Edit many selected posts at once: tags, safety, source, relations, pool. */
@Composable
fun BulkEditDialog(count: Int, onDismiss: () -> Unit, onApply: (BulkOps) -> Unit) {
    var add by remember { mutableStateOf<List<String>>(emptyList()) }
    var remove by remember { mutableStateOf<List<String>>(emptyList()) }
    var safety by remember { mutableStateOf<String?>(null) }
    var setSource by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("") }
    var link by remember { mutableStateOf(false) }
    var pool by remember { mutableStateOf<Pool?>(null) }
    var pickPool by remember { mutableStateOf(false) }

    val nothing = add.isEmpty() && remove.isEmpty() && safety == null && !setSource && !link && pool == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $count posts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (Graph.can("posts:edit:tags")) {
                    Label("Add tags")
                    TagEditor(add, { add = it }, label = "Tags to add")
                    TagCopyBar(add, { add = it })
                    Spacer(Modifier.height(10.dp))
                    Label("Remove tags")
                    TagEditor(remove, { remove = it }, label = "Tags to remove")
                    Spacer(Modifier.height(10.dp))
                }
                if (Graph.can("posts:edit:safety")) {
                    Label("Safety")
                    SafetySelector(safety, { safety = it }, allowUnchanged = true)
                    Spacer(Modifier.height(6.dp))
                }
                if (Graph.can("posts:edit:source")) {
                    CheckLine("Set source", setSource) { setSource = it }
                    if (setSource) OutlinedTextField(source, { source = it }, label = { Text("Source") }, modifier = Modifier.fillMaxWidth())
                }
                if (Graph.can("posts:edit:relations") && count > 1) {
                    CheckLine("Link all selected posts as related", link) { link = it }
                }
                if (Graph.can("pools:edit:posts")) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { pickPool = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(pool?.let { "Add to pool: ${it.name.replace('_', ' ')}" } ?: "Add to a pool…")
                    }
                }
                Text(
                    "Changes are applied one post at a time in the background.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !nothing, onClick = {
                onApply(
                    BulkOps(
                        addTags = add,
                        removeTags = remove,
                        safety = safety,
                        source = if (setSource) source else null,
                        linkRelated = link,
                        addToPoolId = pool?.id,
                    ),
                )
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )

    if (pickPool) PoolPickerDialog(onDismiss = { pickPool = false }) { pool = it }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
}

@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
