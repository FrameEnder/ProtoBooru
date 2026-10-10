package com.frameender.protobooru.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.HomeLayouts
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.settings.ServerSetupCard
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeNav(
    val search: (String) -> Unit,
    val openPost: (Int) -> Unit,
    val openTag: (String) -> Unit,
    val openPool: (Int) -> Unit,
    val tags: () -> Unit,
    val pools: () -> Unit,
    val comments: () -> Unit,
    val users: () -> Unit,
    val history: () -> Unit,
    val imageSearch: () -> Unit,
    val settings: () -> Unit,
    val upload: () -> Unit,
    val updates: () -> Unit,
    val customize: () -> Unit,
)

@Composable
fun HomeScreen(nav: HomeNav, vm: HomeViewModel = viewModel()) {
    val settings by Graph.settings.collectAsState()
    val info by Graph.info.collectAsState()
    val serverError by Graph.serverError.collectAsState()
    val serverDown by Graph.offline.collectAsState()
    val offlineMode = serverDown || settings.forceOffline
    val update by Graph.updater.available.collectAsState()
    val layout = remember(settings.homeLayout) { HomeLayouts.decode(settings.homeLayout) }
    val visible = remember(layout) { layout.filter { it.enabled } }

    val reachable by Graph.serverReachable.collectAsState()
    val libraryVersion by Graph.library.version.collectAsState()
    val savedCount by produceState(0, libraryVersion, offlineMode, settings.safetyTerm, settings.blacklistTags, settings.blacklistMode) {
        value = withContext(Dispatchers.Default) { Graph.library.visible(settings).size }
    }

    // Reloads when the server, account or filters change, and when going offline or back
    // online (offline, every widget and number shows only what's saved on the phone).
    LaunchedEffect(settings.root, settings.token, settings.safetyTerm, settings.blacklistTags, settings.blacklistMode, offlineMode, layout) {
        if (settings.configured) {
            Graph.refreshServerState()
            vm.load(layout)
        }
    }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(info?.config?.name ?: "ProtoBooru")
                        if (settings.configured) {
                            Text(
                                settings.root.removePrefix("http://").removePrefix("https://") +
                                    (if (settings.loggedIn) " · ${settings.username}" else " · anonymous"),
                                style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                            )
                        }
                    }
                },
                actions = {
                    if (settings.configured) {
                        IconButton(onClick = { vm.load(layout, force = true) }) { Icon(Icons.Default.Refresh, "Refresh") }
                        IconButton(onClick = nav.customize) { Icon(Icons.Default.Dashboard, "Customize Home") }
                    }
                    IconButton(onClick = nav.settings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        if (!settings.configured) {
            Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
                Text("Welcome", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Point ProtoBooru at your Szurubooru instance to get started.",
                    style = MaterialTheme.typography.bodyLarge, color = Ink.TextDim,
                )
                Spacer(Modifier.height(20.dp))
                ServerSetupCard()
            }
            return@Scaffold
        }

        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            update?.let { u ->
                item(key = "update") {
                    Surface(
                        onClick = nav.updates,
                        shape = RoundedCornerShape(10.dp),
                        color = Ink.Amber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Ink.Amber.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null, tint = Ink.Amber)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Update available", style = MaterialTheme.typography.titleSmall)
                                Text("${u.title} · build ${u.versionCode} · tap to install", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                            }
                        }
                    }
                }
            }
            if (offlineMode) {
                item(key = "offline") {
                    Surface(
                        onClick = { nav.search("") },
                        shape = RoundedCornerShape(10.dp),
                        color = Ink.Teal.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Ink.Teal.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = Ink.Teal)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (settings.forceOffline) "Offline mode is on" else "You're offline",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "$savedCount posts saved on this phone · tap to browse them",
                                    style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                                )
                                if (settings.forceOffline && reachable) {
                                    Text(
                                        "Your server is reachable",
                                        style = MaterialTheme.typography.labelSmall, color = Ink.Teal,
                                    )
                                }
                            }
                            if (settings.forceOffline && reachable) {
                                TextButton(onClick = Graph::goOnline) { Text("Go online", color = Ink.Teal) }
                            }
                        }
                    }
                }
            }
            if (serverError != null && !offlineMode) {
                item(key = "server-error") {
                    Surface(shape = RoundedCornerShape(10.dp), color = Ink.Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Ink.Red.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, null, tint = Ink.Red)
                            Spacer(Modifier.width(10.dp))
                            Text("Can't reach the server: $serverError", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            items(visible, key = { it.id }) { w ->
                Column(Modifier.padding(top = 8.dp)) {
                    HomeWidgetView(w, vm, nav, settings, info)
                }
            }

            if (visible.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your Home is empty", style = MaterialTheme.typography.titleMedium)
                        Text("Add some widgets to fill it up.", color = Ink.TextDim)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = nav.customize) {
                            Icon(Icons.Default.Dashboard, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Customize Home")
                        }
                    }
                }
            }
        }
    }
}
