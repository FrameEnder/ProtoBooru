package com.frameender.protobooru.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.theme.Ink

/**
 * Asks how much of a search or pool to keep for offline viewing, then starts the save.
 * [total] is how many posts the search has (null if unknown).
 */
@Composable
fun SaveOfflineDialog(
    label: String,
    query: String,
    total: Int?,
    poolId: Int = 0,
    onDismiss: () -> Unit,
) {
    val options = listOf(50, 150, 500, 1000).let { o -> if (total != null) o.filter { it < total } + total else o }.distinct()
    var max by remember { mutableStateOf(options.firstOrNull { it >= 150 } ?: options.last()) }
    var full by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DownloadForOffline, null, tint = Ink.Amber) },
        title = { Text("Save for offline") },
        text = {
            Column {
                Text(
                    "Keeps “$label” on this phone so it still opens when the server can't be reached.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("How many posts", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { n ->
                        FilterChip(max == n, { max = n }, { Text(if (n == total) "All ${Format.count(n)}" else Format.count(n)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(full, { full = it })
                    Text("Include full-size images", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (full) "Uses more space. Videos are skipped, only their thumbnails are kept."
                    else "Thumbnails and post details only: quick and small.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Graph.offlineSaver.save(label, query, max, full, poolId)
                onDismiss()
            }) { Text("Save", maxLines = 1) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", maxLines = 1) } },
        containerColor = Ink.Surface2,
    )
}
