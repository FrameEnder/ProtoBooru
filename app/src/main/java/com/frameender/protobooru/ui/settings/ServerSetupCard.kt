package com.frameender.protobooru.ui.settings

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.frameender.protobooru.BuildConfigInfo
import com.frameender.protobooru.data.AppSettings
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.GridStyle
import com.frameender.protobooru.data.SearchHistory
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.post.NoteTextMode
import com.frameender.protobooru.ui.theme.Accents
import com.frameender.protobooru.ui.theme.Ink
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Server address entry with a live connection test. Used by Settings and first-run setup. */
@Composable
fun ServerSetupCard(onSaved: () -> Unit = {}) {
    val s by Graph.settings.collectAsState()
    val info by Graph.info.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var apiPath by remember(s.apiPath) { mutableStateOf(s.apiPath) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(12.dp), color = Ink.Surface, border = BorderStroke(1.dp, Ink.Line)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(info?.config?.name ?: "Szurubooru server", style = MaterialTheme.typography.titleMedium)
                    if (info != null && s.configured) {
                        Text("${Format.count(info!!.postCount)} posts · ${Format.bytes(info!!.diskUsage)}", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim(); status = null },
                label = { Text("Server URL") },
                placeholder = { Text("http://100.x.y.z:8390") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiPath,
                onValueChange = { apiPath = it.trim(); status = null },
                label = { Text("API path or URL") },
                supportingText = { Text("Default /api. Use a full URL if your API is hosted elsewhere.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    if (ok) Icon(Icons.Default.CheckCircle, null, tint = Ink.Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(status!!, color = if (ok) Ink.Green else Ink.Red, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !busy && url.isNotBlank(),
                onClick = {
                    busy = true
                    status = null
                    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
                    val candidate = AppSettings(serverUrl = normalized, apiPath = apiPath.ifBlank { "/api" })
                    scope.launch {
                        try {
                            val i = Graph.api.testServer(candidate)
                            ok = true
                            status = "Connected to ${i.config.name ?: "server"} · ${i.postCount} posts"
                            val serverChanged = normalized.trimEnd('/') != s.root
                            Graph.updateSettings {
                                val base = it.copy(serverUrl = normalized, apiPath = candidate.apiPath)
                                // Tokens belong to a server; drop them when switching servers.
                                if (serverChanged) base.copy(username = "", token = "") else base
                            }
                            Graph.refreshServerState()
                            onSaved()
                        } catch (e: Exception) {
                            ok = false
                            status = e.message ?: "Could not connect"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test & save")
            }
        }
    }
}
